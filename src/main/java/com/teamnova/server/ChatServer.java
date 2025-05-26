package com.teamnova.server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.teamnova.chat.ChatRoom;
import com.teamnova.config.PropertiesManager;
import com.teamnova.database.DBHelper;
import com.teamnova.user.User;
import com.teamnova.webrtc.VideoRoom;

/**
 * 채팅 서버 메인 클래스
 */
public class ChatServer {

    private static Logger log = LogManager.getLogger(ChatServer.class.getName());

    static final int PORT = Integer.parseInt(PropertiesManager.getProperty("SERVER_PORT"));

    public List<User> userList; // 접속자 리스트
    public static Map<Long, ChatRoom> roomMap; // 채팅방 저장 맵, 키 값이 방 id와 매칭된다.
    public static Map<String, VideoRoom> videoRoomMap; // 영상회의 맵 키 값은 회의방의 uuid이다.

    public void init() {
        log.debug("init: START");

        userList = new CopyOnWriteArrayList<>();
        roomMap = new ConcurrentHashMap<>();
        videoRoomMap = new ConcurrentHashMap<>();

        // db 연결
        DBHelper dbHelper = DBHelper.getInstance();

        // DB에서 생성되었던 방 목록 로드
        roomMap = dbHelper.getServerData();
        log.debug("현재 채팅방 상황: {}", roomMap);

        // 서버소켓 생성
        try (ServerSocket serverSocket = new ServerSocket(PORT);) {
            log.debug("소켓 연결 대기 중... PORT : {}", PORT);

            // 소켓 연결 대기 무한 루프
            while (true) {

                Socket socket = serverSocket.accept();

                // user 객체 생성 후 스레드 실행
                User user = new User(this, socket);
                user.start();
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
        log.debug("init: END");
    }

    // 사용자 목록에 인자로 주어진 사용자를 추가한다.
    public void addUser(User user) {
        int prevUserCount = userList.size();

        userList.add(user);
        log.debug("id = {} / 접속, 접속자 수 : {} -> {}", user.id, prevUserCount,
                userList.size());
    }

    // 접속자 목록에서 주어진 유저 제거
    public void removeUser(User user) {
        int prevUserCount = userList.size();

        // UserConnectionManager를 통해 연결 해제
        user.disconnect();

        userList.remove(user);
        log.debug("id = {} / 접속종료, 접속자 수 : {} -> {}", user.id, prevUserCount,
                userList.size());
    }
}
