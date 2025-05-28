package com.teamnova.dto.chat;

import com.teamnova.command.chat.CreateRoomCommand.RoomType;

public class RoomData {
    public Long id;
    public String roomName;
    public String description;
    public RoomType roomType;
    public Long masterUserId;
    public String created_at;
    public String updated_at;

    public RoomData(Long id, String roomName, String description, RoomType roomType, Long masterUserId) {
        this.id = id;
        this.roomName = roomName;
        this.description = description;
        this.roomType = roomType;
        this.masterUserId = masterUserId;
    }

    public RoomData(Long id, String roomName, String description, String roomType, Long masterUserId) {
        this.id = id;
        this.roomName = roomName;
        this.description = description;
        this.roomType = RoomType.valueOf(roomType);
        this.masterUserId = masterUserId;

    }
}
