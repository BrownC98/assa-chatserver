
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
import com.teamnova.Utils.TimeUtils;
import com.teamnova.command.Action;
import com.teamnova.command.BaseCommand;
import com.teamnova.command.CheckReceiveCommand;
import com.teamnova.command.ConnectCommand;
import com.teamnova.command.CreateRoomCommand;
import com.teamnova.command.CreateVideoRoomCommand;
import com.teamnova.command.ExitRoomCommand;
import com.teamnova.command.ExitVideoRoomCommand;
import com.teamnova.command.GetVideoRoomParticipantCommand;
import com.teamnova.command.IceCandidateCommand;
import com.teamnova.command.InviteCommand;
import com.teamnova.command.JoinVideoRoomCommand;
import com.teamnova.command.MediaStatusCommand;
import com.teamnova.command.ResponseCommand;
import com.teamnova.command.RoomInfoCommand;
import com.teamnova.command.SDPCommand;
import com.teamnova.command.SendMessageCommand;
import com.teamnova.command.SendMessageCommand.MessageType;
import com.teamnova.command.SendMessageCommand.ReadStatus;

/**
 * 유저 클래스
 */
public class User extends Thread {

    private static Logger log = LogManager.getLogger(User.class.getName());

    long id;

    ChatServer server;

    // 연결 관리자
    private UserConnectionManager connectionManager;

    // 메시지 처리자
    private MessageHandler messageHandler;

    // 소켓 접속이 안 된사이 쌓인 메시지 저장 큐
    Queue<String> messageQueue = new LinkedList<>();

    // 방에 초대했지만 현재 접속하지 않은경우를 대처하기 위한 생성자
    public User(long id) {
        this.id = id;
        this.connectionManager = new UserConnectionManager(null, id);
        this.messageHandler = new MessageHandler(this);
    }

    // 사용자가 실제 접속했을 때 사용되는 생성자
    public User(ChatServer server, Socket socket) {
        this.server = server;
        this.connectionManager = new UserConnectionManager(socket, 0); // ID는 나중에 설정됨
        this.messageHandler = new MessageHandler(this);
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
                    createVideoRoom((CreateVideoRoomCommand) command);
                    break;

                case JOIN_VIDEO_ROOM: // 영상 회의 방 참가
                    joinVideoRoom((JoinVideoRoomCommand) command);
                    break;

                case SDP:
                    handleSDP((SDPCommand) command);
                    break;

                case ICE_CANDIDATE:
                    handleIceCandidate((IceCandidateCommand) command);
                    break;

                case EXIT_VIDEO_ROOM:
                    exitVideoRoom((ExitVideoRoomCommand) command);
                    break;

                case MEDIA_STATUS:
                    mediaStatus((MediaStatusCommand) command);
                    break;

                case GET_VIDEO_ROOM_PARTICIPANT:
                    getVideoRoomParticipant((GetVideoRoomParticipantCommand) command);
                    break;

                default:
                    break;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

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

        this.sendMsg(command, false);

        log.debug("getVideoRoomParticipant() - END");
    }

    public void mediaStatus(MediaStatusCommand command) {
        VideoRoom videoRoom = ChatServer.videoRoomMap.get(command.videoRoomId);

        for (User user : videoRoom.userList) {
            if (user.id == command.requesterId)
                continue;
            command.recipientId = user.id;
            user.sendMsg(command, false);
        }
    }

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
            messageCommand.requesterId = this.id;

            messageHandler.sendMessage(messageCommand);

            // 회의 방 객체도 제거
            // ChatServer.videoRoomMap.remove(roomId);
        } else {
        }

        // 명단에서 본인 제거
        videoRoom.userList.remove(this);

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

    // SDP Offer, Answer 처리
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

    // IceCandidate 처리 (새 참가자 -> 기존 참가자들)
    private void handleIceCandidate(IceCandidateCommand command) {
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

    // 영상 회의 방 참가
    public void joinVideoRoom(JoinVideoRoomCommand command) {
        log.debug("joinVideoRoom() - START");

        VideoRoom videoRoom = ChatServer.videoRoomMap.get(command.videoRoomId);

        // 영상회의방 멤버목록에 자신을 추가
        videoRoom.addUser(this);
        UserData userData = DBHelper.getInstance().getUserDataById(command.requesterId);
        command.nickname = userData.nickname;
        command.profileImage = userData.profileImage;

        // 기존 참가자들에게는 새로 참가한 사람의 정보만 제공
        for (User user : videoRoom.userList) {
            // 새로 참가한 사람에게는 전송하지 않음
            if (user.id == this.id)
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
        this.sendMsg(command, false);

        // // 모든 멤버들에게 알림 (본인 포함)
        // for (User user : videoRoom.userList) {
        // user.sendMsg(command, false);
        // }

        log.debug("joinVideoRoom() - END");
    }

    // 영상 회의방 생성
    public void createVideoRoom(CreateVideoRoomCommand command) {
        log.debug("createVideoRoom() : START - params: command = {}", command);

        // 영상회의 객체를 생성한다.
        // 생성시 id는 uuidv4로 자동할당
        VideoRoom videoRoom = new VideoRoom();
        log.debug("생성된 비디오 방 id = {}", videoRoom.id);
        videoRoom.addUser(this); // 생성자를 멤버로 추가
        videoRoom.hostId = this.id;

        // 서버의 영상회의 목록에 추가
        ChatServer.videoRoomMap.put(videoRoom.id, videoRoom);

        // 생성된 방 정보(id)를 호스트에게 전송한다.
        command.videoRoomId = videoRoom.id;
        this.sendMsg(command, false);

        // 영상통화 생성 메시지 커맨드를 생성한다.
        // 메시지 내용은 생성된 회의 id다.
        SendMessageCommand messageCommand = new SendMessageCommand(null, command.roomId, videoRoom.id,
                MessageType.VIDEO_ROOM_OPEN, null, ReadStatus.UNREAD);
        messageCommand.requesterId = this.id;

        // 생성된 메시지 커맨드를 채팅방으로 전송한다.
        messageHandler.sendMessage(messageCommand);

        log.debug("createVideoRoom() : END");
    }

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