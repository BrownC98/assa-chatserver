package com.teamnova.command.user;

import com.teamnova.command.Action;
import com.teamnova.command.BaseCommand;

public class ConnectCommand extends BaseCommand {

    public ConnectCommand() {
        super(Action.CONNECT);
    }

    public static ConnectCommand fromJson(String json) throws Exception {
        return fromJson(json, ConnectCommand.class);
    }
}