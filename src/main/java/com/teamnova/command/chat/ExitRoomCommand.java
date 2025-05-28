package com.teamnova.command.chat;

import java.util.List;

import com.teamnova.command.Action;
import com.teamnova.dto.user.UserData;

// 퇴장 요청시 필요한 데이터 - 방 id, 나가는 사람 id (요청자)
// 남은 멤버가 받아야할 데이터 - 방 id, 메시지 id, 나가는 사람 id (요청자), 
public class ExitRoomCommand extends RoomInfoCommand {
    private static final String TAG = ExitRoomCommand.class.getSimpleName();

    public Long messageId; // 문구를 출력하기 위한 id

    public ExitRoomCommand(Long recipientId, Long roomId, Long requesterId, List<UserData> memberList, Long messageId) {
        super(Action.EXIT_ROOM, recipientId, roomId, memberList);
        this.roomId = roomId;
        this.requesterId = requesterId;
        this.messageId = messageId;
    }

    public ExitRoomCommand(Long roomId) {
        super(Action.EXIT_ROOM, 0L, roomId, null);
    }

    public static ExitRoomCommand fromJson(String json) throws Exception {
        return fromJson(json, ExitRoomCommand.class);
    }

    public SendMessageCommand getMessageCommand() {
        System.out.println(TAG + " getMessageCommand: START");

        // ex -> EXIT:0
        String content = "EXIT:" + this.requesterId;

        SendMessageCommand c = new SendMessageCommand(roomId, content, SendMessageCommand.Type.TEXT);
        c.messageId = this.messageId;
        c.requesterId = 0L;
        c.recipientId = this.recipientId;
        c.createdAT = this.createdAT;

        System.out.println(TAG + " getMessageCommand: return - 생성된 메시지 커맨드 = " + c.toJson());
        return c;
    }
}