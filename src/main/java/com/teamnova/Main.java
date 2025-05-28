package com.teamnova;

import com.teamnova.server.ChatServer;

public class Main {
    public static void main(String[] args) {
        ChatServer server = new ChatServer();
        server.init();
    }
}
