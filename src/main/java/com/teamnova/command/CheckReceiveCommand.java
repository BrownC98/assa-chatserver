package com.teamnova.command;

/**
 * 클라이언트로 부터 수신확인 받는 커맨드
 */
public class CheckReceiveCommand extends BaseCommand {

    public Long commandId;

    public CheckReceiveCommand(Long commandId) {
        super(Action.CHECK_RECEIVE);
        this.commandId = commandId;
    }

    public static CheckReceiveCommand fromJson(String json) throws Exception {
        return fromJson(json, CheckReceiveCommand.class);
    }
}
