package com.teamnova;

import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.teamnova.command.chat.SendMessageCommand;
import com.teamnova.command.chat.SendMessageCommand.MessageType;
import com.teamnova.command.chat.SendMessageCommand.ReadStatus;
import com.teamnova.command.webrtc.CreateVideoRoomCommand;
import com.teamnova.command.webrtc.ExitVideoRoomCommand;
import com.teamnova.command.webrtc.GetVideoRoomParticipantCommand;
import com.teamnova.command.webrtc.IceCandidateCommand;
import com.teamnova.command.webrtc.JoinVideoRoomCommand;
import com.teamnova.command.webrtc.MediaStatusCommand;
import com.teamnova.command.webrtc.SDPCommand;
import com.teamnova.dto.user.UserData;

/**
 * WebRTC 시그널링 처리를 담당하는 클래스
 */
public class WebRTCSignalingHandler {

    private static Logger log = LogManager.getLogger(WebRTCSignalingHandler.class.getName());

    private User user;

    /**
     * 생성자
     * 
     * @param user 사용자 객체
     */
    public WebRTCSignalingHandler(User user) {
        this.user = user;
    }

    /**
     * 영상회의 방 참가자 목록 조회
     */
    public void getVideoRoomParticipant(GetVideoRoomParticipantCommand command) {
        log.debug("getVideoRoomParticipant() - START");

        VideoRoom videoRoom = ChatServer.videoRoomMap.get(command.videoRoomId);

        // 방 멤버 목록 담기
        List<UserData> userList = new ArrayList<>();
        for (User user : videoRoom.userList) {
            UserData ud = DBHelper.getInstance().getUserDataById(user.id);
            userList.add(ud);
        }
        command.userList = userList;

        this.user.sendMsg(command, false);

        log.debug("getVideoRoomParticipant() - END");
    }

    /**
     * 미디어 상태 처리
     */
    public void mediaStatus(MediaStatusCommand command) {
        VideoRoom videoRoom = ChatServer.videoRoomMap.get(command.videoRoomId);

        for (User user : videoRoom.userList) {
            if (user.id == command.requesterId)
                continue;
            command.recipientId = user.id;
            user.sendMsg(command, false);
        }
    }

    /**
     * 영상회의 방 나가기
     */
    public void exitVideoRoom(ExitVideoRoomCommand command) {
        // 모든 사용자에게 알림
        boolean isHost = command.isHost;
        String videoRoomId = command.videoRoomId;

        VideoRoom videoRoom = ChatServer.videoRoomMap.get(videoRoomId);

        // 방장이면 방 자체를 제거후 회의 종료 메시지 전송
        if (isHost) {
            // 영상통화 종료 메시지를 채팅방으로 전송한다.
            SendMessageCommand messageCommand = new SendMessageCommand(null, command.roomId, videoRoom.id,
                    MessageType.VIDEO_ROOM_CLOSE, null, ReadStatus.UNREAD);
            messageCommand.requesterId = this.user.id;

            user.messageHandler.sendMessage(messageCommand);

            // 회의 방 객체도 제거
            // ChatServer.videoRoomMap.remove(roomId);
        } else {
        }

        // 명단에서 본인 제거
        videoRoom.userList.remove(this.user);

        // 회의 방에도 통보
        for (User user : videoRoom.userList) {
            command.recipientId = user.id;
            user.sendMsg(command, false);
        }

        // 인원이 0명이면 제거
        if (videoRoom.userList.size() == 0) {
            ChatServer.videoRoomMap.remove(videoRoomId);
        }
    }

    /**
     * SDP Offer, Answer 처리
     */
    public void handleSDP(SDPCommand command) {
        log.debug("handleSDP() - START");

        long targetId = command.targetId;
        String videoRoomId = command.videoRoomId;

        // videoRoom 객체에서 타겟 유저 꺼내와서 해당 유저에게 커맨드 전송
        VideoRoom videoRoom = ChatServer.videoRoomMap.get(videoRoomId);

        User target = videoRoom.getUserById(targetId);
        target.sendMsg(command, false);

        log.debug("handleSDP() - END");
    }

    /**
     * IceCandidate 처리 (새 참가자 -> 기존 참가자들)
     */
    public void handleIceCandidate(IceCandidateCommand command) {
        log.debug("handleIceCandidate() - START");

        VideoRoom room = ChatServer.videoRoomMap.get(command.videoRoomId);

        long targetId = command.targetId;
        log.debug("ice 후보 전송 {} -> {}", command.requesterId, command.targetId);

        // ice 후보 전송
        for (User user : room.userList) {
            if (user.id == targetId) {
                user.sendMsg(command, false);
                break;
            }
        }

        log.debug("handleIceCandidate() - END");
    }

    /**
     * 영상 회의 방 참가
     */
    public void joinVideoRoom(JoinVideoRoomCommand command) {
        log.debug("joinVideoRoom() - START");

        VideoRoom videoRoom = ChatServer.videoRoomMap.get(command.videoRoomId);

        // 영상회의방 멤버목록에 자신을 추가
        videoRoom.addUser(this.user);
        UserData userData = DBHelper.getInstance().getUserDataById(command.requesterId);
        command.nickname = userData.nickname;
        command.profileImage = userData.profileImage;

        // 기존 참가자들에게는 새로 참가한 사람의 정보만 제공
        for (User user : videoRoom.userList) {
            // 새로 참가한 사람에게는 전송하지 않음
            if (user.id == this.user.id)
                continue;

            user.sendMsg(command, false);
        }

        // 참가자 본인에게는 모든 참가자(본인포함)의 정보를 제공
        // 방 멤버 목록 담기
        List<UserData> userList = new ArrayList<>();
        for (User user : videoRoom.userList) {
            UserData ud = DBHelper.getInstance().getUserDataById(user.id);
            userList.add(ud);
        }
        command.userList = userList;

        // 참가자는 본인 포함 모든 멤버의 명단을 받음
        this.user.sendMsg(command, false);

        log.debug("joinVideoRoom() - END");
    }

    /**
     * 영상 회의방 생성
     */
    public void createVideoRoom(CreateVideoRoomCommand command) {
        log.debug("createVideoRoom() : START - params: command = {}", command);

        // 영상회의 객체를 생성한다.
        // 생성시 id는 uuidv4로 자동할당
        VideoRoom videoRoom = new VideoRoom();
        log.debug("생성된 비디오 방 id = {}", videoRoom.id);
        videoRoom.addUser(this.user); // 생성자를 멤버로 추가
        videoRoom.hostId = this.user.id;

        // 서버의 영상회의 목록에 추가
        ChatServer.videoRoomMap.put(videoRoom.id, videoRoom);

        // 생성된 방 정보(id)를 호스트에게 전송한다.
        command.videoRoomId = videoRoom.id;
        this.user.sendMsg(command, false);

        // 영상통화 생성 메시지 커맨드를 생성한다.
        // 메시지 내용은 생성된 회의 id다.
        SendMessageCommand messageCommand = new SendMessageCommand(null, command.roomId, videoRoom.id,
                MessageType.VIDEO_ROOM_OPEN, null, ReadStatus.UNREAD);
        messageCommand.requesterId = this.user.id;

        // 생성된 메시지 커맨드를 채팅방으로 전송한다.
        user.messageHandler.sendMessage(messageCommand);

        log.debug("createVideoRoom() : END");
    }
}