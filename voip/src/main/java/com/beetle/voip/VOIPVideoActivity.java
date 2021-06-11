package com.beetle.voip;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
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
import com.google.mediapipe.framework.AndroidAssetUtil;
import com.google.mediapipe.glutil.EglManager;
import com.squareup.picasso.Picasso;

import org.webrtc.EglBase;
import org.webrtc.RendererCommon;
import org.webrtc.SurfaceViewRenderer;

import java.util.UUID;



import static android.os.SystemClock.uptimeMillis;


/**
 * Created by houxh on 15/9/8.
 */
public class VOIPVideoActivity extends CallActivity  {
    private static final int PERMISSIONS_REQUEST_CAMERA = 1;
    private static final int PERMISSIONS_REQUEST_RECORD_AUDIO = 2;
    private static final int PERMISSIONS_REQUEST_MEDIA = 3;

    // Local preview screen position before call is connected.
    private static final int LOCAL_X_CONNECTING = 0;
    private static final int LOCAL_Y_CONNECTING = 0;
    private static final int LOCAL_WIDTH_CONNECTING = 100;
    private static final int LOCAL_HEIGHT_CONNECTING = 100;
    // Local preview screen position after call is connected.
    private static final int LOCAL_X_CONNECTED = 72;
    private static final int LOCAL_Y_CONNECTED = 72;
    private static final int LOCAL_WIDTH_CONNECTED = 25;
    private static final int LOCAL_HEIGHT_CONNECTED = 25;
    // Remote video screen position
    private static final int REMOTE_X = 0;
    private static final int REMOTE_Y = 0;
    private static final int REMOTE_WIDTH = 100;
    private static final int REMOTE_HEIGHT = 100;


    protected String peerName;
    protected String peerAvatar;


    protected int duration;
    protected Timer durationTimer;

    protected Button handUpButton;
    protected ImageButton refuseButton;
    protected ImageButton acceptButton;

    protected TextView durationTextView;

    protected PercentFrameLayout localRenderLayout;
    protected PercentFrameLayout remoteRenderLayout;
    protected RendererCommon.ScalingType scalingType;

    protected ImageView  headView;
    private View controlView;


    private MusicIntentReceiver headsetReceiver;
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

        setContentView(R.layout.activity_voip_video);

        getIntent().putExtra(EXTRA_VIDEO_CALL, true);

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

        peerAvatar = intent.getStringExtra("peer_avatar");
        if (peerAvatar == null) {
            peerAvatar = "";
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
        if (TextUtils.isEmpty(channelID)) {
            Log.e(TAG, "channel id is empty");
            return;
        }
        Log.i(TAG, "channel id:" + channelID);

        long appid = APPID;
        long uid = this.currentUID;

        turnUserName = String.format("%d_%d", appid, uid);
        turnPassword = token;

        super.onCreate(savedInstanceState);

        sHandler = new Handler();
        sHandler.post(mHideRunnable);
        final View decorView = getWindow().getDecorView();
        View.OnSystemUiVisibilityChangeListener sl = new View.OnSystemUiVisibilityChangeListener() {
            @Override
            public void onSystemUiVisibilityChange(int visibility) {
                sHandler.post(mHideRunnable);
            }
        };
        decorView.setOnSystemUiVisibilityChangeListener(sl);

        controlView = findViewById(R.id.control);
        handUpButton = (Button) findViewById(R.id.hang_up);
        acceptButton = (ImageButton)findViewById(R.id.accept);
        refuseButton = (ImageButton)findViewById(R.id.refuse);
        durationTextView = (TextView)findViewById(R.id.duration);

        headView = (ImageView) findViewById(R.id.header);

        if (!TextUtils.isEmpty(peerAvatar)) {
            Picasso.get()
                    .load(peerAvatar)
                    .placeholder(R.drawable.avatar_contact)
                    .into(headView);
        }


        scalingType = RendererCommon.ScalingType.SCALE_ASPECT_FILL;

        // Create UI controls.
        localRender = (SurfaceViewRenderer) findViewById(R.id.local_video_view);
        remoteRender = (SurfaceViewRenderer) findViewById(R.id.remote_video_view);
        remoteFrameRender = (FrameViewRenderer)findViewById(R.id.remote_frame_video_view);
        localRenderLayout = (PercentFrameLayout) findViewById(R.id.local_video_layout);
        remoteRenderLayout = (PercentFrameLayout) findViewById(R.id.remote_video_layout);

        // Show/hide call control fragment on view click.
        View.OnClickListener listener = new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                VOIPVideoActivity.this.showOrHideHangUp();
            }
        };

        localRender.setOnClickListener(listener);
        remoteRender.setOnClickListener(listener);
        remoteFrameRender.setOnClickListener(listener);


        AndroidAssetUtil.initializeNativeAssetManager(this.getApplicationContext());

        // Create video renderers.
        eglManager = new EglManager(null);
        eglManager.getNativeContext();
        rootEglBase = EglBase.createEgl14(eglManager.getEgl14Context(), EglBase.CONFIG_PLAIN);
        //rootEglBase = EglBase.create();
        localRender.init(rootEglBase.getEglBaseContext(), null);
        remoteRender.init(rootEglBase.getEglBaseContext(), null);
        remoteFrameRender.init(getApplicationContext(), eglManager);
        localRender.setZOrderMediaOverlay(true);

        if (EXTERNAL_RENDERER) {
            remoteFrameRender.setVisibility(View.VISIBLE);
            remoteRender.setVisibility(View.GONE);
        } else {
            remoteFrameRender.setVisibility(View.GONE);
            remoteRender.setVisibility(View.VISIBLE);
        }

        updateVideoView();



        headsetReceiver = new MusicIntentReceiver();

        if (isCaller) {
            handUpButton.setVisibility(View.VISIBLE);
            acceptButton.setVisibility(View.GONE);
            refuseButton.setVisibility(View.GONE);
        } else {
            handUpButton.setVisibility(View.GONE);
            acceptButton.setVisibility(View.VISIBLE);
            refuseButton.setVisibility(View.VISIBLE);
        }

        requestPermission();
        if (isCaller) {
            this.dialVideo();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSIONS_REQUEST_CAMERA) {
            if (grantResults.length > 0) {
                Log.i(TAG, "camera permission:" + grantResults[0]);
            }
        } else if (requestCode == PERMISSIONS_REQUEST_RECORD_AUDIO) {
            if (grantResults.length > 0) {
                Log.i(TAG, "record audio permission:" + grantResults[0]);
            }
        } else if (requestCode == PERMISSIONS_REQUEST_MEDIA) {
            for (int i = 0; i < grantResults.length; i++) {
                Log.i(TAG, "media permission:" +  permissions[i] + " result:" + grantResults[0]);
            }
        }
    }

    private void requestPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            int cameraPermission = (checkSelfPermission(Manifest.permission.CAMERA));
            int recordPermission = (checkSelfPermission(Manifest.permission.RECORD_AUDIO));

            if (cameraPermission != PackageManager.PERMISSION_GRANTED &&
                    recordPermission != PackageManager.PERMISSION_GRANTED) {
                try {
                    this.requestPermissions(new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO},
                            PERMISSIONS_REQUEST_MEDIA);
                } catch (IllegalStateException e) {
                    e.printStackTrace();
                }
            }
            if (cameraPermission != PackageManager.PERMISSION_GRANTED) {
                try {
                    this.requestPermissions(new String[]{Manifest.permission.CAMERA}, PERMISSIONS_REQUEST_CAMERA);
                } catch (IllegalStateException e) {
                    e.printStackTrace();
                }
            }

            if (recordPermission != PackageManager.PERMISSION_GRANTED) {
                try {
                    this.requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, PERMISSIONS_REQUEST_RECORD_AUDIO);
                } catch (IllegalStateException e) {
                    e.printStackTrace();
                }
            }
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
    protected void startStream() {
        super.startStream();

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        AudioManager am = (AudioManager)getSystemService(AUDIO_SERVICE);
        am.setSpeakerphoneOn(true);
        am.setMode(AudioManager.MODE_IN_COMMUNICATION);

        this.duration = 0;
        this.durationTimer = new Timer() {
            @Override
            protected void fire() {
                VOIPVideoActivity.this.duration += 1;
                String text = String.format("%02d:%02d", VOIPVideoActivity.this.duration/60, VOIPVideoActivity.this.duration%60);
                durationTextView.setText(text);
            }
        };
        this.durationTimer.setTimer(uptimeMillis()+1000, 1000);
        this.durationTimer.resume();
    }

    @Override
    protected void stopStream() {
        super.stopStream();
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        this.durationTimer.suspend();
        this.durationTimer = null;
    }

    @Override
    public void onConnected() {
        super.onConnected();

        localRender.setVisibility(View.VISIBLE);
        remoteRender.setVisibility(View.VISIBLE);
        this.handUpButton.setVisibility(View.VISIBLE);
        this.acceptButton.setVisibility(View.GONE);
        this.refuseButton.setVisibility(View.GONE);
        this.headView.setVisibility(View.GONE);

        showOrHideHangUp();
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

    protected void showOrHideHangUp() {
        if (controlView.getVisibility() == View.VISIBLE) {
            hideHangUp();
        } else {
            showHangUp();
        }
    }
    private void hideHangUp() {
        controlView.setAlpha(1.0f);
        controlView.animate()
                .alpha(0.0f)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        super.onAnimationEnd(animation);
                        controlView.setVisibility(View.GONE);
                    }
                });
    }

    private void showHangUp() {
        controlView.setVisibility(View.VISIBLE);
        controlView.setAlpha(0.0f);
        controlView.animate()
                .alpha(1.0f)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        super.onAnimationEnd(animation);
                        controlView.setVisibility(View.VISIBLE);
                        controlView.setAlpha(1.0f);
                    }
                });

    }

    @Override
    protected void onDestroy () {
        super.onDestroy();
    }


    protected void updateVideoView() {
        remoteRenderLayout.setPosition(REMOTE_X, REMOTE_Y, REMOTE_WIDTH, REMOTE_HEIGHT);
        remoteRender.setScalingType(scalingType);
        remoteRender.setMirror(false);

        if (iceConnected) {
            localRenderLayout.setPosition(
                    LOCAL_X_CONNECTED, LOCAL_Y_CONNECTED, LOCAL_WIDTH_CONNECTED, LOCAL_HEIGHT_CONNECTED);
            localRender.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT);
        } else {
            localRenderLayout.setPosition(
                    LOCAL_X_CONNECTING, LOCAL_Y_CONNECTING, LOCAL_WIDTH_CONNECTING, LOCAL_HEIGHT_CONNECTING);
            localRender.setScalingType(scalingType);
        }
        localRender.setMirror(true);

        localRender.requestLayout();
        remoteRender.requestLayout();
    }

    @Override
    public void onResume() {
        IntentFilter filter = new IntentFilter(Intent.ACTION_HEADSET_PLUG);
        registerReceiver(headsetReceiver, filter);
        super.onResume();
    }
    @Override
    public void onPause() {
        unregisterReceiver(headsetReceiver);
        super.onPause();
    }

    private class MusicIntentReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(Intent.ACTION_HEADSET_PLUG)) {
                AudioManager audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
                int state = intent.getIntExtra("state", -1);
                switch (state) {
                    case 0:
                        Log.d(TAG, "Headset is unplugged");
                        audioManager.setSpeakerphoneOn(true);
                        break;
                    case 1:
                        Log.d(TAG, "Headset is plugged");
                        audioManager.setSpeakerphoneOn(false);
                        break;
                    default:
                        Log.d(TAG, "I have no idea what the headset state is");
                }
            }
        }
    }
}
