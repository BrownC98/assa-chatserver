package com.teamnova.user;

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
import com.google.gson.JsonSyntaxException;
import com.teamnova.chat.ChatRoom;
import com.teamnova.chat.MessageHandler;
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
import com.teamnova.database.DBHelper;
import com.teamnova.server.ChatServer;
import com.teamnova.utils.LoggingConstants;
import com.teamnova.utils.LoggingUtils;
import com.teamnova.utils.PerformanceLogger;
import com.teamnova.utils.TimeUtils;
import com.teamnova.webrtc.WebRTCSignalingHandler;

/**
 * 사용자 클래스 - 리팩토링을 통해 3개 핸들러로 책임 분리
 * - UserConnectionManager: 연결 관리
 * - MessageHandler: 메시지 처리
 * - WebRTCSignalingHandler: WebRTC 시그널링
 */
public class User extends Thread {

    private static final Logger log = LogManager.getLogger(User.class);

    public long id;
    public ChatServer server;

    // 연결 관리자
    private UserConnectionManager connectionManager;

    // 메시지 처리자
    public MessageHandler messageHandler;

    // WebRTC 시그널링 처리자
    private WebRTCSignalingHandler webrtcHandler;

    // 소켓 접속이 안 된사이 쌓인 메시지 저장 큐
    public Queue<String> messageQueue = new LinkedList<>();

    // 세션 추적을 위한 변수들
    private final String sessionId;
    private final long connectionStartTime;
    private int messageCount = 0;
    private long totalProcessingTime = 0;

    // 방에 초대했지만 현재 접속하지 않은경우를 대처하기 위한 생성자
    public User(long id) {
        this.id = id;
        this.sessionId = LoggingUtils.generateSessionId();
        this.connectionStartTime = System.currentTimeMillis();
        this.connectionManager = new UserConnectionManager(null, id);
        this.messageHandler = new MessageHandler(this);
        this.webrtcHandler = new WebRTCSignalingHandler(this);

        log.debug("오프라인 사용자 객체 생성: userId={}, sessionId={}, purpose=INVITATION_PLACEHOLDER",
                id, sessionId);
    }

    // 사용자가 실제 접속했을 때 사용되는 생성자
    public User(ChatServer server, Socket socket) {
        this.server = server;
        this.sessionId = LoggingUtils.generateSessionId();
        this.connectionStartTime = System.currentTimeMillis();
        this.connectionManager = new UserConnectionManager(socket, 0); // ID는 나중에 설정됨
        this.messageHandler = new MessageHandler(this);
        this.webrtcHandler = new WebRTCSignalingHandler(this);

        log.info(LoggingConstants.USER_SESSION_START,
                "unknown", sessionId, socket.getRemoteSocketAddress(),
                Thread.currentThread().getId(), connectionStartTime);
    }

    // 소켓 교체
    public void replaceSocket(Socket socket) {
        String operationId = LoggingUtils.generateOperationId();

        log.debug("소켓 교체 시작: userId={}, sessionId={}, operationId={}, oldSocket={}, newSocket={}",
                id, sessionId, operationId,
                connectionManager.getSocket() != null ? connectionManager.getSocket().getRemoteSocketAddress() : "null",
                socket.getRemoteSocketAddress());

        try {
            connectionManager.replaceSocket(socket);

            log.info("소켓 교체 완료: userId={}, sessionId={}, operationId={}, newSocketAddress={}",
                    id, sessionId, operationId, socket.getRemoteSocketAddress());
        } catch (Exception e) {
            log.error("소켓 교체 실패: userId={}, sessionId={}, operationId={}, error={}",
                    id, sessionId, operationId, e.getMessage(), e);
        }
    }

    // 연결 해제
    public void disconnect() {
        String operationId = LoggingUtils.generateOperationId();

        log.debug("연결 해제 시작: userId={}, sessionId={}, operationId={}",
                id, sessionId, operationId);

        try {
            connectionManager.disconnect();

            log.info("연결 해제 완료: userId={}, sessionId={}, operationId={}",
                    id, sessionId, operationId);
        } catch (Exception e) {
            log.error("연결 해제 중 오류: userId={}, sessionId={}, operationId={}, error={}",
                    id, sessionId, operationId, e.getMessage(), e);
        }
    }

    @Override
    public void run() {
        log.debug("사용자 스레드 시작: userId={}, sessionId={}, threadName={}, threadId={}",
                id, sessionId, Thread.currentThread().getName(), Thread.currentThread().getId());

        try {
            while (connectionManager.isConnected()) {
                try {
                    // 클라이언트로부터 받은 메시지 수신 대기 루프
                    while (true) {
                        log.trace("메시지 대기 상태: userId={}, sessionId={}, socketState={}, isConnected={}",
                                id, sessionId,
                                connectionManager.getSocket() != null && !connectionManager.getSocket().isClosed()
                                        ? "OPEN"
                                        : "CLOSED",
                                connectionManager.isConnected());

                        String line = connectionManager.getInputStream().readLine();

                        if (line == null) {
                            log.warn("연결 종료 신호 감지: userId={}, sessionId={}, messageCount={}, sessionDuration={}ms",
                                    id, sessionId, messageCount, System.currentTimeMillis() - connectionStartTime);
                            throw new IOException("클라이언트가 접속 끊음");
                        }

                        messageCount++;
                        long messageReceivedTime = System.currentTimeMillis();

                        log.debug(LoggingConstants.MESSAGE_RECEIVED,
                                id, sessionId, messageCount, line.length(), messageReceivedTime);

                        // 메시지 처리 시간 측정
                        PerformanceLogger.Timer timer = PerformanceLogger.startMessageTimer(
                                String.format("userId=%d,sessionId=%s", id, sessionId));

                        try {
                            handleMessage(line);
                            long processingTime = timer.stop();
                            totalProcessingTime += processingTime;

                            log.debug(LoggingConstants.MESSAGE_PROCESSED,
                                    id, sessionId, messageCount, processingTime);

                        } catch (Exception e) {
                            long processingTime = timer.stop("ERROR: " + e.getMessage());
                            totalProcessingTime += processingTime;

                            log.error(
                                    "메시지 처리 중 오류: userId={}, sessionId={}, messageCount={}, processingTime={}ms, error={}",
                                    id, sessionId, messageCount, processingTime, e.getMessage(), e);
                        }
                    }
                } catch (IOException e) {
                    log.warn("소켓 통신 오류: userId={}, sessionId={}, messageCount={}, sessionDuration={}ms, error={}",
                            id, sessionId, messageCount,
                            System.currentTimeMillis() - connectionStartTime, e.getMessage());

                    if (server != null) {
                        server.removeUser(this);
                    }
                    break;
                }
            }
        } finally {
            logSessionEnd();
        }
    }

    /**
     * 메시지 처리 메서드
     */
    private void handleMessage(String message) {
        String requestId = LoggingUtils.generateRequestId();

        log.debug("메시지 처리 시작: userId={}, sessionId={}, requestId={}, messageSize={}bytes",
                id, sessionId, requestId, message.length());

        try {
            // JSON 파싱
            JsonObject jsonObject;
            try {
                jsonObject = JsonParser.parseString(message).getAsJsonObject();
            } catch (JsonSyntaxException e) {
                log.error("JSON 파싱 실패: userId={}, sessionId={}, requestId={}, messagePreview={}, error={}",
                        id, sessionId, requestId,
                        LoggingUtils.sanitizeMessageContent(message, 100), e.getMessage(), e);
                return;
            }

            // Action 추출
            String actionStr = jsonObject.get("action").getAsString();
            log.debug("명령 파싱 성공: userId={}, sessionId={}, requestId={}, action={}, hasRequester={}",
                    id, sessionId, requestId, actionStr, jsonObject.has("requesterId"));

            // Action 처리
            handleActions(actionStr, message, requestId);

        } catch (Exception e) {
            log.error("메시지 처리 중 예상치 못한 오류: userId={}, sessionId={}, requestId={}, error={}",
                    id, sessionId, requestId, e.getMessage(), e);
        }
    }

    // action 처리
    private void handleActions(String actionStr, String json, String requestId) {
        log.debug("액션 처리 시작: userId={}, sessionId={}, requestId={}, action={}",
                id, sessionId, requestId, actionStr);

        try {
            // action 문자열을 enum으로 변환
            Action action;
            try {
                action = Action.valueOf(actionStr);
            } catch (IllegalArgumentException e) {
                log.error("알 수 없는 액션: userId={}, sessionId={}, requestId={}, action={}, error={}",
                        id, sessionId, requestId, actionStr, e.getMessage());
                return;
            }

            // action에 맞는 객체 생성
            BaseCommand command = BaseCommand.fromJson(action, json);

            // 서버로 들어오는 모든 요청에 현재시간 기록
            command.createdAT = TimeUtils.getCurrentTimeInUTC();

            // 명령 실행 시간 측정
            long commandStart = System.currentTimeMillis();

            switch (action) {
                case CONNECT: // 소켓 연결
                    connect((ConnectCommand) command, requestId);
                    break;

                case DISCONNECT: // 소켓 연결 해제
                    log.info("연결 해제 요청: userId={}, sessionId={}, requestId={}", id, sessionId, requestId);
                    if (server != null) {
                        server.removeUser(this);
                    }
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
                    log.warn("처리되지 않은 액션: userId={}, sessionId={}, requestId={}, action={}",
                            id, sessionId, requestId, action);
                    break;
            }

            long commandDuration = System.currentTimeMillis() - commandStart;
            log.info("명령 실행 완료: userId={}, sessionId={}, requestId={}, action={}, duration={}ms",
                    id, sessionId, requestId, action, commandDuration);

        } catch (Exception e) {
            log.error("액션 처리 중 오류: userId={}, sessionId={}, requestId={}, action={}, error={}",
                    id, sessionId, requestId, actionStr, e.getMessage(), e);
        }
    }

    // 사용자 접속처리
    private void connect(ConnectCommand command, String requestId) {
        PerformanceLogger.Timer timer = PerformanceLogger.startTimer("USER_CONNECT",
                String.format("userId=%d,sessionId=%s", command.requesterId, sessionId));

        log.debug("사용자 연결 처리 시작: requestId={}, sessionId={}, requesterId={}",
                requestId, sessionId, command.requesterId);

        try {
            // 사용자 정보 획득
            id = command.requesterId;

            log.info("사용자 서버 접속: userId={}, sessionId={}, requestId={}, socketAddress={}",
                    id, sessionId, requestId,
                    connectionManager.getSocket() != null ? connectionManager.getSocket().getRemoteSocketAddress()
                            : "unknown");

            // 사용자 정보로 사용자가 접속한 방 목록 획득
            List<Long> roomIds = DBHelper.getInstance().getEnteredRoomIds(id);
            log.debug("사용자 접속 방 목록 조회: userId={}, sessionId={}, requestId={}, roomCount={}, roomIds={}",
                    id, sessionId, requestId, roomIds.size(), roomIds);

            // 기존 접속자 리스트에 해당 유저가 없으면 추가
            boolean isUserExist = false;
            for (User user : server.userList) {
                if (user.id == this.id) {
                    log.debug("기존 접속자 발견: userId={}, sessionId={}, requestId={}, existingSessionId={}",
                            id, sessionId, requestId, user.sessionId);
                    isUserExist = true;
                    break;
                }
            }

            if (!isUserExist) {
                log.debug("신규 접속자 추가: userId={}, sessionId={}, requestId={}",
                        id, sessionId, requestId);
                server.addUser(this);
            }

            // 각 방에 기존에 저장된 해당 user객체의 소켓을 새 것으로 바꾼다.
            List<Long> updatedRoomIds = new ArrayList<>();
            for (Long roomId : roomIds) {
                try {
                    ChatRoom chatRoom = server.roomMap.get(roomId);
                    if (chatRoom == null) {
                        log.warn("존재하지 않는 채팅방: userId={}, sessionId={}, requestId={}, roomId={}",
                                id, sessionId, requestId, roomId);
                        continue;
                    }

                    List<User> memberList = chatRoom.userList;

                    // 해당 방에 현재 사용자와 id가 같은 유저객체를 찾아 새것으로 교체한다.
                    for (int i = 0; i < memberList.size(); i++) {
                        if (memberList.get(i).id == this.id) {
                            memberList.set(i, this);
                            updatedRoomIds.add(roomId);
                            log.debug("채팅방 사용자 객체 교체: userId={}, sessionId={}, requestId={}, roomId={}",
                                    id, sessionId, requestId, roomId);
                            break;
                        }
                    }
                } catch (Exception e) {
                    log.error("채팅방 사용자 객체 교체 실패: userId={}, sessionId={}, requestId={}, roomId={}, error={}",
                            id, sessionId, requestId, roomId, e.getMessage(), e);
                }
            }

            log.debug("소켓 교체 완료: userId={}, sessionId={}, requestId={}, updatedRoomCount={}, updatedRoomIds={}",
                    id, sessionId, requestId, updatedRoomIds.size(), updatedRoomIds);

            // 이 유저에 대해 NOT_SENT 상태인 커맨드를 다시 전송시도한다.
            List<ResponseCommand> notSentCommands = DBHelper.getInstance().getNotSentCommands(command.requesterId);

            log.debug("미전송 메시지 처리: userId={}, sessionId={}, requestId={}, notSentCount={}",
                    id, sessionId, requestId, notSentCommands.size());

            // 연결이 끊어져 못 보낸 메시지 전부 보내기
            int sentCount = 0;
            int failedCount = 0;
            for (ResponseCommand notSentCommand : notSentCommands) {
                try {
                    sendMsg(notSentCommand, false);
                    sentCount++;
                } catch (Exception e) {
                    failedCount++;
                    log.error("미전송 메시지 재전송 실패: userId={}, sessionId={}, requestId={}, commandId={}, error={}",
                            id, sessionId, requestId, notSentCommand.id, e.getMessage(), e);
                }
            }

            log.info(
                    "미전송 메시지 재전송 완료: userId={}, sessionId={}, requestId={}, totalCount={}, sentCount={}, failedCount={}",
                    id, sessionId, requestId, notSentCommands.size(), sentCount, failedCount);

            // 현재 접속자 현황 로깅
            List<Long> currentUserIds = new ArrayList<>();
            for (User user : server.userList) {
                currentUserIds.add(user.id);
            }

            long duration = timer.stop();
            log.info("사용자 연결 처리 완료: userId={}, sessionId={}, requestId={}, duration={}ms, " +
                    "totalUsers={}, currentUserIds={}, processedRooms={}, resentMessages={}",
                    id, sessionId, requestId, duration, server.userList.size(),
                    currentUserIds, updatedRoomIds.size(), sentCount);

        } catch (Exception e) {
            timer.stop("ERROR: " + e.getMessage());
            log.error("사용자 연결 처리 실패: userId={}, sessionId={}, requestId={}, error={}",
                    id, sessionId, requestId, e.getMessage(), e);
        }
    }

    // 이 유저의 클라이언트에 메시지 전송
    public void sendMsg(ResponseCommand command, boolean commandSave) {
        String operationId = LoggingUtils.generateOperationId();

        log.debug("메시지 전송 시작: userId={}, sessionId={}, operationId={}, commandType={}, commandSave={}, recipientId={}",
                id, sessionId, operationId, command.getClass().getSimpleName(), commandSave,
                command.recipientId != null ? command.recipientId : "broadcast");

        try {
            if (commandSave) {
                // 커맨드 전송내역 기록
                Long insertedId = DBHelper.getInstance().insertResponseCommand(command);
                command.id = insertedId;
                log.debug("메시지 DB 저장 완료: userId={}, sessionId={}, operationId={}, commandId={}",
                        id, sessionId, operationId, insertedId);
            }

            if (connectionManager.isConnected()) {
                String jsonMessage = command.toJson();
                connectionManager.getOutputStream().println(jsonMessage);

                log.info("메시지 전송 성공: userId={}, sessionId={}, operationId={}, messageSize={}bytes, commandType={}",
                        id, sessionId, operationId, jsonMessage.length(), command.getClass().getSimpleName());

                log.debug("전송된 메시지 내용: userId={}, sessionId={}, operationId={}, content={}",
                        id, sessionId, operationId, LoggingUtils.sanitizeMessageContent(jsonMessage, 200));
            } else {
                log.warn(
                        "연결 해제 상태로 메시지 전송 불가: userId={}, sessionId={}, operationId={}, commandType={}, queuedForLater=true",
                        id, sessionId, operationId, command.getClass().getSimpleName());

                // 메시지 큐에 추가 (필요시)
                messageQueue.offer(command.toJson());
            }

        } catch (Exception e) {
            log.error("메시지 전송 실패: userId={}, sessionId={}, operationId={}, commandType={}, error={}",
                    id, sessionId, operationId, command.getClass().getSimpleName(), e.getMessage(), e);
        }
    }

    /**
     * 세션 종료 로깅
     */
    private void logSessionEnd() {
        long sessionDuration = System.currentTimeMillis() - connectionStartTime;
        long avgProcessingTime = messageCount > 0 ? totalProcessingTime / messageCount : 0;

        log.info(LoggingConstants.USER_SESSION_END,
                id, sessionId, messageCount, sessionDuration, avgProcessingTime);

        // 성능 통계 로깅
        if (sessionDuration > LoggingConstants.PERFORMANCE_SESSION_DURATION_WARN_MS) {
            log.warn("장시간 세션 감지: userId={}, sessionId={}, duration={}ms, threshold={}ms",
                    id, sessionId, sessionDuration, LoggingConstants.PERFORMANCE_SESSION_DURATION_WARN_MS);
        }
    }

    // Getter 메서드들
    public String getSessionId() {
        return sessionId;
    }

    public long getConnectionStartTime() {
        return connectionStartTime;
    }

    public int getMessageCount() {
        return messageCount;
    }

    public long getTotalProcessingTime() {
        return totalProcessingTime;
    }
}