

package com.beetle.voip;


import java.net.InetSocketAddress;




public class Stun {
    private static final String TAG = "face";
	public Stun() {
		super();
	}


    public static final int StunTypeUnknown=0;
    public static final int StunTypeFailure = 1;
    public static final int StunTypeOpen = 2;
    public static final int StunTypeBlocked = 3;
    public static final int StunTypeIndependentFilter = 4;
    public static final int StunTypeDependentFilter = 5;
    public static final int StunTypePortDependedFilter = 6;
    public static final int StunTypeDependentMapping = 7;
    public static final int StunTypeFirewall = 8;

    public native int getNatType(String stunServer);

    public native InetSocketAddress mapPort(String stunServer, int port);

    static {
        System.loadLibrary("stun");
    }
}

