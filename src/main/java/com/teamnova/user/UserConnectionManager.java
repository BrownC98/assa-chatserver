package com.teamnova.user;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * 사용자 연결 관리를 담당하는 클래스
 */
public class UserConnectionManager {

    private static Logger log = LogManager.getLogger(UserConnectionManager.class.getName());

    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private boolean isConnected;
    private long userId;

    /**
     * 생성자
     * 
     * @param socket 소켓 연결
     * @param userId 사용자 ID
     */
    public UserConnectionManager(Socket socket, long userId) {
        this.socket = socket;
        this.userId = userId;
        this.isConnected = false;

        if (socket != null) {
            initializeStreams();
        }
    }

    /**
     * 스트림 초기화
     */
    private void initializeStreams() {
        try {
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);
            isConnected = true;
            log.debug("사용자 ID={} 스트림 초기화 완료", userId);
        } catch (IOException e) {
            log.error("스트림 초기화 실패: userId={}", userId, e);
            isConnected = false;
        }
    }

    /**
     * 소켓 교체
     * 
     * @param newSocket 새로운 소켓
     */
    public void replaceSocket(Socket newSocket) {
        log.debug("사용자 ID={} 소켓을 새것으로 교체", userId);

        // 기존 소켓 정리
        closeCurrentSocket();

        // 새 소켓 설정
        this.socket = newSocket;
        if (newSocket != null) {
            initializeStreams();
        }
    }

    /**
     * 현재 소켓 종료
     */
    private void closeCurrentSocket() {
        if (socket != null && !socket.isClosed()) {
            try {
                if (in != null) {
                    in.close();
                }
                if (out != null) {
                    out.close();
                }
                socket.close();
                log.debug("사용자 ID={} 기존 소켓 종료 완료", userId);
            } catch (IOException e) {
                log.error("소켓 종료 중 오류: userId={}", userId, e);
            }
        }
        isConnected = false;
    }

    /**
     * 연결 해제
     */
    public void disconnect() {
        log.debug("사용자 ID={} 연결 해제 시작", userId);
        closeCurrentSocket();
        socket = null;
        in = null;
        out = null;
        isConnected = false;
        log.debug("사용자 ID={} 연결 해제 완료", userId);
    }

    /**
     * 연결 상태 확인
     * 
     * @return 연결 여부
     */
    public boolean isConnected() {
        return isConnected && socket != null && !socket.isClosed();
    }

    /**
     * 소켓 반환
     * 
     * @return 현재 소켓
     */
    public Socket getSocket() {
        return socket;
    }

    /**
     * 입력 스트림 반환
     * 
     * @return BufferedReader
     */
    public BufferedReader getInputStream() {
        return in;
    }

    /**
     * 출력 스트림 반환
     * 
     * @return PrintWriter
     */
    public PrintWriter getOutputStream() {
        return out;
    }
}