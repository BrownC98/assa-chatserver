package com.teamnova.command;

import java.util.List;

import com.teamnova.dto.user.UserData;
import com.teamnova.command.CreateRoomCommand.RoomType;

// 요청시 필요한 데이터 - roomId, 요청자
// 응답시 필요한 데이터 - roomId, 요청자, 현재 멤버현황
public class RoomInfoCommand extends ResponseCommand {

    public Long roomId;
    public Long masterId = 0L;
    public List<UserData> memberList; // 현재 멤버 리스트
    public String roomName;
    public String description;
    public RoomType roomType = RoomType.NORMAL;

    public RoomInfoCommand(Long recipientId, Long roomId, List<UserData> memberList) {
        super(Action.ROOM_INFO, recipientId);
        this.roomId = roomId;
        this.memberList = memberList;
    }

    public RoomInfoCommand(Action action, Long recipientId, Long roomId,  List<UserData> memberList) {
        super(action, recipientId);
        this.roomId = roomId;
        this.memberList = memberList;
    }

    public static RoomInfoCommand fromJson(String json) throws Exception {
        return fromJson(json, RoomInfoCommand.class);
    }
}
