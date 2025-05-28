package com.teamnova.command;

import java.util.List;

public class VideoRoomCommand extends BaseCommand {

    public Long roomId;
    public String videoRoomId; // UUID
    public Object sdp; // SessionDescription 대신 Object 사용 (WebRTC 의존성 제거)
    public List<Object> iceCandidates; // IceCandidate 대신 Object 사용
    public TYPE type;

    public VideoRoomCommand(Long roomId, TYPE type) {
        super(Action.VIDEO_ROOM);
        this.roomId = roomId;
        this.type = type;
    }

    public static VideoRoomCommand fromJson(String json) throws Exception {
        return fromJson(json, VideoRoomCommand.class);
    }

    public enum TYPE {
        CREATE, JOIN, EXIT
    }
}