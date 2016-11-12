package io.gobelieve.voip_demo;

import android.content.Context;
import android.media.AudioManager;
import android.os.Bundle;
import android.view.Window;
import android.view.WindowManager;



/**
 * Created by houxh on 15/9/8.
 */
public class VOIPVoiceActivity extends VOIPActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        setContentView(R.layout.activity_voip_voice);
        super.onCreate(savedInstanceState);
    }

    protected void dial() {
        this.voipSession.dial();
    }

    protected void startStream() {
        super.startStream();



        AudioManager audioManager = (AudioManager)getSystemService(Context.AUDIO_SERVICE);
        audioManager.setSpeakerphoneOn(false);
        audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
    }

    protected void stopStream() {
        super.stopStream();

    }

    @Override
    protected void onDestroy () {

        super.onDestroy();
    }

}
