package com.teamnova.dto;

public class MessageStatus {
    public long id;
    public long messageId;
    public long userId; // 읽은 사람 id
    public String status; // 메시지 상태 READ, UNREAD, NOT_SENT 중 하나
    public String readAt; // 읽은 시간

    public MessageStatus(long id, long messageId, long userId, String status, String readAt) {
        this.id = id;
        this.messageId = messageId;
        this.userId = userId;
        this.status = status;
        this.readAt = readAt;
    }

    public MessageStatus() {
    }

    // 상태값 enum
    public enum TYPE {
        NOT_SENT,       // 전송 안 됨 (전송을 아직 안 했거나, 소켓이 없어 전송을 못한 경우 포함)
        SENT,           // 전송됨 (안 읽음)
        READ            // 수신자가 메시지를 읽음
    }
}
