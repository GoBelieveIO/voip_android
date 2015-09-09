package io.gobelieve.voip_demo;

import android.content.Context;
import android.media.AudioManager;
import android.os.Bundle;
import android.util.Log;
import android.view.Window;
import android.view.WindowManager;

import com.beetle.VOIPEngine;
import com.beetle.voip.BytePacket;
import com.beetle.voip.VOIPSession;

import java.net.InetAddress;
import java.net.UnknownHostException;

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

        long selfUID = currentUID;
        String relayIP = this.voipSession.getRelayIP();
        if (relayIP == null) {
            relayIP = "121.42.143.50";
        }
        Log.i(TAG, "relay ip:" + relayIP);
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

        this.voip = new VOIPEngine(this.isCaller, token, selfUID, peerUID, relayIP, VOIPSession.VOIP_PORT,
                peerIP, peerPort);
        this.voip.initNative();
        this.voip.start();

        AudioManager audioManager = (AudioManager)getSystemService(Context.AUDIO_SERVICE);
        audioManager.setSpeakerphoneOn(false);
    }

    protected void stopStream() {
        super.stopStream();
        if (this.voip == null) {
            Log.w(TAG, "voip is inactive");
            return;
        }
        Log.i(TAG, "stop stream");
        this.voip.stop();
        this.voip.destroyNative();
        this.voip = null;
    }

    @Override
    protected void onDestroy () {
        if (this.voip != null) {
            Log.e(TAG, "voip is not null");
            System.exit(1);
        }
        super.onDestroy();
    }

}
