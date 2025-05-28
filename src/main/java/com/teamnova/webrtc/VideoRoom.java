package com.teamnova.webrtc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.teamnova.user.User;
import com.teamnova.utils.LoggingUtils;

/**
 * WebRTC 영상회의 방을 관리하는 클래스
 * 참가자 관리, ICE 후보 추적 등의 기능을 제공합니다.
 */
public class VideoRoom {

    private static final Logger log = LogManager.getLogger(VideoRoom.class);

    public String id;
    public Long hostId;
    public List<User> userList;
    public int chatRoomId; // 연결된 채팅방 ID

    // key - 유저 id, value - ice 후보를 보낸 유저 목록
    public Map<Long, List<Long>> receivedIceCandidateMap = new HashMap<>();

    // 영상방 생성 시간
    private final long createdAt;

    /**
     * 영상방 생성자
     */
    public VideoRoom() {
        this.id = UUID.randomUUID().toString();
        this.userList = new ArrayList<>();
        this.createdAt = System.currentTimeMillis();

        log.info("영상방 생성: videoRoomId={}, createdAt={}, threadId={}",
                id, createdAt, Thread.currentThread().getId());
    }

    /**
     * 영상방에 사용자를 추가합니다.
     */
    public void addUser(User user) {
        String operationId = LoggingUtils.generateOperationId();

        log.debug("영상방 사용자 추가 시작: operationId={}, videoRoomId={}, userId={}, currentParticipants={}",
                operationId, id, user.id, userList.size());

        try {
            // 중복 참가 확인
            boolean alreadyExists = userList.stream()
                    .anyMatch(u -> u.id == user.id);

            if (alreadyExists) {
                log.warn("이미 참가한 사용자 중복 추가 시도: operationId={}, videoRoomId={}, userId={}",
                        operationId, id, user.id);
                return;
            }

            userList.add(user);

            // 첫 번째 사용자는 호스트로 설정
            if (hostId == null) {
                hostId = user.id;
                log.info("영상방 호스트 설정: operationId={}, videoRoomId={}, hostId={}",
                        operationId, id, hostId);
            }

            log.info("영상방 사용자 추가 완료: operationId={}, videoRoomId={}, userId={}, " +
                    "participantCount={}, isHost={}, sessionAge={}ms",
                    operationId, id, user.id, userList.size(),
                    user.id == hostId, System.currentTimeMillis() - createdAt);

        } catch (Exception e) {
            log.error("영상방 사용자 추가 실패: operationId={}, videoRoomId={}, userId={}, error={}",
                    operationId, id, user.id, e.getMessage(), e);
            throw new RuntimeException("사용자 추가 실패", e);
        }
    }

    /**
     * 영상방에서 사용자를 제거합니다.
     */
    public boolean removeUser(User user) {
        String operationId = LoggingUtils.generateOperationId();

        log.debug("영상방 사용자 제거 시작: operationId={}, videoRoomId={}, userId={}, currentParticipants={}",
                operationId, id, user.id, userList.size());

        try {
            boolean removed = userList.removeIf(u -> u.id == user.id);

            if (removed) {
                // ICE 후보 맵에서도 제거
                receivedIceCandidateMap.remove(user.id);

                boolean wasHost = user.id == hostId;

                // 호스트가 나간 경우 처리
                if (wasHost) {
                    if (userList.isEmpty()) {
                        hostId = null;
                        log.info("영상방 호스트 퇴장 - 방 비움: operationId={}, videoRoomId={}, formerHostId={}",
                                operationId, id, user.id);
                    } else {
                        // 다음 사용자를 호스트로 지정
                        hostId = userList.get(0).id;
                        log.info("영상방 호스트 변경: operationId={}, videoRoomId={}, formerHostId={}, newHostId={}",
                                operationId, id, user.id, hostId);
                    }
                }

                log.info("영상방 사용자 제거 완료: operationId={}, videoRoomId={}, userId={}, " +
                        "participantCount={}, wasHost={}, newHostId={}, sessionDuration={}ms",
                        operationId, id, user.id, userList.size(), wasHost, hostId,
                        System.currentTimeMillis() - createdAt);

                return true;
            } else {
                log.warn("영상방에서 존재하지 않는 사용자 제거 시도: operationId={}, videoRoomId={}, userId={}",
                        operationId, id, user.id);
                return false;
            }

        } catch (Exception e) {
            log.error("영상방 사용자 제거 실패: operationId={}, videoRoomId={}, userId={}, error={}",
                    operationId, id, user.id, e.getMessage(), e);
            throw new RuntimeException("사용자 제거 실패", e);
        }
    }

    /**
     * 사용자 ID로 사용자를 조회합니다.
     */
    public User getUserById(Long userId) {
        log.trace("영상방 사용자 조회: videoRoomId={}, targetUserId={}, totalParticipants={}",
                id, userId, userList.size());

        try {
            for (User user : userList) {
                if (user.id == userId) {
                    log.trace("영상방 사용자 조회 성공: videoRoomId={}, userId={}, isHost={}",
                            id, userId, userId.equals(hostId));
                    return user;
                }
            }

            log.debug("영상방에서 사용자 찾을 수 없음: videoRoomId={}, targetUserId={}, participantIds={}",
                    id, userId, userList.stream().map(u -> u.id).toList());
            return null;

        } catch (Exception e) {
            log.error("영상방 사용자 조회 중 오류: videoRoomId={}, targetUserId={}, error={}",
                    id, userId, e.getMessage(), e);
            return null;
        }
    }

    /**
     * 영상방이 비어있는지 확인합니다.
     */
    public boolean isEmpty() {
        boolean empty = userList.isEmpty();
        log.trace("영상방 비어있음 확인: videoRoomId={}, isEmpty={}, participantCount={}",
                id, empty, userList.size());
        return empty;
    }

    /**
     * 영상방 참가자 수를 반환합니다.
     */
    public int getParticipantCount() {
        int count = userList.size();
        log.trace("영상방 참가자 수 조회: videoRoomId={}, participantCount={}", id, count);
        return count;
    }

    /**
     * 영상방 호스트 ID를 반환합니다.
     */
    public Long getHostId() {
        log.trace("영상방 호스트 조회: videoRoomId={}, hostId={}", id, hostId);
        return hostId;
    }

    /**
     * 영상방 생성 시간을 반환합니다.
     */
    public long getCreatedAt() {
        return createdAt;
    }

    /**
     * 영상방 지속 시간을 반환합니다.
     */
    public long getDuration() {
        long duration = System.currentTimeMillis() - createdAt;
        log.trace("영상방 지속 시간 조회: videoRoomId={}, duration={}ms", id, duration);
        return duration;
    }

    /**
     * 연결된 채팅방 ID를 설정합니다.
     */
    public void setChatRoomId(int chatRoomId) {
        log.debug("영상방 채팅방 연결: videoRoomId={}, chatRoomId={}", id, chatRoomId);
        this.chatRoomId = chatRoomId;
    }

    /**
     * 연결된 채팅방 ID를 반환합니다.
     */
    public int getChatRoomId() {
        return chatRoomId;
    }

    /**
     * 영상방 참가자 목록을 반환합니다.
     */
    public List<User> getParticipants() {
        log.trace("영상방 참가자 목록 조회: videoRoomId={}, participantCount={}", id, userList.size());
        return new ArrayList<>(userList); // 방어적 복사
    }

    /**
     * 영상방 정보를 문자열로 반환합니다.
     */
    @Override
    public String toString() {
        return String.format("VideoRoom{id='%s', hostId=%d, participantCount=%d, chatRoomId=%d, duration=%dms}",
                id, hostId, userList.size(), chatRoomId, getDuration());
    }
}
