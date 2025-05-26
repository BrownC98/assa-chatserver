/**
 * 서버 시작/종료, 클라이언트 연결 수락 관련 클래스들
 * 
 * 이 패키지는 ASSA 채팅 서버의 핵심 서버 기능을 담당합니다.
 * - ChatServer: 메인 서버 클래스, 소켓 연결 관리
 * - 클라이언트 연결 수락 및 User 스레드 생성
 * - 전역 데이터 구조 관리 (userList, roomMap, videoRoomMap)
 */
package com.teamnova.server; 