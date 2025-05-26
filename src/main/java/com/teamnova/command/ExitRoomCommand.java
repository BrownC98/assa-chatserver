package com.teamnova.command;

import java.util.List;

import com.teamnova.dto.user.UserData;

// 퇴장 요청시 필요한 데이터 - 방 id, 나가는 사람 id (요청자)
// 남은 멤버가 받아야할 데이터 - 방 id, 메시지 id, 나가는 사람 id (요청자), 
public class ExitRoomCommand extends RoomInfoCommand {

    public Long messageId; // 문구를 출력하기 위한 id

    public ExitRoomCommand(Long recipientId, Long roomId, Long requesterId, List<UserData> memberList, Long messageId) {
        super(Action.EXIT_ROOM, recipientId, roomId, memberList);
        this.roomId = roomId;
        this.requesterId = requesterId;
        this.messageId = messageId;
    }

    public static ExitRoomCommand fromJson(String json) throws Exception {
        return fromJson(json, ExitRoomCommand.class);
    }
}
