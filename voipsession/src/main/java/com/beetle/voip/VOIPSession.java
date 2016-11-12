package com.beetle.voip;


import android.util.Log;
import com.beetle.im.Timer;
import com.beetle.im.VOIPControl;
import com.beetle.im.VOIPObserver;


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

    private long currentUID;
    private long peerUID;

    private int dialCount;
    private long dialBeginTimestamp;
    private Timer dialTimer;

    private Timer acceptTimer;

    private Timer refuseTimer;

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
    }

    public void setObserver(VOIPSessionObserver ob) {
        this.observer = ob;
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
        state = VOIPSession.VOIP_REFUSING;

        this.refuseTimer = new Timer() {
            @Override
            protected void fire() {
                VOIPSession.this.observer.onRefuseFinshed();
            }
        };
        this.refuseTimer.setTimer(uptimeMillis()+1000*10);
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


                state = VOIPSession.VOIP_CONNECTED;

                observer.onConnected();
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
            }
        } else if (state == VOIPSession.VOIP_REFUSING) {
            if (command.cmd == VOIPCommand.VOIP_COMMAND_REFUSED) {
                this.refuseTimer.suspend();
                this.refuseTimer = null;

                Log.i(TAG, "refuse finished");
                state = VOIPSession.VOIP_REFUSED;
                observer.onRefuseFinshed();
            } else if (command.cmd == VOIPCommand.VOIP_COMMAND_DIAL ||
                    command.cmd == VOIPCommand.VOIP_COMMAND_DIAL_VIDEO) {
                this.sendDialRefuse();
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

        boolean r = VOIPService.getInstance().sendVOIPControl(ctl);
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
        sendControlCommand(VOIPCommand.VOIP_COMMAND_REFUSED);
    }

    private void sendConnected() {
        VOIPControl ctl = new VOIPControl();
        ctl.sender = currentUID;
        ctl.receiver = peerUID;

        VOIPCommand command = new VOIPCommand();

        command.cmd = VOIPCommand.VOIP_COMMAND_CONNECTED;


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
        ctl.content = command.getContent();

        VOIPService.getInstance().sendVOIPControl(ctl);
    }

    private void sendDialRefuse() {
        sendControlCommand(VOIPCommand.VOIP_COMMAND_REFUSE);
    }

    private void sendHangUp() {
        sendControlCommand(VOIPCommand.VOIP_COMMAND_HANG_UP);
    }



}
