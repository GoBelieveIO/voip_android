package com.beetle.voip;

import android.os.Bundle;


/**
 * Created by houxh on 2016/12/27.
 * 保存通话记录
 */
public class CallActivity extends VOIPActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    protected void onDestroy () {
        super.onDestroy();
    }

    @Override
    public void dialVideo() {
        super.dialVideo();
    }

    @Override
    public void dialVoice() {
        super.dialVoice();
    }


    @Override
    public void hangup() {
        super.hangup();
    }

    @Override
    public void accept() {
        super.accept();
    }

    @Override
    public void refuse() {
        super.refuse();
    }

    @Override
    public void onRefuse() {
        super.onRefuse();
    }

    @Override
    public void onConnected() {
        super.onConnected();

    }

    @Override
    protected void startStream() {
        super.startStream();
    }

    @Override
    protected void stopStream() {
        super.stopStream();
    }

}
