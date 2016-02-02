package com.beetle.voip;

import android.os.AsyncTask;
import android.text.TextUtils;
import android.util.Log;

import com.beetle.voip.VOIPCommand;
import com.beetle.im.BytePacket;
import com.beetle.im.Timer;
import com.beetle.im.VOIPControl;
import com.beetle.im.VOIPObserver;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.Date;

import static android.os.SystemClock.uptimeMillis;

/**
 * Created by houxh on 15/3/12.
 */
public class VOIPSession implements VOIPObserver {


    private static final int VOIP_LISTENING = 0;
    private static final int VOIP_DIALING = 1;//呼叫对方
    private static final int VOIP_CONNECTED = 2;//通话连接成功
    private static final int VOIP_ACCEPTING = 3;//询问用户是否接听来电
    private static final int VOIP_ACCEPTED = 4;//用户接听来电
    private static final int VOIP_REFUSING = 5;//来电被拒
    private static final int VOIP_REFUSED = 6;//(来/去)电已被拒
    private static final int VOIP_HANGED_UP = 7;//通话被挂断
    private static final int VOIP_SHUTDOWN = 8;//对方正在通话中，连接被终止


    //session mode
    private static final int SESSION_VOICE = 0;
    private static final int SESSION_VIDEO = 1;

    private static final String TAG = "voip";

    public static final String STUN_SERVER = "stun.counterpath.net";
    public static String VOIP_HOST = "voipnode.gobelieve.io";
    public static final int VOIP_PORT = 20002;

    public static void setVOIPHost(String host) {
        VOIP_HOST = host;
    }

    private String voipHostIP;
    private String voipHost;
    private int voipPort;
    private String stunServer;
    private long currentUID;
    private long peerUID;

    private InetSocketAddress mappedAddr;
    private int natType = Stun.StunTypeUnknown;

    public VOIPCommand.NatPortMap localNatMap;
    public VOIPCommand.NatPortMap peerNatMap;

    private String relayIP;
    private boolean refreshing;

    private int dialCount;
    private long dialBeginTimestamp;
    private Timer dialTimer;

    private Timer acceptTimer;
    private long acceptTimestamp;
    private Timer refuseTimer;
    private long refuseTimestamp;


    private VOIPSessionObserver observer;

    private int state;
    private int mode;

    public static interface VOIPSessionObserver  {
        //对方拒绝接听
        public void onRefuse();
        //对方挂断通话
        public void onHangUp();

        //对方正在通话
        public void onTalking();

        public void onDialTimeout();
        public void onAcceptTimeout();
        public void onConnected();
        public void onRefuseFinshed();
    };


    public VOIPSession(long currentUID, long peerUID) {
        state = VOIP_ACCEPTING;
        this.currentUID = currentUID;
        this.peerUID = peerUID;
        this.voipHost = VOIP_HOST;
        this.voipPort = VOIP_PORT;
        this.stunServer = STUN_SERVER;
        this.refreshing = false;
    }

    //获取中转服务器IP地址
    public String getRelayIP() {
        return relayIP;
    }

    public void setObserver(VOIPSessionObserver ob) {
        this.observer = ob;
    }

    public void holePunch() {
        AsyncTask task = new AsyncTask<Object, Object, Boolean>() {

            private InetSocketAddress ma;
            private int natType;

            @Override
            protected Boolean doInBackground(Object[] params) {
                try {
                    Log.i(TAG, "discovery...");
                    Stun stun = new Stun();
                    int stype = stun.getNatType(STUN_SERVER);

                    Log.i(TAG, "nat type:" + stype);

                    if (stype == Stun.StunTypeOpen || stype == Stun.StunTypeIndependentFilter ||
                            stype == Stun.StunTypeDependentFilter || stype == Stun.StunTypePortDependedFilter) {
                        int count = 0;
                        while (count++ < 8) {
                            InetSocketAddress ma = stun.mapPort(STUN_SERVER, VOIP_PORT);
                            if (ma == null) {
                                continue;
                            }
                            Log.i(TAG, "mapped address:" + ma.getAddress().getHostAddress() + ":" + ma.getPort());
                            this.ma = ma;
                            this.natType = stype;
                            return true;
                        }
                        return false;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    System.out.println(e.getMessage());
                }
                return false;
            }

            @Override
            protected void onPostExecute(Boolean result) {
                if (!result) {
                    Log.i(TAG, "map port fail");
                    return;
                }

                VOIPSession.this.mappedAddr = this.ma;
                VOIPSession.this.natType = this.natType;

                if (VOIPSession.this.localNatMap != null) {
                    return;
                }

                if (natType == Stun.StunTypeOpen || natType == Stun.StunTypeIndependentFilter ||
                        natType == Stun.StunTypeDependentFilter || natType == Stun.StunTypePortDependedFilter) {
                    try {
                        VOIPCommand.NatPortMap natMap = new VOIPCommand.NatPortMap();
                        natMap.ip = BytePacket.packInetAddress(ma.getAddress().getAddress());
                        natMap.port = (short) ma.getPort();

                        VOIPSession.this.localNatMap = natMap;

                        byte[] addr = BytePacket.unpackInetAddress(natMap.ip);
                        InetAddress iaddr = InetAddress.getByAddress(addr);
                        Log.i(TAG, "address:" + iaddr.getHostAddress() + "--" + ma.getAddress().getHostAddress());
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        };
        task.execute();


        refreshHost();
    }

    private void refreshHost() {
        if (!TextUtils.isEmpty(this.voipHostIP) || this.refreshing) {
            return;
        }
        this.refreshing = true;
        //解析voip中专服务器的域名
        new AsyncTask<Void, Integer, String>() {
            @Override
            protected String doInBackground(Void... params) {
                for (int i = 0; i < 10; i++) {
                    String ip = lookupHost(VOIPSession.this.voipHost);
                    if (TextUtils.isEmpty(ip)) {
                        try {
                            Thread.sleep((long) (0.05 * 1000));
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        continue;
                    } else {
                        return ip;
                    }
                }
                return "";
            }

            private String lookupHost(String host) {
                try {
                    InetAddress inetAddress = InetAddress.getByName(host);
                    Log.i(TAG, "host name:" + inetAddress.getHostName() + " " + inetAddress.getHostAddress());
                    return inetAddress.getHostAddress();
                } catch (UnknownHostException exception) {
                    exception.printStackTrace();
                    return "";
                }
            }

            @Override
            protected void onPostExecute(String result) {
                if (!TextUtils.isEmpty(result)) {
                    VOIPSession.this.voipHostIP = result;
                }
                VOIPSession.this.refreshing = false;
            }
        }.execute();
    }

    public void dial() {
        state = VOIPSession.VOIP_DIALING;
        mode = SESSION_VOICE;
        this.dialBeginTimestamp = getNow();

        sendDial();

        this.dialTimer = new Timer() {
            @Override
            protected void fire() {
                VOIPSession.this.sendDial();
            }
        };
        this.dialTimer.setTimer(uptimeMillis()+1000, 1000);
        this.dialTimer.resume();

    }

    public void dialVideo() {
        state = VOIPSession.VOIP_DIALING;
        mode = SESSION_VIDEO;
        this.dialBeginTimestamp = getNow();

        sendDial();

        this.dialTimer = new Timer() {
            @Override
            protected void fire() {
                VOIPSession.this.sendDial();
            }
        };
        this.dialTimer.setTimer(uptimeMillis()+1000, 1000);
        this.dialTimer.resume();
    }

    public void accept() {
        Log.i(TAG, "accepting...");

        state = VOIPSession.VOIP_ACCEPTED;


        if (this.localNatMap == null) {
            this.localNatMap = new VOIPCommand.NatPortMap();
        }

        this.acceptTimer = new Timer() {
            @Override
            protected void fire() {
                VOIPSession.this.sendDialAccept();
            }
        };
        this.acceptTimer.setTimer(uptimeMillis()+1000, 1000);
        this.acceptTimer.resume();

        this.acceptTimestamp = getNow();
        sendDialAccept();

    }

    public void refuse() {
        Log.i(TAG, "refusing...");
        state = VOIPSession.VOIP_REFUSING;


        this.refuseTimestamp = getNow();
        this.refuseTimer = new Timer() {
            @Override
            protected void fire() {
                VOIPSession.this.sendDialRefuse();
            }
        };
        this.refuseTimer.setTimer(uptimeMillis()+1000, 1000);
        this.refuseTimer.resume();

        sendDialRefuse();
    }

    public void hangup() {
        Log.i(TAG, "hangup...");
        if (state == VOIPSession.VOIP_DIALING) {
            this.dialTimer.suspend();
            this.dialTimer = null;

            sendHangUp();
            state = VOIPSession.VOIP_HANGED_UP;
        } else if (state == VOIPSession.VOIP_CONNECTED) {
            sendHangUp();
            state = VOIPSession.VOIP_HANGED_UP;

        } else {
            Log.i(TAG, "invalid voip state:" + state);
        }
    }

    @Override
    public void onVOIPControl(VOIPControl ctl) {
        if (ctl.sender != peerUID) {
            sendTalking(ctl.sender);
            return;
        }

        VOIPCommand command = new VOIPCommand(ctl.content);

        Log.i(TAG, "state:" + state + " command:" + command.cmd);
        if (state == VOIPSession.VOIP_DIALING) {
            if (command.cmd == VOIPCommand.VOIP_COMMAND_ACCEPT) {
                this.peerNatMap = command.natMap;
                if (this.localNatMap == null) {
                    this.localNatMap = new VOIPCommand.NatPortMap();
                }
                if (this.relayIP == null) {
                    this.relayIP = this.voipHostIP;
                }
                sendConnected();
                state = VOIPSession.VOIP_CONNECTED;

                this.dialTimer.suspend();
                this.dialTimer = null;

                Log.i(TAG, "voip connected");
                observer.onConnected();


            } else if (command.cmd == VOIPCommand.VOIP_COMMAND_REFUSE) {
                state = VOIPSession.VOIP_REFUSED;
                sendRefused();


                this.dialTimer.suspend();
                this.dialTimer = null;

                this.observer.onRefuse();
            } else if (command.cmd == VOIPCommand.VOIP_COMMAND_TALKING) {
                state = VOIPSession.VOIP_SHUTDOWN;

                this.dialTimer.suspend();
                this.dialTimer = null;
                this.observer.onTalking();
            }
        } else if (state == VOIPSession.VOIP_ACCEPTING) {
            if (command.cmd == VOIPCommand.VOIP_COMMAND_HANG_UP) {
                state = VOIPSession.VOIP_HANGED_UP;
                observer.onHangUp();
            }
        } else if (state == VOIPSession.VOIP_ACCEPTED) {
            if (command.cmd == VOIPCommand.VOIP_COMMAND_CONNECTED) {
                this.acceptTimer.suspend();
                this.acceptTimer = null;

                this.peerNatMap = command.natMap;

                if (command.relayIP > 0) {
                    try {
                        this.relayIP = InetAddress.getByAddress(BytePacket.unpackInetAddress(command.relayIP)).getHostAddress();
                    } catch (Exception e) {
                        this.relayIP = this.voipHostIP;
                    }
                } else {
                    this.relayIP = this.voipHostIP;
                }

                state = VOIPSession.VOIP_CONNECTED;

                observer.onConnected();
            } else if (command.cmd == VOIPCommand.VOIP_COMMAND_HANG_UP) {
                this.acceptTimer.suspend();
                this.acceptTimer = null;

                state = VOIPSession.VOIP_HANGED_UP;

                observer.onHangUp();
            }
        } else if (state == VOIPSession.VOIP_CONNECTED) {
            if (command.cmd == VOIPCommand.VOIP_COMMAND_HANG_UP) {
                state = VOIPSession.VOIP_HANGED_UP;

                observer.onHangUp();

            } else if (command.cmd == VOIPCommand.VOIP_COMMAND_ACCEPT) {
                sendConnected();
            }
        } else if (state == VOIPSession.VOIP_REFUSING) {
            if (command.cmd == VOIPCommand.VOIP_COMMAND_REFUSED) {
                Log.i(TAG, "refuse finished");
                state = VOIPSession.VOIP_REFUSED;

                this.refuseTimer.suspend();
                this.refuseTimer = null;

                observer.onRefuseFinshed();
            }
        }
    }

    public static int getNow() {
        Date date = new Date();
        long t = date.getTime();
        return (int)(t/1000);
    }


    private void sendControlCommand(int cmd) {
        VOIPControl ctl = new VOIPControl();
        ctl.sender = currentUID;
        ctl.receiver = peerUID;

        VOIPCommand command = new VOIPCommand();
        command.cmd = cmd;
        ctl.content = command.getContent();
        VOIPService.getInstance().sendVOIPControl(ctl);
    }

    private void sendDial() {
        VOIPControl ctl = new VOIPControl();
        ctl.sender = currentUID;
        ctl.receiver = peerUID;

        VOIPCommand command = new VOIPCommand();
        if (mode == SESSION_VOICE) {
            command.cmd = VOIPCommand.VOIP_COMMAND_DIAL;
        } else if (mode == SESSION_VIDEO) {
            command.cmd = VOIPCommand.VOIP_COMMAND_DIAL_VIDEO;
        } else {
            assert(false);
        }
        command.dialCount = this.dialCount + 1;
        ctl.content = command.getContent();

        if (!TextUtils.isEmpty(this.voipHostIP)) {
            Log.i(TAG, "dial......");
            boolean r = VOIPService.getInstance().sendVOIPControl(ctl);
            if (r) {
                this.dialCount = this.dialCount + 1;
            } else {
                Log.i(TAG, "dial fail");
            }
        } else {
            Log.i(TAG, "voip host ip is empty");
            refreshHost();
        }

        long now = getNow();
        if (now - this.dialBeginTimestamp >= 60) {
            Log.i(TAG, "dial timeout");
            this.dialTimer.suspend();
            this.dialTimer = null;

            this.observer.onDialTimeout();
        }
    }

    private void sendRefused() {
        sendControlCommand(VOIPCommand.VOIP_COMMAND_REFUSED);
    }

    private void sendConnected() {
        VOIPControl ctl = new VOIPControl();
        ctl.sender = currentUID;
        ctl.receiver = peerUID;

        VOIPCommand command = new VOIPCommand();

        command.cmd = VOIPCommand.VOIP_COMMAND_CONNECTED;
        command.natMap = this.localNatMap;
        try {
            InetAddress[] addresses = InetAddress.getAllByName(this.relayIP);
            command.relayIP = BytePacket.packInetAddress(addresses[0].getAddress());
        } catch (Exception e) {
            command.relayIP = 0;
        }

        ctl.content = command.getContent();
        VOIPService.getInstance().sendVOIPControl(ctl);
    }

    private void sendTalking(long receiver) {
        VOIPControl ctl = new VOIPControl();
        ctl.sender = currentUID;
        ctl.receiver = receiver;
        VOIPCommand command = new VOIPCommand();
        command.cmd = VOIPCommand.VOIP_COMMAND_TALKING;
        ctl.content = command.getContent();
        VOIPService.getInstance().sendVOIPControl(ctl);
    }

    private void sendDialAccept() {
        VOIPControl ctl = new VOIPControl();
        ctl.sender = currentUID;
        ctl.receiver = peerUID;
        VOIPCommand command = new VOIPCommand();
        command.cmd = VOIPCommand.VOIP_COMMAND_ACCEPT;
        command.natMap = this.localNatMap;
        ctl.content = command.getContent();

        VOIPService.getInstance().sendVOIPControl(ctl);

        long now = getNow();
        if (now - this.acceptTimestamp >= 10) {
            Log.i(TAG, "accept timeout");
            this.acceptTimer.suspend();

            this.observer.onAcceptTimeout();
        }
    }

    private void sendDialRefuse() {
        sendControlCommand(VOIPCommand.VOIP_COMMAND_REFUSE);

        long now = getNow();
        if (now - this.refuseTimestamp > 10) {
            Log.i(TAG, "refuse timeout");
            this.refuseTimer.suspend();


            state = VOIPSession.VOIP_REFUSED;

            observer.onRefuseFinshed();
        }
    }

    private void sendHangUp() {
        sendControlCommand(VOIPCommand.VOIP_COMMAND_HANG_UP);
    }



}
