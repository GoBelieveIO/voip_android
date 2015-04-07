package com.beetle.voip;

/**
 * Created by houxh on 14-7-23.
 */
public interface VOIPServiceObserver {
    public void onConnectState(VOIPService.ConnectState state);
}
