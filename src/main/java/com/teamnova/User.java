
package com.teamnova;

import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.teamnova.command.Action;
import com.teamnova.command.BaseCommand;
import com.teamnova.command.ResponseCommand;
import com.teamnova.command.chat.CheckReceiveCommand;
import com.teamnova.command.chat.CreateRoomCommand;
import com.teamnova.command.chat.ExitRoomCommand;
import com.teamnova.command.chat.InviteCommand;
import com.teamnova.command.chat.RoomInfoCommand;
import com.teamnova.command.chat.SendMessageCommand;
import com.teamnova.command.user.ConnectCommand;
import com.teamnova.command.webrtc.CreateVideoRoomCommand;
import com.teamnova.command.webrtc.ExitVideoRoomCommand;
import com.teamnova.command.webrtc.GetVideoRoomParticipantCommand;
import com.teamnova.command.webrtc.IceCandidateCommand;
import com.teamnova.command.webrtc.JoinVideoRoomCommand;
import com.teamnova.command.webrtc.MediaStatusCommand;
import com.teamnova.command.webrtc.SDPCommand;
import com.teamnova.utils.TimeUtils;

/**
 * 사용자 클래스 - 리팩토링을 통해 3개 핸들러로 책임 분리
 * - UserConnectionManager: 연결 관리
 * - MessageHandler: 메시지 처리
 * - WebRTCSignalingHandler: WebRTC 시그널링
 */
public class User extends Thread {

    private static Logger log = LogManager.getLogger(User.class.getName());

    long id;

    ChatServer server;

    // 연결 관리자
    private UserConnectionManager connectionManager;

    // 메시지 처리자
    MessageHandler messageHandler;

    // WebRTC 시그널링 처리자
    private WebRTCSignalingHandler webrtcHandler;

    // 소켓 접속이 안 된사이 쌓인 메시지 저장 큐
    Queue<String> messageQueue = new LinkedList<>();

    // 방에 초대했지만 현재 접속하지 않은경우를 대처하기 위한 생성자
    public User(long id) {
        this.id = id;
        this.connectionManager = new UserConnectionManager(null, id);
        this.messageHandler = new MessageHandler(this);
        this.webrtcHandler = new WebRTCSignalingHandler(this);
    }

    // 사용자가 실제 접속했을 때 사용되는 생성자
    public User(ChatServer server, Socket socket) {
        this.server = server;
        this.connectionManager = new UserConnectionManager(socket, 0); // ID는 나중에 설정됨
        this.messageHandler = new MessageHandler(this);
        this.webrtcHandler = new WebRTCSignalingHandler(this);
    }

    // 소켓 교체
    public void replaceSocket(Socket socket) {
        log.debug("id = {} 의 소켓을 새것으로 교체", id);
        connectionManager.replaceSocket(socket);
    }

    // 연결 해제
    public void disconnect() {
        log.debug("사용자 ID={} 연결 해제", id);
        connectionManager.disconnect();
    }

    @Override
    public void run() {
        while (connectionManager.isConnected()) {
            try {

                // 클라이언트로부터 받은 메시지 수신 대기 루프
                while (true) {

                    String line = connectionManager.getInputStream().readLine();
                    log.debug("id={} 클라이언트로 부터 요청 받음 : {}", id, line);

                    if (line == null)
                        throw new IOException("클라이언트가 접속 끊음");

                    // json 문자열에서 action 값 얻기
                    JsonObject recvJsonObj = (JsonObject) JsonParser.parseString(line);
                    String action = recvJsonObj.get("action").getAsString();
                    log.debug("추출된 action={}", action);

                    handleActions(action, recvJsonObj.toString());
                }
            } catch (IOException e) {
                // e.printStackTrace();
                log.debug("id={} 클라이언트의 접속이 끊어짐", this.id);
                server.removeUser(this);
            }
        }
    }

    // action 처리
    private void handleActions(String actionStr, String json) {
        log.debug("handleActions() action={}", actionStr);

        // action 문자열을 enum으로 변환
        Action action = Action.valueOf(actionStr);

        try {
            // action에 맞는 객체 생성
            BaseCommand command = BaseCommand.fromJson(action, json);

            // 서버로 들어오는 모든 요청에 현재시간 기록
            command.createdAT = TimeUtils.getCurrentTimeInUTC();

            switch (action) {
                case CONNECT: // 소켓 연결
                    connect((ConnectCommand) command);
                    break;

                case DISCONNECT: // 소켓 연결 해제
                    server.removeUser(this);
                    break;

                case CREATE_ROOM: // 채팅방 생성
                    messageHandler.createRoom((CreateRoomCommand) command);
                    break;

                case ROOM_INFO: // 방 정보 얻기
                    messageHandler.roomInfo((RoomInfoCommand) command);
                    break;

                case SEND_MESSAGE: // 메시지 전송
                    messageHandler.sendMessage((SendMessageCommand) command);
                    break;

                case CHECK_RECEIVE: // 수신 확인처리
                    messageHandler.checkReceive((CheckReceiveCommand) command);
                    break;

                case EXIT_ROOM: // 채팅방 나가기
                    messageHandler.roomExit((ExitRoomCommand) command);
                    break;

                case INVITE: // 채팅방 초대
                    messageHandler.roomInvite((InviteCommand) command);
                    break;

                case CREATE_VIDEO_ROOM: // 영상 회의 방 생성
                    webrtcHandler.createVideoRoom((CreateVideoRoomCommand) command);
                    break;

                case JOIN_VIDEO_ROOM: // 영상 회의 방 참가
                    webrtcHandler.joinVideoRoom((JoinVideoRoomCommand) command);
                    break;

                case SDP:
                    webrtcHandler.handleSDP((SDPCommand) command);
                    break;

                case ICE_CANDIDATE:
                    webrtcHandler.handleIceCandidate((IceCandidateCommand) command);
                    break;

                case EXIT_VIDEO_ROOM:
                    webrtcHandler.exitVideoRoom((ExitVideoRoomCommand) command);
                    break;

                case MEDIA_STATUS:
                    webrtcHandler.mediaStatus((MediaStatusCommand) command);
                    break;

                case GET_VIDEO_ROOM_PARTICIPANT:
                    webrtcHandler.getVideoRoomParticipant((GetVideoRoomParticipantCommand) command);
                    break;

                default:
                    break;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    // getVideoRoomParticipant 메서드는 WebRTCSignalingHandler로 이동됨

    // mediaStatus 메서드는 WebRTCSignalingHandler로 이동됨

    // exitVideoRoom 메서드는 WebRTCSignalingHandler로 이동됨

    // handleSDP, handleIceCandidate 메서드는 WebRTCSignalingHandler로 이동됨

    // joinVideoRoom 메서드는 WebRTCSignalingHandler로 이동됨

    // createVideoRoom 메서드는 WebRTCSignalingHandler로 이동됨

    // 사용자 접속처리
    private void connect(ConnectCommand command) {
        log.debug("connect() start");
        // https://www.notion.so/655c0be054e942c19a75f446ee29c84f?pvs=4#787357d16e364a6ebe3ed8c2cc32d98b
        // 사용자 정보 획득
        id = command.requesterId;

        log.debug("id={} / 서버 접속", id);

        // 사용자 정보로 사용자가 접속한 방 목록 획득
        List<Long> roomIds = DBHelper.getInstance().getEnteredRoomIds(id);
        log.debug("id={} / 접속한 방 목록 : {}", id, roomIds);

        // 기존 접속자 리스트에 해당 유저가 없으면 추가
        boolean isUserExist = false;
        for (User user : server.userList) {
            if (user.id == this.id) {
                log.debug("id={} / 접속 리스트에 있음", id);
                isUserExist = true;
                break;
            }
        }

        if (!isUserExist) {
            log.debug("id={} / 접속 리스트에 없음", id);
            server.addUser(this);
        }

        // 로그용
        List<Long> log_roomIds = new ArrayList<>();

        // 각 방에 기존에 저장된 해당 user객체의 소켓을 새 것으로 바꾼다.
        for (Long roomId : roomIds) {
            // 채팅방 맵의 채팅방 멤버목록 획득
            ChatRoom chatRoom = server.roomMap.get(roomId);
            List<User> memberList = chatRoom.userList;

            // 해당 방에 현재 사용자와 id가 같은 유저객체를 찾아 새것으로 교체한다.
            for (int i = 0; i < memberList.size(); i++) {
                if (memberList.get(i).id == this.id) {
                    // 객체 갈아끼기
                    memberList.set(i, this);
                    log_roomIds.add(roomId);
                    break;
                }
            }
        }
        log.debug("id={} / 소켓이 변경된 방 id 목록 = {}", id, log_roomIds);

        // 이 유저에 대해 NOT_SENT 상태인 커맨드를 다시 전송시도한다.
        List<ResponseCommand> notSentCommands = DBHelper.getInstance().getNotSentCommands(command.requesterId);

        // 연결이 끊어져 못 보낸 메시지 전부 보내기
        for (ResponseCommand notSentCommand : notSentCommands) {
            sendMsg(notSentCommand, false);
        }

        // 로그용 코드
        List<Long> idList = new ArrayList<>();
        for (User user : server.userList) {
            idList.add(user.id);
        }

        log.debug("현재 접속자 현황 id = {}", idList);
        log.debug("connect() end");
    }

    // createRoom 메서드는 MessageHandler로 이동됨

    // roomInfo 메서드는 MessageHandler로 이동됨

    // roomExit 메서드는 MessageHandler로 이동됨

    // SendMessage 메서드는 MessageHandler로 이동됨

    // 이 유저의 클라이언트에 메시지 전송
    public void sendMsg(ResponseCommand command, boolean commandSave) {
        log.debug("sendMsg: START - params: userId={} command={}, commandSave={}", this.id, command, commandSave);

        if (commandSave) {
            // 커맨드 전송내역 기록
            Long insertedId = DBHelper.getInstance().insertResponseCommand(command);
            command.id = insertedId;
        }

        if (connectionManager.isConnected()) {
            connectionManager.getOutputStream().println(command.toJson());
            log.debug("보낸 메시지 내용 : {}", command.toJson());
        } else {
            log.debug("연결이 끊어진 상태로 메시지 전송 불가: userId={}", this.id);
        }

        log.debug("sendMsg: END");
    }
}