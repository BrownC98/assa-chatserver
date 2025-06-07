package com.teamnova.command.chat;

import java.util.List;

import com.teamnova.command.Action;
import com.teamnova.command.ResponseCommand;
import com.teamnova.dto.user.UserData;

// 요청시 필요한 데이터 - roomId, 요청자
// 응답시 필요한 데이터 - roomId, 요청자, 현재 멤버현황
public class RoomInfoCommand extends ResponseCommand {

    public Long masterId = 0L;
    public List<UserData> memberList;
    public String roomName;
    public String description;
    public CreateRoomCommand.RoomType roomType = CreateRoomCommand.RoomType.NORMAL;
    public String thumbnail; // 🆕 채팅방 썸네일 이미지 필드 추가
    public String coverImageUrl; // 🆕 오픈채팅방 커버 이미지 URL 필드 추가 (클라이언트 호환성)
    public Integer currentMembers; // 🆕 현재 참여 멤버 수 필드 추가

    public RoomInfoCommand(Long recipientId, Long roomId, List<UserData> memberList) {
        super(Action.ROOM_INFO, recipientId);
        this.roomId = roomId;
        this.memberList = memberList;
    }

    // 자식 생성시 사용
    public RoomInfoCommand(Action action, Long recipientId, Long roomId, List<UserData> memberList) {
        super(action, recipientId);
        this.roomId = roomId;
        this.memberList = memberList;
    }

    public static RoomInfoCommand fromJson(String json) throws Exception {
        return fromJson(json, RoomInfoCommand.class);
    }

    public SendMessageCommand getMessageCommand() {
        return null;
    }
}