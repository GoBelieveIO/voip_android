package com.beetle.voip;

import org.json.JSONException;
import org.json.JSONObject;

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

    public static final int VOIP_COMMAND_PING = 10;

    public int cmd;
    public String channelID;

    public VOIPCommand() {

    }

    public VOIPCommand(JSONObject obj) {
        this.cmd = obj.optInt("command", 0);
        this.channelID = obj.optString("channel_id", "");
    }

    public JSONObject getContent() {
        try {
            JSONObject obj = new JSONObject();
            obj.put("command", this.cmd);
            obj.put("channel_id", this.channelID != null ? this.channelID : "");
            return obj;
        } catch (JSONException e) {
            return null;
        }
    }

}
