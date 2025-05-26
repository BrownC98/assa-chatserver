package com.teamnova.command.chat;

import com.teamnova.command.Action;
import com.teamnova.command.ResponseCommand;

public class SendMessageCommand extends ResponseCommand {

    public Long messageId;
    public Long roomId;
    public String content;
    public MessageType type;
    public ReadStatus readStatus;

    public SendMessageCommand(Long recipientId, Long roomId, String content, MessageType type,
            TransmissionStatus ts, ReadStatus rs) {
        super(Action.SEND_MESSAGE, recipientId);
        this.roomId = roomId;
        this.content = content;
        this.type = type;
        this.transmissionStatus = ts;
        this.readStatus = rs;
    }

    public static SendMessageCommand fromJson(String json) throws Exception {
        return fromJson(json, SendMessageCommand.class);
    }

    public enum MessageType {
        TEXT,
        IMAGE,
        VIDEO,
        VIDEO_ROOM_OPEN,
        VIDEO_ROOM_CLOSE
    }

    public enum ReadStatus {
        READ,
        UNREAD
    }
}