package com.beetle;

/**
 * Created by houxh on 16/2/2.
 */
public class VOIPCapture {
    private long nativeVOIP;
    private boolean isFrontCamera;
    private long render;

    public VOIPCapture( boolean isFrontCamera,
                      long render) {
        this.isFrontCamera = isFrontCamera;
        this.render = render;
    }

    public native void nativeInit();
    public native void destroyNative();
    public native void startCapture();
    public native void stopCapture();
    public native void switchCamera();

    static {
        System.loadLibrary("voip");
    }

}
