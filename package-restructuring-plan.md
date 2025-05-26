# ASSA Chat Server 패키지 구조 개선 계획

## 📋 작업 개요

- **목적**: 현재 혼재된 패키지 구조를 논리적이고 확장 가능한 구조로 개선
- **시간**: 1일(8시간) 내 완료
- **방법**: 점진적 패키지 이동 + 테스트 기반 안전한 리팩토링
- **핵심**: 기존 기능 100% 유지 + 명확한 책임 분리

---

## 🎯 현재 패키지 구조 분석

### 현재 상태 (문제점)

```
com.teamnova/
├── User.java (308줄) - 핵심 사용자 관리
├── UserConnectionManager.java (143줄) - 연결 관리
├── MessageHandler.java (348줄) - 메시지 처리
├── WebRTCSignalingHandler.java (221줄) - WebRTC 시그널링
├── ChatServer.java (82줄) - 메인 서버
├── DBHelper.java (728줄) - 데이터베이스 처리
├── ChatRoom.java (58줄) - 채팅방 관리
├── VideoRoom.java (38줄) - 영상방 관리
├── PropertiesManager.java (33줄) - 설정 관리
├── Main.java (9줄) - 진입점
├── [기타 DTO 클래스들] - 루트에 혼재
├── command/ (18개 Command 클래스)
├── dto/ (5개 DTO 클래스)
└── Utils/ (3개 유틸 클래스)
```

### 문제점 식별

1. **루트 패키지 혼잡**: 핵심 클래스들이 루트에 모두 위치
2. **책임 분리 부족**: 서버, 사용자, 메시지, WebRTC 로직이 혼재
3. **확장성 부족**: 새로운 기능 추가 시 구조적 혼란
4. **테스트 어려움**: 패키지별 독립적 테스트 불가능
5. **의존성 복잡**: 순환 참조 및 강결합 문제

---

## 🏗️ 목표 패키지 구조

### 개선된 구조 (목표)

```
com.teamnova/
├── Main.java (진입점)
├── server/                    # 서버 관련
│   ├── ChatServer.java
│   ├── ServerConfig.java
│   └── ServerManager.java
├── user/                      # 사용자 관리
│   ├── User.java
│   ├── UserConnectionManager.java
│   ├── UserRepository.java
│   └── UserService.java
├── chat/                      # 채팅 기능
│   ├── ChatRoom.java
│   ├── ChatRoomManager.java
│   ├── MessageHandler.java
│   └── MessageService.java
├── webrtc/                    # WebRTC 기능
│   ├── VideoRoom.java
│   ├── VideoRoomManager.java
│   ├── WebRTCSignalingHandler.java
│   └── SignalingService.java
├── database/                  # 데이터베이스
│   ├── DBHelper.java
│   ├── ConnectionPool.java
│   └── repository/
│       ├── UserRepository.java
│       ├── ChatRoomRepository.java
│       └── MessageRepository.java
├── command/                   # 명령 처리
│   ├── Action.java
│   ├── BaseCommand.java
│   ├── chat/                  # 채팅 명령
│   ├── webrtc/               # WebRTC 명령
│   └── user/                 # 사용자 명령
├── dto/                      # 데이터 전송 객체
│   ├── chat/
│   ├── webrtc/
│   └── user/
├── config/                   # 설정 관리
│   ├── PropertiesManager.java
│   ├── DatabaseConfig.java
│   └── ServerConfig.java
├── utils/                    # 유틸리티 (소문자)
│   ├── TimeUtils.java
│   ├── LogUtils.java
│   └── ValidationUtils.java
└── exception/                # 예외 처리
    ├── ChatServerException.java
    ├── DatabaseException.java
    └── WebRTCException.java
```

---

## 📅 1일 패키지 구조 개선 타임라인

### Phase 1: 준비 및 분석 (1시간) - 09:00~10:00

#### 1.1 현재 상태 백업 및 분석

- [ ] **Git 백업 커밋**
  ```bash
  git add .
  git commit -m "패키지 구조 개선 시작 전 백업"
  ```
- [ ] **현재 패키지 구조 문서화**
  - [ ] 클래스별 의존성 관계 파악
  - [ ] import 문 분석
  - [ ] 순환 참조 확인

#### 1.2 이동 계획 수립

- [ ] **우선순위별 이동 계획**
  1. 독립적인 유틸리티 클래스 (Utils → utils)
  2. 설정 관련 클래스 (config 패키지)
  3. DTO 클래스들 (기능별 하위 패키지)
  4. Command 클래스들 (기능별 하위 패키지)
  5. 핵심 비즈니스 로직 클래스들

#### 1.3 테스트 환경 확인

- [ ] **기존 테스트 실행**
  ```bash
  mvn test
  ```
- [ ] **패키지 이동 테스트 준비**

### Phase 2: 유틸리티 및 설정 패키지 정리 (1시간) - 10:00~11:00

#### 2.1 utils 패키지 정리

- [ ] **utils 패키지 생성** (소문자로 변경)
  ```java
  // src/main/java/com/teamnova/utils/
  ```
- [ ] **기존 Utils 클래스들 이동**
  - [ ] `Utils/TimeUtils.java` → `utils/TimeUtils.java`
  - [ ] `Utils/Log.java` → `utils/LogUtils.java`
  - [ ] `Utils/Utils.java` → `utils/CommonUtils.java`

#### 2.2 config 패키지 생성

- [ ] **config 패키지 생성**
  ```java
  // src/main/java/com/teamnova/config/
  ```
- [ ] **설정 관련 클래스 이동**
  - [ ] `PropertiesManager.java` → `config/PropertiesManager.java`
- [ ] **새로운 설정 클래스 생성**
  - [ ] `config/DatabaseConfig.java` (DB 설정 분리)
  - [ ] `config/ServerConfig.java` (서버 설정 분리)

#### 2.3 컴파일 및 테스트

- [ ] **컴파일 확인**
  ```bash
  mvn compile
  ```
- [ ] **import 문 수정**
- [ ] **테스트 실행**
  ```bash
  mvn test
  ```

### Phase 3: DTO 패키지 구조화 (1시간) - 11:00~12:00

#### 3.1 DTO 하위 패키지 생성

- [ ] **기능별 DTO 패키지 생성**
  ```java
  // src/main/java/com/teamnova/dto/chat/
  // src/main/java/com/teamnova/dto/webrtc/
  // src/main/java/com/teamnova/dto/user/
  ```

#### 3.2 DTO 클래스 분류 및 이동

- [ ] **채팅 관련 DTO**

  - [ ] `dto/Message.java` → `dto/chat/Message.java`
  - [ ] `dto/RoomData.java` → `dto/chat/RoomData.java`
  - [ ] `dto/MessageStatus.java` → `dto/chat/MessageStatus.java`

- [ ] **WebRTC 관련 DTO**

  - [ ] `dto/SDP.java` → `dto/webrtc/SDP.java`
  - [ ] `SessionDescription.java` → `dto/webrtc/SessionDescription.java`
  - [ ] `IceCandidate.java` → `dto/webrtc/IceCandidate.java`

- [ ] **사용자 관련 DTO**
  - [ ] `dto/Member.java` → `dto/user/Member.java`
  - [ ] `UserData.java` → `dto/user/UserData.java`

#### 3.3 import 문 수정 및 테스트

- [ ] **모든 클래스의 import 문 수정**
- [ ] **컴파일 및 테스트**

### 점심시간 (1시간) - 12:00~13:00

### Phase 4: Command 패키지 구조화 (1.5시간) - 13:00~14:30

#### 4.1 Command 하위 패키지 생성

- [ ] **기능별 Command 패키지 생성**
  ```java
  // src/main/java/com/teamnova/command/chat/
  // src/main/java/com/teamnova/command/webrtc/
  // src/main/java/com/teamnova/command/user/
  ```

#### 4.2 Command 클래스 분류 및 이동

- [ ] **채팅 관련 Command**

  - [ ] `CreateRoomCommand.java` → `command/chat/`
  - [ ] `CreateOpenChatCommand.java` → `command/chat/`
  - [ ] `SendMessageCommand.java` → `command/chat/`
  - [ ] `RoomInfoCommand.java` → `command/chat/`
  - [ ] `ExitRoomCommand.java` → `command/chat/`
  - [ ] `InviteCommand.java` → `command/chat/`
  - [ ] `CheckReceiveCommand.java` → `command/chat/`

- [ ] **WebRTC 관련 Command**

  - [ ] `CreateVideoRoomCommand.java` → `command/webrtc/`
  - [ ] `JoinVideoRoomCommand.java` → `command/webrtc/`
  - [ ] `ExitVideoRoomCommand.java` → `command/webrtc/`
  - [ ] `SDPCommand.java` → `command/webrtc/`
  - [ ] `IceCandidateCommand.java` → `command/webrtc/`
  - [ ] `MediaStatusCommand.java` → `command/webrtc/`
  - [ ] `GetVideoRoomParticipantCommand.java` → `command/webrtc/`

- [ ] **사용자 관련 Command**

  - [ ] `ConnectCommand.java` → `command/user/`

- [ ] **공통 Command (루트 유지)**
  - [ ] `Action.java` (루트 유지)
  - [ ] `BaseCommand.java` (루트 유지)
  - [ ] `ResponseCommand.java` (루트 유지)

#### 4.3 Command Factory 패턴 적용

- [ ] **CommandFactory 클래스 생성**
  ```java
  // command/CommandFactory.java
  ```
- [ ] **Action enum과 Command 매핑 개선**

### Phase 5: 핵심 비즈니스 로직 패키지화 (2시간) - 14:30~16:30

#### 5.1 server 패키지 생성

- [ ] **server 패키지 생성**
  ```java
  // src/main/java/com/teamnova/server/
  ```
- [ ] **서버 관련 클래스 이동**
  - [ ] `ChatServer.java` → `server/ChatServer.java`
  - [ ] `Main.java` (루트 유지 - 진입점)

#### 5.2 user 패키지 생성

- [ ] **user 패키지 생성**
  ```java
  // src/main/java/com/teamnova/user/
  ```
- [ ] **사용자 관련 클래스 이동**
  - [ ] `User.java` → `user/User.java`
  - [ ] `UserConnectionManager.java` → `user/UserConnectionManager.java`

#### 5.3 chat 패키지 생성

- [ ] **chat 패키지 생성**
  ```java
  // src/main/java/com/teamnova/chat/
  ```
- [ ] **채팅 관련 클래스 이동**
  - [ ] `ChatRoom.java` → `chat/ChatRoom.java`
  - [ ] `MessageHandler.java` → `chat/MessageHandler.java`

#### 5.4 webrtc 패키지 생성

- [ ] **webrtc 패키지 생성**
  ```java
  // src/main/java/com/teamnova/webrtc/
  ```
- [ ] **WebRTC 관련 클래스 이동**
  - [ ] `VideoRoom.java` → `webrtc/VideoRoom.java`
  - [ ] `WebRTCSignalingHandler.java` → `webrtc/WebRTCSignalingHandler.java`

#### 5.5 database 패키지 생성

- [ ] **database 패키지 생성**
  ```java
  // src/main/java/com/teamnova/database/
  ```
- [ ] **데이터베이스 관련 클래스 이동**
  - [ ] `DBHelper.java` → `database/DBHelper.java`

### Phase 6: 의존성 정리 및 최종 검증 (1.5시간) - 16:30~18:00

#### 6.1 import 문 전체 정리

- [ ] **모든 클래스의 import 문 수정**
- [ ] **순환 참조 해결**
- [ ] **불필요한 import 제거**

#### 6.2 패키지 정보 파일 생성

- [ ] **각 패키지에 package-info.java 생성**
  ```java
  /**
   * 채팅 기능 관련 클래스들
   */
  package com.teamnova.chat;
  ```

#### 6.3 최종 컴파일 및 테스트

- [ ] **전체 컴파일**
  ```bash
  mvn clean compile
  ```
- [ ] **전체 테스트 실행**
  ```bash
  mvn test
  ```
- [ ] **기능 동작 확인**

#### 6.4 문서화 및 커밋

- [ ] **패키지 구조 문서 업데이트**
- [ ] **README.md 업데이트**
- [ ] **최종 커밋**
  ```bash
  git add .
  git commit -m "패키지 구조 개선 완료 - 기능별 패키지 분리"
  ```

---

## 📊 성공 기준

### ✅ 최소 성공 기준 (반드시 달성)

- [ ] 기존 기능 100% 동작
- [ ] 컴파일 에러 없음
- [ ] 최소 5개 패키지 분리 완료 (utils, config, dto, command, 핵심 로직 1개)
- [ ] 순환 참조 해결

### 🎯 이상적 성공 기준 (목표)

- [ ] 8개 패키지 모두 분리 완료
- [ ] package-info.java 작성 완료
- [ ] 의존성 관계 명확화
- [ ] 테스트 코드도 패키지별 분리

---

## 🔧 패키지별 책임 정의

### server 패키지

- **책임**: 서버 시작/종료, 클라이언트 연결 수락
- **주요 클래스**: ChatServer, ServerManager

### user 패키지

- **책임**: 사용자 생명주기 관리, 연결 상태 관리
- **주요 클래스**: User, UserConnectionManager, UserService

### chat 패키지

- **책임**: 채팅방 관리, 메시지 처리
- **주요 클래스**: ChatRoom, MessageHandler, ChatRoomManager

### webrtc 패키지

- **책임**: WebRTC 시그널링, 영상방 관리
- **주요 클래스**: VideoRoom, WebRTCSignalingHandler, SignalingService

### database 패키지

- **책임**: 데이터베이스 연결, 쿼리 실행
- **주요 클래스**: DBHelper, ConnectionPool, Repository 클래스들

### command 패키지

- **책임**: 클라이언트 요청 처리, 명령 실행
- **하위 패키지**: chat, webrtc, user

### dto 패키지

- **책임**: 데이터 전송 객체 정의
- **하위 패키지**: chat, webrtc, user

### config 패키지

- **책임**: 설정 관리, 환경 변수 처리
- **주요 클래스**: PropertiesManager, DatabaseConfig, ServerConfig

### utils 패키지

- **책임**: 공통 유틸리티 기능
- **주요 클래스**: TimeUtils, LogUtils, ValidationUtils

---

## 🚨 주의사항 및 리스크 관리

### 주의사항

1. **점진적 이동**: 한 번에 모든 클래스를 이동하지 말고 단계별로 진행
2. **테스트 우선**: 각 단계마다 반드시 컴파일 및 테스트 확인
3. **의존성 주의**: 순환 참조가 발생하지 않도록 주의
4. **백업 필수**: 각 Phase 완료 후 Git 커밋

### 리스크 대응

- **컴파일 에러**: 즉시 이전 커밋으로 롤백
- **테스트 실패**: 해당 이동만 원복 후 다음 단계 진행
- **시간 부족**: 우선순위에 따라 핵심 패키지만 분리

### 우선순위 (시간 부족 시)

1. **1순위**: utils, config (독립적)
2. **2순위**: dto (의존성 적음)
3. **3순위**: command (구조화 효과 큼)
4. **4순위**: 핵심 비즈니스 로직 (user, chat, webrtc)

---

**🎯 목표**: 논리적이고 확장 가능한 패키지 구조 구축  
**🔑 핵심**: 기능별 명확한 책임 분리 + 의존성 최소화  
**🛠️ 방법**: 점진적 이동 + 테스트 기반 안전한 리팩토링

> ⚡ **완벽한 구조보다는 실용적인 개선**을 목표로, 1일 내에 실질적인 패키지 구조 개선을 달성합니다!
