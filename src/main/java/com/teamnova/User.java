
package com.teamnova;

import java.io.IOException;
import java.net.Socket;
import java.sql.SQLException;
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
import com.teamnova.command.ResponseCommand.TransmissionStatus;
import com.teamnova.command.RoomInfoCommand;
import com.teamnova.command.SDPCommand;
import com.teamnova.command.SendMessageCommand;
import com.teamnova.command.SendMessageCommand.MessageType;
import com.teamnova.command.SendMessageCommand.ReadStatus;
import com.teamnova.dto.RoomData;

/**
 * 유저 클래스
 */
public class User extends Thread {

    private static Logger log = LogManager.getLogger(User.class.getName());

    long id;

    ChatServer server;

    // 연결 관리자
    private UserConnectionManager connectionManager;

    // 소켓 접속이 안 된사이 쌓인 메시지 저장 큐
    Queue<String> messageQueue = new LinkedList<>();

    // 방에 초대했지만 현재 접속하지 않은경우를 대처하기 위한 생성자
    public User(long id) {
        this.id = id;
        this.connectionManager = new UserConnectionManager(null, id);
    }

    // 사용자가 실제 접속했을 때 사용되는 생성자
    public User(ChatServer server, Socket socket) {
        this.server = server;
        this.connectionManager = new UserConnectionManager(socket, 0); // ID는 나중에 설정됨
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
                    createRoom((CreateRoomCommand) command);
                    break;

                case ROOM_INFO: // 방 정보 얻기
                    roomInfo((RoomInfoCommand) command);
                    break;

                case SEND_MESSAGE: // 메시지 전송
                    SendMessage((SendMessageCommand) command);
                    break;

                case CHECK_RECEIVE: // 수신 확인처리
                    checkReceive((CheckReceiveCommand) command);
                    break;

                case EXIT_ROOM: // 채팅방 나가기
                    roomExit((ExitRoomCommand) command);
                    break;

                case INVITE: // 채팅방 초대
                    roomInvite((InviteCommand) command);
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

            SendMessage(messageCommand);

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
        SendMessage(messageCommand);

        log.debug("createVideoRoom() : END");
    }

    // 수신확인 처리
    public void checkReceive(CheckReceiveCommand command) {
        log.debug("checkReceive() : START - params: command = {}", command);

        // db에 해당 커맨드의 수신 결과를 기록한다.
        DBHelper.getInstance().updateResponseCommandStatus(command.commandId, TransmissionStatus.SENT);

        log.debug("checkReceive() : END");
    }

    // 채팅방 초대
    private void roomInvite(InviteCommand command) {
        log.debug("roomInvite : START");

        Long roomId = command.roomId; // 초대한 방 id

        ChatRoom room = ChatServer.roomMap.get(roomId);

        // 로그용 리스트
        List<Long> connectedUserList = new ArrayList<>();
        List<Long> notConnectedUserList = new ArrayList<>();

        // 제공받은 유저정보 목록을 통해 유저들을 방 멤버로 추가
        for (Long userId : command.invitedIdList) {

            // 멤버 - 채팅방 관계 테이블 insert
            DBHelper.getInstance().insertUserChatRoomsRelation(roomId, userId);
            log.debug("uesrId={}, roomId={} 관계테이블 레코드 생성", userId, roomId);

            // 서버에 있는 채팅방 객체에 새 멤버를 추가한다.

            // 전달받은 id를 가진 유저가 서버 접속자 리스트에 있으면 바로 채팅방 멤버에 포함시키고,
            // 아니면 소켓이 할당되지 않는 유저객체를 만든다음 포함시킨다. (해당 유저가 현재 소켓에 접속하지 않은 경우)
            boolean isFind = false;

            log.debug("서버 접속자 명단 순회");
            // 서버 접속자 명단 순회
            for (int i = 0; i < server.userList.size(); i++) {
                User connectedUser = server.userList.get(i);
                if (userId == connectedUser.id) {
                    // 접속자 찾음
                    room.userList.add(connectedUser);
                    connectedUserList.add(userId);
                    isFind = true;
                    break;
                }
            }

            // 제공받은 유저 정보가 접속자 중에 없으면
            if (!isFind) {
                // 새 객체 생성 후 채팅방 멤버로 추가
                User tempUser = new User(userId);
                notConnectedUserList.add(userId);
                room.userList.add(tempUser);
            }
            log.debug("id={} / 새 채팅방에 멤버로 추가됨", userId);

        }
        log.debug("멤버중에 현재 소켓서버에 접속한 인원목록 = {}", connectedUserList);

        // 채팅 메시지 객체를 생성 해서 db에 저장하여 id를 얻는다.(메시지 간 id 중복 방지)
        // 전송여부는 info 커맨드의 그것을 사용하고, 읽음 여부는 기본적으로 읽음으로 처리한다.
        // 메시지 문구 자체는 클라이언트에서 생성해야하기 때문에 여기는 메시지가 존재한다는 사실만 기록한다.
        SendMessageCommand messageCommand = new SendMessageCommand(0L, roomId, "", MessageType.TEXT,
                TransmissionStatus.NOT_SENT, ReadStatus.READ);

        // 생성한 메시지 정보를 db에 저장한다.
        Long messageId = DBHelper.getInstance().insertMessage(messageCommand);
        command.messageId = messageId; // 생성된 메시지 id 할당

        // nickname, profileImage가 추가된 리스트로 변경
        command.memberList = DBHelper.getInstance().getMemberData(roomId);

        // 채팅방에 대한 추가 정보 입력
        RoomData roomData = DBHelper.getInstance().getRoomData(roomId);
        command.roomName = roomData.roomName;
        command.description = roomData.description;
        command.masterId = roomData.masterUserId;
        command.roomType = roomData.roomType;

        log.debug("isNewOpenChat = {}", command.isNewOpenChatMember);

        // 기존 멤버들에게 새로운 멤버 추가사실을 알린다.
        // ex) oo님이 xx님을 초대했습니다.
        room.broadcastToRoom(command);
        log.debug("roomInvite : END");
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

    // 새로운 채팅방 생성
    private void createRoom(CreateRoomCommand command) {
        log.info("createRoom: START");

        // 새 채팅방 객체 생성
        ChatRoom newRoom = new ChatRoom();

        // 새 채팅방을 db에 추가 후, id 획득
        Long roomId = null;

        log.info("생성할 방 타입 = {}", command.roomType);
        if (command.roomType == CreateRoomCommand.RoomType.NORMAL) {
            try {
                roomId = DBHelper.getInstance().insertRoom(null, null, command.roomType, 0L);
            } catch (SQLException e) {
                e.printStackTrace();
                log.error(e);
            }
            newRoom.id = roomId;

        } else if (command.roomType == CreateRoomCommand.RoomType.OPEN) {

            // db 에 방 정보 추가
            try {
                roomId = DBHelper.getInstance().insertRoom(command.roomName, command.description, command.roomType,
                        command.requesterId);
            } catch (SQLException e) {
                e.printStackTrace();
                log.error(e);
            }

            // 서버에 저장할 방 데이터 저장
            RoomData roomData = DBHelper.getInstance().getRoomData(roomId);
            newRoom.id = roomData.id;
            newRoom.roomName = roomData.roomName;
            newRoom.description = roomData.description;
            newRoom.roomType = roomData.roomType;
            newRoom.masterUserId = roomData.masterUserId;
        }

        // 서버의 방 목록에 새 방을 추가
        ChatServer.roomMap.put(roomId, newRoom);
        log.debug("새로운 방 객체를 roomMap에 저장 key={}, value={}", roomId, newRoom);

        // 멤버 초대 커맨드 생성
        InviteCommand inviteCommand = new InviteCommand(0L, roomId, null, command.invitedIdList, null);
        inviteCommand.requesterId = command.requesterId;
        inviteCommand.roomType = command.roomType;

        if (CreateRoomCommand.RoomType.OPEN == command.roomType) {
            // 오픈 채팅인 경우 추가 데이터 포함
            inviteCommand.roomName = command.roomName;
            inviteCommand.description = command.description;
            inviteCommand.masterId = command.requesterId;
        }

        // 방 생성자는 자기 자신을 초대하는 것으로 처리 됨
        roomInvite(inviteCommand);

        log.info("createRoom: END");
        return;
    }

    // 방 정보를 클라이언트에 반환
    private void roomInfo(RoomInfoCommand command) throws Exception {
        log.info("roomInfo: START - params: command={}", command);

        // 파싱
        long roomId = command.roomId;
        log.debug("roomId={}", roomId);

        ChatRoom room = ChatServer.roomMap.get(roomId);

        if (room == null) {
            log.debug("요구한 채팅방이 존재하지 않음, roomId = {}", roomId);
            throw new Exception("요구한 채팅방이 존재하지 않음, roomId = {}");
        }

        boolean isInfoChange = false;

        // 수신된 커맨드에 방이름이나, 소개글이 null, 공백이 아니면 전달받은 값으로 DB 컬럼을 수정한다.
        if (command.roomName != null && !command.roomName.isEmpty()) {
            DBHelper.getInstance().updateRoomName(roomId, command.roomName);
            isInfoChange = true;
        }

        if (command.description != null) {
            // 소개글은 공백 허용
            DBHelper.getInstance().updateRoomDescription(roomId, command.description);
            isInfoChange = true;
        }

        // 방데이터
        RoomData roomData = DBHelper.getInstance().getRoomData(roomId);

        // 멤버 데이터
        List<UserData> userDatas = DBHelper.getInstance().getMemberData(roomId);

        // 클라이언트 응답값 생성
        RoomInfoCommand roomInfoCommand = new RoomInfoCommand(command.requesterId, roomId, userDatas);
        roomInfoCommand.masterId = roomData.masterUserId;
        roomInfoCommand.roomName = roomData.roomName;
        roomInfoCommand.description = roomData.description;
        roomInfoCommand.roomType = roomData.roomType;

        if (isInfoChange) {
            // 방 정보가 바뀌면 모든 멤버에게 정보가 바겼음을 알림
            room.broadcastToRoom(roomInfoCommand);
        } else {
            this.sendMsg(roomInfoCommand, true);
        }

        log.info("roomInfo: END");
    }

    // 채팅방 나가기
    public void roomExit(ExitRoomCommand command) {
        log.debug("roomExit: START");

        Long roomId = command.roomId;
        Long userId = command.requesterId;

        // 채팅방 객체에서 해당 유저 멤버에서 제거
        ChatRoom chatRoom = ChatServer.roomMap.get(roomId);
        List<User> userList = chatRoom.userList;

        // 방에서 id가 동일한 유저 제거
        for (int i = 0; i < userList.size(); i++) {
            if (userList.get(i).id == userId) {
                userList.remove(i);
                log.debug("userId : {} 가 roomId : {} 에서 제거됨", userId, roomId);
                break;
            }
        }

        // db 에서도 퇴장처리
        DBHelper.getInstance().exitRoom(roomId, userId);

        // 만약 나간사람이 방장이면 해당 사실도 기록한다.
        RoomData roomData = DBHelper.getInstance().getRoomData(roomId);

        if (roomData.masterUserId == userId) {
            log.info("방장이 퇴장함");
            DBHelper.getInstance().exitHost(roomId);
        }

        // 남은 사람이 하나도 없으면 db에서 테이블을 지우고 여기서 종료한다.
        boolean isNoMember = true;
        List<UserData> members = DBHelper.getInstance().getMemberData(roomId);
        for (UserData u : members) {
            // exitedAt이 null인 사람이 한 명이라도 있다면 아직 채팅방에 사람이 남아있는 것이다.
            if (u.isExit == false) {
                log.debug("아직 안 나간 사람 발견");
                isNoMember = false;
                break;
            }
        }

        if (isNoMember) {
            DBHelper.getInstance().deleteRoom(roomId);
            log.info("roomExit: END - 방에 아무도 없어서 db에서 제거함 roomId={}", roomId);
            return;
        }

        // 해당 채팅방에 메시지가 올라온 기록이 없는 경우도 db에서 제거
        if (!DBHelper.getInstance().isChatMessageExist(roomId)) {
            DBHelper.getInstance().deleteRoom(roomId);
            log.info("roomExit: END - 이 채팅방에서 전송된 메시지가 하나도 없이 생성자가 나가서 db에서 제거함roomId={}", roomId);
            return;
        }

        // 메시지 id 획득용 더미 메시지
        SendMessageCommand messageCommand = new SendMessageCommand(0L, roomId, "", MessageType.TEXT,
                TransmissionStatus.NOT_SENT, ReadStatus.READ);

        // 생성한 메시지 정보를 db에 저장한다.
        Long messageId = DBHelper.getInstance().insertMessage(messageCommand);
        command.messageId = messageId; // 삽입된 id 할당

        log.debug("roomData.roomName = {}, roomData.description = {}", roomData.roomName, roomData.description);
        command.roomName = roomData.roomName;
        command.description = roomData.description;
        log.debug("command.roomName = {}, command.description = {}", command.roomName, command.description);

        // 남은 멤버들에게 이 유저가 나갔음을 알림
        chatRoom.broadcastToRoom(command);
        log.debug("roomExit: END");
    }

    // 메시지 전송요청 처리
    public void SendMessage(SendMessageCommand command) {
        log.debug("SendMessage: START - params: command={}", command);

        // 전달받은 메시지는 일단 NOT_SENT, UNREAD 로 설정
        command.transmissionStatus = TransmissionStatus.NOT_SENT;
        command.readStatus = ReadStatus.UNREAD;

        // 받은 메시지 정보를 db에 저장한다.
        Long lastInsertedId = DBHelper.getInstance().insertMessage(command);
        command.messageId = lastInsertedId;

        // 삽입된 id로 메시지 상태 테이블을 insert한다.
        DBHelper.getInstance().insertMessageReadStatus(command);

        // 메시지를 채팅방 모두(전송자 포함)에게 전송한다.
        ChatServer.roomMap.get(command.roomId).broadcastToRoom(command);
        log.debug("SendMessage: END");
    }

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