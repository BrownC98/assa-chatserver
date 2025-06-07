package com.teamnova;

import com.teamnova.server.ChatServer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Main {
    private static final Logger log = LogManager.getLogger(Main.class);
    
    public static void main(String[] args) {
        log.info("========== CHATSERVER MAIN 시작 ==========");
        log.info("메인 애플리케이션이 시작되었습니다.");
        
        ChatServer server = new ChatServer();
        server.init();
    }
}
