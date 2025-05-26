package com.teamnova.chat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.teamnova.webrtc.VideoRoom;
import com.teamnova.command.ResponseCommand;
import com.teamnova.command.ResponseCommand.TransmissionStatus;
import com.teamnova.command.chat.CreateRoomCommand.RoomType;
import com.teamnova.user.User;

/**
 * 채팅방 클래스
 */
public class ChatRoom {

    private static Logger log = LogManager.getLogger(ChatRoom.class.getName());

    public Long id; // 채팅방 id
    public RoomType roomType; // 채팅방 종류 "NORMAL", "OPEN"
    public String roomName;
    public String description;
    public Long masterUserId;

    public List<User> userList = new CopyOnWriteArrayList<>(); // 이 채팅방에 속한 사용자 리스트
    public List<VideoRoom> videoRooms = new ArrayList<>();

    // 방 내부 모든 멤버에게 커맨드 전송
    public void broadcastToRoom(ResponseCommand command) {
        log.debug("broadcastToRoom: START");
        log.debug("roomId={} / 채팅방 전체인원에게 메시지 전송", id);

        command.transmissionStatus = TransmissionStatus.NOT_SENT;

        // 방 멤버들을 순회하며 메시지를 전송한다.
        for (User user : userList) {

            // 메시지 커맨드에 수신자 id 기록
            command.recipientId = user.id;

            try {
                log.debug("id = {} 에게 메시지 전송", command.recipientId);
                // 보낼 수 있다면 메시지 전송 후 메시지 전송 상태를 SENT로 기록
                user.sendMsg(command, true);

            } catch (Exception e) {
                // 에러 발생해도 db에는 not_sent로 기록 되기 때문에 다음 유저 작업 진행하면 된다.
                log.debug("send fail to {}", user.id);
            }
        }

        log.debug("broadcastToRoom: END");
    }
}
