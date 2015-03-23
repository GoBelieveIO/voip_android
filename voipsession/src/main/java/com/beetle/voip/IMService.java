package com.beetle.voip;


import android.os.AsyncTask;
import android.util.Log;
import com.beetle.AsyncTCP;
import com.beetle.TCPConnectCallback;
import com.beetle.TCPReadCallback;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Date;


import static android.os.SystemClock.uptimeMillis;

/**
 * Created by houxh on 14-7-21.
 */
public class IMService {

    public enum ConnectState {
        STATE_UNCONNECTED,
        STATE_CONNECTING,
        STATE_CONNECTED,
        STATE_CONNECTFAIL,
    }

    private final String TAG = "imservice";
    private final int HEARTBEAT = 60*3;
    private AsyncTCP tcp;
    private boolean stopped = true;
    private Timer connectTimer;
    private Timer heartbeatTimer;
    private int pingTimestamp = 0;
    private int connectFailCount = 0;
    private int seq = 0;
    private ConnectState connectState = ConnectState.STATE_UNCONNECTED;

    private String hostIP;
    private int timestamp;

    private String host;
    private int port;
    private long uid;

    ArrayList<IMServiceObserver> observers = new ArrayList<IMServiceObserver>();
    ArrayList<VOIPObserver> voipObservers = new ArrayList<VOIPObserver>();

    private byte[] data;

    private static IMService im = new IMService();

    public static IMService getInstance() {
        return im;
    }

    public IMService() {
        connectTimer = new Timer() {
            @Override
            protected void fire() {
                IMService.this.connect();
            }
        };

        heartbeatTimer = new Timer() {
            @Override
            protected void fire() {
                int n = now();
                if (pingTimestamp > 0 && n - pingTimestamp > 60) {
                    Log.i(TAG, "ping timeout");
                    IMService.this.connectState = ConnectState.STATE_UNCONNECTED;
                    IMService.this.publishConnectState();
                    IMService.this.close();
                } else {
                    IMService.this.sendPing();
                }
            }
        };
    }

    public ConnectState getConnectState() {
        return connectState;
    }
    public void setHost(String host) {
        this.host = host;
    }
    public void setPort(int port) {
        this.port = port;
    }
    public void setUid(long uid) {
        this.uid = uid;
    }

    public String getHostIP() {
        return this.hostIP;
    }


    public void addObserver(IMServiceObserver ob) {
        if (observers.contains(ob)) {
            return;
        }
        observers.add(ob);
    }

    public void removeObserver(IMServiceObserver ob) {
        observers.remove(ob);
    }

    public void pushVOIPObserver(VOIPObserver ob) {
        if (voipObservers.contains(ob)) {
            return;
        }
        voipObservers.add(ob);
    }

    public void popVOIPObserver(VOIPObserver ob) {
        voipObservers.remove(ob);
    }

    public void start() {
        if (this.uid == 0) {
            throw new RuntimeException("NO UID PROVIDED");
        }

        if (!this.stopped) {
            Log.i(TAG, "already started");
            return;
        }
        this.stopped = false;
        connectTimer.setTimer(uptimeMillis());
        connectTimer.resume();

        heartbeatTimer.setTimer(uptimeMillis(), HEARTBEAT*1000);
        heartbeatTimer.resume();
    }

    public void stop() {
        if (this.stopped) {
            Log.i(TAG, "already stopped");
            return;
        }
        stopped = true;
        heartbeatTimer.suspend();
        connectTimer.suspend();
        this.close();
    }

    public boolean sendVOIPControl(VOIPControl ctl) {
        Message msg = new Message();
        msg.cmd = Command.MSG_VOIP_CONTROL;
        msg.body = ctl;
        return sendMessage(msg);
    }

    private void close() {
        if (this.tcp != null) {
            Log.i(TAG, "close tcp");
            this.tcp.close();
            this.tcp = null;
        }
        if (this.stopped) {
            return;
        }

        Log.d(TAG, "start connect timer");

        long t;
        if (this.connectFailCount > 60) {
            t = uptimeMillis() + 60*1000;
        } else {
            t = uptimeMillis() + this.connectFailCount*1000;
        }
        connectTimer.setTimer(t);
    }

    public static int now() {
        Date date = new Date();
        long t = date.getTime();
        return (int)(t/1000);
    }

    private void refreshHost() {
        new AsyncTask<Void, Integer, String>() {
            @Override
            protected String doInBackground(Void... urls) {
                return lookupHost(IMService.this.host);
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
                if (result.length() > 0) {
                    IMService.this.hostIP = result;
                    IMService.this.timestamp = now();
                }
            }
        }.execute();
    }

    private void connect() {
        if (this.tcp != null) {
            return;
        }
        if (this.stopped) {
            Log.e(TAG, "opps....");
            return;
        }

        if (hostIP == null || hostIP.length() == 0) {
            refreshHost();
            IMService.this.connectFailCount++;
            Log.i(TAG, "host ip is't resolved");

            long t;
            if (this.connectFailCount > 60) {
                t = uptimeMillis() + 60*1000;
            } else {
                t = uptimeMillis() + this.connectFailCount*1000;
            }
            connectTimer.setTimer(t);
            return;
        }

        if (now() - timestamp > 5*60) {
            refreshHost();
        }

        this.connectState = ConnectState.STATE_CONNECTING;
        IMService.this.publishConnectState();
        this.tcp = new AsyncTCP();
        Log.i(TAG, "new tcp...");

        this.tcp.setConnectCallback(new TCPConnectCallback() {
            @Override
            public void onConnect(Object tcp, int status) {
                if (status != 0) {
                    Log.i(TAG, "connect err:" + status);
                    IMService.this.connectFailCount++;
                    IMService.this.connectState = ConnectState.STATE_CONNECTFAIL;
                    IMService.this.publishConnectState();
                    IMService.this.close();
                } else {
                    Log.i(TAG, "tcp connected");
                    IMService.this.connectFailCount = 0;
                    IMService.this.connectState = ConnectState.STATE_CONNECTED;
                    IMService.this.publishConnectState();
                    IMService.this.sendAuth();
                    IMService.this.tcp.startRead();
                }
            }
        });

        this.tcp.setReadCallback(new TCPReadCallback() {
            @Override
            public void onRead(Object tcp, byte[] data) {
                if (data.length == 0) {
                    IMService.this.connectState = ConnectState.STATE_UNCONNECTED;
                    IMService.this.publishConnectState();
                    IMService.this.handleClose();
                } else {
                    boolean b = IMService.this.handleData(data);
                    if (!b) {
                        IMService.this.connectState = ConnectState.STATE_UNCONNECTED;
                        IMService.this.publishConnectState();
                        IMService.this.handleClose();
                    }
                }
            }
        });

        boolean r = this.tcp.connect(this.hostIP, this.port);
        Log.i(TAG, "tcp connect:" + r);
        if (!r) {
            this.tcp = null;
            IMService.this.connectFailCount++;
            IMService.this.connectState = ConnectState.STATE_CONNECTFAIL;
            publishConnectState();
            Log.d(TAG, "start connect timer");

            long t;
            if (this.connectFailCount > 60) {
                t = uptimeMillis() + 60*1000;
            } else {
                t = uptimeMillis() + this.connectFailCount*1000;
            }
            connectTimer.setTimer(t);
        }
    }

    private void handleAuthStatus(Message msg) {
        Integer status = (Integer)msg.body;
        Log.d(TAG, "auth status:" + status);
    }



    private void handleClose() {
        close();
    }

    private void handleACK(Message msg) {

    }



    private void handleVOIPControl(Message msg) {
        VOIPControl ctl = (VOIPControl)msg.body;

        int count = voipObservers.size();
        if (count == 0) {
            return;
        }
        VOIPObserver ob = voipObservers.get(count-1);
        ob.onVOIPControl(ctl);
    }



    private void handlePong() {
        Log.i(TAG, "pong");
        pingTimestamp = 0;
    }

    private void handleMessage(Message msg) {
        if (msg.cmd == Command.MSG_AUTH_STATUS) {
            handleAuthStatus(msg);
        } else if (msg.cmd == Command.MSG_ACK) {
            handleACK(msg);
        } else if (msg.cmd == Command.MSG_VOIP_CONTROL) {
            handleVOIPControl(msg);
        } else if (msg.cmd == Command.MSG_PONG) {
            handlePong();
        } else {
            Log.i(TAG, "unknown message cmd:"+msg.cmd);
        }
    }

    private void appendData(byte[] data) {
        if (this.data != null) {
            int l = this.data.length + data.length;
            byte[] buf = new byte[l];
            System.arraycopy(this.data, 0, buf, 0, this.data.length);
            System.arraycopy(data, 0, buf, this.data.length, data.length);
            this.data = buf;
        } else {
            this.data = data;
        }
    }

    private boolean handleData(byte[] data) {
        appendData(data);

        int pos = 0;
        while (true) {
            if (this.data.length < pos + 4) {
                break;
            }
            int len = BytePacket.readInt32(this.data, pos);
            if (this.data.length < pos + 4 + Message.HEAD_SIZE + len) {
                break;
            }
            Message msg = new Message();
            byte[] buf = new byte[Message.HEAD_SIZE + len];
            System.arraycopy(this.data, pos+4, buf, 0, Message.HEAD_SIZE+len);
            if (!msg.unpack(buf)) {
                Log.i(TAG, "unpack message error");
                return false;
            }
            handleMessage(msg);
            pos += 4 + Message.HEAD_SIZE + len;
        }

        byte[] left = new byte[this.data.length - pos];
        System.arraycopy(this.data, pos, left, 0, left.length);
        this.data = left;
        return true;
    }

    private void sendAuth() {
        Message msg = new Message();
        msg.cmd = Command.MSG_AUTH;
        msg.body = new Long(this.uid);
        sendMessage(msg);
    }

    private void sendPing() {
        Log.i(TAG, "send ping");
        Message msg = new Message();
        msg.cmd = Command.MSG_PING;
        sendMessage(msg);
        if (connectState == ConnectState.STATE_CONNECTED) {
            pingTimestamp = now();
        } else {
            pingTimestamp = 0;
        }
    }

    private boolean sendMessage(Message msg) {
        if (this.tcp == null || connectState != ConnectState.STATE_CONNECTED) return false;
        this.seq++;
        msg.seq = this.seq;
        byte[] p = msg.pack();
        int l = p.length - Message.HEAD_SIZE;
        byte[] buf = new byte[p.length + 4];
        BytePacket.writeInt32(l, buf, 0);
        System.arraycopy(p, 0, buf, 4, p.length);
        this.tcp.writeData(buf);
        return true;
    }


    private void publishConnectState() {
        for (int i = 0; i < observers.size(); i++ ) {
            IMServiceObserver ob = observers.get(i);
            ob.onConnectState(connectState);
        }
    }
}
