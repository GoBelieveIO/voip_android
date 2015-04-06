package com.beetle;

public class VOIPEngine {
	private long nativeVOIP;

	public native void initNative(String token, long selfUID, long peerUID, String relayIP, int voipPort, String peerIP, int peerPort, boolean isHeadphone);
	public native void destroyNative();
	public native void start();	
	public native void stop();

    static {
        System.loadLibrary("voip");
    }
}