package io.gobelieve.voip_demo;

import android.os.AsyncTask;
import android.text.TextUtils;
import android.util.Log;

import com.beetle.NativeWebRtcContextRegistry;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Created by houxh on 15/9/3.
 */
public class Application extends android.app.Application {
    @Override
    public void onCreate() {
        super.onCreate();

        //初始化webrtc 只能调用一次
        new NativeWebRtcContextRegistry().register(getApplicationContext());

        refreshHost();
    }

    private void refreshHost() {
        new AsyncTask<Void, Integer, Integer>() {
            @Override
            protected Integer doInBackground(Void... urls) {
                for (int i = 0; i < 10; i++) {
                    String imHost = lookupHost("imnode.gobelieve.io");
                    String voipHost = lookupHost("voipnode.gobelieve.io");
                    String apiHost = lookupHost("api.gobelieve.io");
                    String demoHost = lookupHost("demo.gobelieve.io");
                    if (TextUtils.isEmpty(imHost) ||
                            TextUtils.isEmpty(voipHost) ||
                            TextUtils.isEmpty(apiHost) ||
                            TextUtils.isEmpty(demoHost)) {
                        try {
                            Thread.sleep((long) (0.05 * 1000));
                        } catch (InterruptedException e) {
                        }
                        continue;
                    } else {
                        break;
                    }
                }
                return 0;
            }

            private String lookupHost(String host) {
                try {
                    InetAddress inetAddress = InetAddress.getByName(host);
                    Log.i("beetle", "host name:" + inetAddress.getHostName() + " " + inetAddress.getHostAddress());
                    return inetAddress.getHostAddress();
                } catch (UnknownHostException exception) {
                    exception.printStackTrace();
                    return "";
                }
            }
        }.execute();
    }
}
