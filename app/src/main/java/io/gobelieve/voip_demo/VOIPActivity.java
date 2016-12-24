package io.gobelieve.voip_demo;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.util.Log;
import com.beetle.im.IMService;
import com.beetle.im.RTMessage;
import com.beetle.im.RTMessageObserver;
import com.beetle.im.Timer;

import org.json.JSONException;
import org.json.JSONObject;
import java.util.Date;
import static android.os.SystemClock.uptimeMillis;

public class VOIPActivity extends WebRTCActivity implements RTMessageObserver  {
    protected static final String TAG = "face";
    protected static final long APPID = 7;

    public static long activityCount = 0;

    protected String channelID;
    protected long currentUID;
    protected long peerUID;
    protected MediaPlayer player;
    protected boolean isConnected;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "voip activity on create");
        activityCount++;

        IMService.getInstance().addRTObserver(this);

        if (isCaller) {
            playOutgoingCall();
        } else {
            playIncomingCall();
        }
    }


    @Override
    protected void onDestroy () {
        Log.i(TAG, "voip activity on destroy");
        IMService.getInstance().removeRTObserver(this);
        activityCount--;

        if (this.player != null) {
            this.player.stop();
            this.player = null;
        }

        close();
        super.onDestroy();
    }


    protected void dismiss() {
        setResult(RESULT_OK);
        finish();
    }

    private boolean getHeadphoneStatus() {
        AudioManager audioManager = (AudioManager)getSystemService(AUDIO_SERVICE);
        boolean headphone = audioManager.isWiredHeadsetOn() || audioManager.isBluetoothA2dpOn();
        return headphone;
    }

    protected void playResource(int resID) {
        try {
            AssetFileDescriptor afd = getResources().openRawResourceFd(resID);
            player = new MediaPlayer();
            player.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
            afd.close();
            player.setLooping(true);
            player.setAudioStreamType(AudioManager.STREAM_MUSIC);
            AudioManager am = (AudioManager)getSystemService(Context.AUDIO_SERVICE);
            am.setSpeakerphoneOn(true);
            am.setMode(AudioManager.STREAM_MUSIC);
            player.prepare();
            player.start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    protected void playOutgoingCall() {
        playResource(R.raw.call);
    }

    protected void playIncomingCall() {
        playResource(R.raw.start);
    }

    protected void startStream() {
        super.startStream();
    }

    protected void stopStream() {
        super.stopStream();
    }

    public static int getNow() {
        Date date = new Date();
        long t = date.getTime();
        return (int)(t/1000);
    }

    //对方拒绝接听
    public void onRefuse() {
        this.player.stop();
        this.player = null;

        dismiss();
    }

    //对方挂断通话
    public void onHangUp() {
        if (this.isConnected) {
            stopStream();
            dismiss();
        } else {
            this.player.stop();
            this.player = null;
            dismiss();
        }
    }
    //呼叫对方时，对方正在通话
    public void onTalking() {
        this.player.stop();
        this.player = null;
        dismiss();
    }

    public void onDialTimeout() {
        this.player.stop();
        this.player = null;
        dismiss();
    }

    public void onAcceptTimeout() {
        dismiss();
    }

    public void onConnected() {
        if (this.player != null) {
            this.player.stop();
            this.player = null;
        }

        Log.i(TAG, "voip connected");
        startStream();
        this.isConnected = true;
    }

    public void onDisconnect() {
        stopStream();
        dismiss();
    }

    @Override
    protected void sendP2PMessage(JSONObject json) {
        RTMessage rt = new RTMessage();
        rt.sender = this.currentUID;
        rt.receiver = this.peerUID;

        try {
            JSONObject obj = new JSONObject();
            obj.put("p2p", json);
            rt.content = obj.toString();
            Log.i(TAG, "send rt message:" + rt.content);
            IMService.getInstance().sendRTMessage(rt);
        } catch (JSONException e ) {
            e.printStackTrace();
        }
    }

    @Override
    public void onRTMessage(RTMessage rt) {
        if (rt.sender != peerUID) {
            return;
        }

        try {
            JSONObject json = new JSONObject(rt.content);
            if (json.has("p2p")) {
                JSONObject obj = json.getJSONObject("p2p");
                Log.i(TAG, "recv p2p message:" + rt.content);
                processP2PMessage(obj);
            } else if (json.has("voip")) {
                JSONObject obj = json.getJSONObject("voip");
                Log.i(TAG, "recv voip message:" + rt.content);
                processVOIPMessage(rt.sender, obj);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }

    }


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



    private int dialBeginTimestamp;
    private Timer dialTimer;

    private Timer acceptTimer;

    private Timer pingTimer;

    //上一次收到对方发来的ping的时间戳
    private int lastPingTimestamp;

    private int state;
    private int mode;


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
                VOIPActivity.this.sendPing();
                //检查自己上一次收到的对方发来的ping的时间
                int now = getNow();
                if (now - lastPingTimestamp > 10) {
                    VOIPActivity.this.onDisconnect();
                }
            }
        };
        this.pingTimer.setTimer(uptimeMillis()+100, 1000);
        this.pingTimer.resume();
    }
    public void dialVoice() {
        state = VOIP_DIALING;
        mode = SESSION_VOICE;
        this.dialBeginTimestamp = getNow();

        sendDial();

        this.dialTimer = new Timer() {
            @Override
            protected void fire() {
                VOIPActivity.this.sendDial();
            }
        };
        this.dialTimer.setTimer(uptimeMillis()+1000, 1000);
        this.dialTimer.resume();

    }

    public void dialVideo() {
        state = VOIP_DIALING;
        mode = SESSION_VIDEO;
        this.dialBeginTimestamp = getNow();

        sendDial();

        this.dialTimer = new Timer() {
            @Override
            protected void fire() {
                VOIPActivity.this.sendDial();
            }
        };
        this.dialTimer.setTimer(uptimeMillis()+1000, 1000);
        this.dialTimer.resume();
    }

    public void accept() {
        Log.i(TAG, "accepting...");

        state = VOIP_ACCEPTED;

        this.acceptTimer = new Timer() {
            @Override
            protected void fire() {
                VOIPActivity.this.onAcceptTimeout();
            }
        };
        this.acceptTimer.setTimer(uptimeMillis()+1000*10);
        this.acceptTimer.resume();

        sendDialAccept();
    }

    public void refuse() {
        Log.i(TAG, "refusing...");
        state = VOIP_REFUSED;
        sendDialRefuse();
    }

    public void hangup() {
        Log.i(TAG, "hangup...");
        if (state == VOIP_DIALING) {
            this.dialTimer.suspend();
            this.dialTimer = null;

            sendHangUp();
            state = VOIP_HANGED_UP;
        } else if (state == VOIP_CONNECTED) {
            sendHangUp();
            state = VOIP_HANGED_UP;

        } else {
            Log.i(TAG, "invalid voip state:" + state);
        }
    }


    public void processVOIPMessage(long sender, JSONObject obj) {
        if (sender != peerUID) {
            sendTalking(sender);
            return;
        }

        VOIPCommand command = new VOIPCommand(obj);
        Log.i(TAG, "state:" + state + " command:" + command.cmd);
        if (state == VOIP_DIALING) {
            if (command.cmd == VOIPCommand.VOIP_COMMAND_ACCEPT) {
                sendConnected();
                state = VOIP_CONNECTED;

                this.dialTimer.suspend();
                this.dialTimer = null;

                Log.i(TAG, "voip connected");
                onConnected();
                this.ping();
            } else if (command.cmd == VOIPCommand.VOIP_COMMAND_REFUSE) {
                state = VOIP_REFUSED;
                sendRefused();

                this.dialTimer.suspend();
                this.dialTimer = null;

                this.onRefuse();
            } else if (command.cmd == VOIPCommand.VOIP_COMMAND_TALKING) {
                state = VOIP_SHUTDOWN;

                this.dialTimer.suspend();
                this.dialTimer = null;
                this.onTalking();
            }
        } else if (state == VOIP_ACCEPTING) {
            if (command.cmd == VOIPCommand.VOIP_COMMAND_HANG_UP) {
                state = VOIP_HANGED_UP;
                onHangUp();
            }
        } else if (state == VOIP_ACCEPTED) {
            if (command.cmd == VOIPCommand.VOIP_COMMAND_CONNECTED) {
                this.acceptTimer.suspend();
                this.acceptTimer = null;
                state = VOIP_CONNECTED;
                onConnected();
                this.ping();

            } else if (command.cmd == VOIPCommand.VOIP_COMMAND_HANG_UP) {
                this.acceptTimer.suspend();
                this.acceptTimer = null;
                state = VOIP_HANGED_UP;
                onHangUp();
            } else if (command.cmd == VOIPCommand.VOIP_COMMAND_DIAL ||
                    command.cmd == VOIPCommand.VOIP_COMMAND_DIAL_VIDEO) {
                this.sendDialAccept();
            }
        } else if (state == VOIP_CONNECTED) {
            if (command.cmd == VOIPCommand.VOIP_COMMAND_HANG_UP) {
                state = VOIP_HANGED_UP;
                onHangUp();
            } else if (command.cmd == VOIPCommand.VOIP_COMMAND_ACCEPT) {
                sendConnected();
            } else if (command.cmd == VOIPCommand.VOIP_COMMAND_PING) {
                lastPingTimestamp = getNow();
            }
        }
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

            this.onDialTimeout();
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
