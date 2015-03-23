package com.beetle.voip;

import java.util.Arrays;


/**
 * Created by houxh on 14-7-23.
 */



class Command{
    public static final int MSG_HEARTBEAT = 1;
    public static final int MSG_AUTH = 2;
    public static final int MSG_AUTH_STATUS = 3;
    public static final int MSG_IM = 4;
    public static final int MSG_ACK = 5;
    public static final int MSG_RST = 6;
    public static final int MSG_GROUP_NOTIFICATION = 7;
    public static final int MSG_GROUP_IM = 8;
    public static final int MSG_PEER_ACK = 9;
    public static final int MSG_INPUTTING = 10;
    public static final int MSG_SUBSCRIBE_ONLINE_STATE = 11;
    public static final int MSG_ONLINE_STATE = 12;
    public static final int MSG_PING = 13;
    public static final int MSG_PONG = 14;

    public static final int MSG_VOIP_CONTROL = 64;
    public static final int MSG_VOIP_DATA = 65;
}





class Message {

    public static final int HEAD_SIZE = 8;
    public int cmd;
    public int seq;
    public Object body;

    public byte[] pack() {
        int pos = 0;
        byte[] buf = new byte[64*1024];
        BytePacket.writeInt32(seq, buf, pos);
        pos += 4;
        buf[pos] = (byte)cmd;
        pos += 4;

        if (cmd == Command.MSG_HEARTBEAT || cmd == Command.MSG_PING) {
            return Arrays.copyOf(buf, HEAD_SIZE);
        } else if (cmd == Command.MSG_AUTH) {
            BytePacket.writeInt64((Long) body, buf, pos);
            return Arrays.copyOf(buf, HEAD_SIZE+8);
        } else if (cmd == Command.MSG_ACK) {
            BytePacket.writeInt32((Integer)body, buf, pos);
            return Arrays.copyOf(buf, HEAD_SIZE+4);
        } else if (cmd == Command.MSG_VOIP_CONTROL) {
            VOIPControl ctl = (VOIPControl)body;
            BytePacket.writeInt64(ctl.sender, buf, pos);
            pos += 8;
            BytePacket.writeInt64(ctl.receiver, buf, pos);
            pos += 8;
            BytePacket.writeInt32(ctl.cmd, buf, pos);
            pos += 4;

            if (ctl.cmd == VOIPControl.VOIP_COMMAND_DIAL) {
                BytePacket.writeInt32(ctl.dialCount, buf, pos);
                pos += 4;
                return Arrays.copyOf(buf, HEAD_SIZE + 24);
            } else if (ctl.cmd == VOIPControl.VOIP_COMMAND_ACCEPT ||
                    ctl.cmd == VOIPControl.VOIP_COMMAND_CONNECTED) {
                if (ctl.natMap != null) {
                    BytePacket.writeInt32(ctl.natMap.ip, buf, pos);
                    pos += 4;
                    BytePacket.writeInt16(ctl.natMap.port, buf, pos);
                    pos += 2;
                    return Arrays.copyOf(buf, HEAD_SIZE+26);
                } else {
                    return Arrays.copyOf(buf, HEAD_SIZE + 20);
                }
            } else {
                return Arrays.copyOf(buf, HEAD_SIZE + 20);
            }
        }
        return null;
    }

    public boolean unpack(byte[] data) {
        int pos = 0;
        this.seq = BytePacket.readInt32(data, pos);
        pos += 4;
        cmd = data[pos];
        pos += 4;
        if (cmd == Command.MSG_RST) {
            return true;
        } else if (cmd == Command.MSG_AUTH_STATUS) {
            int status = BytePacket.readInt32(data, pos);
            this.body = new Integer(status);
            return true;
        } else if (cmd == Command.MSG_ACK) {
            int s = BytePacket.readInt32(data, pos);
            this.body = new Integer(s);
            return true;
        } else if (cmd == Command.MSG_VOIP_CONTROL) {
            VOIPControl ctl = new VOIPControl();
            ctl.sender = BytePacket.readInt64(data, pos);
            pos += 8;
            ctl.receiver = BytePacket.readInt64(data, pos);
            pos += 8;
            ctl.cmd = BytePacket.readInt32(data, pos);
            pos += 4;
            if (ctl.cmd == VOIPControl.VOIP_COMMAND_DIAL) {
                ctl.dialCount = BytePacket.readInt32(data, pos);
            } else if (ctl.cmd == VOIPControl.VOIP_COMMAND_ACCEPT ||
                    ctl.cmd == VOIPControl.VOIP_COMMAND_CONNECTED) {
                if (data.length >= HEAD_SIZE + 26) {
                    ctl.natMap = new VOIPControl.NatPortMap();
                    ctl.natMap.ip = BytePacket.readInt32(data, pos);
                    pos += 4;
                    ctl.natMap.port = BytePacket.readInt16(data, pos);
                    pos += 2;
                }
            }
            this.body = ctl;
            return true;
        } else if (cmd == Command.MSG_PONG) {
            return true;
        } else {
            return false;
        }
    }
}
