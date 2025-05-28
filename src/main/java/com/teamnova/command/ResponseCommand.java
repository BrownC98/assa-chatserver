package com.teamnova.command;

/**
 * 커맨드 중 클라이언트에게 응답하는 것
 */
public abstract class ResponseCommand extends BaseCommand {

    public Long recipientId; // 수신자 id
    public TransmissionStatus transmissionStatus; // 전송상태
    public Long roomId;

    public ResponseCommand(Action action, Long recipientId) {
        super(action);
        this.recipientId = recipientId;
        this.transmissionStatus = TransmissionStatus.NOT_SENT;
    }

    public enum TransmissionStatus {
        SENT,
        NOT_SENT
    }
}
