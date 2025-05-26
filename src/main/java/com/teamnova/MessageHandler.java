package com.teamnova;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.teamnova.command.CheckReceiveCommand;
import com.teamnova.command.CreateRoomCommand;
import com.teamnova.command.ExitRoomCommand;
import com.teamnova.command.InviteCommand;
import com.teamnova.command.ResponseCommand.TransmissionStatus;
import com.teamnova.command.RoomInfoCommand;
import com.teamnova.command.SendMessageCommand;
import com.teamnova.command.SendMessageCommand.MessageType;
import com.teamnova.command.SendMessageCommand.ReadStatus;
import com.teamnova.dto.RoomData;

/**
 * 메시지 처리를 담당하는 클래스
 */
public class MessageHandler {

    private static Logger log = LogManager.getLogger(MessageHandler.class.getName());

    private User user;
    private DBHelper dbHelper;

    /**
     * 생성자
     * 
     * @param user 사용자 객체
     */
    public MessageHandler(User user) {
        this.user = user;
        this.dbHelper = DBHelper.getInstance();
    }

    /**
     * 메시지 전송요청 처리
     */
    public void sendMessage(SendMessageCommand command) {
        log.debug("SendMessage: START - params: command={}", command);

        // 전달받은 메시지는 일단 NOT_SENT, UNREAD 로 설정
        command.transmissionStatus = TransmissionStatus.NOT_SENT;
        command.readStatus = ReadStatus.UNREAD;

        // 받은 메시지 정보를 db에 저장한다.
        Long lastInsertedId = dbHelper.insertMessage(command);
        command.messageId = lastInsertedId;

        // 삽입된 id로 메시지 상태 테이블을 insert한다.
        dbHelper.insertMessageReadStatus(command);

        // 메시지를 채팅방 모두(전송자 포함)에게 전송한다.
        ChatServer.roomMap.get(command.roomId).broadcastToRoom(command);
        log.debug("SendMessage: END");
    }

    /**
     * 수신확인 처리
     */
    public void checkReceive(CheckReceiveCommand command) {
        log.debug("checkReceive() : START - params: command = {}", command);

        // db에 해당 커맨드의 수신 결과를 기록한다.
        dbHelper.updateResponseCommandStatus(command.commandId, TransmissionStatus.SENT);

        log.debug("checkReceive() : END");
    }

    /**
     * 새로운 채팅방 생성
     */
    public void createRoom(CreateRoomCommand command) {
        log.info("createRoom: START");

        // 새 채팅방 객체 생성
        ChatRoom newRoom = new ChatRoom();

        // 새 채팅방을 db에 추가 후, id 획득
        Long roomId = null;

        log.info("생성할 방 타입 = {}", command.roomType);
        if (command.roomType == CreateRoomCommand.RoomType.NORMAL) {
            try {
                roomId = dbHelper.insertRoom(null, null, command.roomType, 0L);
            } catch (SQLException e) {
                e.printStackTrace();
                log.error(e);
            }
            newRoom.id = roomId;

        } else if (command.roomType == CreateRoomCommand.RoomType.OPEN) {

            // db 에 방 정보 추가
            try {
                roomId = dbHelper.insertRoom(command.roomName, command.description, command.roomType,
                        command.requesterId);
            } catch (SQLException e) {
                e.printStackTrace();
                log.error(e);
            }

            // 서버에 저장할 방 데이터 저장
            RoomData roomData = dbHelper.getRoomData(roomId);
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
    }

    /**
     * 방 정보를 클라이언트에 반환
     */
    public void roomInfo(RoomInfoCommand command) throws Exception {
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
            dbHelper.updateRoomName(roomId, command.roomName);
            isInfoChange = true;
        }

        if (command.description != null) {
            // 소개글은 공백 허용
            dbHelper.updateRoomDescription(roomId, command.description);
            isInfoChange = true;
        }

        // 방데이터
        RoomData roomData = dbHelper.getRoomData(roomId);

        // 멤버 데이터
        List<UserData> userDatas = dbHelper.getMemberData(roomId);

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
            user.sendMsg(roomInfoCommand, true);
        }

        log.info("roomInfo: END");
    }

    /**
     * 채팅방 나가기
     */
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
        dbHelper.exitRoom(roomId, userId);

        // 만약 나간사람이 방장이면 해당 사실도 기록한다.
        RoomData roomData = dbHelper.getRoomData(roomId);

        if (roomData.masterUserId == userId) {
            log.info("방장이 퇴장함");
            dbHelper.exitHost(roomId);
        }

        // 남은 사람이 하나도 없으면 db에서 테이블을 지우고 여기서 종료한다.
        boolean isNoMember = true;
        List<UserData> members = dbHelper.getMemberData(roomId);
        for (UserData u : members) {
            // exitedAt이 null인 사람이 한 명이라도 있다면 아직 채팅방에 사람이 남아있는 것이다.
            if (u.isExit == false) {
                log.debug("아직 안 나간 사람 발견");
                isNoMember = false;
                break;
            }
        }

        if (isNoMember) {
            dbHelper.deleteRoom(roomId);
            log.info("roomExit: END - 방에 아무도 없어서 db에서 제거함 roomId={}", roomId);
            return;
        }

        // 해당 채팅방에 메시지가 올라온 기록이 없는 경우도 db에서 제거
        if (!dbHelper.isChatMessageExist(roomId)) {
            dbHelper.deleteRoom(roomId);
            log.info("roomExit: END - 이 채팅방에서 전송된 메시지가 하나도 없이 생성자가 나가서 db에서 제거함roomId={}", roomId);
            return;
        }

        // 메시지 id 획득용 더미 메시지
        SendMessageCommand messageCommand = new SendMessageCommand(0L, roomId, "", MessageType.TEXT,
                TransmissionStatus.NOT_SENT, ReadStatus.READ);

        // 생성한 메시지 정보를 db에 저장한다.
        Long messageId = dbHelper.insertMessage(messageCommand);
        command.messageId = messageId; // 삽입된 id 할당

        log.debug("roomData.roomName = {}, roomData.description = {}", roomData.roomName, roomData.description);
        command.roomName = roomData.roomName;
        command.description = roomData.description;
        log.debug("command.roomName = {}, command.description = {}", command.roomName, command.description);

        // 남은 멤버들에게 이 유저가 나갔음을 알림
        chatRoom.broadcastToRoom(command);
        log.debug("roomExit: END");
    }

    /**
     * 채팅방 초대
     */
    public void roomInvite(InviteCommand command) {
        log.debug("roomInvite : START");

        Long roomId = command.roomId; // 초대한 방 id

        ChatRoom room = ChatServer.roomMap.get(roomId);

        // 로그용 리스트
        List<Long> connectedUserList = new ArrayList<>();
        List<Long> notConnectedUserList = new ArrayList<>();

        // 제공받은 유저정보 목록을 통해 유저들을 방 멤버로 추가
        for (Long userId : command.invitedIdList) {

            // 멤버 - 채팅방 관계 테이블 insert
            dbHelper.insertUserChatRoomsRelation(roomId, userId);
            log.debug("uesrId={}, roomId={} 관계테이블 레코드 생성", userId, roomId);

            // 서버에 있는 채팅방 객체에 새 멤버를 추가한다.

            // 전달받은 id를 가진 유저가 서버 접속자 리스트에 있으면 바로 채팅방 멤버에 포함시키고,
            // 아니면 소켓이 할당되지 않는 유저객체를 만든다음 포함시킨다. (해당 유저가 현재 소켓에 접속하지 않은 경우)
            boolean isFind = false;

            log.debug("서버 접속자 명단 순회");
            // 서버 접속자 명단 순회
            for (int i = 0; i < user.server.userList.size(); i++) {
                User connectedUser = user.server.userList.get(i);
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
        Long messageId = dbHelper.insertMessage(messageCommand);
        command.messageId = messageId; // 생성된 메시지 id 할당

        // nickname, profileImage가 추가된 리스트로 변경
        command.memberList = dbHelper.getMemberData(roomId);

        // 채팅방에 대한 추가 정보 입력
        RoomData roomData = dbHelper.getRoomData(roomId);
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
}