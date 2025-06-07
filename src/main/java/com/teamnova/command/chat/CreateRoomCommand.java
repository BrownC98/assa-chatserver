package com.teamnova.command.chat;

import java.util.List;

import com.teamnova.command.Action;
import com.teamnova.command.BaseCommand;

public class CreateRoomCommand extends BaseCommand {

    public List<Long> invitedIdList;
    public String roomName;
    public String description;
    public RoomType roomType = RoomType.NORMAL;
    public String thumbnail; // 🆕 채팅방 썸네일 이미지 필드 추가
    public String coverImageUrl; // 🆕 오픈채팅방 커버 이미지 URL 필드 추가 (클라이언트 호환성)

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

    // 🆕 이미지 URL들을 포함하는 새로운 생성자 추가
    public CreateRoomCommand(String roomName, String description, RoomType roomType, String thumbnail, String coverImageUrl) {
        super(Action.CREATE_ROOM);
        this.roomName = roomName;
        this.description = description;
        this.roomType = roomType;
        this.thumbnail = thumbnail;
        this.coverImageUrl = coverImageUrl;
    }

    public static CreateRoomCommand fromJson(String json) throws Exception {
        return fromJson(json, CreateRoomCommand.class);
    }

    public enum RoomType {
        NORMAL,
        OPEN
    }
}