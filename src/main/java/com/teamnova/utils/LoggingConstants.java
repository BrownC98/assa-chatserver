package com.teamnova.utils;

/**
 * ASSA Chat Server 로깅 관련 상수 정의
 * 
 * 이 클래스는 로그 메시지 템플릿, 에러 코드, 성능 임계값 등
 * 로깅과 관련된 모든 상수를 중앙 집중식으로 관리합니다.
 */
public final class LoggingConstants {

    // 생성자 private으로 인스턴스 생성 방지
    private LoggingConstants() {
        throw new AssertionError("LoggingConstants는 인스턴스화할 수 없습니다.");
    }

    // ========================================
    // 로그 메시지 템플릿 상수
    // ========================================

    /** 메서드 시작 템플릿 */
    public static final String METHOD_START = "{}() - START: {}";

    /** 메서드 종료 템플릿 */
    public static final String METHOD_END = "{}() - END: result={}, duration={}ms";

    /** 사용자 세션 시작 템플릿 */
    public static final String USER_SESSION_START = "사용자 세션 시작: userId={}, sessionId={}, socketAddress={}, threadId={}, timestamp={}";

    /** 사용자 세션 종료 템플릿 */
    public static final String USER_SESSION_END = "사용자 세션 종료: userId={}, sessionId={}, totalMessages={}, sessionDuration={}ms, avgProcessingTime={}ms";

    /** 메시지 수신 템플릿 */
    public static final String MESSAGE_RECEIVED = "메시지 수신: userId={}, sessionId={}, messageCount={}, messageSize={}bytes, timestamp={}";

    /** 메시지 처리 완료 템플릿 */
    public static final String MESSAGE_PROCESSED = "메시지 처리 완료: userId={}, sessionId={}, messageCount={}, processingTime={}ms";

    /** 메시지 전송 시작 템플릿 */
    public static final String MESSAGE_SEND_START = "메시지 전송 시작: userId={}, sessionId={}, operationId={}, roomId={}, messageType={}, contentPreview={}";

    /** 메시지 전송 성공 템플릿 */
    public static final String MESSAGE_SEND_SUCCESS = "메시지 전송 성공: userId={}, sessionId={}, operationId={}, roomId={}, messageId={}, memberCount={}, duration={}ms, messageSize={}bytes";

    /** 메시지 전송 실패 템플릿 */
    public static final String ERROR_MESSAGE_SEND_FAILED = "메시지 전송 실패: userId={}, sessionId={}, operationId={}, roomId={}, error={}";

    /** 메시지 전송 완료 템플릿 */
    public static final String MESSAGE_SEND_COMPLETE = "메시지 전송 완료: operationId={}, senderId={}, roomId={}, messageId={}, "
            +
            "totalParticipants={}, onlineCount={}, offlineCount={}, sentCount={}, failedCount={}, " +
            "totalDuration={}ms, dbDuration={}ms, deliveryRate={}%";

    /** 채팅방 생성 템플릿 */
    public static final String ROOM_CREATED = "채팅방 생성 완료: roomId={}, roomName={}, masterId={}, participantLimit={}, roomType={}";

    /** 채팅방 입장 템플릿 */
    public static final String ROOM_JOINED = "사용자 채팅방 입장: userId={}, userName={}, roomId={}, roomName={}, entryMethod={}, participantCount={}";

    /** 채팅방 퇴장 템플릿 */
    public static final String ROOM_EXITED = "사용자 채팅방 퇴장: userId={}, roomId={}, exitReason={}, remainingParticipants={}";

    /** SDP 시그널링 시작 템플릿 */
    public static final String SDP_SIGNALING_START = "SDP 시그널링 시작: signalId={}, fromUserId={}, toUserId={}, sdpType={}, sdpSize={}bytes";

    /** SDP 시그널링 완료 템플릿 */
    public static final String SDP_SIGNALING_COMPLETE = "SDP 시그널링 완료: signalId={}, fromUserId={}, toUserId={}, sdpType={}, "
            +
            "duration={}ms, messageSize={}bytes";

    /** ICE 후보 교환 템플릿 */
    public static final String ICE_CANDIDATE_EXCHANGE = "ICE Candidate 전송: fromUserId={}, toUserId={}, candidate={}, signalId={}";

    /** 영상방 생성 템플릿 */
    public static final String VIDEO_ROOM_CREATED = "영상방 생성 완료: videoRoomId={}, masterId={}, maxParticipants={}, codecType={}";

    /** 영상방 참가 템플릿 */
    public static final String VIDEO_ROOM_JOINED = "영상방 참가: videoRoomId={}, userId={}, userName={}, participantCount={}, userAgent={}";

    /** 영상방 퇴장 템플릿 */
    public static final String VIDEO_ROOM_EXITED = "영상방 퇴장: videoRoomId={}, userId={}, exitReason={}, remainingParticipants={}";

    /** 데이터베이스 쿼리 시작 템플릿 */
    public static final String DB_QUERY_START = "DB 쿼리 시작: queryType={}, table={}, operationId={}, parameters={}";

    /** 데이터베이스 쿼리 완료 템플릿 */
    public static final String DB_QUERY_COMPLETE = "DB 쿼리 완료: queryType={}, table={}, operationId={}, affectedRows={}, duration={}ms";

    /** 트랜잭션 시작 템플릿 */
    public static final String TRANSACTION_START = "트랜잭션 시작: transactionId={}, scope={}, isolationLevel={}";

    /** 트랜잭션 커밋 템플릿 */
    public static final String TRANSACTION_COMMIT = "트랜잭션 커밋: transactionId={}, duration={}ms, operationCount={}";

    /** 트랜잭션 롤백 템플릿 */
    public static final String TRANSACTION_ROLLBACK = "트랜잭션 롤백: transactionId={}, reason={}, duration={}ms";

    // ========================================
    // 에러 코드 상수
    // ========================================

    /** 일반 에러 코드 */
    public static final String ERROR_GENERAL = "GENERAL_ERROR";

    /** 데이터베이스 연결 에러 */
    public static final String ERROR_DB_CONNECTION = "DB_CONNECTION_ERROR";

    /** 데이터베이스 쿼리 에러 */
    public static final String ERROR_DB_QUERY = "DB_QUERY_ERROR";

    /** 소켓 통신 에러 */
    public static final String ERROR_SOCKET_COMMUNICATION = "SOCKET_COMMUNICATION_ERROR";

    /** JSON 파싱 에러 */
    public static final String ERROR_JSON_PARSING = "JSON_PARSING_ERROR";

    /** 메시지 전송 에러 */
    public static final String ERROR_MESSAGE_SEND = "MESSAGE_SEND_ERROR";

    /** 사용자 인증 에러 */
    public static final String ERROR_USER_AUTH = "USER_AUTH_ERROR";

    /** 채팅방 접근 에러 */
    public static final String ERROR_ROOM_ACCESS = "ROOM_ACCESS_ERROR";

    /** 채팅방 찾을 수 없음 에러 */
    public static final String ERROR_ROOM_NOT_FOUND = "채팅방을 찾을 수 없음: userId={}, sessionId={}, operationId={}, roomId={}";

    /** WebRTC 시그널링 에러 */
    public static final String ERROR_WEBRTC_SIGNALING = "WEBRTC_SIGNALING_ERROR";

    /** 리소스 부족 에러 */
    public static final String ERROR_RESOURCE_EXHAUSTED = "RESOURCE_EXHAUSTED_ERROR";

    /** 권한 부족 에러 */
    public static final String ERROR_INSUFFICIENT_PERMISSION = "INSUFFICIENT_PERMISSION_ERROR";

    /** 타임아웃 에러 */
    public static final String ERROR_TIMEOUT = "TIMEOUT_ERROR";

    /** 데이터 검증 에러 */
    public static final String ERROR_DATA_VALIDATION = "DATA_VALIDATION_ERROR";

    // ========================================
    // 성능 임계값 상수 (밀리초)
    // ========================================

    /** 메시지 처리 시간 경고 임계값 */
    public static final long PERFORMANCE_MESSAGE_PROCESSING_WARN_MS = 1000L;

    /** 메시지 처리 시간 임계값 */
    public static final long THRESHOLD_MESSAGE_PROCESSING_TIME = 1000L;

    /** 메시지 처리 시간 에러 임계값 */
    public static final long PERFORMANCE_MESSAGE_PROCESSING_ERROR_MS = 5000L;

    /** 메시지 크기 임계값 (bytes) */
    public static final int THRESHOLD_MESSAGE_SIZE = 10240; // 10KB

    /** 데이터베이스 쿼리 경고 임계값 */
    public static final long PERFORMANCE_DB_QUERY_WARN_MS = 100L;

    /** 데이터베이스 쿼리 에러 임계값 */
    public static final long PERFORMANCE_DB_QUERY_ERROR_MS = 1000L;

    /** SDP 시그널링 경고 임계값 */
    public static final long PERFORMANCE_SDP_SIGNALING_WARN_MS = 500L;

    /** SDP 시그널링 에러 임계값 */
    public static final long PERFORMANCE_SDP_SIGNALING_ERROR_MS = 2000L;

    /** 소켓 응답 시간 경고 임계값 */
    public static final long PERFORMANCE_SOCKET_RESPONSE_WARN_MS = 200L;

    /** 소켓 응답 시간 에러 임계값 */
    public static final long PERFORMANCE_SOCKET_RESPONSE_ERROR_MS = 1000L;

    /** 세션 지속 시간 경고 임계값 (1시간) */
    public static final long PERFORMANCE_SESSION_DURATION_WARN_MS = 3600000L;

    /** 메모리 사용량 경고 임계값 (MB) */
    public static final long PERFORMANCE_MEMORY_USAGE_WARN_MB = 512L;

    /** 메모리 사용량 에러 임계값 (MB) */
    public static final long PERFORMANCE_MEMORY_USAGE_ERROR_MB = 1024L;

    /** 채팅방 초대 처리 시간 임계값 */
    public static final long THRESHOLD_ROOM_INVITE_TIME = 2000L;

    // ========================================
    // 로그 컨텍스트 키 상수
    // ========================================

    /** 사용자 ID 컨텍스트 키 */
    public static final String CONTEXT_USER_ID = "userId";

    /** 세션 ID 컨텍스트 키 */
    public static final String CONTEXT_SESSION_ID = "sessionId";

    /** 요청 ID 컨텍스트 키 */
    public static final String CONTEXT_REQUEST_ID = "requestId";

    /** 작업 ID 컨텍스트 키 */
    public static final String CONTEXT_OPERATION_ID = "operationId";

    /** 시그널 ID 컨텍스트 키 */
    public static final String CONTEXT_SIGNAL_ID = "signalId";

    /** 트랜잭션 ID 컨텍스트 키 */
    public static final String CONTEXT_TRANSACTION_ID = "transactionId";

    /** 채팅방 ID 컨텍스트 키 */
    public static final String CONTEXT_ROOM_ID = "roomId";

    /** 메시지 ID 컨텍스트 키 */
    public static final String CONTEXT_MESSAGE_ID = "messageId";

    /** 스레드 ID 컨텍스트 키 */
    public static final String CONTEXT_THREAD_ID = "threadId";

    /** 클라이언트 IP 컨텍스트 키 */
    public static final String CONTEXT_CLIENT_IP = "clientIP";

    // ========================================
    // 로그 카테고리 상수
    // ========================================

    /** 사용자 관련 로그 카테고리 */
    public static final String CATEGORY_USER = "USER";

    /** 메시지 관련 로그 카테고리 */
    public static final String CATEGORY_MESSAGE = "MESSAGE";

    /** 채팅방 관련 로그 카테고리 */
    public static final String CATEGORY_CHAT_ROOM = "CHAT_ROOM";

    /** WebRTC 관련 로그 카테고리 */
    public static final String CATEGORY_WEBRTC = "WEBRTC";

    /** 데이터베이스 관련 로그 카테고리 */
    public static final String CATEGORY_DATABASE = "DATABASE";

    /** 서버 관련 로그 카테고리 */
    public static final String CATEGORY_SERVER = "SERVER";

    /** 성능 관련 로그 카테고리 */
    public static final String CATEGORY_PERFORMANCE = "PERFORMANCE";

    /** 보안 관련 로그 카테고리 */
    public static final String CATEGORY_SECURITY = "SECURITY";

    // ========================================
    // 시스템 상태 상수
    // ========================================

    /** 시스템 시작 상태 */
    public static final String SYSTEM_STATUS_STARTING = "STARTING";

    /** 시스템 실행 상태 */
    public static final String SYSTEM_STATUS_RUNNING = "RUNNING";

    /** 시스템 종료 중 상태 */
    public static final String SYSTEM_STATUS_SHUTTING_DOWN = "SHUTTING_DOWN";

    /** 시스템 종료 상태 */
    public static final String SYSTEM_STATUS_STOPPED = "STOPPED";

    /** 시스템 에러 상태 */
    public static final String SYSTEM_STATUS_ERROR = "ERROR";

    // ========================================
    // 연결 상태 상수
    // ========================================

    /** 연결됨 상태 */
    public static final String CONNECTION_STATUS_CONNECTED = "CONNECTED";

    /** 연결 해제됨 상태 */
    public static final String CONNECTION_STATUS_DISCONNECTED = "DISCONNECTED";

    /** 연결 중 상태 */
    public static final String CONNECTION_STATUS_CONNECTING = "CONNECTING";

    /** 재연결 중 상태 */
    public static final String CONNECTION_STATUS_RECONNECTING = "RECONNECTING";

    /** 연결 실패 상태 */
    public static final String CONNECTION_STATUS_FAILED = "FAILED";

    // ========================================
    // 메시지 타입 상수
    // ========================================

    /** 텍스트 메시지 */
    public static final String MESSAGE_TYPE_TEXT = "TEXT";

    /** 이미지 메시지 */
    public static final String MESSAGE_TYPE_IMAGE = "IMAGE";

    /** 비디오 메시지 */
    public static final String MESSAGE_TYPE_VIDEO = "VIDEO";

    /** 시스템 메시지 */
    public static final String MESSAGE_TYPE_SYSTEM = "SYSTEM";

    /** 영상방 시작 메시지 */
    public static final String MESSAGE_TYPE_VIDEO_ROOM_OPEN = "VIDEO_ROOM_OPEN";

    /** 영상방 종료 메시지 */
    public static final String MESSAGE_TYPE_VIDEO_ROOM_CLOSE = "VIDEO_ROOM_CLOSE";

    // ========================================
    // 액션 타입 상수
    // ========================================

    /** 연결 액션 */
    public static final String ACTION_CONNECT = "CONNECT";

    /** 연결 해제 액션 */
    public static final String ACTION_DISCONNECT = "DISCONNECT";

    /** 메시지 전송 액션 */
    public static final String ACTION_SEND_MESSAGE = "SEND_MESSAGE";

    /** 채팅방 생성 액션 */
    public static final String ACTION_CREATE_ROOM = "CREATE_ROOM";

    /** 채팅방 입장 액션 */
    public static final String ACTION_JOIN_ROOM = "JOIN_ROOM";

    /** 채팅방 퇴장 액션 */
    public static final String ACTION_EXIT_ROOM = "EXIT_ROOM";

    /** SDP 교환 액션 */
    public static final String ACTION_SDP = "SDP";

    /** ICE 후보 교환 액션 */
    public static final String ACTION_ICE_CANDIDATE = "ICE_CANDIDATE";

    /** 영상방 생성 액션 */
    public static final String ACTION_CREATE_VIDEO_ROOM = "CREATE_VIDEO_ROOM";

    /** 영상방 참가 액션 */
    public static final String ACTION_JOIN_VIDEO_ROOM = "JOIN_VIDEO_ROOM";

    // ========================================
    // 기타 유용한 상수
    // ========================================

    /** 기본 타임아웃 (밀리초) */
    public static final long DEFAULT_TIMEOUT_MS = 30000L;

    /** 최대 재시도 횟수 */
    public static final int MAX_RETRY_COUNT = 3;

    /** 기본 페이지 크기 */
    public static final int DEFAULT_PAGE_SIZE = 20;

    /** 최대 메시지 길이 */
    public static final int MAX_MESSAGE_LENGTH = 1000;

    /** 최대 채팅방 이름 길이 */
    public static final int MAX_ROOM_NAME_LENGTH = 50;

    /** 최대 사용자 이름 길이 */
    public static final int MAX_USER_NAME_LENGTH = 30;

    /** 기본 인코딩 */
    public static final String DEFAULT_ENCODING = "UTF-8";

    /** 날짜 포맷 */
    public static final String DATE_FORMAT_PATTERN = "yyyy-MM-dd HH:mm:ss.SSS";

    /** ISO 날짜 포맷 */
    public static final String ISO_DATE_FORMAT_PATTERN = "yyyy-MM-dd'T'HH:mm:ss.SSSZ";
}