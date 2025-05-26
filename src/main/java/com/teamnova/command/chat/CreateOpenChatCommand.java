package com.teamnova.command.chat;

import com.teamnova.command.Action;
import com.teamnova.command.BaseCommand;

public class CreateOpenChatCommand extends BaseCommand {

    // 방이름
    public String roomName;
    public String description;

    public CreateOpenChatCommand(String roomName, String description) {
        super(Action.CREATE_OPEN_CHAT);
        this.roomName = roomName;
        this.description = description;
    }

    public static CreateOpenChatCommand fromJson(String json) throws Exception {
        return fromJson(json, CreateOpenChatCommand.class);
    }
}