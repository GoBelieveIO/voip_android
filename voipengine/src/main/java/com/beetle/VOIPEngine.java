package com.beetle;

public class VOIPEngine {
	private long nativeVOIP;
    private boolean isCaller;
    private String token;
    private long selfUID;
    private long peerUID;
    private String relayIP;
    private int voipPort;
    private String peerIP;
    private int peerPort;

    private boolean videoEnabled;
    private long localRender;
    private long remoteRender;

    public VOIPEngine(boolean isCaller, String token, long selfUID, long peerUID,
                      String relayIP, int voipPort, String peerIP, int peerPort) {
        this.isCaller = isCaller;
        this.token = token;
        this.selfUID = selfUID;
        this.peerUID = peerUID;
        this.relayIP = relayIP;
        this.voipPort = voipPort;
        this.peerIP = peerIP;
        this.peerPort = peerPort;
        this.videoEnabled = false;
    }


    public VOIPEngine(boolean isCaller, String token, long selfUID, long peerUID,
                      String relayIP, int voipPort, String peerIP, int peerPort,
                      long localRender, long remoteRender) {
        this.isCaller = isCaller;
        this.token = token;
        this.selfUID = selfUID;
        this.peerUID = peerUID;
        this.relayIP = relayIP;
        this.voipPort = voipPort;
        this.peerIP = peerIP;
        this.peerPort = peerPort;
        this.localRender = localRender;
        this.remoteRender = remoteRender;
        this.videoEnabled = true;
    }

    public void setToken(String token) {
        this.token = token;
        nativeSetToken(token);
    }

    public void initNative() {
        nativeInit(videoEnabled, selfUID, peerUID, relayIP, voipPort, peerIP, peerPort);
    }

    private native void nativeSetToken(String token);

	private native void nativeInit(boolean videoEnabled, long selfUID, long peerUID, String relayIP,
                                   int voipPort, String peerIP, int peerPort);

	public native void destroyNative();

	public native void start();	
	public native void stop();
    public native void switchCamera();

    static {
        System.loadLibrary("voip");
    }
}