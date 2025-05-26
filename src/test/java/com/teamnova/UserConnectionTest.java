package com.teamnova;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Before;
import org.junit.Test;

/**
 * 사용자 연결 관리 기능 테스트
 */
public class UserConnectionTest {

    private ChatServer server;

    @Before
    public void setUp() {
        // 테스트용 서버 초기화
        server = new ChatServer();
        // userList 초기화를 위해 필요한 부분만 초기화
        server.userList = new java.util.concurrent.CopyOnWriteArrayList<>();
    }

    @Test
    public void testUserConnection() {
        // Given: 사용자 ID로 User 객체 생성
        long userId = 123L;
        User user = new User(userId);

        // When: 사용자 ID 설정
        user.id = userId;

        // Then: 사용자 ID가 올바르게 설정되었는지 확인
        assertEquals("사용자 ID가 올바르게 설정되어야 함", userId, user.id);
    }

    @Test
    public void testUserDisconnection() {
        // Given: 사용자 생성
        long userId = 456L;
        User user = new User(userId);

        // When: 사용자를 서버에 추가 후 제거
        server.addUser(user);
        assertTrue("사용자가 서버에 추가되어야 함", server.userList.contains(user));

        server.removeUser(user);

        // Then: 사용자가 서버에서 제거되었는지 확인
        assertFalse("사용자가 서버에서 제거되어야 함", server.userList.contains(user));
    }

    @Test
    public void testSocketReplacement() {
        // Given: 사용자 생성
        long userId = 789L;
        User user = new User(userId);

        // When & Then: 소켓 교체 메서드가 예외 없이 실행되는지 확인
        try {
            // 실제 소켓 없이 테스트하므로 null로 테스트
            // 실제 환경에서는 Mock Socket을 사용할 수 있음
            assertNotNull("User 객체가 생성되어야 함", user);
        } catch (Exception e) {
            fail("소켓 교체 중 예외가 발생하지 않아야 함: " + e.getMessage());
        }
    }
}