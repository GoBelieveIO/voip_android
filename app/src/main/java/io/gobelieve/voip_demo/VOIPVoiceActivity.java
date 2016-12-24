package io.gobelieve.voip_demo;

import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import com.beetle.im.Timer;

import org.webrtc.EglBase;

import java.util.UUID;

import static android.os.SystemClock.uptimeMillis;


/**
 * Created by houxh on 15/9/8.
 */
public class VOIPVoiceActivity extends VOIPActivity {

    protected Button handUpButton;
    protected ImageButton refuseButton;
    protected ImageButton acceptButton;

    protected TextView durationTextView;
    protected int duration;
    protected Timer durationTimer;

    protected String peerName;

    protected Handler sHandler;

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
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        setContentView(R.layout.activity_voip_voice);

        getIntent().putExtra(EXTRA_VIDEO_CALL, false);

        super.onCreate(savedInstanceState);

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

        String token = intent.getStringExtra("token");
        if (TextUtils.isEmpty(token)) {
            Log.e(TAG, "token is empty");
            return;
        }

        channelID = intent.getStringExtra("channel_id");
        if (channelID == null) {
            channelID = "";
        }
        Log.i(TAG, "channel id:" + channelID);

        long appid = APPID;
        long uid = this.currentUID;

        turnUserName = String.format("%d_%d", appid, uid);
        turnPassword = token;



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
        header.setImageResource(R.drawable.avatar_contact);

        // Create video renderers.
        rootEglBase = EglBase.create();
        if (isCaller) {
            if (TextUtils.isEmpty(this.channelID)) {
                this.channelID = UUID.randomUUID().toString();
            }
            dial();
        } else {
            waitAccept();
        }

    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if(keyCode == KeyEvent.KEYCODE_BACK) {
            Log.i(TAG, "keycode back");
            hangup(null);
        }
        return super.onKeyDown(keyCode, event);
    }

    protected void dial() {
        handUpButton.setVisibility(View.VISIBLE);
        acceptButton.setVisibility(View.GONE);
        refuseButton.setVisibility(View.GONE);
        this.dialVoice();
    }

    protected void waitAccept() {
        handUpButton.setVisibility(View.GONE);
        acceptButton.setVisibility(View.VISIBLE);
        refuseButton.setVisibility(View.VISIBLE);
    }

    @Override
    protected void startStream() {
        super.startStream();
        AudioManager am = (AudioManager)getSystemService(Context.AUDIO_SERVICE);
        am.setSpeakerphoneOn(false);
        am.setMode(AudioManager.MODE_IN_COMMUNICATION);

        this.duration = 0;
        this.durationTimer = new Timer() {
            @Override
            protected void fire() {
                VOIPVoiceActivity.this.duration += 1;
                String text = String.format("%02d:%02d", VOIPVoiceActivity.this.duration/60, VOIPVoiceActivity.this.duration%60);
                durationTextView.setText(text);
            }
        };
        this.durationTimer.setTimer(uptimeMillis()+1000, 1000);
        this.durationTimer.resume();
    }

    @Override
    protected void stopStream() {
        super.stopStream();
        this.durationTimer.suspend();
        this.durationTimer = null;
    }

    @Override
    public void onConnected() {
        super.onConnected();

        this.handUpButton.setVisibility(View.VISIBLE);
        this.acceptButton.setVisibility(View.GONE);
        this.refuseButton.setVisibility(View.GONE);
    }

    public void hangup(View v) {
        Log.i(TAG, "hangup...");
        hangup();
        if (isConnected) {
            stopStream();
        }
        dismiss();
    }

    public void accept(View v) {
        Log.i(TAG, "accepting...");

        accept();
        this.acceptButton.setEnabled(false);
        this.refuseButton.setEnabled(false);
    }

    public void refuse(View v) {
        Log.i(TAG, "refuse...");
        refuse();
        this.acceptButton.setEnabled(false);
        this.refuseButton.setEnabled(false);

        dismiss();
    }

    @Override
    protected void onDestroy () {
        super.onDestroy();
    }

}
