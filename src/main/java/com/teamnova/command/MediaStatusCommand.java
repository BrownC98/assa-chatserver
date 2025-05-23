package com.teamnova.command;

/**
 * 영상회의중 카메라, 마이크 on / off 상태를 전달하기 위한 커맨드
 */
public class MediaStatusCommand extends ResponseCommand {

    public String videoRoomId;
    public boolean isEnabled;
    public MediaType mediaType;

    public MediaStatusCommand(String videoRoomId, MediaType mediaType, boolean isEnabled) {
        super(Action.MEDIA_STATUS, 0L);
        this.videoRoomId = videoRoomId;
        this.isEnabled = isEnabled;
        this.mediaType = mediaType;
    }

    public static RoomInfoCommand fromJson(String json) throws Exception {
        return fromJson(json, RoomInfoCommand.class);
    }

    public enum MediaType {
        VIDEO,
        AUDIO
    }

}
