package com.beetle.voip;


import android.util.Log;

import com.beetle.im.IMService;
import com.beetle.im.RTMessage;
import com.beetle.im.RTMessageObserver;
import com.beetle.im.Timer;
import com.beetle.im.VOIPControl;
import com.beetle.im.VOIPObserver;


import org.json.JSONException;
import org.json.JSONObject;

import java.util.Date;

import static android.os.SystemClock.uptimeMillis;

/**
 * Created by houxh on 15/3/12.
 */
public class VOIPSession implements RTMessageObserver {
    private static final int VOIP_LISTENING = 0;
    private static final int VOIP_DIALING = 1;//呼叫对方
    private static final int VOIP_CONNECTED = 2;//通话连接成功
    private static final int VOIP_ACCEPTING = 3;//询问用户是否接听来电
    private static final int VOIP_ACCEPTED = 4;//用户接听来电
    private static final int VOIP_REFUSED = 6;//(来/去)电已被拒
    private static final int VOIP_HANGED_UP = 7;//通话被挂断
    private static final int VOIP_SHUTDOWN = 8;//对方正在通话中，连接被终止


    //session mode
    private static final int SESSION_VOICE = 0;
    private static final int SESSION_VIDEO = 1;

    private static final String TAG = "voip";

    private String channelID;
    private long currentUID;
    private long peerUID;

    private int dialBeginTimestamp;
    private Timer dialTimer;

    private Timer acceptTimer;

    private Timer pingTimer;

    //上一次收到对方发来的ping的时间戳
    private int lastPingTimestamp;

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

        //对方异常断开
        public void onDisconnect();
    };


    public VOIPSession(long currentUID, long peerUID, String channelID) {
        state = VOIP_ACCEPTING;
        this.currentUID = currentUID;
        this.peerUID = peerUID;
        this.channelID = channelID;
    }

    public void setObserver(VOIPSessionObserver ob) {
        this.observer = ob;
    }

    public void setChannelID(String channelID) {
        this.channelID = channelID;
    }

    public void close() {
        if (this.dialTimer != null) {
            this.dialTimer.suspend();
            this.dialTimer = null;
        }
        if (this.acceptTimer != null) {
            this.acceptTimer.suspend();
            this.acceptTimer = null;
        }
        if (this.pingTimer != null) {
            this.pingTimer.suspend();
            this.pingTimer = null;
        }
    }


    public void ping() {
        lastPingTimestamp = getNow();
        this.pingTimer = new Timer() {
            @Override
            protected void fire() {
                VOIPSession.this.sendPing();
                //检查自己上一次收到的对方发来的ping的时间
                int now = getNow();
                if (now - lastPingTimestamp > 10) {
                    observer.onDisconnect();
                }
            }
        };
        this.pingTimer.setTimer(uptimeMillis()+100, 1000);
        this.pingTimer.resume();
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

        this.acceptTimer = new Timer() {
            @Override
            protected void fire() {
                VOIPSession.this.observer.onAcceptTimeout();
            }
        };
        this.acceptTimer.setTimer(uptimeMillis()+1000*10);
        this.acceptTimer.resume();

        sendDialAccept();
    }

    public void refuse() {
        Log.i(TAG, "refusing...");
        state = VOIPSession.VOIP_REFUSED;
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
    public void onRTMessage(RTMessage rt) {
        JSONObject obj = null;
        try {
            JSONObject json = new JSONObject(rt.content);
            obj = json.getJSONObject("voip");
        } catch (JSONException e) {
            e.printStackTrace();
            return;
        }

        if (rt.sender != peerUID) {
            sendTalking(rt.sender);
            return;
        }

        VOIPCommand command = new VOIPCommand(obj);
        Log.i(TAG, "state:" + state + " command:" + command.cmd);
        if (state == VOIPSession.VOIP_DIALING) {
            if (command.cmd == VOIPCommand.VOIP_COMMAND_ACCEPT) {
                sendConnected();
                state = VOIPSession.VOIP_CONNECTED;

                this.dialTimer.suspend();
                this.dialTimer = null;

                Log.i(TAG, "voip connected");
                observer.onConnected();
                this.ping();
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
                state = VOIPSession.VOIP_CONNECTED;
                observer.onConnected();
                this.ping();

            } else if (command.cmd == VOIPCommand.VOIP_COMMAND_HANG_UP) {
                this.acceptTimer.suspend();
                this.acceptTimer = null;
                state = VOIPSession.VOIP_HANGED_UP;
                observer.onHangUp();
            } else if (command.cmd == VOIPCommand.VOIP_COMMAND_DIAL ||
                    command.cmd == VOIPCommand.VOIP_COMMAND_DIAL_VIDEO) {
                this.sendDialAccept();
            }
        } else if (state == VOIPSession.VOIP_CONNECTED) {
            if (command.cmd == VOIPCommand.VOIP_COMMAND_HANG_UP) {
                state = VOIPSession.VOIP_HANGED_UP;
                observer.onHangUp();
            } else if (command.cmd == VOIPCommand.VOIP_COMMAND_ACCEPT) {
                sendConnected();
            } else if (command.cmd == VOIPCommand.VOIP_COMMAND_PING) {
                lastPingTimestamp = getNow();
            }
        }
    }

    public static int getNow() {
        Date date = new Date();
        long t = date.getTime();
        return (int)(t/1000);
    }


    private void sendControlCommand(int cmd) {
        RTMessage rt = new RTMessage();
        rt.sender = currentUID;
        rt.receiver = peerUID;

        try {
            VOIPCommand command = new VOIPCommand();
            command.cmd = cmd;
            command.channelID = this.channelID;
            JSONObject obj = command.getContent();
            if (obj == null) {
                return;
            }
            JSONObject json = new JSONObject();
            json.put("voip", obj);
            rt.content = json.toString();
            IMService.getInstance().sendRTMessage(rt);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void sendDial() {
        if (mode == SESSION_VOICE) {
            sendControlCommand(VOIPCommand.VOIP_COMMAND_DIAL);
        } else if (mode == SESSION_VIDEO) {
            sendControlCommand(VOIPCommand.VOIP_COMMAND_DIAL_VIDEO);
        } else {
            assert(false);
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
        sendControlCommand(VOIPCommand.VOIP_COMMAND_CONNECTED);
    }

    private void sendTalking(long receiver) {
        RTMessage rt = new RTMessage();
        rt.sender = currentUID;
        rt.receiver = receiver;

        try {
            VOIPCommand command = new VOIPCommand();
            command.cmd = VOIPCommand.VOIP_COMMAND_TALKING;
            command.channelID = this.channelID;
            JSONObject obj = command.getContent();
            if (obj == null) {
                return;
            }
            JSONObject json = new JSONObject();
            json.put("voip", obj);
            rt.content = json.toString();
            IMService.getInstance().sendRTMessage(rt);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void sendPing() {
        sendControlCommand(VOIPCommand.VOIP_COMMAND_PING);
    }

    private void sendDialAccept() {
        sendControlCommand(VOIPCommand.VOIP_COMMAND_ACCEPT);
    }

    private void sendDialRefuse() {
        sendControlCommand(VOIPCommand.VOIP_COMMAND_REFUSE);
    }

    private void sendHangUp() {
        sendControlCommand(VOIPCommand.VOIP_COMMAND_HANG_UP);
    }



}
