package com.teamnova.command;

import com.teamnova.dto.webrtc.IceCandidate;

public class IceCandidateCommand extends ResponseCommand {

    public String videoRoomId;
    public IceCandidate iceCandidate;
    public long targetId;

    public IceCandidateCommand(String videoRoomId, long targetId, IceCandidate iceCandidate) {
        super(Action.ICE_CANDIDATE, 0L);
        this.videoRoomId = videoRoomId;
        this.targetId = targetId;
        this.iceCandidate = iceCandidate;
    }

    public static IceCandidateCommand fromJson(String json) throws Exception {
        return fromJson(json, IceCandidateCommand.class);
    }
}
