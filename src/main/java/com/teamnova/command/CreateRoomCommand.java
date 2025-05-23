package com.teamnova.command;

import java.util.List;

public class CreateRoomCommand extends BaseCommand {

    public List<Long> invitedIdList;
    public String roomName;
    public String description;
    public RoomType roomType;

    public CreateRoomCommand(List<Long> invitedIdList) {
        super(Action.CREATE_ROOM);
        this.invitedIdList = invitedIdList;
    }

    public CreateRoomCommand(String roomName, String description, RoomType roomType) {
        super(Action.CREATE_ROOM);
        this.roomName = roomName;
        this.description = description;
        this.roomType = roomType;
    }

    public static CreateRoomCommand fromJson(String json) throws Exception {
        return fromJson(json, CreateRoomCommand.class);
    }

    public enum RoomType{
        NORMAL,
        OPEN
    }
}