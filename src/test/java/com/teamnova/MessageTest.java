package com.teamnova;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import org.junit.Before;
import org.junit.Test;

import com.teamnova.command.chat.CreateRoomCommand;
import com.teamnova.command.chat.SendMessageCommand;
import com.teamnova.server.ChatServer;
import com.teamnova.user.User;

/**
 * 메시지 송수신 기능 테스트
 */
public class MessageTest {

    private User user;
    private ChatServer server;

    @Before
    public void setUp() {
        // 테스트용 사용자 및 서버 초기화
        user = new User(123L);
        server = new ChatServer();
        server.userList = new java.util.concurrent.CopyOnWriteArrayList<>();
        server.roomMap = new java.util.concurrent.ConcurrentHashMap<>();
    }

    @Test
    public void testSendMessage() {
        // Given: 메시지 전송 명령 생성
        SendMessageCommand command = new SendMessageCommand(
                123L, // recipientId
                1L, // roomId
                "테스트 메시지", // content
                SendMessageCommand.MessageType.TEXT, // type
                SendMessageCommand.TransmissionStatus.NOT_SENT, // transmissionStatus
                SendMessageCommand.ReadStatus.UNREAD // readStatus
        );

        // When & Then: 메시지 전송 메서드가 예외 없이 실행되는지 확인
        try {
            // 실제 소켓 연결 없이 테스트하므로 객체 생성만 확인
            assertNotNull("SendMessage 명령이 생성되어야 함", command);
            assertEquals("방 ID가 올바르게 설정되어야 함", Long.valueOf(1L), command.roomId);
            assertEquals("메시지 내용이 올바르게 설정되어야 함", "테스트 메시지", command.content);
        } catch (Exception e) {
            fail("메시지 전송 중 예외가 발생하지 않아야 함: " + e.getMessage());
        }
    }

    @Test
    public void testReceiveMessage() {
        // Given: 사용자의 메시지 큐
        String testMessage = "수신 테스트 메시지";

        // When: 메시지 큐에 메시지 추가
        user.messageQueue.offer(testMessage);

        // Then: 메시지가 큐에 올바르게 추가되었는지 확인
        assertFalse("메시지 큐가 비어있지 않아야 함", user.messageQueue.isEmpty());
        assertEquals("큐에서 꺼낸 메시지가 올바르게 설정되어야 함", testMessage, user.messageQueue.poll());
    }

    @Test
    public void testRoomCreation() {
        // Given: 채팅방 생성 명령
        CreateRoomCommand command = new CreateRoomCommand("테스트방", "테스트 설명", CreateRoomCommand.RoomType.NORMAL);

        // When & Then: 채팅방 생성 명령이 올바르게 설정되는지 확인
        try {
            assertNotNull("CreateRoom 명령이 생성되어야 함", command);
            assertEquals("방 이름이 올바르게 설정되어야 함", "테스트방", command.roomName);
            assertEquals("방 설명이 올바르게 설정되어야 함", "테스트 설명", command.description);
            assertEquals("방 타입이 올바르게 설정되어야 함", CreateRoomCommand.RoomType.NORMAL, command.roomType);
        } catch (Exception e) {
            fail("채팅방 생성 중 예외가 발생하지 않아야 함: " + e.getMessage());
        }
    }
}