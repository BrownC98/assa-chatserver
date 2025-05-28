package com.teamnova.webrtc;

import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.teamnova.command.chat.SendMessageCommand;
import com.teamnova.command.chat.SendMessageCommand.ReadStatus;
import com.teamnova.command.chat.SendMessageCommand.Type;
import com.teamnova.command.webrtc.CreateVideoRoomCommand;
import com.teamnova.command.webrtc.ExitVideoRoomCommand;
import com.teamnova.command.webrtc.GetVideoRoomParticipantCommand;
import com.teamnova.command.webrtc.IceCandidateCommand;
import com.teamnova.command.webrtc.JoinVideoRoomCommand;
import com.teamnova.command.webrtc.MediaStatusCommand;
import com.teamnova.command.webrtc.SDPCommand;
import com.teamnova.database.DBHelper;
import com.teamnova.dto.user.UserData;
import com.teamnova.server.ChatServer;
import com.teamnova.user.User;
import com.teamnova.utils.LoggingConstants;
import com.teamnova.utils.LoggingUtils;
import com.teamnova.utils.PerformanceLogger;

/**
 * WebRTC 시그널링 처리를 담당하는 클래스
 * 
 * 주요 기능:
 * - SDP Offer/Answer 교환
 * - ICE Candidate 교환
 * - 영상방 생성/참가/퇴장 관리
 * - 미디어 상태 관리
 */
public class WebRTCSignalingHandler {

        private static final Logger log = LogManager.getLogger(WebRTCSignalingHandler.class);

        private User user;

        /**
         * 생성자
         * 
         * @param user 사용자 객체
         */
        public WebRTCSignalingHandler(User user) {
                this.user = user;

                log.debug("WebRTC 시그널링 핸들러 생성: userId={}, sessionId={}",
                                user.id, user.getSessionId());
        }

        /**
         * 영상회의 방 참가자 목록 조회
         */
        public void getVideoRoomParticipant(GetVideoRoomParticipantCommand command) {
                String signalId = LoggingUtils.generateSignalId();
                PerformanceLogger.Timer timer = PerformanceLogger.startTimer("GET_VIDEO_ROOM_PARTICIPANT",
                                String.format("userId=%d,videoRoomId=%s,signalId=%s", user.id, command.videoRoomId,
                                                signalId));

                log.debug("영상방 참가자 목록 조회 시작: userId={}, sessionId={}, signalId={}, videoRoomId={}",
                                user.id, user.getSessionId(), signalId, command.videoRoomId);

                try {
                        VideoRoom videoRoom = ChatServer.videoRoomMap.get(command.videoRoomId);

                        if (videoRoom == null) {
                                log.warn("존재하지 않는 영상방 접근: userId={}, sessionId={}, signalId={}, videoRoomId={}",
                                                user.id, user.getSessionId(), signalId, command.videoRoomId);
                                return;
                        }

                        log.debug("영상방 정보 확인: userId={}, sessionId={}, signalId={}, videoRoomId={}, participantCount={}, hostId={}",
                                        user.id, user.getSessionId(), signalId, command.videoRoomId,
                                        videoRoom.userList.size(), videoRoom.hostId);

                        // 방 멤버 목록 담기
                        List<UserData> userList = new ArrayList<>();
                        int dbQueryCount = 0;
                        for (User roomUser : videoRoom.userList) {
                                try {
                                        UserData ud = DBHelper.getInstance().getUserDataById(roomUser.id);
                                        userList.add(ud);
                                        dbQueryCount++;

                                        log.trace("참가자 정보 조회: userId={}, sessionId={}, signalId={}, participantId={}, nickname={}",
                                                        user.id, user.getSessionId(), signalId, roomUser.id,
                                                        LoggingUtils.sanitizeUserName(ud.nickname));
                                } catch (Exception e) {
                                        log.error("참가자 정보 조회 실패: userId={}, sessionId={}, signalId={}, participantId={}, error={}",
                                                        user.id, user.getSessionId(), signalId, roomUser.id,
                                                        e.getMessage(), e);
                                }
                        }

                        command.userList = userList;
                        this.user.sendMsg(command, false);

                        long duration = timer.stop();
                        log.info("영상방 참가자 목록 조회 완료: userId={}, sessionId={}, signalId={}, videoRoomId={}, " +
                                        "participantCount={}, dbQueryCount={}, duration={}ms",
                                        user.id, user.getSessionId(), signalId, command.videoRoomId,
                                        userList.size(), dbQueryCount, duration);

                } catch (Exception e) {
                        timer.stop("ERROR: " + e.getMessage());
                        log.error("영상방 참가자 목록 조회 중 오류: userId={}, sessionId={}, signalId={}, videoRoomId={}, error={}",
                                        user.id, user.getSessionId(), signalId, command.videoRoomId, e.getMessage(), e);
                }
        }

        /**
         * 미디어 상태 처리
         */
        public void mediaStatus(MediaStatusCommand command) {
                String signalId = LoggingUtils.generateSignalId();
                PerformanceLogger.Timer timer = PerformanceLogger.startTimer("MEDIA_STATUS",
                                String.format("userId=%d,videoRoomId=%s,signalId=%s", user.id, command.videoRoomId,
                                                signalId));

                log.debug("미디어 상태 변경 시작: userId={}, sessionId={}, signalId={}, videoRoomId={}, " +
                                "mediaType={}, isEnabled={}",
                                user.id, user.getSessionId(), signalId, command.videoRoomId,
                                command.mediaType, command.isEnabled);

                try {
                        VideoRoom videoRoom = ChatServer.videoRoomMap.get(command.videoRoomId);

                        if (videoRoom == null) {
                                log.warn("존재하지 않는 영상방에서 미디어 상태 변경: userId={}, sessionId={}, signalId={}, videoRoomId={}",
                                                user.id, user.getSessionId(), signalId, command.videoRoomId);
                                return;
                        }

                        int notificationCount = 0;
                        int failedCount = 0;

                        for (User roomUser : videoRoom.userList) {
                                if (roomUser.id == command.requesterId) {
                                        continue;
                                }

                                try {
                                        command.recipientId = roomUser.id;
                                        roomUser.sendMsg(command, false);
                                        notificationCount++;

                                        log.trace("미디어 상태 알림 전송: userId={}, sessionId={}, signalId={}, targetUserId={}, "
                                                        +
                                                        "mediaType={}, isEnabled={}",
                                                        user.id, user.getSessionId(), signalId, roomUser.id,
                                                        command.mediaType, command.isEnabled);
                                } catch (Exception e) {
                                        failedCount++;
                                        log.error("미디어 상태 알림 전송 실패: userId={}, sessionId={}, signalId={}, targetUserId={}, error={}",
                                                        user.id, user.getSessionId(), signalId, roomUser.id,
                                                        e.getMessage(), e);
                                }
                        }

                        long duration = timer.stop();
                        log.info("미디어 상태 변경 완료: userId={}, sessionId={}, signalId={}, videoRoomId={}, " +
                                        "mediaType={}, isEnabled={}, notificationCount={}, failedCount={}, duration={}ms",
                                        user.id, user.getSessionId(), signalId, command.videoRoomId,
                                        command.mediaType, command.isEnabled, notificationCount, failedCount, duration);

                } catch (Exception e) {
                        timer.stop("ERROR: " + e.getMessage());
                        log.error("미디어 상태 변경 중 오류: userId={}, sessionId={}, signalId={}, videoRoomId={}, error={}",
                                        user.id, user.getSessionId(), signalId, command.videoRoomId, e.getMessage(), e);
                }
        }

        /**
         * 영상회의 방 나가기
         */
        public void exitVideoRoom(ExitVideoRoomCommand command) {
                String signalId = LoggingUtils.generateSignalId();
                PerformanceLogger.Timer timer = PerformanceLogger.startTimer("EXIT_VIDEO_ROOM",
                                String.format("userId=%d,videoRoomId=%s,signalId=%s", user.id, command.videoRoomId,
                                                signalId));

                log.debug("영상방 퇴장 시작: userId={}, sessionId={}, signalId={}, videoRoomId={}, isHost={}, roomId={}",
                                user.id, user.getSessionId(), signalId, command.videoRoomId, command.isHost,
                                command.roomId);

                try {
                        boolean isHost = command.isHost;
                        String videoRoomId = command.videoRoomId;

                        VideoRoom videoRoom = ChatServer.videoRoomMap.get(videoRoomId);

                        if (videoRoom == null) {
                                log.warn("존재하지 않는 영상방 퇴장 시도: userId={}, sessionId={}, signalId={}, videoRoomId={}",
                                                user.id, user.getSessionId(), signalId, videoRoomId);
                                return;
                        }

                        int initialParticipantCount = videoRoom.userList.size();

                        // 방장이면 방 자체를 제거후 회의 종료 메시지 전송
                        if (isHost) {
                                log.info("영상방 호스트 퇴장 - 방 종료: userId={}, sessionId={}, signalId={}, videoRoomId={}, participantCount={}",
                                                user.id, user.getSessionId(), signalId, videoRoomId,
                                                initialParticipantCount);

                                try {
                                        // 영상통화 종료 메시지를 채팅방으로 전송한다.
                                        SendMessageCommand messageCommand = new SendMessageCommand(command.roomId,
                                                        videoRoom.id, Type.VIDEO_ROOM_CLOSE);
                                        messageCommand.readStatus = ReadStatus.UNREAD;
                                        messageCommand.requesterId = this.user.id;

                                        user.messageHandler.sendMessage(messageCommand);

                                        log.debug("영상방 종료 메시지 전송 완료: userId={}, sessionId={}, signalId={}, videoRoomId={}, chatRoomId={}",
                                                        user.id, user.getSessionId(), signalId, videoRoomId,
                                                        command.roomId);
                                } catch (Exception e) {
                                        log.error("영상방 종료 메시지 전송 실패: userId={}, sessionId={}, signalId={}, videoRoomId={}, error={}",
                                                        user.id, user.getSessionId(), signalId, videoRoomId,
                                                        e.getMessage(), e);
                                }
                        }

                        // 명단에서 본인 제거
                        boolean removed = videoRoom.userList.remove(this.user);
                        log.debug(
                                        "영상방에서 사용자 제거: userId={}, sessionId={}, signalId={}, videoRoomId={}, removed={}, remainingCount={}",
                                        user.id, user.getSessionId(), signalId, videoRoomId, removed,
                                        videoRoom.userList.size());

                        // 회의 방에도 통보
                        int notificationCount = 0;
                        int failedCount = 0;
                        for (User roomUser : videoRoom.userList) {
                                try {
                                        ExitVideoRoomCommand exitCommand = new ExitVideoRoomCommand();
                                        exitCommand.isHost = command.isHost;
                                        exitCommand.videoRoomId = command.videoRoomId;
                                        exitCommand.roomId = command.roomId;
                                        exitCommand.requesterId = command.requesterId;
                                        roomUser.sendMsg(exitCommand, false);
                                        notificationCount++;

                                        log.trace("영상방 퇴장 알림 전송: userId={}, sessionId={}, signalId={}, targetUserId={}",
                                                        user.id, user.getSessionId(), signalId, roomUser.id);
                                } catch (Exception e) {
                                        failedCount++;
                                        log.error("영상방 퇴장 알림 전송 실패: userId={}, sessionId={}, signalId={}, targetUserId={}, error={}",
                                                        user.id, user.getSessionId(), signalId, roomUser.id,
                                                        e.getMessage(), e);
                                }
                        }

                        // 인원이 0명이면 제거
                        boolean roomRemoved = false;
                        if (videoRoom.userList.size() == 0) {
                                ChatServer.videoRoomMap.remove(videoRoomId);
                                roomRemoved = true;
                                log.info("빈 영상방 제거: userId={}, sessionId={}, signalId={}, videoRoomId={}",
                                                user.id, user.getSessionId(), signalId, videoRoomId);
                        }

                        long duration = timer.stop();
                        log.info(LoggingConstants.VIDEO_ROOM_EXITED + ", signalId={}, initialParticipantCount={}, " +
                                        "notificationCount={}, failedCount={}, roomRemoved={}, duration={}ms",
                                        videoRoomId, user.id, isHost ? "HOST_EXIT" : "PARTICIPANT_EXIT",
                                        videoRoom.userList.size(),
                                        signalId, initialParticipantCount, notificationCount, failedCount, roomRemoved,
                                        duration);

                } catch (Exception e) {
                        timer.stop("ERROR: " + e.getMessage());
                        log.error("영상방 퇴장 중 오류: userId={}, sessionId={}, signalId={}, videoRoomId={}, error={}",
                                        user.id, user.getSessionId(), signalId, command.videoRoomId, e.getMessage(), e);
                }
        }

        /**
         * SDP Offer, Answer 처리
         */
        public void handleSDP(SDPCommand command) {
                String signalId = LoggingUtils.generateSignalId();
                PerformanceLogger.Timer timer = PerformanceLogger.startTimer("SDP_SIGNALING",
                                String.format("userId=%d,videoRoomId=%s,signalId=%s", user.id, command.videoRoomId,
                                                signalId));

                log.debug(LoggingConstants.SDP_SIGNALING_START,
                                signalId, user.id, command.targetId, command.sdp.type,
                                command.sdp.description != null ? command.sdp.description.length() : 0);

                try {
                        long targetId = command.targetId;
                        String videoRoomId = command.videoRoomId;

                        // videoRoom 객체에서 타겟 유저 꺼내와서 해당 유저에게 커맨드 전송
                        VideoRoom videoRoom = ChatServer.videoRoomMap.get(videoRoomId);

                        if (videoRoom == null) {
                                log.warn("존재하지 않는 영상방에서 SDP 시그널링: userId={}, sessionId={}, signalId={}, videoRoomId={}, targetId={}",
                                                user.id, user.getSessionId(), signalId, videoRoomId, targetId);
                                return;
                        }

                        User target = videoRoom.getUserById(targetId);
                        if (target == null) {
                                log.warn("영상방에서 대상 사용자 찾을 수 없음: userId={}, sessionId={}, signalId={}, videoRoomId={}, targetId={}",
                                                user.id, user.getSessionId(), signalId, videoRoomId, targetId);
                                return;
                        }

                        // SDP 유효성 검증
                        if (command.sdp == null || command.sdp.description == null
                                        || command.sdp.description.trim().isEmpty()) {
                                log.warn("유효하지 않은 SDP 데이터: userId={}, sessionId={}, signalId={}, videoRoomId={}, targetId={}",
                                                user.id, user.getSessionId(), signalId, videoRoomId, targetId);
                                return;
                        }

                        log.debug("SDP 시그널링 검증 완료: userId={}, sessionId={}, signalId={}, videoRoomId={}, " +
                                        "targetId={}, sdpType={}, sdpLength={}",
                                        user.id, user.getSessionId(), signalId, videoRoomId, targetId,
                                        command.sdp.type, command.sdp.description.length());

                        target.sendMsg(command, false);

                        long duration = timer.stop();
                        log.info(LoggingConstants.SDP_SIGNALING_COMPLETE,
                                        signalId, user.id, targetId, command.sdp.type, duration,
                                        command.toJson().length());

                        // 성능 임계값 체크
                        if (duration > LoggingConstants.PERFORMANCE_SDP_SIGNALING_WARN_MS) {
                                log.warn("SDP 시그널링 성능 경고: userId={}, sessionId={}, signalId={}, duration={}ms, threshold={}ms",
                                                user.id, user.getSessionId(), signalId, duration,
                                                LoggingConstants.PERFORMANCE_SDP_SIGNALING_WARN_MS);
                        }

                } catch (Exception e) {
                        timer.stop("ERROR: " + e.getMessage());
                        log.error("SDP 시그널링 중 오류: userId={}, sessionId={}, signalId={}, videoRoomId={}, targetId={}, error={}",
                                        user.id, user.getSessionId(), signalId, command.videoRoomId, command.targetId,
                                        e.getMessage(), e);
                }
        }

        /**
         * IceCandidate 처리 (새 참가자 -> 기존 참가자들)
         */
        public void handleIceCandidate(IceCandidateCommand command) {
                String signalId = LoggingUtils.generateSignalId();
                PerformanceLogger.Timer timer = PerformanceLogger.startTimer("ICE_CANDIDATE",
                                String.format("userId=%d,videoRoomId=%s,signalId=%s", user.id, command.videoRoomId,
                                                signalId));

                log.debug(
                                "ICE Candidate 처리 시작: userId={}, sessionId={}, signalId={}, videoRoomId={}, targetId={}, candidate={}",
                                user.id, user.getSessionId(), signalId, command.videoRoomId, command.targetId,
                                LoggingUtils.sanitizeIceCandidate(command.iceCandidate));

                try {
                        VideoRoom room = ChatServer.videoRoomMap.get(command.videoRoomId);

                        if (room == null) {
                                log.warn("존재하지 않는 영상방에서 ICE Candidate 처리: userId={}, sessionId={}, signalId={}, videoRoomId={}",
                                                user.id, user.getSessionId(), signalId, command.videoRoomId);
                                return;
                        }

                        long targetId = command.targetId;

                        // ICE 후보 유효성 검증
                        if (command.iceCandidate == null || command.iceCandidate.sdp == null) {
                                log.warn("유효하지 않은 ICE Candidate: userId={}, sessionId={}, signalId={}, videoRoomId={}, targetId={}",
                                                user.id, user.getSessionId(), signalId, command.videoRoomId, targetId);
                                return;
                        }

                        log.debug("ICE Candidate 검증 완료: userId={}, sessionId={}, signalId={}, videoRoomId={}, " +
                                        "targetId={}, candidateType={}, candidateLength={}",
                                        user.id, user.getSessionId(), signalId, command.videoRoomId, targetId,
                                        command.iceCandidate.sdp.split(" ").length > 2
                                                        ? command.iceCandidate.sdp.split(" ")[2]
                                                        : "unknown",
                                        command.iceCandidate.sdp.length());

                        // ice 후보 전송
                        boolean sent = false;
                        for (User roomUser : room.userList) {
                                if (roomUser.id == targetId) {
                                        roomUser.sendMsg(command, false);
                                        sent = true;

                                        log.trace("ICE Candidate 전송 성공: userId={}, sessionId={}, signalId={}, targetUserId={}",
                                                        user.id, user.getSessionId(), signalId, targetId);
                                        break;
                                }
                        }

                        if (!sent) {
                                log.warn(
                                                "ICE Candidate 전송 실패 - 대상 사용자 없음: userId={}, sessionId={}, signalId={}, videoRoomId={}, targetId={}",
                                                user.id, user.getSessionId(), signalId, command.videoRoomId, targetId);
                        }

                        long duration = timer.stop();
                        log.info(LoggingConstants.ICE_CANDIDATE_EXCHANGE,
                                        user.id, targetId, LoggingUtils.sanitizeIceCandidate(command.iceCandidate),
                                        signalId);

                        log.debug("ICE Candidate 처리 완료: userId={}, sessionId={}, signalId={}, videoRoomId={}, " +
                                        "targetId={}, sent={}, duration={}ms",
                                        user.id, user.getSessionId(), signalId, command.videoRoomId, targetId, sent,
                                        duration);

                } catch (Exception e) {
                        timer.stop("ERROR: " + e.getMessage());
                        log.error(
                                        "ICE Candidate 처리 중 오류: userId={}, sessionId={}, signalId={}, videoRoomId={}, targetId={}, error={}",
                                        user.id, user.getSessionId(), signalId, command.videoRoomId, command.targetId,
                                        e.getMessage(), e);
                }
        }

        /**
         * 영상 회의 방 참가
         */
        public void joinVideoRoom(JoinVideoRoomCommand command) {
                String signalId = LoggingUtils.generateSignalId();
                PerformanceLogger.Timer timer = PerformanceLogger.startTimer("JOIN_VIDEO_ROOM",
                                String.format("userId=%d,videoRoomId=%s,signalId=%s", user.id, command.videoRoomId,
                                                signalId));

                log.debug("영상방 참가 시작: userId={}, sessionId={}, signalId={}, videoRoomId={}, requesterId={}",
                                user.id, user.getSessionId(), signalId, command.videoRoomId, command.requesterId);

                try {
                        VideoRoom videoRoom = ChatServer.videoRoomMap.get(command.videoRoomId);

                        if (videoRoom == null) {
                                log.warn("존재하지 않는 영상방 참가 시도: userId={}, sessionId={}, signalId={}, videoRoomId={}",
                                                user.id, user.getSessionId(), signalId, command.videoRoomId);
                                return;
                        }

                        int initialParticipantCount = videoRoom.userList.size();

                        // 이미 참가한 사용자인지 확인
                        boolean alreadyJoined = videoRoom.userList.stream()
                                        .anyMatch(u -> u.id == this.user.id);

                        if (alreadyJoined) {
                                log.warn("이미 참가한 영상방 재참가 시도: userId={}, sessionId={}, signalId={}, videoRoomId={}",
                                                user.id, user.getSessionId(), signalId, command.videoRoomId);
                                return;
                        }

                        // 영상회의방 멤버목록에 자신을 추가
                        videoRoom.addUser(this.user);

                        log.debug("영상방 멤버 추가 완료: userId={}, sessionId={}, signalId={}, videoRoomId={}, " +
                                        "participantCount={} -> {}",
                                        user.id, user.getSessionId(), signalId, command.videoRoomId,
                                        initialParticipantCount, videoRoom.userList.size());

                        // 사용자 정보 조회
                        UserData userData = null;
                        try {
                                userData = DBHelper.getInstance().getUserDataById(command.requesterId);
                                command.nickname = userData.nickname;
                                command.profileImage = userData.profileImage;

                                log.debug("참가자 정보 조회 완료: userId={}, sessionId={}, signalId={}, nickname={}, profileImage={}",
                                                user.id, user.getSessionId(), signalId,
                                                LoggingUtils.sanitizeUserName(userData.nickname),
                                                userData.profileImage != null ? "present" : "null");
                        } catch (Exception e) {
                                log.error("참가자 정보 조회 실패: userId={}, sessionId={}, signalId={}, error={}",
                                                user.id, user.getSessionId(), signalId, e.getMessage(), e);
                        }

                        // 기존 참가자들에게는 새로 참가한 사람의 정보만 제공
                        int notificationCount = 0;
                        int failedCount = 0;
                        for (User roomUser : videoRoom.userList) {
                                // 새로 참가한 사람에게는 전송하지 않음
                                if (roomUser.id == this.user.id) {
                                        continue;
                                }

                                try {
                                        roomUser.sendMsg(command, false);
                                        notificationCount++;

                                        log.trace("영상방 참가 알림 전송: userId={}, sessionId={}, signalId={}, targetUserId={}",
                                                        user.id, user.getSessionId(), signalId, roomUser.id);
                                } catch (Exception e) {
                                        failedCount++;
                                        log.error("영상방 참가 알림 전송 실패: userId={}, sessionId={}, signalId={}, targetUserId={}, error={}",
                                                        user.id, user.getSessionId(), signalId, roomUser.id,
                                                        e.getMessage(), e);
                                }
                        }

                        // 참가자 본인에게는 모든 참가자(본인포함)의 정보를 제공
                        List<UserData> userList = new ArrayList<>();
                        int dbQueryCount = 0;
                        for (User roomUser : videoRoom.userList) {
                                try {
                                        UserData ud = DBHelper.getInstance().getUserDataById(roomUser.id);
                                        userList.add(ud);
                                        dbQueryCount++;
                                } catch (Exception e) {
                                        log.error("참가자 목록 조회 실패: userId={}, sessionId={}, signalId={}, participantId={}, error={}",
                                                        user.id, user.getSessionId(), signalId, roomUser.id,
                                                        e.getMessage(), e);
                                }
                        }
                        command.userList = userList;

                        // 참가자는 본인 포함 모든 멤버의 명단을 받음
                        this.user.sendMsg(command, false);

                        long duration = timer.stop();
                        log.info(LoggingConstants.VIDEO_ROOM_JOINED + ", signalId={}, initialParticipantCount={}, " +
                                        "notificationCount={}, failedCount={}, dbQueryCount={}, duration={}ms",
                                        command.videoRoomId, user.id, userData != null ? userData.nickname : "unknown",
                                        videoRoom.userList.size(), user.getSessionId(),
                                        signalId, initialParticipantCount, notificationCount, failedCount, dbQueryCount,
                                        duration);

                } catch (Exception e) {
                        timer.stop("ERROR: " + e.getMessage());
                        log.error("영상방 참가 중 오류: userId={}, sessionId={}, signalId={}, videoRoomId={}, error={}",
                                        user.id, user.getSessionId(), signalId, command.videoRoomId, e.getMessage(), e);
                }
        }

        /**
         * 영상 회의방 생성
         */
        public void createVideoRoom(CreateVideoRoomCommand command) {
                String signalId = LoggingUtils.generateSignalId();
                PerformanceLogger.Timer timer = PerformanceLogger.startTimer("CREATE_VIDEO_ROOM",
                                String.format("userId=%d,roomId=%d,signalId=%s", user.id, command.roomId, signalId));

                log.debug("영상방 생성 시작: userId={}, sessionId={}, signalId={}, chatRoomId={}, requesterId={}",
                                user.id, user.getSessionId(), signalId, command.roomId, command.requesterId);

                try {
                        // 영상회의 객체를 생성한다.
                        // 생성시 id는 uuidv4로 자동할당
                        VideoRoom videoRoom = new VideoRoom();
                        String videoRoomId = videoRoom.id;

                        log.debug("영상방 객체 생성 완료: userId={}, sessionId={}, signalId={}, videoRoomId={}, hostId={}",
                                        user.id, user.getSessionId(), signalId, videoRoomId, user.id);

                        videoRoom.addUser(this.user); // 생성자를 멤버로 추가
                        videoRoom.hostId = this.user.id;

                        // 서버의 영상회의 목록에 추가
                        ChatServer.videoRoomMap.put(videoRoom.id, videoRoom);

                        log.debug("영상방 서버 등록 완료: userId={}, sessionId={}, signalId={}, videoRoomId={}, " +
                                        "totalVideoRooms={}, participantCount={}",
                                        user.id, user.getSessionId(), signalId, videoRoomId,
                                        ChatServer.videoRoomMap.size(), videoRoom.userList.size());

                        // 생성된 방 정보(id)를 호스트에게 전송한다.
                        command.videoRoomId = videoRoom.id;
                        this.user.sendMsg(command, false);

                        // 영상통화 생성 메시지 커맨드를 생성한다.
                        // 메시지 내용은 생성된 회의 id다.
                        try {
                                SendMessageCommand messageCommand = new SendMessageCommand(command.roomId, videoRoom.id,
                                                Type.VIDEO_ROOM_OPEN);
                                messageCommand.readStatus = ReadStatus.UNREAD;
                                messageCommand.requesterId = this.user.id;

                                // 생성된 메시지 커맨드를 채팅방으로 전송한다.
                                user.messageHandler.sendMessage(messageCommand);

                                log.debug("영상방 생성 메시지 전송 완료: userId={}, sessionId={}, signalId={}, videoRoomId={}, chatRoomId={}",
                                                user.id, user.getSessionId(), signalId, videoRoomId, command.roomId);
                        } catch (Exception e) {
                                log.error(
                                                "영상방 생성 메시지 전송 실패: userId={}, sessionId={}, signalId={}, videoRoomId={}, chatRoomId={}, error={}",
                                                user.id, user.getSessionId(), signalId, videoRoomId, command.roomId,
                                                e.getMessage(), e);
                        }

                        long duration = timer.stop();
                        log.info(LoggingConstants.VIDEO_ROOM_CREATED,
                                        videoRoomId, user.id, videoRoom.userList.size(), "default");

                        log.debug("영상방 생성 완료: userId={}, sessionId={}, signalId={}, videoRoomId={}, chatRoomId={}, " +
                                        "duration={}ms, totalVideoRooms={}",
                                        user.id, user.getSessionId(), signalId, videoRoomId, command.roomId,
                                        duration, ChatServer.videoRoomMap.size());

                } catch (Exception e) {
                        timer.stop("ERROR: " + e.getMessage());
                        log.error("영상방 생성 중 오류: userId={}, sessionId={}, signalId={}, chatRoomId={}, error={}",
                                        user.id, user.getSessionId(), signalId, command.roomId, e.getMessage(), e);
                }
        }
}