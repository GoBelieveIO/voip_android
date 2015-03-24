package com.beetle.voip;

import android.os.AsyncTask;
import android.util.Log;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Date;

import static android.os.SystemClock.uptimeMillis;

/**
 * Created by houxh on 15/3/12.
 */
public class VOIPSession implements VOIPObserver {


    public static final int VOIP_LISTENING = 0;
    public static final int VOIP_DIALING = 1;//呼叫对方
    public static final int VOIP_CONNECTED = 2;//通话连接成功
    public static final int VOIP_ACCEPTING = 3;//询问用户是否接听来电
    public static final int VOIP_ACCEPTED = 4;//用户接听来电
    public static final int VOIP_REFUSING = 5;//来电被拒
    public static final int VOIP_REFUSED = 6;//(来/去)电已被拒
    public static final int VOIP_HANGED_UP = 7;//通话被挂断
    public static final int VOIP_RESETED = 8;//通话连接被重置

    private static final String TAG = "voip";

    public static final String STUN_SERVER = "stun.counterpath.net";
    public static final int VOIP_PORT = 20001;


    private int voipPort;
    private String stunServer;
    private long currentUID;
    private long peerUID;

    private InetSocketAddress mappedAddr;
    private int natType = Stun.StunTypeUnknown;

    public VOIPControl.NatPortMap localNatMap;
    public VOIPControl.NatPortMap peerNatMap;


    private int dialCount;
    private long dialBeginTimestamp;
    private Timer dialTimer;

    private Timer acceptTimer;
    private long acceptTimestamp;
    private Timer refuseTimer;
    private long refuseTimestamp;


    private VOIPSessionObserver observer;

    public int state;


    public static interface VOIPSessionObserver  {
        //对方拒绝接听
        public void onRefuse();
        //对方挂断通话
        public void onHangUp();
        //回话被重置
        public void onReset();

        public void onDialTimeout();
        public void onAcceptTimeout();
        public void onConnected();
        public void onRefuseFinshed();
    };


    public VOIPSession(long currentUID, long peerUID) {
        state = VOIP_ACCEPTING;
        this.currentUID = currentUID;
        this.peerUID = peerUID;
        this.voipPort = VOIP_PORT;
        this.stunServer = STUN_SERVER;
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
                        VOIPControl.NatPortMap natMap = new VOIPControl.NatPortMap();
                        natMap.ip = BytePacket.packInetAddress(ma.getAddress().getAddress());
                        natMap.port = (short)ma.getPort();

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
    }

    public void dial() {
        state = VOIPSession.VOIP_DIALING;
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
            this.localNatMap = new VOIPControl.NatPortMap();
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
            sendTalking();
            return;
        }

        Log.i(TAG, "state:" + state + " command:" + ctl.cmd);
        if (state == VOIPSession.VOIP_DIALING) {
            if (ctl.cmd == VOIPControl.VOIP_COMMAND_ACCEPT) {
                this.peerNatMap = ctl.natMap;
                if (this.localNatMap == null) {
                    this.localNatMap = new VOIPControl.NatPortMap();
                }

                sendConnected();
                state = VOIPSession.VOIP_CONNECTED;

                this.dialTimer.suspend();
                this.dialTimer = null;

                Log.i(TAG, "voip connected");
                observer.onConnected();


            } else if (ctl.cmd == VOIPControl.VOIP_COMMAND_REFUSE) {
                state = VOIPSession.VOIP_REFUSED;
                sendRefused();


                this.dialTimer.suspend();
                this.dialTimer = null;

                this.observer.onRefuse();
            } else if (ctl.cmd == VOIPControl.VOIP_COMMAND_DIAL) {
                this.dialTimer.suspend();
                this.dialTimer = null;

                state = VOIPSession.VOIP_ACCEPTED;

                if (this.localNatMap == null) {
                    this.localNatMap = new VOIPControl.NatPortMap();
                }
                this.acceptTimestamp = getNow();
                this.acceptTimer = new Timer() {
                    @Override
                    protected void fire() {
                        VOIPSession.this.sendDialAccept();
                    }
                };
                this.acceptTimer.setTimer(uptimeMillis() + 1000, 1000);
                this.acceptTimer.resume();
                sendDialAccept();
            }
        } else if (state == VOIPSession.VOIP_ACCEPTING) {
            if (ctl.cmd == VOIPControl.VOIP_COMMAND_HANG_UP) {
                state = VOIPSession.VOIP_HANGED_UP;
                observer.onHangUp();
            }
        } else if (state == VOIPSession.VOIP_ACCEPTED) {
            if (ctl.cmd == VOIPControl.VOIP_COMMAND_CONNECTED) {
                this.acceptTimer.suspend();
                this.acceptTimer = null;

                this.peerNatMap = ctl.natMap;
                state = VOIPSession.VOIP_CONNECTED;

                observer.onConnected();
            } else if (ctl.cmd == VOIPControl.VOIP_COMMAND_ACCEPT) {
                Log.i(TAG, "simultaneous voip connected");
                this.acceptTimer.suspend();
                this.acceptTimer = null;

                this.peerNatMap = ctl.natMap;
                state = VOIPSession.VOIP_CONNECTED;

                observer.onConnected();
            } else if (ctl.cmd == VOIPControl.VOIP_COMMAND_HANG_UP) {
                this.acceptTimer.suspend();
                this.acceptTimer = null;

                state = VOIPSession.VOIP_HANGED_UP;

                observer.onHangUp();
            }
        } else if (state == VOIPSession.VOIP_CONNECTED) {
            if (ctl.cmd == VOIPControl.VOIP_COMMAND_HANG_UP) {
                state = VOIPSession.VOIP_HANGED_UP;

                observer.onHangUp();

            } else if (ctl.cmd == VOIPControl.VOIP_COMMAND_RESET) {
                state = VOIPSession.VOIP_RESETED;

                observer.onReset();
            } else if (ctl.cmd == VOIPControl.VOIP_COMMAND_ACCEPT) {
                sendConnected();
            }
        } else if (state == VOIPSession.VOIP_REFUSING) {
            if (ctl.cmd == VOIPControl.VOIP_COMMAND_REFUSED) {
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
        ctl.cmd = cmd;
        IMService.getInstance().sendVOIPControl(ctl);
    }

    private void sendDial() {
        VOIPControl ctl = new VOIPControl();
        ctl.sender = currentUID;
        ctl.receiver = peerUID;
        ctl.cmd = VOIPControl.VOIP_COMMAND_DIAL;
        ctl.dialCount = this.dialCount + 1;

        Log.i(TAG, "dial......");
        boolean r = IMService.getInstance().sendVOIPControl(ctl);
        if (r) {
            this.dialCount = this.dialCount + 1;
        } else {
            Log.i(TAG, "dial fail");
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
        sendControlCommand(VOIPControl.VOIP_COMMAND_REFUSED);
    }

    private void sendConnected() {
        VOIPControl ctl = new VOIPControl();
        ctl.sender = currentUID;
        ctl.receiver = peerUID;
        ctl.cmd = VOIPControl.VOIP_COMMAND_CONNECTED;
        ctl.natMap = this.localNatMap;
        IMService.getInstance().sendVOIPControl(ctl);
    }

    private void sendTalking() {
        sendControlCommand(VOIPControl.VOIP_COMMAND_TALKING);
    }

    private void sendReset() {
        sendControlCommand(VOIPControl.VOIP_COMMAND_RESET);
    }

    private void sendDialAccept() {
        VOIPControl ctl = new VOIPControl();
        ctl.sender = currentUID;
        ctl.receiver = peerUID;
        ctl.cmd = VOIPControl.VOIP_COMMAND_ACCEPT;
        ctl.natMap = this.localNatMap;
        IMService.getInstance().sendVOIPControl(ctl);

        long now = getNow();
        if (now - this.acceptTimestamp >= 10) {
            Log.i(TAG, "accept timeout");
            this.acceptTimer.suspend();

            this.observer.onAcceptTimeout();
        }
    }

    private void sendDialRefuse() {
        sendControlCommand(VOIPControl.VOIP_COMMAND_REFUSE);

        long now = getNow();
        if (now - this.refuseTimestamp > 10) {
            Log.i(TAG, "refuse timeout");
            this.refuseTimer.suspend();


            state = VOIPSession.VOIP_REFUSED;

            observer.onRefuseFinshed();
        }
    }

    private void sendHangUp() {
        sendControlCommand(VOIPControl.VOIP_COMMAND_HANG_UP);
    }



}
