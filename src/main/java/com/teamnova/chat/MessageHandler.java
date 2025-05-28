package com.teamnova.chat;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.teamnova.command.ResponseCommand.TransmissionStatus;
import com.teamnova.command.chat.CheckReceiveCommand;
import com.teamnova.command.chat.CreateRoomCommand;
import com.teamnova.command.chat.ExitRoomCommand;
import com.teamnova.command.chat.InviteCommand;
import com.teamnova.command.chat.RoomInfoCommand;
import com.teamnova.command.chat.SendMessageCommand;
import com.teamnova.command.chat.SendMessageCommand.ReadStatus;
import com.teamnova.command.chat.SendMessageCommand.Type;
import com.teamnova.database.DBHelper;
import com.teamnova.dto.chat.RoomData;
import com.teamnova.dto.user.UserData;
import com.teamnova.server.ChatServer;
import com.teamnova.user.User;
import com.teamnova.utils.LoggingConstants;
import com.teamnova.utils.LoggingUtils;
import com.teamnova.utils.PerformanceLogger;

/**
 * 메시지 처리를 담당하는 클래스
 * 
 * 이 클래스는 채팅 메시지 전송, 수신 확인, 채팅방 관리 등의
 * 메시지 관련 모든 기능을 처리합니다.
 */
public class MessageHandler {

        private static final Logger log = LogManager.getLogger(MessageHandler.class);
        private static final Logger performanceLog = LogManager.getLogger("PERFORMANCE");

        private User user;
        private DBHelper dbHelper;
        private String handlerId;

        /**
         * 생성자
         * 
         * @param user 사용자 객체
         */
        public MessageHandler(User user) {
                this.user = user;
                this.dbHelper = DBHelper.getInstance();
                this.handlerId = LoggingUtils.generateOperationId();

                log.info("MessageHandler 초기화: userId={}, sessionId={}, handlerId={}",
                                user.id, user.getSessionId(), handlerId);
        }

        /**
         * 메시지 전송요청 처리
         */
        public void sendMessage(SendMessageCommand command) {
                String operationId = LoggingUtils.generateOperationId();
                PerformanceLogger.Timer timer = PerformanceLogger.startTimer("sendMessage");

                log.info(LoggingConstants.MESSAGE_SEND_START,
                                user.id, user.getSessionId(), operationId, command.roomId,
                                command.type, LoggingUtils.sanitizeMessageContent(command.content, 50));

                try {
                        // 메시지 크기 체크
                        int messageSize = command.content != null ? command.content.length() : 0;
                        if (messageSize > LoggingConstants.THRESHOLD_MESSAGE_SIZE) {
                                log.warn("대용량 메시지 감지: userId={}, sessionId={}, operationId={}, roomId={}, messageSize={}bytes",
                                                user.id, user.getSessionId(), operationId, command.roomId, messageSize);
                        }

                        // 전달받은 메시지는 일단 NOT_SENT, UNREAD 로 설정
                        command.transmissionStatus = TransmissionStatus.NOT_SENT;
                        command.readStatus = ReadStatus.UNREAD;

                        log.debug(
                                        "메시지 상태 초기화: userId={}, sessionId={}, operationId={}, roomId={}, transmissionStatus={}, readStatus={}",
                                        user.id, user.getSessionId(), operationId, command.roomId,
                                        command.transmissionStatus, command.readStatus);

                        // 받은 메시지 정보를 db에 저장한다.
                        Long lastInsertedId = dbHelper.insertMessage(command);
                        command.messageId = lastInsertedId;

                        log.debug("메시지 DB 저장 완료: userId={}, sessionId={}, operationId={}, roomId={}, messageId={}",
                                        user.id, user.getSessionId(), operationId, command.roomId, lastInsertedId);

                        // 삽입된 id로 메시지 상태 테이블을 insert한다.
                        dbHelper.insertMessageReadStatus(command);

                        log.debug("메시지 읽음 상태 테이블 생성: userId={}, sessionId={}, operationId={}, roomId={}, messageId={}",
                                        user.id, user.getSessionId(), operationId, command.roomId, command.messageId);

                        // 채팅방 존재 여부 확인
                        ChatRoom chatRoom = ChatServer.roomMap.get(command.roomId);
                        if (chatRoom == null) {
                                log.error(LoggingConstants.ERROR_ROOM_NOT_FOUND,
                                                user.id, user.getSessionId(), operationId, command.roomId);
                                throw new IllegalArgumentException("채팅방을 찾을 수 없습니다: roomId=" + command.roomId);
                        }

                        // 메시지를 채팅방 모두(전송자 포함)에게 전송한다.
                        int memberCount = chatRoom.userList.size();
                        log.debug("채팅방 브로드캐스트 시작: userId={}, sessionId={}, operationId={}, roomId={}, memberCount={}",
                                        user.id, user.getSessionId(), operationId, command.roomId, memberCount);

                        chatRoom.broadcastToRoom(command);

                        long duration = timer.stop();
                        log.info(LoggingConstants.MESSAGE_SEND_SUCCESS,
                                        user.id, user.getSessionId(), operationId, command.roomId,
                                        command.messageId, memberCount, duration, messageSize);

                        // 성능 메트릭 기록
                        performanceLog.info(
                                        "메시지 전송 성능: operationId={}, roomId={}, memberCount={}, messageSize={}bytes, duration={}ms",
                                        operationId, command.roomId, memberCount, messageSize, duration);

                        // 임계값 체크
                        if (duration > LoggingConstants.THRESHOLD_MESSAGE_PROCESSING_TIME) {
                                log.warn(
                                                "메시지 처리 시간 임계값 초과: userId={}, sessionId={}, operationId={}, roomId={}, duration={}ms, threshold={}ms",
                                                user.id, user.getSessionId(), operationId, command.roomId, duration,
                                                LoggingConstants.THRESHOLD_MESSAGE_PROCESSING_TIME);
                        }

                } catch (Exception e) {
                        timer.stop("ERROR: " + e.getMessage());
                        log.error(LoggingConstants.ERROR_MESSAGE_SEND_FAILED,
                                        user.id, user.getSessionId(), operationId, command.roomId, e.getMessage(), e);
                        throw e;
                }
        }

        /**
         * 수신확인 처리
         */
        public void checkReceive(CheckReceiveCommand command) {
                String operationId = LoggingUtils.generateOperationId();
                PerformanceLogger.Timer timer = PerformanceLogger.startDatabaseTimer("checkReceive",
                                "response_commands");

                log.debug("수신 확인 처리 시작: userId={}, sessionId={}, operationId={}, commandId={}",
                                user.id, user.getSessionId(), operationId, command.commandId);

                try {
                        // db에 해당 커맨드의 수신 결과를 기록한다.
                        dbHelper.updateResponseCommandStatus(command.commandId, TransmissionStatus.SENT);

                        long duration = timer.stop();
                        log.info("수신 확인 처리 완료: userId={}, sessionId={}, operationId={}, commandId={}, duration={}ms",
                                        user.id, user.getSessionId(), operationId, command.commandId, duration);

                        // 성능 임계값 체크
                        if (duration > LoggingConstants.PERFORMANCE_DB_QUERY_WARN_MS) {
                                log.warn(
                                                "수신 확인 처리 시간 임계값 초과: userId={}, sessionId={}, operationId={}, commandId={}, duration={}ms, threshold={}ms",
                                                user.id, user.getSessionId(), operationId, command.commandId, duration,
                                                LoggingConstants.PERFORMANCE_DB_QUERY_WARN_MS);
                        }

                } catch (Exception e) {
                        timer.stop("ERROR: " + e.getMessage());
                        log.error("수신 확인 처리 실패: userId={}, sessionId={}, operationId={}, commandId={}, error={}",
                                        user.id, user.getSessionId(), operationId, command.commandId, e.getMessage(),
                                        e);
                        throw e;
                }
        }

        /**
         * 새로운 채팅방 생성
         */
        public void createRoom(CreateRoomCommand command) {
                String operationId = LoggingUtils.generateOperationId();
                PerformanceLogger.Timer timer = PerformanceLogger.startTimer("createRoom");

                log.info("채팅방 생성 시작: userId={}, sessionId={}, operationId={}, roomType={}, roomName={}, invitedCount={}",
                                user.id, user.getSessionId(), operationId, command.roomType,
                                LoggingUtils.sanitizeMessageContent(command.roomName, 30),
                                command.invitedIdList != null ? command.invitedIdList.size() : 0);

                try {
                        // 새 채팅방 객체 생성
                        ChatRoom newRoom = new ChatRoom();
                        Long roomId = null;

                        log.debug("채팅방 객체 생성 완료: userId={}, sessionId={}, operationId={}, roomType={}",
                                        user.id, user.getSessionId(), operationId, command.roomType);

                        // 방 타입별 처리
                        if (command.roomType == CreateRoomCommand.RoomType.NORMAL) {
                                log.debug("일반 채팅방 생성 처리: userId={}, sessionId={}, operationId={}",
                                                user.id, user.getSessionId(), operationId);

                                PerformanceLogger.Timer dbTimer = PerformanceLogger.startDatabaseTimer("insertRoom",
                                                "chat_rooms");
                                try {
                                        roomId = dbHelper.insertRoom(null, null, command.roomType, 0L);
                                        long dbDuration = dbTimer.stop();

                                        log.debug("일반 채팅방 DB 저장 완료: userId={}, sessionId={}, operationId={}, roomId={}, dbDuration={}ms",
                                                        user.id, user.getSessionId(), operationId, roomId, dbDuration);
                                } catch (SQLException e) {
                                        dbTimer.stop("ERROR: " + e.getMessage());
                                        log.error("일반 채팅방 DB 저장 실패: userId={}, sessionId={}, operationId={}, error={}",
                                                        user.id, user.getSessionId(), operationId, e.getMessage(), e);
                                        throw new RuntimeException("채팅방 생성 실패", e);
                                }
                                newRoom.id = roomId;

                        } else if (command.roomType == CreateRoomCommand.RoomType.OPEN) {
                                log.debug("오픈 채팅방 생성 처리: userId={}, sessionId={}, operationId={}, roomName={}, description={}",
                                                user.id, user.getSessionId(), operationId,
                                                LoggingUtils.sanitizeMessageContent(command.roomName, 30),
                                                LoggingUtils.sanitizeMessageContent(command.description, 50));

                                PerformanceLogger.Timer dbTimer = PerformanceLogger.startDatabaseTimer("insertRoom",
                                                "chat_rooms");
                                try {
                                        roomId = dbHelper.insertRoom(command.roomName, command.description,
                                                        command.roomType,
                                                        command.requesterId);
                                        long dbDuration = dbTimer.stop();

                                        log.debug("오픈 채팅방 DB 저장 완료: userId={}, sessionId={}, operationId={}, roomId={}, dbDuration={}ms",
                                                        user.id, user.getSessionId(), operationId, roomId, dbDuration);
                                } catch (SQLException e) {
                                        dbTimer.stop("ERROR: " + e.getMessage());
                                        log.error("오픈 채팅방 DB 저장 실패: userId={}, sessionId={}, operationId={}, roomName={}, error={}",
                                                        user.id, user.getSessionId(), operationId, command.roomName,
                                                        e.getMessage(), e);
                                        throw new RuntimeException("채팅방 생성 실패", e);
                                }

                                // 서버에 저장할 방 데이터 로드
                                PerformanceLogger.Timer dataLoadTimer = PerformanceLogger.startDatabaseTimer(
                                                "getRoomData",
                                                "chat_rooms");
                                try {
                                        RoomData roomData = dbHelper.getRoomData(roomId);
                                        long loadDuration = dataLoadTimer.stop();

                                        newRoom.id = roomData.id;
                                        newRoom.roomName = roomData.roomName;
                                        newRoom.description = roomData.description;
                                        newRoom.roomType = roomData.roomType;
                                        newRoom.masterUserId = roomData.masterUserId;

                                        log.debug("채팅방 데이터 로드 완료: userId={}, sessionId={}, operationId={}, roomId={}, loadDuration={}ms",
                                                        user.id, user.getSessionId(), operationId, roomId,
                                                        loadDuration);
                                } catch (Exception e) {
                                        dataLoadTimer.stop("ERROR: " + e.getMessage());
                                        log.error("채팅방 데이터 로드 실패: userId={}, sessionId={}, operationId={}, roomId={}, error={}",
                                                        user.id, user.getSessionId(), operationId, roomId,
                                                        e.getMessage(), e);
                                        throw e;
                                }
                        }

                        // 서버의 방 목록에 새 방을 추가
                        ChatServer.roomMap.put(roomId, newRoom);
                        log.debug("채팅방 서버 등록 완료: userId={}, sessionId={}, operationId={}, roomId={}, totalRooms={}",
                                        user.id, user.getSessionId(), operationId, roomId, ChatServer.roomMap.size());

                        // 멤버 초대 커맨드 생성
                        InviteCommand inviteCommand = new InviteCommand(0L, roomId, null, command.invitedIdList, null);
                        inviteCommand.requesterId = command.requesterId;
                        inviteCommand.roomType = command.roomType;

                        if (CreateRoomCommand.RoomType.OPEN == command.roomType) {
                                // 오픈 채팅인 경우 추가 데이터 포함
                                inviteCommand.roomName = command.roomName;
                                inviteCommand.description = command.description;
                                inviteCommand.masterId = command.requesterId;

                                log.debug("오픈 채팅방 초대 명령 생성: userId={}, sessionId={}, operationId={}, roomId={}, masterId={}",
                                                user.id, user.getSessionId(), operationId, roomId, command.requesterId);
                        }

                        log.debug("멤버 초대 처리 시작: userId={}, sessionId={}, operationId={}, roomId={}, invitedCount={}",
                                        user.id, user.getSessionId(), operationId, roomId,
                                        command.invitedIdList.size());

                        // 방 생성자는 자기 자신을 초대하는 것으로 처리 됨
                        roomInvite(inviteCommand);

                        long duration = timer.stop();
                        log.info(LoggingConstants.ROOM_CREATED,
                                        roomId, LoggingUtils.sanitizeMessageContent(command.roomName, 30),
                                        command.requesterId, command.invitedIdList.size(), command.roomType);

                        log.info(
                                        "채팅방 생성 완료: userId={}, sessionId={}, operationId={}, roomId={}, roomType={}, duration={}ms, invitedCount={}",
                                        user.id, user.getSessionId(), operationId, roomId, command.roomType, duration,
                                        command.invitedIdList.size());

                } catch (Exception e) {
                        timer.stop("ERROR: " + e.getMessage());
                        log.error("채팅방 생성 실패: userId={}, sessionId={}, operationId={}, roomType={}, error={}",
                                        user.id, user.getSessionId(), operationId, command.roomType, e.getMessage(), e);
                        throw e;
                }
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
                String operationId = LoggingUtils.generateOperationId();
                PerformanceLogger.Timer timer = PerformanceLogger.startTimer("roomExit");

                log.info("채팅방 나가기 시작: userId={}, sessionId={}, operationId={}, roomId={}, requesterId={}",
                                user.id, user.getSessionId(), operationId, command.roomId, command.requesterId);

                try {
                        Long roomId = command.roomId;
                        Long userId = command.requesterId;

                        // 채팅방 객체에서 해당 유저 멤버에서 제거
                        ChatRoom chatRoom = ChatServer.roomMap.get(roomId);

                        if (chatRoom == null) {
                                log.error("채팅방을 찾을 수 없음: userId={}, sessionId={}, operationId={}, roomId={}",
                                                user.id, user.getSessionId(), operationId, roomId);
                                throw new RuntimeException("채팅방을 찾을 수 없습니다: " + roomId);
                        }

                        List<User> userList = chatRoom.userList;
                        int initialMemberCount = userList.size();
                        boolean userRemoved = false;

                        log.debug("채팅방 멤버 제거 시작: userId={}, sessionId={}, operationId={}, roomId={}, currentMembers={}",
                                        user.id, user.getSessionId(), operationId, roomId, initialMemberCount);

                        // 방에서 id가 동일한 유저 제거
                        for (int i = 0; i < userList.size(); i++) {
                                if (userList.get(i).id == userId) {
                                        userList.remove(i);
                                        userRemoved = true;
                                        log.debug("채팅방 멤버 제거 성공: userId={}, sessionId={}, operationId={}, roomId={}, removedUserId={}",
                                                        user.id, user.getSessionId(), operationId, roomId, userId);
                                        break;
                                }
                        }

                        if (!userRemoved) {
                                log.warn("채팅방에서 제거할 사용자를 찾을 수 없음: userId={}, sessionId={}, operationId={}, roomId={}, targetUserId={}",
                                                user.id, user.getSessionId(), operationId, roomId, userId);
                        }

                        // db 에서도 퇴장처리
                        PerformanceLogger.Timer dbExitTimer = PerformanceLogger.startDatabaseTimer("exitRoom",
                                        "user_chatroom_map");
                        try {
                                dbHelper.exitRoom(roomId, userId);
                                long dbExitDuration = dbExitTimer.stop();

                                log.debug(
                                                "DB 퇴장 처리 완료: userId={}, sessionId={}, operationId={}, roomId={}, targetUserId={}, duration={}ms",
                                                user.id, user.getSessionId(), operationId, roomId, userId,
                                                dbExitDuration);
                        } catch (Exception e) {
                                dbExitTimer.stop("ERROR: " + e.getMessage());
                                log.error("DB 퇴장 처리 실패: userId={}, sessionId={}, operationId={}, roomId={}, targetUserId={}, error={}",
                                                user.id, user.getSessionId(), operationId, roomId, userId,
                                                e.getMessage(), e);
                                throw e;
                        }

                        // 채팅방 데이터 로드
                        PerformanceLogger.Timer roomDataTimer = PerformanceLogger.startDatabaseTimer("getRoomData",
                                        "chat_rooms");
                        RoomData roomData;
                        try {
                                roomData = dbHelper.getRoomData(roomId);
                                long roomDataDuration = roomDataTimer.stop();

                                log.debug(
                                                "채팅방 데이터 로드 완료: userId={}, sessionId={}, operationId={}, roomId={}, masterId={}, duration={}ms",
                                                user.id, user.getSessionId(), operationId, roomId,
                                                roomData.masterUserId, roomDataDuration);
                        } catch (Exception e) {
                                roomDataTimer.stop("ERROR: " + e.getMessage());
                                log.error("채팅방 데이터 로드 실패: userId={}, sessionId={}, operationId={}, roomId={}, error={}",
                                                user.id, user.getSessionId(), operationId, roomId, e.getMessage(), e);
                                throw e;
                        }

                        // 만약 나간사람이 방장이면 해당 사실도 기록한다.
                        boolean isMasterExit = roomData.masterUserId != null && roomData.masterUserId.equals(userId);
                        if (isMasterExit) {
                                log.info("방장 퇴장 감지: userId={}, sessionId={}, operationId={}, roomId={}, masterId={}",
                                                user.id, user.getSessionId(), operationId, roomId, userId);

                                PerformanceLogger.Timer exitHostTimer = PerformanceLogger.startDatabaseTimer("exitHost",
                                                "chat_rooms");
                                try {
                                        dbHelper.exitHost(roomId);
                                        long exitHostDuration = exitHostTimer.stop();

                                        log.debug("방장 퇴장 DB 처리 완료: userId={}, sessionId={}, operationId={}, roomId={}, duration={}ms",
                                                        user.id, user.getSessionId(), operationId, roomId,
                                                        exitHostDuration);
                                } catch (Exception e) {
                                        exitHostTimer.stop("ERROR: " + e.getMessage());
                                        log.error("방장 퇴장 DB 처리 실패: userId={}, sessionId={}, operationId={}, roomId={}, error={}",
                                                        user.id, user.getSessionId(), operationId, roomId,
                                                        e.getMessage(), e);
                                        throw e;
                                }
                        }

                        // 남은 멤버 확인
                        PerformanceLogger.Timer memberCheckTimer = PerformanceLogger.startDatabaseTimer("getMemberData",
                                        "user_chatroom_map");
                        List<UserData> members;
                        try {
                                members = dbHelper.getMemberData(roomId);
                                long memberCheckDuration = memberCheckTimer.stop();

                                log.debug(
                                                "멤버 데이터 로드 완료: userId={}, sessionId={}, operationId={}, roomId={}, totalMembers={}, duration={}ms",
                                                user.id, user.getSessionId(), operationId, roomId, members.size(),
                                                memberCheckDuration);
                        } catch (Exception e) {
                                memberCheckTimer.stop("ERROR: " + e.getMessage());
                                log.error("멤버 데이터 로드 실패: userId={}, sessionId={}, operationId={}, roomId={}, error={}",
                                                user.id, user.getSessionId(), operationId, roomId, e.getMessage(), e);
                                throw e;
                        }

                        // 남은 사람이 하나도 없으면 db에서 테이블을 지우고 여기서 종료한다.
                        boolean isNoMember = true;
                        int activeMemberCount = 0;
                        for (UserData u : members) {
                                // exitedAt이 null인 사람이 한 명이라도 있다면 아직 채팅방에 사람이 남아있는 것이다.
                                if (!u.isExit) {
                                        activeMemberCount++;
                                        isNoMember = false;
                                }
                        }

                        log.debug(
                                        "멤버 상태 확인 완료: userId={}, sessionId={}, operationId={}, roomId={}, totalMembers={}, activeMembers={}, isEmpty={}",
                                        user.id, user.getSessionId(), operationId, roomId, members.size(),
                                        activeMemberCount, isNoMember);

                        if (isNoMember) {
                                PerformanceLogger.Timer deleteRoomTimer = PerformanceLogger.startDatabaseTimer(
                                                "deleteRoom",
                                                "chat_rooms");
                                try {
                                        dbHelper.deleteRoom(roomId);
                                        long deleteRoomDuration = deleteRoomTimer.stop();
                                        long totalDuration = timer.stop();

                                        log.info(
                                                        "빈 채팅방 삭제 완료: userId={}, sessionId={}, operationId={}, roomId={}, deleteRoomDuration={}ms, totalDuration={}ms",
                                                        user.id, user.getSessionId(), operationId, roomId,
                                                        deleteRoomDuration, totalDuration);
                                        return;
                                } catch (Exception e) {
                                        deleteRoomTimer.stop("ERROR: " + e.getMessage());
                                        log.error("빈 채팅방 삭제 실패: userId={}, sessionId={}, operationId={}, roomId={}, error={}",
                                                        user.id, user.getSessionId(), operationId, roomId,
                                                        e.getMessage(), e);
                                        throw e;
                                }
                        }

                        // 해당 채팅방에 메시지가 올라온 기록이 없는 경우도 db에서 제거
                        PerformanceLogger.Timer messageCheckTimer = PerformanceLogger.startDatabaseTimer(
                                        "isChatMessageExist",
                                        "messages");
                        boolean hasMessages;
                        try {
                                hasMessages = dbHelper.isChatMessageExist(roomId);
                                long messageCheckDuration = messageCheckTimer.stop();

                                log.debug(
                                                "메시지 존재 여부 확인 완료: userId={}, sessionId={}, operationId={}, roomId={}, hasMessages={}, duration={}ms",
                                                user.id, user.getSessionId(), operationId, roomId, hasMessages,
                                                messageCheckDuration);
                        } catch (Exception e) {
                                messageCheckTimer.stop("ERROR: " + e.getMessage());
                                log.error("메시지 존재 여부 확인 실패: userId={}, sessionId={}, operationId={}, roomId={}, error={}",
                                                user.id, user.getSessionId(), operationId, roomId, e.getMessage(), e);
                                throw e;
                        }

                        if (!hasMessages) {
                                PerformanceLogger.Timer deleteEmptyRoomTimer = PerformanceLogger.startDatabaseTimer(
                                                "deleteRoom",
                                                "chat_rooms");
                                try {
                                        dbHelper.deleteRoom(roomId);
                                        long deleteEmptyRoomDuration = deleteEmptyRoomTimer.stop();
                                        long totalDuration = timer.stop();

                                        log.info(
                                                        "메시지 없는 채팅방 삭제 완료: userId={}, sessionId={}, operationId={}, roomId={}, deleteRoomDuration={}ms, totalDuration={}ms",
                                                        user.id, user.getSessionId(), operationId, roomId,
                                                        deleteEmptyRoomDuration, totalDuration);
                                        return;
                                } catch (Exception e) {
                                        deleteEmptyRoomTimer.stop("ERROR: " + e.getMessage());
                                        log.error("메시지 없는 채팅방 삭제 실패: userId={}, sessionId={}, operationId={}, roomId={}, error={}",
                                                        user.id, user.getSessionId(), operationId, roomId,
                                                        e.getMessage(), e);
                                        throw e;
                                }
                        }

                        // 퇴장 알림 메시지 생성 및 저장
                        PerformanceLogger.Timer exitMessageTimer = PerformanceLogger.startDatabaseTimer("insertMessage",
                                        "messages");
                        try {
                                SendMessageCommand messageCommand = new SendMessageCommand(roomId, "", Type.TEXT);
                                messageCommand.transmissionStatus = TransmissionStatus.NOT_SENT;
                                messageCommand.readStatus = ReadStatus.read;

                                Long messageId = dbHelper.insertMessage(messageCommand);
                                command.messageId = messageId;
                                long exitMessageDuration = exitMessageTimer.stop();

                                log.debug(
                                                "퇴장 메시지 DB 저장 완료: userId={}, sessionId={}, operationId={}, roomId={}, messageId={}, duration={}ms",
                                                user.id, user.getSessionId(), operationId, roomId, messageId,
                                                exitMessageDuration);
                        } catch (Exception e) {
                                exitMessageTimer.stop("ERROR: " + e.getMessage());
                                log.error("퇴장 메시지 DB 저장 실패: userId={}, sessionId={}, operationId={}, roomId={}, error={}",
                                                user.id, user.getSessionId(), operationId, roomId, e.getMessage(), e);
                                throw e;
                        }

                        // 채팅방 정보 설정
                        command.roomName = roomData.roomName;
                        command.description = roomData.description;

                        log.debug("퇴장 명령 정보 설정 완료: userId={}, sessionId={}, operationId={}, roomId={}, roomName={}, description={}",
                                        user.id, user.getSessionId(), operationId, roomId,
                                        LoggingUtils.sanitizeMessageContent(command.roomName, 50),
                                        LoggingUtils.sanitizeMessageContent(command.description, 100));

                        // 남은 멤버들에게 이 유저가 나갔음을 알림
                        PerformanceLogger.Timer broadcastTimer = PerformanceLogger.startTimer("broadcastToRoom");
                        try {
                                chatRoom.broadcastToRoom(command);
                                long broadcastDuration = broadcastTimer.stop();

                                log.debug(
                                                "퇴장 알림 브로드캐스트 완료: userId={}, sessionId={}, operationId={}, roomId={}, remainingMembers={}, duration={}ms",
                                                user.id, user.getSessionId(), operationId, roomId,
                                                chatRoom.userList.size(), broadcastDuration);
                        } catch (Exception e) {
                                broadcastTimer.stop("ERROR: " + e.getMessage());
                                log.error("퇴장 알림 브로드캐스트 실패: userId={}, sessionId={}, operationId={}, roomId={}, error={}",
                                                user.id, user.getSessionId(), operationId, roomId, e.getMessage(), e);
                                throw e;
                        }

                        long duration = timer.stop();

                        log.info(
                                        "채팅방 나가기 완료: userId={}, sessionId={}, operationId={}, roomId={}, exitUserId={}, isMasterExit={}, remainingMembers={}, duration={}ms",
                                        user.id, user.getSessionId(), operationId, roomId, userId, isMasterExit,
                                        chatRoom.userList.size(),
                                        duration);

                } catch (Exception e) {
                        timer.stop("ERROR: " + e.getMessage());
                        log.error("채팅방 나가기 실패: userId={}, sessionId={}, operationId={}, roomId={}, error={}",
                                        user.id, user.getSessionId(), operationId, command.roomId, e.getMessage(), e);
                        throw e;
                }
        }

        /**
         * 채팅방 초대
         */
        public void roomInvite(InviteCommand command) {
                String operationId = LoggingUtils.generateOperationId();
                PerformanceLogger.Timer timer = PerformanceLogger.startTimer("roomInvite");

                log.info("채팅방 초대 시작: userId={}, sessionId={}, operationId={}, roomId={}, invitedCount={}",
                                user.id, user.getSessionId(), operationId, command.roomId,
                                command.invitedIdList != null ? command.invitedIdList.size() : 0);

                try {
                        Long roomId = command.roomId;
                        ChatRoom room = ChatServer.roomMap.get(roomId);

                        if (room == null) {
                                log.error("채팅방을 찾을 수 없음: userId={}, sessionId={}, operationId={}, roomId={}",
                                                user.id, user.getSessionId(), operationId, roomId);
                                throw new RuntimeException("채팅방을 찾을 수 없습니다: " + roomId);
                        }

                        log.debug("채팅방 확인 완료: userId={}, sessionId={}, operationId={}, roomId={}, currentMembers={}",
                                        user.id, user.getSessionId(), operationId, roomId, room.userList.size());

                        // 로그용 리스트
                        List<Long> connectedUserList = new ArrayList<>();
                        List<Long> notConnectedUserList = new ArrayList<>();
                        int dbSuccessCount = 0;
                        int dbFailCount = 0;

                        // 제공받은 유저정보 목록을 통해 유저들을 방 멤버로 추가
                        PerformanceLogger.Timer dbTimer = PerformanceLogger.startDatabaseTimer(
                                        "insertUserChatRoomsRelation",
                                        "user_chatroom_map");

                        for (Long userId : command.invitedIdList) {
                                try {
                                        // 멤버 - 채팅방 관계 테이블 insert
                                        dbHelper.insertUserChatRoomsRelation(roomId, userId);
                                        dbSuccessCount++;

                                        log.trace(
                                                        "사용자 채팅방 관계 DB 저장 성공: userId={}, sessionId={}, operationId={}, roomId={}, invitedUserId={}",
                                                        user.id, user.getSessionId(), operationId, roomId, userId);
                                } catch (Exception e) {
                                        dbFailCount++;
                                        log.error(
                                                        "사용자 채팅방 관계 DB 저장 실패: userId={}, sessionId={}, operationId={}, roomId={}, invitedUserId={}, error={}",
                                                        user.id, user.getSessionId(), operationId, roomId, userId,
                                                        e.getMessage(), e);
                                }

                                // 서버에 있는 채팅방 객체에 새 멤버를 추가한다.
                                boolean isFind = false;

                                log.trace(
                                                "서버 접속자 명단 순회: userId={}, sessionId={}, operationId={}, targetUserId={}, totalConnectedUsers={}",
                                                user.id, user.getSessionId(), operationId, userId,
                                                user.server.userList.size());

                                // 서버 접속자 명단 순회
                                for (int i = 0; i < user.server.userList.size(); i++) {
                                        User connectedUser = user.server.userList.get(i);
                                        if (userId.equals(connectedUser.id)) {
                                                // 접속자 찾음
                                                room.userList.add(connectedUser);
                                                connectedUserList.add(userId);
                                                isFind = true;

                                                log.debug(
                                                                "온라인 사용자 채팅방 추가: userId={}, sessionId={}, operationId={}, roomId={}, connectedUserId={}",
                                                                user.id, user.getSessionId(), operationId, roomId,
                                                                userId);
                                                break;
                                        }
                                }

                                // 제공받은 유저 정보가 접속자 중에 없으면
                                if (!isFind) {
                                        // 새 객체 생성 후 채팅방 멤버로 추가
                                        User tempUser = new User(userId);
                                        notConnectedUserList.add(userId);
                                        room.userList.add(tempUser);

                                        log.debug("오프라인 사용자 채팅방 추가: userId={}, sessionId={}, operationId={}, roomId={}, offlineUserId={}",
                                                        user.id, user.getSessionId(), operationId, roomId, userId);
                                }
                        }

                        long dbDuration = dbTimer.stop();
                        log.debug(
                                        "채팅방 초대 DB 처리 완료: userId={}, sessionId={}, operationId={}, roomId={}, dbSuccessCount={}, dbFailCount={}, dbDuration={}ms",
                                        user.id, user.getSessionId(), operationId, roomId, dbSuccessCount, dbFailCount,
                                        dbDuration);

                        log.debug(
                                        "초대 처리 결과: userId={}, sessionId={}, operationId={}, roomId={}, connectedUsers={}, offlineUsers={}",
                                        user.id, user.getSessionId(), operationId, roomId, connectedUserList,
                                        notConnectedUserList);

                        // 채팅 메시지 객체를 생성 해서 db에 저장하여 id를 얻는다.(메시지 간 id 중복 방지)
                        PerformanceLogger.Timer messageTimer = PerformanceLogger.startDatabaseTimer("insertMessage",
                                        "messages");
                        try {
                                SendMessageCommand messageCommand = new SendMessageCommand(roomId, "", Type.TEXT);
                                messageCommand.transmissionStatus = TransmissionStatus.NOT_SENT;
                                messageCommand.readStatus = ReadStatus.read;

                                Long messageId = dbHelper.insertMessage(messageCommand);
                                command.messageId = messageId;
                                long messageDuration = messageTimer.stop();

                                log.debug(
                                                "초대 메시지 DB 저장 완료: userId={}, sessionId={}, operationId={}, roomId={}, messageId={}, duration={}ms",
                                                user.id, user.getSessionId(), operationId, roomId, messageId,
                                                messageDuration);
                        } catch (Exception e) {
                                messageTimer.stop("ERROR: " + e.getMessage());
                                log.error("초대 메시지 DB 저장 실패: userId={}, sessionId={}, operationId={}, roomId={}, error={}",
                                                user.id, user.getSessionId(), operationId, roomId, e.getMessage(), e);
                                throw e;
                        }

                        // 채팅방 추가 정보 로드
                        PerformanceLogger.Timer dataLoadTimer = PerformanceLogger.startDatabaseTimer("getRoomData",
                                        "chat_rooms");
                        try {
                                // nickname, profileImage가 추가된 리스트로 변경
                                command.memberList = dbHelper.getMemberData(roomId);

                                // 채팅방에 대한 추가 정보 입력
                                RoomData roomData = dbHelper.getRoomData(roomId);
                                command.roomName = roomData.roomName;
                                command.description = roomData.description;
                                command.masterId = roomData.masterUserId;
                                command.roomType = roomData.roomType;

                                long dataLoadDuration = dataLoadTimer.stop();
                                log.debug(
                                                "채팅방 데이터 로드 완료: userId={}, sessionId={}, operationId={}, roomId={}, memberCount={}, duration={}ms",
                                                user.id, user.getSessionId(), operationId, roomId,
                                                command.memberList.size(), dataLoadDuration);
                        } catch (Exception e) {
                                dataLoadTimer.stop("ERROR: " + e.getMessage());
                                log.error("채팅방 데이터 로드 실패: userId={}, sessionId={}, operationId={}, roomId={}, error={}",
                                                user.id, user.getSessionId(), operationId, roomId, e.getMessage(), e);
                                throw e;
                        }

                        log.debug("오픈 채팅 멤버 여부: userId={}, sessionId={}, operationId={}, roomId={}, isNewOpenChatMember={}",
                                        user.id, user.getSessionId(), operationId, roomId, command.isNewOpenChatMember);

                        // 기존 멤버들에게 새로운 멤버 추가사실을 알린다.
                        PerformanceLogger.Timer broadcastTimer = PerformanceLogger.startTimer("broadcastToRoom");
                        try {
                                room.broadcastToRoom(command);
                                long broadcastDuration = broadcastTimer.stop();

                                log.debug(
                                                "초대 알림 브로드캐스트 완료: userId={}, sessionId={}, operationId={}, roomId={}, targetMembers={}, duration={}ms",
                                                user.id, user.getSessionId(), operationId, roomId, room.userList.size(),
                                                broadcastDuration);
                        } catch (Exception e) {
                                broadcastTimer.stop("ERROR: " + e.getMessage());
                                log.error("초대 알림 브로드캐스트 실패: userId={}, sessionId={}, operationId={}, roomId={}, error={}",
                                                user.id, user.getSessionId(), operationId, roomId, e.getMessage(), e);
                                throw e;
                        }

                        long duration = timer.stop();

                        log.info(
                                        "채팅방 초대 완료: userId={}, sessionId={}, operationId={}, roomId={}, totalInvited={}, connectedCount={}, offlineCount={}, duration={}ms",
                                        user.id, user.getSessionId(), operationId, roomId, command.invitedIdList.size(),
                                        connectedUserList.size(), notConnectedUserList.size(), duration);

                } catch (Exception e) {
                        timer.stop("ERROR: " + e.getMessage());
                        log.error("채팅방 초대 실패: userId={}, sessionId={}, operationId={}, roomId={}, error={}",
                                        user.id, user.getSessionId(), operationId, command.roomId, e.getMessage(), e);
                        throw e;
                }
        }
}