package com.beetle.voip;

import com.beetle.im.BytePacket;

import java.util.Arrays;

/**
 * Created by houxh on 16/2/2.
 */
public class VOIPCommand {
    public static final int VOIP_COMMAND_DIAL = 1;

    public static final int VOIP_COMMAND_ACCEPT = 2;
    public static final int VOIP_COMMAND_CONNECTED = 3;
    public static final int VOIP_COMMAND_REFUSE = 4;
    public static final int VOIP_COMMAND_REFUSED = 5;
    public static final int VOIP_COMMAND_HANG_UP = 6;
    public static final int VOIP_COMMAND_TALKING = 8;

    public static final int VOIP_COMMAND_DIAL_VIDEO = 9;


    public int cmd;
    public int dialCount;//只对VOIP_COMMAND_DIAL, VOIP_COMMAND_DIAL_VIDEO有意义


    public NatPortMap natMap; //VOIP_COMMAND_ACCEPT，VOIP_COMMAND_CONNECTED
    public static class NatPortMap {
        public int ip;
        public short port;
    }

    public int relayIP;//VOIP_COMMAND_CONNECTED


    public VOIPCommand() {

    }

    public VOIPCommand(byte[] content) {
        byte[] data = content;
        int pos = 0;

        this.cmd = BytePacket.readInt32(data, pos);
        pos += 4;
        if (this.cmd == VOIP_COMMAND_DIAL ||
                this.cmd == VOIP_COMMAND_DIAL_VIDEO) {
            this.dialCount = BytePacket.readInt32(data, pos);
        } else if (this.cmd == VOIP_COMMAND_ACCEPT) {
            if (data.length >= 10) {
                this.natMap = new NatPortMap();
                this.natMap.ip = BytePacket.readInt32(data, pos);
                pos += 4;
                this.natMap.port = BytePacket.readInt16(data, pos);
                pos += 2;
            }
        } else if (this.cmd == VOIP_COMMAND_CONNECTED) {
            if (data.length >= 10) {
                this.natMap = new NatPortMap();
                this.natMap.ip = BytePacket.readInt32(data, pos);
                pos += 4;
                this.natMap.port = BytePacket.readInt16(data, pos);
                pos += 2;
            }
            if (data.length >= 14) {
                this.relayIP = BytePacket.readInt32(data, pos);
                pos += 4;
            }
        }
    }

    public byte[] getContent() {
        int pos = 0;
        byte[] buf = new byte[64 * 1024];

        BytePacket.writeInt32(this.cmd, buf, pos);
        pos += 4;

        if (this.cmd == VOIP_COMMAND_DIAL ||
                this.cmd == VOIP_COMMAND_DIAL_VIDEO) {
            BytePacket.writeInt32(this.dialCount, buf, pos);
            pos += 4;
            return Arrays.copyOf(buf, 8);
        } else if (this.cmd == VOIP_COMMAND_ACCEPT) {
            if (this.natMap != null) {
                BytePacket.writeInt32(this.natMap.ip, buf, pos);
                pos += 4;
                BytePacket.writeInt16(this.natMap.port, buf, pos);
                pos += 2;
            } else {
                BytePacket.writeInt32(0, buf, pos);
                pos += 4;
                BytePacket.writeInt16((short) (0), buf, pos);
                pos += 2;
            }
            return Arrays.copyOf(buf, 10);
        } else if (this.cmd == VOIP_COMMAND_CONNECTED) {
            if (this.natMap != null) {
                BytePacket.writeInt32(this.natMap.ip, buf, pos);
                pos += 4;
                BytePacket.writeInt16(this.natMap.port, buf, pos);
                pos += 2;
            } else {
                BytePacket.writeInt32(0, buf, pos);
                pos += 4;
                BytePacket.writeInt16((short) (0), buf, pos);
                pos += 2;
            }
            BytePacket.writeInt32(this.relayIP, buf, pos);
            pos += 4;
            return Arrays.copyOf(buf, 14);
        } else {
            return Arrays.copyOf(buf, 4);
        }
    }

}
