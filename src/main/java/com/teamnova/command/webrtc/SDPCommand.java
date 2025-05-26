package com.teamnova.command.webrtc;

import com.teamnova.command.Action;
import com.teamnova.command.ResponseCommand;
import com.teamnova.dto.webrtc.SessionDescription;

public class SDPCommand extends ResponseCommand {

    public String videoRoomId;
    public Long targetId;
    public SessionDescription sdp;

    public SDPCommand(String videoRoomId, Long targetId, SessionDescription sdp) {
        super(Action.SDP, 0L);
        this.videoRoomId = videoRoomId;
        this.targetId = targetId;
        this.sdp = sdp;
    }

    public static SDPCommand fromJson(String json) throws Exception {
        return fromJson(json, SDPCommand.class);
    }
}