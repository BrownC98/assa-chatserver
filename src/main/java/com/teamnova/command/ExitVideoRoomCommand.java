package com.teamnova.command;

public class ExitVideoRoomCommand extends ResponseCommand {

    public Boolean isHost;
    public String videoRoomId;
    public Long roomId;  

    public ExitVideoRoomCommand() {
        super(Action.EXIT_VIDEO_ROOM, 0L);
    }

    public static ExitVideoRoomCommand fromJson(String json) throws Exception {
        return fromJson(json, ExitVideoRoomCommand.class);
    }
}

