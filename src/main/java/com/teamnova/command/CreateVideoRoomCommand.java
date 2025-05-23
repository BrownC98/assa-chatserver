package com.teamnova.command;

/**
 * 영상통화 방 생성 요청 커맨드
 */
public class CreateVideoRoomCommand extends ResponseCommand {

    public Long roomId;
    public String videoRoomId;

    public CreateVideoRoomCommand(Long roomId) {
        super(Action.CREATE_VIDEO_ROOM, 0L);
        this.roomId = roomId;
    }

    public static CreateVideoRoomCommand fromJson(String json) throws Exception {
        return fromJson(json, CreateVideoRoomCommand.class);
    }
}

