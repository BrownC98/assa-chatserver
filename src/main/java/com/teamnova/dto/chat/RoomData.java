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
    public String thumbnail; // 🆕 채팅방 썸네일 이미지 필드 추가
    public String coverImage; // 🆕 오픈채팅방 커버 이미지 URL 필드 추가
    public Integer currentMembers; // 🆕 현재 참여 멤버 수 필드 추가

    public RoomData(Long id, String roomName, String description, RoomType roomType, Long masterUserId) {
        this.id = id;
        this.roomName = roomName;
        this.description = description;
        this.roomType = roomType;
        this.masterUserId = masterUserId;
    }

    // 🆕 이미지 URL들과 멤버 수를 포함하는 새로운 생성자 (RoomType 버전)
    public RoomData(Long id, String roomName, String description, RoomType roomType, Long masterUserId, 
                   String thumbnail, String coverImage, Integer currentMembers) {
        this.id = id;
        this.roomName = roomName;
        this.description = description;
        this.roomType = roomType;
        this.masterUserId = masterUserId;
        this.thumbnail = thumbnail;
        this.coverImage = coverImage;
        this.currentMembers = currentMembers;
    }

    public RoomData(Long id, String roomName, String description, String roomType, Long masterUserId) {
        this.id = id;
        this.roomName = roomName;
        this.description = description;
        this.roomType = RoomType.valueOf(roomType);
        this.masterUserId = masterUserId;
    }

    // 🆕 이미지 URL들과 멤버 수를 포함하는 새로운 생성자 (String roomType 버전)
    public RoomData(Long id, String roomName, String description, String roomType, Long masterUserId, 
                   String thumbnail, String coverImage, Integer currentMembers) {
        this.id = id;
        this.roomName = roomName;
        this.description = description;
        this.roomType = RoomType.valueOf(roomType);
        this.masterUserId = masterUserId;
        this.thumbnail = thumbnail;
        this.coverImage = coverImage;
        this.currentMembers = currentMembers;
    }
}
