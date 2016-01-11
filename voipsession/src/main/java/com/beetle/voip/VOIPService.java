package com.beetle.voip;


import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.util.Log;
import com.beetle.AsyncTCP;
import com.beetle.TCPConnectCallback;
import com.beetle.TCPReadCallback;
import com.beetle.im.IMService;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Date;


import static android.os.SystemClock.uptimeMillis;

/**
 * Created by houxh on 14-7-21.
 */
public class VOIPService extends IMService {

    private static VOIPService im = new VOIPService();

    public static VOIPService getInstance() {
        return im;
    }

}
