package com.beetle;

public class VOIP {
	private long nativeVOIP;

	public native void initNative(long selfUID, long peerUID, String relayIP, String peerIP, int peerPort, boolean isHeadphone);
	public native void destroyNative();
	public native void start();	
	public native void stop();

    static {
        System.loadLibrary("voip");
    }
}