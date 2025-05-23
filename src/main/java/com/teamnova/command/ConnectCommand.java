package com.teamnova.command;

public class ConnectCommand extends BaseCommand {

    public ConnectCommand() {
        super(Action.CONNECT);
    }
    
    public static ConnectCommand fromJson(String json) throws Exception {
        return fromJson(json, ConnectCommand.class);
    }
}
