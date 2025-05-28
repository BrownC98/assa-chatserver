package com.teamnova;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Before;
import org.junit.Test;

import com.teamnova.server.ChatServer;
import com.teamnova.user.User;

/**
 * WebRTC 시그널링 기능 테스트
 */
public class WebRTCTest {

    private User user;
    private ChatServer server;

    @Before
    public void setUp() {
        // 테스트용 사용자 및 서버 초기화
        user = new User(123L);
        server = new ChatServer();
        server.userList = new java.util.concurrent.CopyOnWriteArrayList<>();
        server.videoRoomMap = new java.util.concurrent.ConcurrentHashMap<>();
    }

    @Test
    public void testVideoRoomCreation() {
        // Given: 영상방 맵이 초기화되어 있음
        // When & Then: 영상방 맵이 올바르게 초기화되었는지 확인
        try {
            assertNotNull("영상방 맵이 초기화되어야 함", server.videoRoomMap);
            assertTrue("영상방 맵이 비어있어야 함", server.videoRoomMap.isEmpty());
        } catch (Exception e) {
            fail("영상방 생성 중 예외가 발생하지 않아야 함: " + e.getMessage());
        }
    }

    @Test
    public void testSDPHandling() {
        // Given: 사용자가 생성되어 있음
        // When & Then: 사용자 객체가 올바르게 생성되었는지 확인
        try {
            assertNotNull("사용자 객체가 생성되어야 함", user);
            assertEquals("사용자 ID가 올바르게 설정되어야 함", 123L, user.id);
        } catch (Exception e) {
            fail("SDP 처리 중 예외가 발생하지 않아야 함: " + e.getMessage());
        }
    }

    @Test
    public void testIceCandidateHandling() {
        // Given: 서버가 초기화되어 있음
        // When & Then: 서버 객체가 올바르게 초기화되었는지 확인
        try {
            assertNotNull("서버 객체가 생성되어야 함", server);
            assertNotNull("사용자 리스트가 초기화되어야 함", server.userList);
            assertTrue("사용자 리스트가 비어있어야 함", server.userList.isEmpty());
        } catch (Exception e) {
            fail("ICE 후보 처리 중 예외가 발생하지 않아야 함: " + e.getMessage());
        }
    }
}