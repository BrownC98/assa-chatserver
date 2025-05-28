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
import com.teamnova.utils.LoggingConstants;
import com.teamnova.utils.LoggingUtils;
import com.teamnova.utils.PerformanceLogger;
import com.teamnova.webrtc.VideoRoom;

/**
 * 채팅 서버 메인 클래스
 */
public class ChatServer {

    private static final Logger log = LogManager.getLogger(ChatServer.class);

    static final int PORT = Integer.parseInt(PropertiesManager.getProperty("SERVER_PORT"));

    public List<User> userList; // 접속자 리스트
    public static Map<Long, ChatRoom> roomMap; // 채팅방 저장 맵, 키 값이 방 id와 매칭된다.
    public static Map<String, VideoRoom> videoRoomMap; // 영상회의 맵 키 값은 회의방의 uuid이다.

    // 서버 상태 추적
    private final String serverId;
    private final long serverStartTime;
    private volatile boolean isRunning = false;
    private int totalConnectionsAccepted = 0;

    public ChatServer() {
        this.serverId = LoggingUtils.generateOperationId();
        this.serverStartTime = System.currentTimeMillis();
    }

    public void init() {
        String operationId = LoggingUtils.generateOperationId();
        PerformanceLogger.Timer serverInitTimer = PerformanceLogger.startTimer("SERVER_INIT",
                String.format("serverId=%s,port=%d", serverId, PORT));

        log.info("서버 초기화 시작: serverId={}, operationId={}, port={}, startTime={}",
                serverId, operationId, PORT, serverStartTime);

        try {
            // 데이터 구조 초기화
            userList = new CopyOnWriteArrayList<>();
            roomMap = new ConcurrentHashMap<>();
            videoRoomMap = new ConcurrentHashMap<>();

            log.debug(
                    "데이터 구조 초기화 완료: serverId={}, operationId={}, userListType={}, roomMapType={}, videoRoomMapType={}",
                    serverId, operationId, userList.getClass().getSimpleName(),
                    roomMap.getClass().getSimpleName(), videoRoomMap.getClass().getSimpleName());

            // 데이터베이스 연결 및 초기화
            PerformanceLogger.Timer dbInitTimer = PerformanceLogger.startDatabaseTimer("INIT", "server_data");
            try {
                DBHelper dbHelper = DBHelper.getInstance();
                log.debug("데이터베이스 연결 성공: serverId={}, operationId={}", serverId, operationId);

                // DB에서 생성되었던 방 목록 로드
                roomMap = dbHelper.getServerData();
                long dbInitDuration = dbInitTimer.stop();

                log.info("채팅방 데이터 로드 완료: serverId={}, operationId={}, roomCount={}, loadDuration={}ms",
                        serverId, operationId, roomMap.size(), dbInitDuration);

                // 채팅방 상세 정보 로깅
                if (log.isDebugEnabled()) {
                    roomMap.forEach((roomId, chatRoom) -> {
                        log.debug("로드된 채팅방: serverId={}, operationId={}, roomId={}, roomName={}, memberCount={}",
                                serverId, operationId, roomId, chatRoom.roomName, chatRoom.userList.size());
                    });
                }

            } catch (Exception e) {
                dbInitTimer.stop("ERROR: " + e.getMessage());
                log.error("데이터베이스 초기화 실패: serverId={}, operationId={}, error={}",
                        serverId, operationId, e.getMessage(), e);
                throw e;
            }

            // 성능 모니터링 시작
            PerformanceLogger.startPeriodicLogging(5); // 5분마다 성능 리포트
            log.info("성능 모니터링 시작: serverId={}, operationId={}, interval=5분", serverId, operationId);

            long initDuration = serverInitTimer.stop();
            log.info("서버 초기화 완료: serverId={}, operationId={}, duration={}ms, port={}, roomCount={}",
                    serverId, operationId, initDuration, PORT, roomMap.size());

            // 서버 소켓 시작
            startServerSocket(operationId);

        } catch (Exception e) {
            serverInitTimer.stop("ERROR: " + e.getMessage());
            log.fatal("서버 초기화 실패: serverId={}, operationId={}, error={}, systemState=SHUTTING_DOWN",
                    serverId, operationId, e.getMessage(), e);
            throw new RuntimeException("서버 초기화 실패", e);
        }
    }

    /**
     * 서버 소켓 시작 및 클라이언트 연결 수락
     */
    private void startServerSocket(String initOperationId) {
        log.info("서버 소켓 시작: serverId={}, operationId={}, port={}, bindAddress=0.0.0.0",
                serverId, initOperationId, PORT);

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            isRunning = true;

            log.info("서버 소켓 바인딩 성공: serverId={}, port={}, localAddress={}, status={}",
                    serverId, PORT, serverSocket.getLocalSocketAddress(), LoggingConstants.SYSTEM_STATUS_RUNNING);

            // 소켓 연결 대기 무한 루프
            while (isRunning) {
                try {
                    log.trace("클라이언트 연결 대기 중: serverId={}, port={}, currentConnections={}",
                            serverId, PORT, userList.size());

                    Socket socket = serverSocket.accept();
                    totalConnectionsAccepted++;

                    String clientAddress = socket.getRemoteSocketAddress().toString();
                    String connectionId = LoggingUtils.generateOperationId();

                    log.info("클라이언트 연결 수락: serverId={}, connectionId={}, clientAddress={}, " +
                            "totalConnections={}, currentActiveConnections={}",
                            serverId, connectionId, clientAddress, totalConnectionsAccepted, userList.size());

                    // 연결 상태 체크
                    checkServerResources(connectionId);

                    // User 객체 생성 후 스레드 실행
                    try {
                        User user = new User(this, socket);
                        user.start();

                        log.debug("사용자 스레드 시작: serverId={}, connectionId={}, clientAddress={}, threadName={}",
                                serverId, connectionId, clientAddress, user.getName());

                    } catch (Exception e) {
                        log.error("사용자 스레드 생성 실패: serverId={}, connectionId={}, clientAddress={}, error={}",
                                serverId, connectionId, clientAddress, e.getMessage(), e);

                        // 소켓 정리
                        try {
                            socket.close();
                        } catch (IOException closeException) {
                            log.warn("소켓 정리 실패: serverId={}, connectionId={}, error={}",
                                    serverId, connectionId, closeException.getMessage());
                        }
                    }

                } catch (IOException e) {
                    if (isRunning) {
                        log.error("클라이언트 연결 수락 실패: serverId={}, port={}, error={}",
                                serverId, PORT, e.getMessage(), e);
                    } else {
                        log.info("서버 종료로 인한 연결 수락 중단: serverId={}, port={}",
                                serverId, PORT);
                    }
                }
            }

        } catch (IOException e) {
            log.fatal("서버 소켓 생성 실패: serverId={}, port={}, error={}, systemState=SHUTTING_DOWN",
                    serverId, PORT, e.getMessage(), e);
            throw new RuntimeException("서버 소켓 생성 실패", e);
        } finally {
            isRunning = false;
            log.info("서버 소켓 종료: serverId={}, port={}, totalConnectionsAccepted={}, finalUserCount={}",
                    serverId, PORT, totalConnectionsAccepted, userList.size());
        }
    }

    /**
     * 서버 리소스 상태 체크
     */
    private void checkServerResources(String connectionId) {
        // 메모리 사용량 체크
        PerformanceLogger.checkMemoryUsage();

        // 현재 연결 수 체크
        int currentConnections = userList.size();
        if (currentConnections > 1000) { // 임계값 설정
            log.warn("높은 동시 연결 수: serverId={}, connectionId={}, currentConnections={}, threshold=1000",
                    serverId, connectionId, currentConnections);
        }

        // 스레드 수 체크
        int activeThreads = Thread.activeCount();
        if (activeThreads > 500) { // 임계값 설정
            log.warn("높은 스레드 수: serverId={}, connectionId={}, activeThreads={}, threshold=500",
                    serverId, connectionId, activeThreads);
        }

        log.debug("서버 리소스 상태: serverId={}, connectionId={}, connections={}, threads={}, {}",
                serverId, connectionId, currentConnections, activeThreads, LoggingUtils.formatMemoryInfo());
    }

    // 사용자 목록에 인자로 주어진 사용자를 추가한다.
    public void addUser(User user) {
        String operationId = LoggingUtils.generateOperationId();
        int prevUserCount = userList.size();

        log.debug("사용자 추가 시작: serverId={}, operationId={}, userId={}, sessionId={}, prevUserCount={}",
                serverId, operationId, user.id, user.getSessionId(), prevUserCount);

        try {
            userList.add(user);
            int newUserCount = userList.size();

            log.info("사용자 추가 완료: serverId={}, operationId={}, userId={}, sessionId={}, " +
                    "userCount={}->{}, totalConnectionsAccepted={}",
                    serverId, operationId, user.id, user.getSessionId(),
                    prevUserCount, newUserCount, totalConnectionsAccepted);

            // 사용자 수 임계값 체크
            if (newUserCount > 500) { // 임계값 설정
                log.warn("높은 사용자 수: serverId={}, operationId={}, userCount={}, threshold=500",
                        serverId, operationId, newUserCount);
            }

            // 주기적으로 사용자 목록 상태 로깅 (100명 단위)
            if (newUserCount % 100 == 0) {
                logUserListStatus(operationId);
            }

        } catch (Exception e) {
            log.error("사용자 추가 실패: serverId={}, operationId={}, userId={}, error={}",
                    serverId, operationId, user.id, e.getMessage(), e);
        }
    }

    // 접속자 목록에서 주어진 유저 제거
    public void removeUser(User user) {
        String operationId = LoggingUtils.generateOperationId();
        int prevUserCount = userList.size();
        long sessionDuration = System.currentTimeMillis() - user.getConnectionStartTime();

        log.debug("사용자 제거 시작: serverId={}, operationId={}, userId={}, sessionId={}, " +
                "prevUserCount={}, sessionDuration={}ms",
                serverId, operationId, user.id, user.getSessionId(), prevUserCount, sessionDuration);

        try {
            // UserConnectionManager를 통해 연결 해제
            user.disconnect();

            boolean removed = userList.remove(user);
            int newUserCount = userList.size();

            if (removed) {
                log.info("사용자 제거 완료: serverId={}, operationId={}, userId={}, sessionId={}, " +
                        "userCount={}->{}, sessionDuration={}ms, messageCount={}",
                        serverId, operationId, user.id, user.getSessionId(),
                        prevUserCount, newUserCount, sessionDuration, user.getMessageCount());
            } else {
                log.warn("사용자 제거 실패 - 목록에 없음: serverId={}, operationId={}, userId={}, sessionId={}",
                        serverId, operationId, user.id, user.getSessionId());
            }

            // 주기적으로 사용자 목록 상태 로깅 (100명 단위)
            if (newUserCount % 100 == 0) {
                logUserListStatus(operationId);
            }

        } catch (Exception e) {
            log.error("사용자 제거 중 오류: serverId={}, operationId={}, userId={}, sessionId={}, error={}",
                    serverId, operationId, user.id, user.getSessionId(), e.getMessage(), e);
        }
    }

    /**
     * 사용자 목록 상태 로깅
     */
    private void logUserListStatus(String operationId) {
        int userCount = userList.size();
        long serverUptime = System.currentTimeMillis() - serverStartTime;

        log.info("사용자 목록 상태: serverId={}, operationId={}, currentUsers={}, " +
                "totalConnectionsAccepted={}, serverUptime={}ms, avgSessionTime={}ms",
                serverId, operationId, userCount, totalConnectionsAccepted, serverUptime,
                calculateAverageSessionTime());

        // 상세 사용자 정보 (DEBUG 레벨)
        if (log.isDebugEnabled() && userCount <= 20) {
            userList.forEach(user -> {
                long sessionDuration = System.currentTimeMillis() - user.getConnectionStartTime();
                log.debug("활성 사용자: serverId={}, operationId={}, userId={}, sessionId={}, " +
                        "sessionDuration={}ms, messageCount={}",
                        serverId, operationId, user.id, user.getSessionId(),
                        sessionDuration, user.getMessageCount());
            });
        }
    }

    /**
     * 평균 세션 시간 계산
     */
    private long calculateAverageSessionTime() {
        if (userList.isEmpty()) {
            return 0;
        }

        long totalSessionTime = 0;
        long currentTime = System.currentTimeMillis();

        for (User user : userList) {
            totalSessionTime += (currentTime - user.getConnectionStartTime());
        }

        return totalSessionTime / userList.size();
    }

    /**
     * 서버 종료
     */
    public void shutdown() {
        String operationId = LoggingUtils.generateOperationId();
        long serverUptime = System.currentTimeMillis() - serverStartTime;

        log.info("서버 종료 시작: serverId={}, operationId={}, currentUsers={}, " +
                "totalConnectionsAccepted={}, serverUptime={}ms",
                serverId, operationId, userList.size(), totalConnectionsAccepted, serverUptime);

        isRunning = false;

        // 모든 사용자 연결 해제
        int disconnectedUsers = 0;
        for (User user : userList) {
            try {
                user.disconnect();
                disconnectedUsers++;
            } catch (Exception e) {
                log.error("사용자 연결 해제 실패: serverId={}, operationId={}, userId={}, error={}",
                        serverId, operationId, user.id, e.getMessage());
            }
        }

        log.info("서버 종료 완료: serverId={}, operationId={}, disconnectedUsers={}, " +
                "totalUptime={}ms, totalConnectionsServed={}",
                serverId, operationId, disconnectedUsers, serverUptime, totalConnectionsAccepted);
    }

    // Getter 메서드들
    public String getServerId() {
        return serverId;
    }

    public long getServerStartTime() {
        return serverStartTime;
    }

    public int getTotalConnectionsAccepted() {
        return totalConnectionsAccepted;
    }

    public boolean isRunning() {
        return isRunning;
    }

    /**
     * 채팅방 조회 (null 체크 포함)
     */
    public static ChatRoom getChatRoom(Long roomId) {
        ChatRoom chatRoom = roomMap.get(roomId);
        if (chatRoom == null) {
            log.warn("존재하지 않는 채팅방 조회 시도: roomId={}", roomId);
        }
        return chatRoom;
    }

    /**
     * 영상방 조회 (null 체크 포함)
     */
    public static VideoRoom getVideoRoom(String videoRoomId) {
        VideoRoom videoRoom = videoRoomMap.get(videoRoomId);
        if (videoRoom == null) {
            log.warn("존재하지 않는 영상방 조회 시도: videoRoomId={}", videoRoomId);
        }
        return videoRoom;
    }
}
