package com.teamnova.command.chat;

import com.teamnova.command.Action;
import com.teamnova.command.ResponseCommand;

public class SendMessageCommand extends ResponseCommand {

    public Long messageId;
    public String content;
    public Type type;
    public ReadStatus readStatus;
    public boolean isTimeShow = true; // 채팅방에서 메시지 시간을 표시할지 여부
    public boolean isProfileShow = true; // 채팅방에서 프로필 사진을 표시할지 여부

    public SendMessageCommand(Long roomId, String content, Type type) {
        super(Action.SEND_MESSAGE, 0L); // 수신자 id는 0(서버) 로 설정
        this.roomId = roomId;
        this.content = content;
        this.type = type;
        this.readStatus = ReadStatus.UNREAD;
    }

    public static SendMessageCommand fromJson(String json) throws Exception {
        return fromJson(json, SendMessageCommand.class);
    }

    public enum Type {
        TEXT,
        IMAGE,
        VIDEO,
        VIDEO_ROOM_OPEN,
        VIDEO_ROOM_CLOSE
    }

    public enum ReadStatus {
        read,
        UNREAD
    }

    @Override
    public String toString() {
        return "SendMessageCommand{" +
                "messageId=" + messageId +
                ", content='" + content + '\'' +
                ", type=" + type +
                ", readStatus=" + readStatus +
                ", isTimeShow=" + isTimeShow +
                ", isProfileShow=" + isProfileShow +
                ", recipientId=" + recipientId +
                ", transmissionStatus=" + transmissionStatus +
                ", roomId=" + roomId +
                ", id=" + id +
                ", action=" + action +
                ", requesterId=" + requesterId +
                ", createdAT='" + createdAT + '\'' +
                '}';
    }
}