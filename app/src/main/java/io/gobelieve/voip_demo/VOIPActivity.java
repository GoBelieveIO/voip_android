package io.gobelieve.voip_demo;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.content.res.Configuration;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.opengl.GLSurfaceView;
import android.os.Build;
import android.os.Handler;
import android.os.Bundle;
import android.text.TextUtils;
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

import com.beetle.VOIPEngine;
import com.beetle.voip.VOIPService;
import com.beetle.voip.VOIPSession;

import com.beetle.voip.BytePacket;
import com.beetle.voip.Timer;

import org.webrtc.RendererCommon;
import org.webrtc.VideoRenderer;
import org.webrtc.VideoRendererGui;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Date;


import static android.os.SystemClock.uptimeMillis;

public class VOIPActivity extends Activity implements VOIPSession.VOIPSessionObserver {

    protected static final String TAG = "face";

    protected boolean isCaller;

    protected long currentUID;
    protected long peerUID;
    protected String peerName;

    protected String token;


    private Button handUpButton;
    private ImageButton refuseButton;
    private ImageButton acceptButton;

    private TextView durationTextView;

    protected VOIPEngine voip;
    private int duration;
    private Timer durationTimer;

    private MediaPlayer player;

    private static Handler sHandler;


    protected VOIPSession voipSession;
    private boolean isConnected;




    protected boolean isP2P() {
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
        Log.i(TAG, "voip activity on create");
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

        token = intent.getStringExtra("token");
        if (TextUtils.isEmpty(token)) {
            Log.e(TAG, "token is empty");
            return;
        }






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


        voipSession = new VOIPSession(currentUID, peerUID);
        voipSession.setObserver(this);
        voipSession.holePunch();

        VOIPService.getInstance().pushVOIPObserver(this.voipSession);

        if (isCaller) {
            handUpButton.setVisibility(View.VISIBLE);
            acceptButton.setVisibility(View.GONE);
            refuseButton.setVisibility(View.GONE);

            dial();

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
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        // Checks the orientation of the screen
        if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            Log.i(TAG, "landscape");
        } else if (newConfig.orientation == Configuration.ORIENTATION_PORTRAIT){
            Log.i(TAG, "portrait");
        }
    }

    @Override
    protected void onDestroy () {
        Log.i(TAG, "voip activity on destroy");

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
        VOIPService.getInstance().stop();
        finish();
    }



    private boolean getHeadphoneStatus() {
        AudioManager audioManager = (AudioManager)getSystemService(AUDIO_SERVICE);
        boolean headphone = audioManager.isWiredHeadsetOn() || audioManager.isBluetoothA2dpOn();
        return headphone;
    }

    protected void dial() {

    }

    protected void startStream() {
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
    }

    protected void stopStream() {
        this.durationTimer.suspend();
        this.durationTimer = null;
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
