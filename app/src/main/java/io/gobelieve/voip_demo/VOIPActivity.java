package io.gobelieve.voip_demo;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Handler;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import com.beetle.VOIP;
import com.beetle.voip.VOIPSession;

import com.beetle.voip.BytePacket;
import com.beetle.voip.IMService;
import com.beetle.voip.Timer;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Date;


import static android.os.SystemClock.uptimeMillis;

public class VOIPActivity extends Activity implements VOIPSession.VOIPSessionObserver {

    private static final String TAG = "face";

    private boolean isCaller;

    private long currentUID;
    private long peerUID;
    private String peerName;


    private Button handUpButton;
    private ImageButton refuseButton;
    private ImageButton acceptButton;

    private TextView durationTextView;

    private VOIP voip;
    private int duration;
    private Timer durationTimer;

    private MediaPlayer player;

    private static Handler sHandler;


    private VOIPSession voipSession;
    private boolean isConnected;

    private boolean isP2P() {
        if (this.voipSession.localNatMap == null || this.voipSession.peerNatMap == null) {
            return false;
        }
        if (this.voipSession.localNatMap.ip != 0 && this.voipSession.peerNatMap.ip != 0) {
            return true;
        }
        return  false;
    }

    Runnable mHideRunnable = new Runnable() {
        @Override
        public void run() {
            int flags;
            int curApiVersion = android.os.Build.VERSION.SDK_INT;
            // This work only for android 4.4+
            if (curApiVersion >= Build.VERSION_CODES.KITKAT) {
                // This work only for android 4.4+
                // hide navigation bar permanently in android activity
                // touch the screen, the navigation bar will not show
                flags = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE
                        | View.SYSTEM_UI_FLAG_FULLSCREEN;

            } else {
                // touch the screen, the navigation bar will show
                flags = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
            }

            // must be executed in main thread :)
            getWindow().getDecorView().setSystemUiVisibility(flags);
        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        setContentView(R.layout.activity_voip);

        sHandler = new Handler();
        sHandler.post(mHideRunnable);
        final View decorView = getWindow().getDecorView();
        View.OnSystemUiVisibilityChangeListener sl = new View.OnSystemUiVisibilityChangeListener() {
            @Override
            public void onSystemUiVisibilityChange(int visibility)
            {
                sHandler.post(mHideRunnable);
            }
        };
        decorView.setOnSystemUiVisibilityChangeListener(sl);


        handUpButton = (Button)findViewById(R.id.hang_up);
        acceptButton = (ImageButton)findViewById(R.id.accept);
        refuseButton = (ImageButton)findViewById(R.id.refuse);
        durationTextView = (TextView)findViewById(R.id.duration);

        ImageView header = (ImageView)findViewById(R.id.header);


        Intent intent = getIntent();


        isCaller = intent.getBooleanExtra("is_caller", false);
        peerUID = intent.getLongExtra("peer_uid", 0);

        if (peerUID == 0) {
            Log.e(TAG, "peer uid is 0");
            return;
        }

        peerName = intent.getStringExtra("peer_name");

        if (peerName == null) {
            peerName = "";
        }

        currentUID = intent.getLongExtra("current_uid", 0);

        if (currentUID == 0) {
            Log.e(TAG, "peer uid is 0");
            return;
        }



        header.setImageResource(R.drawable.avatar_contact);


        voipSession = new VOIPSession(currentUID, peerUID);
        voipSession.setObserver(this);
        voipSession.holePunch();

        IMService.getInstance().pushVOIPObserver(this.voipSession);

        if (isCaller) {
            handUpButton.setVisibility(View.VISIBLE);
            acceptButton.setVisibility(View.GONE);
            refuseButton.setVisibility(View.GONE);

            voipSession.dial();


            try {
                AssetFileDescriptor afd = getResources().openRawResourceFd(R.raw.call);
                player = new MediaPlayer();
                player.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
                afd.close();
                player.setLooping(true);
                player.setAudioStreamType(AudioManager.STREAM_MUSIC);

                AudioManager am = (AudioManager)getSystemService(Context.AUDIO_SERVICE);
                am.setSpeakerphoneOn(false);
                am.setMode(AudioManager.STREAM_MUSIC);
                player.prepare();
                player.start();

            } catch (Exception e) {
                e.printStackTrace();
            }

        } else {
            handUpButton.setVisibility(View.GONE);
            acceptButton.setVisibility(View.VISIBLE);
            refuseButton.setVisibility(View.VISIBLE);

            try {
                AssetFileDescriptor afd = getResources().openRawResourceFd(R.raw.start);
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

    }



    @Override
    protected void onDestroy () {
        if (this.voip != null) {
            Log.e(TAG, "voip is not null");
            System.exit(1);
        }

        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_voip, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if(keyCode == KeyEvent.KEYCODE_BACK) {
            Log.i(TAG, "keycode back");
            hangup(null);
        }
        return super.onKeyDown(keyCode, event);
    }

    public void hangup(View v) {
        Log.i(TAG, "hangup...");
        voipSession.hangup();
        if (isConnected) {
            stopStream();
            dismiss();
        } else {
            this.player.stop();
            this.player = null;
            dismiss();
        }
    }

    public void accept(View v) {
        Log.i(TAG, "accepting...");
        voipSession.accept();
        this.player.stop();
        this.player = null;

        this.acceptButton.setEnabled(false);
        this.refuseButton.setEnabled(false);
    }

    public void refuse(View v) {
        Log.i(TAG, "refusing...");

        voipSession.refuse();

        this.player.stop();
        this.player = null;
        this.acceptButton.setEnabled(false);
        this.refuseButton.setEnabled(false);
    }

    private void dismiss() {
        IMService.getInstance().stop();
        finish();
    }



    private boolean getHeadphoneStatus() {
        AudioManager audioManager = (AudioManager)getSystemService(AUDIO_SERVICE);
        boolean headphone = audioManager.isWiredHeadsetOn() || audioManager.isBluetoothA2dpOn();
        return headphone;
    }

    private void startStream() {
        if (this.voip != null) {
            Log.w(TAG, "voip is active");
            return;
        }

        try {
            if (this.voipSession.localNatMap != null && this.voipSession.localNatMap.ip != 0) {
                String ip = InetAddress.getByAddress(BytePacket.unpackInetAddress(this.voipSession.localNatMap.ip)).getHostAddress();
                int port = this.voipSession.localNatMap.port;
                Log.i(TAG, "local nat map:" + ip + ":" + port);
            }
            if (this.voipSession.peerNatMap != null && this.voipSession.peerNatMap.ip != 0) {
                String ip = InetAddress.getByAddress(BytePacket.unpackInetAddress(this.voipSession.peerNatMap.ip)).getHostAddress();
                int port = this.voipSession.peerNatMap.port;
                Log.i(TAG, "peer nat map:" + ip + ":" + port);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (isP2P()) {

            Log.i(TAG, "start p2p stream");
        } else {
            Log.i(TAG, "start stream");
        }

        AudioManager am = (AudioManager)getSystemService(Context.AUDIO_SERVICE);
        am.setSpeakerphoneOn(false);
        am.setMode(AudioManager.MODE_IN_COMMUNICATION);
        this.duration = 0;
        this.durationTimer = new Timer() {
            @Override
            protected void fire() {
                VOIPActivity.this.duration += 1;
                String text = String.format("%02d:%02d", VOIPActivity.this.duration/60, VOIPActivity.this.duration%60);
                Log.i(TAG, "ddd:" + text);
                durationTextView.setText(text);
            }
        };
        this.durationTimer.setTimer(uptimeMillis()+1000, 1000);
        this.durationTimer.resume();

        this.voip = new VOIP();
        long selfUID = currentUID;
        String relayIP = this.voipSession.getRelayIP();
        Log.i(TAG, "relay ip:" + relayIP);
        boolean headphone = getHeadphoneStatus();
        String peerIP = "";
        int peerPort = 0;
        try {
            if (isP2P()) {
                peerIP = InetAddress.getByAddress(BytePacket.unpackInetAddress(this.voipSession.peerNatMap.ip)).getHostAddress();
                peerPort = this.voipSession.peerNatMap.port;
                Log.i(TAG, "peer ip:" + peerIP + " port:" + peerPort);
            }
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
        this.voip.initNative(selfUID, peerUID, relayIP, peerIP, peerPort, headphone);

        this.voip.start();
    }

    private void stopStream() {
        if (this.voip == null) {
            Log.w(TAG, "voip is inactive");
            return;
        }
        Log.i(TAG, "stop stream");
        this.durationTimer.suspend();
        this.durationTimer = null;
        this.voip.stop();
        this.voip.destroyNative();
        this.voip = null;
    }

    public static int getNow() {
        Date date = new Date();
        long t = date.getTime();
        return (int)(t/1000);
    }

    //对方拒绝接听
    @Override
    public void onRefuse() {
        this.player.stop();
        this.player = null;

        dismiss();
    }
    //对方挂断通话
    @Override
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
    @Override
    public void onTalking() {
        this.player.stop();
        this.player = null;
        dismiss();
    }

    @Override
    public void onDialTimeout() {
        this.player.stop();
        this.player = null;
        dismiss();
    }
    @Override
    public void onAcceptTimeout() {
        dismiss();
    }
    @Override
    public void onConnected() {
        if (this.player != null) {
            this.player.stop();
            this.player = null;
        }


        Log.i(TAG, "voip connected");
        startStream();

        this.handUpButton.setVisibility(View.VISIBLE);
        this.acceptButton.setVisibility(View.GONE);
        this.refuseButton.setVisibility(View.GONE);

        this.isConnected = true;

    }
    @Override
    public void onRefuseFinshed() {
        dismiss();
    }
}
