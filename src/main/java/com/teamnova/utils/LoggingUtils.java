package com.teamnova.utils;

import java.util.UUID;
import java.util.regex.Pattern;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * 로깅 관련 유틸리티 클래스
 * 민감정보 마스킹, 공통 로그 포맷, 성능 측정 등의 기능을 제공합니다.
 */
public class LoggingUtils {

    private static final Logger log = LogManager.getLogger(LoggingUtils.class);

    // 민감정보 마스킹을 위한 패턴
    private static final Pattern EMAIL_PATTERN = Pattern.compile("([a-zA-Z0-9._%+-]+)@([a-zA-Z0-9.-]+\\.[a-zA-Z]{2,})");
    private static final Pattern PHONE_PATTERN = Pattern.compile("(\\d{3})(\\d{4})(\\d{4})");
    private static final String MASK_CHAR = "*";

    /**
     * 이메일 주소를 마스킹합니다.
     * 예: user@example.com -> us****@example.com
     */
    public static String maskEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            return "****";
        }

        if (!email.contains("@")) {
            return "****";
        }

        String[] parts = email.split("@");
        if (parts.length != 2 || parts[0].length() < 2) {
            return "****@****";
        }

        String localPart = parts[0];
        String domainPart = parts[1];

        if (localPart.length() <= 2) {
            return localPart.charAt(0) + "****@" + domainPart;
        } else {
            return localPart.substring(0, 2) + "****@" + domainPart;
        }
    }

    /**
     * 전화번호를 마스킹합니다.
     * 예: 01012345678 -> 010****5678
     */
    public static String maskPhone(String phone) {
        if (phone == null || phone.trim().isEmpty()) {
            return "****";
        }

        // 숫자만 추출
        String numbersOnly = phone.replaceAll("[^0-9]", "");

        if (numbersOnly.length() < 8) {
            return "****";
        }

        if (numbersOnly.length() == 11) {
            // 휴대폰 번호: 010-1234-5678 -> 010****5678
            return numbersOnly.substring(0, 3) + "****" + numbersOnly.substring(7);
        } else if (numbersOnly.length() == 10) {
            // 일반 전화: 02-1234-5678 -> 02****5678
            return numbersOnly.substring(0, 2) + "****" + numbersOnly.substring(6);
        } else {
            // 기타 경우
            return numbersOnly.substring(0, 3) + "****" + numbersOnly.substring(numbersOnly.length() - 2);
        }
    }

    /**
     * 토큰을 마스킹합니다.
     * 예: abcdefghijklmnop -> abcd****mnop
     */
    public static String maskToken(String token) {
        if (token == null || token.trim().isEmpty()) {
            return "****";
        }

        if (token.length() <= 8) {
            return "****";
        }

        return token.substring(0, 4) + "****" + token.substring(token.length() - 4);
    }

    /**
     * 비밀번호를 완전히 마스킹합니다.
     */
    public static String maskPassword(String password) {
        return "****";
    }

    /**
     * 고유한 요청 ID를 생성합니다.
     * 로그 추적을 위해 사용됩니다.
     */
    public static String generateRequestId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * 고유한 세션 ID를 생성합니다.
     * 사용자 세션 추적을 위해 사용됩니다.
     */
    public static String generateSessionId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * 고유한 오퍼레이션 ID를 생성합니다.
     * 비즈니스 로직 추적을 위해 사용됩니다.
     */
    public static String generateOperationId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * 고유한 시그널 ID를 생성합니다.
     * WebRTC 시그널링 추적을 위해 사용됩니다.
     */
    public static String generateSignalId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * 사용자 이름을 안전하게 로깅하기 위해 마스킹합니다.
     * 개인정보 보호를 위해 일부만 표시합니다.
     */
    public static String sanitizeUserName(String userName) {
        if (userName == null || userName.trim().isEmpty()) {
            return "****";
        }

        if (userName.length() <= 2) {
            return userName.charAt(0) + "****";
        } else if (userName.length() <= 4) {
            return userName.substring(0, 2) + "****";
        } else {
            return userName.substring(0, 2) + "****" + userName.substring(userName.length() - 1);
        }
    }

    /**
     * ICE Candidate 정보를 안전하게 로깅하기 위해 마스킹합니다.
     * 네트워크 정보 보호를 위해 일부만 표시합니다.
     */
    public static String sanitizeIceCandidate(Object iceCandidate) {
        if (iceCandidate == null) {
            return "null";
        }

        String candidateStr = iceCandidate.toString();
        if (candidateStr.length() > 50) {
            return candidateStr.substring(0, 20) + "****" + candidateStr.substring(candidateStr.length() - 10);
        }
        return candidateStr;
    }

    /**
     * 메시지 내용을 안전하게 로깅하기 위해 길이를 제한하고 민감정보를 마스킹합니다.
     */
    public static String sanitizeMessageContent(String content, int maxLength) {
        if (content == null) {
            return "null";
        }

        // 길이 제한
        String sanitized = content.length() > maxLength ? content.substring(0, maxLength) + "..." : content;

        // 이메일 마스킹
        sanitized = EMAIL_PATTERN.matcher(sanitized).replaceAll(match -> {
            return maskEmail(match.group());
        });

        // 전화번호 마스킹
        sanitized = PHONE_PATTERN.matcher(sanitized).replaceAll(match -> {
            return maskPhone(match.group());
        });

        return sanitized;
    }

    /**
     * 바이트 크기를 읽기 쉬운 형태로 변환합니다.
     */
    public static String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + "B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.1fKB", bytes / 1024.0);
        } else if (bytes < 1024 * 1024 * 1024) {
            return String.format("%.1fMB", bytes / (1024.0 * 1024.0));
        } else {
            return String.format("%.1fGB", bytes / (1024.0 * 1024.0 * 1024.0));
        }
    }

    /**
     * 시간을 읽기 쉬운 형태로 변환합니다.
     */
    public static String formatDuration(long milliseconds) {
        if (milliseconds < 1000) {
            return milliseconds + "ms";
        } else if (milliseconds < 60000) {
            return String.format("%.1fs", milliseconds / 1000.0);
        } else if (milliseconds < 3600000) {
            return String.format("%.1fm", milliseconds / 60000.0);
        } else {
            return String.format("%.1fh", milliseconds / 3600000.0);
        }
    }

    /**
     * 스레드 정보를 포맷합니다.
     */
    public static String formatThreadInfo() {
        Thread currentThread = Thread.currentThread();
        return String.format("Thread[%s-%d]",
                currentThread.getName(),
                currentThread.getId());
    }

    /**
     * 메모리 사용량 정보를 포맷합니다.
     */
    public static String formatMemoryInfo() {
        Runtime runtime = Runtime.getRuntime();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;
        long maxMemory = runtime.maxMemory();

        return String.format("Memory[used=%s, total=%s, max=%s, usage=%.1f%%]",
                formatBytes(usedMemory),
                formatBytes(totalMemory),
                formatBytes(maxMemory),
                (usedMemory * 100.0) / maxMemory);
    }

    /**
     * 로그 메시지에 컨텍스트 정보를 추가합니다.
     */
    public static String addContext(String message, String userId, String sessionId) {
        return String.format("[User:%s][Session:%s] %s",
                userId != null ? userId : "unknown",
                sessionId != null ? sessionId : "unknown",
                message);
    }

    /**
     * 로그 메시지에 요청 컨텍스트를 추가합니다.
     */
    public static String addRequestContext(String message, String requestId, String operation) {
        return String.format("[Request:%s][Operation:%s] %s",
                requestId != null ? requestId : "unknown",
                operation != null ? operation : "unknown",
                message);
    }
}