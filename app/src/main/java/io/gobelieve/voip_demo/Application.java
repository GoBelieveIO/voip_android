package io.gobelieve.voip_demo;

import com.beetle.NativeWebRtcContextRegistry;

/**
 * Created by houxh on 15/9/3.
 */
public class Application extends android.app.Application {
    @Override
    public void onCreate() {
        super.onCreate();

        //初始化webrtc 只能调用一次
        new NativeWebRtcContextRegistry().register(getApplicationContext());
    }
}
