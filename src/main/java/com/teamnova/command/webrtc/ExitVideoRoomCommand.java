package com.teamnova.command.webrtc;

import com.teamnova.command.Action;
import com.teamnova.command.ResponseCommand;

public class ExitVideoRoomCommand extends ResponseCommand {

    public Boolean isHost = false;
    public String videoRoomId;

    public ExitVideoRoomCommand() {
        super(Action.EXIT_VIDEO_ROOM, 0L);
    }

    public static ExitVideoRoomCommand fromJson(String json) throws Exception {
        return fromJson(json, ExitVideoRoomCommand.class);
    }
}