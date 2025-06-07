package com.teamnova.database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.teamnova.chat.ChatRoom;
import com.teamnova.command.Action;
import com.teamnova.command.BaseCommand;
import com.teamnova.command.ResponseCommand;
import com.teamnova.command.ResponseCommand.TransmissionStatus;
import com.teamnova.command.chat.CreateRoomCommand;
import com.teamnova.command.chat.CreateRoomCommand.RoomType;
import com.teamnova.command.chat.SendMessageCommand;
import com.teamnova.config.PropertiesManager;
import com.teamnova.dto.chat.Message;
import com.teamnova.dto.chat.MessageStatus;
import com.teamnova.dto.chat.RoomData;
import com.teamnova.dto.user.UserData;
import com.teamnova.user.User;
import com.teamnova.utils.LoggingUtils;
import com.teamnova.utils.PerformanceLogger;
import com.teamnova.utils.TimeUtils;

/**
 * db 접속 및 사용을 편리하게하는 메소드
 */
public class DBHelper {

    private static final Logger log = LogManager.getLogger(DBHelper.class);

    private static DBHelper instance = null;

    private Connection conn;
    private Statement statement;
    private ResultSet resultSet;

    private String imgHost; // 이미지 호스트 경로

    // 성능 임계값 (밀리초)
    private static final long SLOW_QUERY_THRESHOLD_MS = 100;
    private static final long VERY_SLOW_QUERY_THRESHOLD_MS = 1000;

    // 연결 상태 추적
    private long connectionStartTime;
    private int queryCount = 0;
    private long totalQueryTime = 0;

    public static DBHelper getInstance() {
        if (instance == null) {
            synchronized (DBHelper.class) {
                if (instance == null) {
                    log.debug("DBHelper 싱글톤 인스턴스 생성 시작");
                    instance = new DBHelper();
                    log.info("DBHelper 싱글톤 인스턴스 생성 완료");
                }
            }
        }
        return instance;
    }

    private DBHelper() {
        String operationId = LoggingUtils.generateOperationId();

        log.debug("DBHelper 초기화 시작: operationId={}", operationId);

        try {
            this.imgHost = PropertiesManager.getProperty("IMG_HOST");

            // imgHost가 null인 경우 기본값 설정
            if (this.imgHost == null || this.imgHost.trim().isEmpty()) {
                this.imgHost = ""; // 빈 문자열로 설정하여 null 연결 방지
                log.warn("IMG_HOST 설정이 없습니다. 빈 문자열로 설정됩니다: operationId={}", operationId);
            }

            log.debug("이미지 호스트 설정 완료: operationId={}, imgHost={}", operationId, imgHost);

            connect(operationId);

            log.info("DBHelper 초기화 완료: operationId={}, imgHost={}", operationId, imgHost);
        } catch (Exception e) {
            log.fatal("DBHelper 초기화 실패: operationId={}, error={}", operationId, e.getMessage(), e);
            throw new RuntimeException("데이터베이스 초기화 실패", e);
        }
    }

    // db 연결
    private void connect(String operationId) {
        PerformanceLogger.Timer timer = PerformanceLogger.startTimer("DB_CONNECT",
                String.format("operationId=%s", operationId));

        log.debug("데이터베이스 연결 시작: operationId={}", operationId);

        try {
            // 드라이버 로딩
            Class.forName("com.mysql.cj.jdbc.Driver");
            log.debug("MySQL 드라이버 로딩 완료: operationId={}", operationId);

            // DB 접속 정보 가져오기
            String dbUrl = PropertiesManager.getProperty("DB_URL");
            String dbUser = PropertiesManager.getProperty("DB_USER");
            String dbPw = PropertiesManager.getProperty("DB_PW");

            // 민감정보 마스킹하여 로깅
            String maskedUrl = LoggingUtils.maskToken(dbUrl);
            String maskedUser = LoggingUtils.sanitizeUserName(dbUser);

            log.debug("데이터베이스 연결 정보: operationId={}, url={}, user={}",
                    operationId, maskedUrl, maskedUser);

            // Connection 객체 얻기
            connectionStartTime = System.currentTimeMillis();
            conn = DriverManager.getConnection(dbUrl, dbUser, dbPw);

            // Statement 객체 얻기
            statement = conn.createStatement();

            long connectionTime = timer.stop();

            log.info("데이터베이스 연결 성공: operationId={}, connectionTime={}ms, url={}",
                    operationId, connectionTime, maskedUrl);

            // 연결 상태 확인
            if (conn.isValid(5)) {
                log.debug("데이터베이스 연결 유효성 확인 완료: operationId={}", operationId);
            } else {
                log.warn("데이터베이스 연결 유효성 확인 실패: operationId={}", operationId);
            }

        } catch (ClassNotFoundException e) {
            timer.stop("ERROR: Driver not found");
            log.error("MySQL 드라이버를 찾을 수 없음: operationId={}, error={}", operationId, e.getMessage(), e);
            throw new RuntimeException("MySQL 드라이버 로딩 실패", e);
        } catch (SQLException e) {
            timer.stop("ERROR: Connection failed");
            log.error("데이터베이스 연결 실패: operationId={}, sqlState={}, errorCode={}, error={}",
                    operationId, e.getSQLState(), e.getErrorCode(), e.getMessage(), e);
            throw new RuntimeException("데이터베이스 연결 실패", e);
        } catch (Exception e) {
            timer.stop("ERROR: " + e.getMessage());
            log.error("데이터베이스 연결 중 예상치 못한 오류: operationId={}, error={}",
                    operationId, e.getMessage(), e);
            throw new RuntimeException("데이터베이스 연결 중 오류", e);
        }
    }

    /**
     * 데이터베이스에 저장된 방목록과 방에 소속된 멤버 정보를 Map 형태로 반환
     */
    public Map<Long, ChatRoom> getServerData() {
        String operationId = LoggingUtils.generateOperationId();
        PerformanceLogger.Timer timer = PerformanceLogger.startTimer("GET_SERVER_DATA",
                String.format("operationId=%s", operationId));

        log.debug("서버 데이터 로드 시작: operationId={}", operationId);

        Map<Long, ChatRoom> resultMap = new HashMap<>();
        String query = "select users.id as user_id, chat_room_id from users, user_chatroom_map where users.id = user_chatroom_map.user_id";

        int roomCount = 0;
        int userCount = 0;

        try {
            PreparedStatement psmt = conn.prepareStatement(query);
            log.debug("서버 데이터 쿼리 실행: operationId={}", operationId);

            ResultSet rs = psmt.executeQuery();

            while (rs.next()) {
                long roomId = rs.getLong("chat_room_id");
                long userId = rs.getLong("user_id");

                if (!resultMap.containsKey(roomId)) {
                    // 처음 보는 방번호인 경우 key 생성
                    ChatRoom room = new ChatRoom();
                    room.id = roomId;
                    resultMap.put(roomId, room);
                    roomCount++;

                    log.trace("새 채팅방 발견: operationId={}, roomId={}, totalRooms={}",
                            operationId, roomId, roomCount);
                }

                // user 객체 생성
                User user = new User(userId);
                resultMap.get(roomId).userList.add(user);
                userCount++;

                log.trace("사용자 추가: operationId={}, roomId={}, userId={}, roomUserCount={}",
                        operationId, roomId, userId, resultMap.get(roomId).userList.size());
            }

            long duration = timer.stop();
            log.info("서버 데이터 로드 완료: operationId={}, roomCount={}, userCount={}, duration={}ms",
                    operationId, roomCount, userCount, duration);

            // 성능 임계값 체크
            if (duration > SLOW_QUERY_THRESHOLD_MS) {
                log.warn("서버 데이터 로드 성능 경고: operationId={}, duration={}ms, threshold={}ms",
                        operationId, duration, SLOW_QUERY_THRESHOLD_MS);
            }

        } catch (SQLException e) {
            timer.stop("ERROR: " + e.getSQLState());
            log.error("서버 데이터 로드 실패: operationId={}, sqlState={}, errorCode={}, error={}",
                    operationId, e.getSQLState(), e.getErrorCode(), e.getMessage(), e);
            throw new RuntimeException("서버 데이터 로드 실패", e);
        }

        return resultMap;
    }

    // 유저 - 테이블 관계 insert
    public void insertUserChatRoomsRelation(long roomId, long userId) {
        log.debug("insertUserChatRoomsRelation(): START - params: roomId={}, userId={}", roomId, userId);

        String sql = "insert into user_chatroom_map(chat_room_id, user_id, entered_at) value(?, ?, ?)";
        PreparedStatement ps;

        try {
            ps = conn.prepareStatement(sql);
            ps.setLong(1, roomId);
            ps.setLong(2, userId);
            ps.setString(3, TimeUtils.getCurrentTimeInUTC());

            log.debug("생성된 쿼리={}", ps.toString());

            int row = ps.executeUpdate();

            log.debug("변경된 row : {}", row);
        } catch (SQLException e) {
            e.printStackTrace();
        }

        log.debug("insertUserChatRoomsRelation(): END");
    }

    // 주어진 id user가 속한 채팅방 id들을 반환
    public List<Long> getEnteredRoomIds(Long id) {
        List<Long> ret = new ArrayList<>();

        String query = "select * from user_chatroom_map where user_id = ?";

        try {
            // preparedStatement 세팅
            PreparedStatement psmt = conn.prepareStatement(query);
            psmt.setLong(1, id);
            ResultSet rs = psmt.executeQuery();
            while (rs.next()) {
                Long roomId = rs.getLong("chat_room_id");
                ret.add(roomId);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return ret;
    }

    // 채팅방 정보 추가
    public Long insertRoom(
            String roomName,
            String description,
            CreateRoomCommand.RoomType roomType,
            Long masterUserId) throws SQLException {
        return insertRoom(roomName, description, roomType, masterUserId, null, null);
    }

    // 🆕 이미지 URL들을 포함한 오버로드된 insertRoom 메서드
    public Long insertRoom(
            String roomName,
            String description,
            CreateRoomCommand.RoomType roomType,
            Long masterUserId,
            String thumbnail,
            String coverImageUrl) throws SQLException {
        log.debug("insertRoom(): START - roomName={}, roomType={}, thumbnail={}, coverImageUrl={}", 
                roomName, roomType, thumbnail, coverImageUrl);

        String sql = "";
        if (roomType == RoomType.NORMAL) {
            sql = "insert into chat_rooms() values()";
        } else if (roomType == RoomType.OPEN) {
            // 동적으로 SQL 구성
            StringBuilder sqlBuilder = new StringBuilder("insert into chat_rooms(room_name, description, room_type, master_user_id");
            StringBuilder valuesBuilder = new StringBuilder("values('" + roomName + "', '" + description + "', '" + roomType.toString() + "', '" + masterUserId + "'");
            
            if (thumbnail != null && !thumbnail.isEmpty()) {
                sqlBuilder.append(", thumbnail");
                valuesBuilder.append(", '").append(thumbnail).append("'");
            }
            
            if (coverImageUrl != null && !coverImageUrl.isEmpty()) {
                sqlBuilder.append(", cover_image");
                valuesBuilder.append(", '").append(coverImageUrl).append("'");
            }
            
            sqlBuilder.append(") ");
            valuesBuilder.append(")");
            sql = sqlBuilder.toString() + valuesBuilder.toString();
        }

        Long insertedId = null;
        log.debug("sql={}", sql);

        // 마지막으로 추가한 데이터의 id 얻어오기
        PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
        ps.execute();
        ResultSet rs = ps.getGeneratedKeys();
        if (rs.next()) {
            insertedId = rs.getLong(1);
            log.debug("새로 추가된 방 id = {}", insertedId);
        }

        log.debug("insertRoom(): END - return={}", insertedId);
        return insertedId;
    }

    public RoomData getRoomData(Long roomId) {
        log.debug("RoomData() - START - params : roomId = {}", roomId);
        RoomData ret = null;

        String sql = "SELECT * from chat_rooms WHERE id = ?";

        try {
            PreparedStatement psmt = conn.prepareStatement(sql);
            psmt.setLong(1, roomId);

            log.debug("query = {}", psmt);

            ResultSet rs = psmt.executeQuery();

            while (rs.next()) {

                // user 객체 생성
                // long roomId-ret = rs.getLong("id");
                String roomName = rs.getString("room_name");
                String description = rs.getString("description");
                String roomType = rs.getString("room_type");
                Long masterId = rs.getLong("master_user_id");
                String thumbnail = rs.getString("thumbnail"); // 🆕 썸네일 이미지 조회
                String coverImage = rs.getString("cover_image"); // 🆕 커버 이미지 조회
                Integer currentMembers = rs.getInt("current_members"); // 🆕 현재 멤버 수 조회
                // String exitedAt = rs.getString("exited_at");

                log.debug("roomName={}, description={}, roomType={}, masterId={}, thumbnail={}, coverImage={}, currentMembers={}",
                        roomName, description, roomType, masterId, thumbnail, coverImage, currentMembers);
                ret = new RoomData(roomId, roomName, description, roomType, masterId, thumbnail, coverImage, currentMembers);
                break;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        log.debug("ret.roomName={}, ret.description={}, ret.roomType={}, ret.masterId={}",
                ret.roomName, ret.description, ret.roomType, ret.masterUserId);
        log.debug("RoomData() - END - return = {}", ret);
        return ret;
    }

    public void updateRoomName(Long roomId, String roomName) {
        log.info("updateRoomName() - START, params : roomId = {}, roomName = {}", roomId, roomName);

        // 이름 업데이트
        String query = "UPDATE chat_rooms " +
                "SET room_name = ? " +
                "WHERE id = ? ;";

        try (PreparedStatement psmt = conn.prepareStatement(query)) {
            psmt.setString(1, roomName);
            psmt.setLong(2, roomId);

            log.debug("query = {}", psmt);

            psmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }

        log.info("updateRoomName() : END");
    }

    public void updateRoomDescription(Long roomId, String description) {
        log.info("updateRoomDescription() - START, params : roomId = {}, description = {}", roomId, description);

        // 이름 업데이트
        String query = "UPDATE chat_rooms " +
                "SET description = ? " +
                "WHERE id = ? ;";

        try (PreparedStatement psmt = conn.prepareStatement(query)) {
            psmt.setString(1, description);
            psmt.setLong(2, roomId);

            log.debug("query = {}", psmt);

            psmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }

        log.info("updateRoomDescription() : END");
    }

    // 🆕 채팅방 썸네일 이미지 업데이트 메서드 추가
    public void updateRoomThumbnail(Long roomId, String thumbnail) {
        log.info("updateRoomThumbnail() - START, params : roomId = {}, thumbnail = {}", roomId, thumbnail);

        String query = "UPDATE chat_rooms " +
                "SET thumbnail = ? " +
                "WHERE id = ? ;";

        try (PreparedStatement psmt = conn.prepareStatement(query)) {
            psmt.setString(1, thumbnail);
            psmt.setLong(2, roomId);

            log.debug("query = {}", psmt);

            psmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }

        log.info("updateRoomThumbnail() : END");
    }

    // 🆕 채팅방 커버 이미지 업데이트 메서드 추가
    public void updateRoomCoverImage(Long roomId, String coverImage) {
        log.info("updateRoomCoverImage() - START, params : roomId = {}, coverImage = {}", roomId, coverImage);

        String query = "UPDATE chat_rooms " +
                "SET cover_image = ? " +
                "WHERE id = ? ;";

        try (PreparedStatement psmt = conn.prepareStatement(query)) {
            psmt.setString(1, coverImage);
            psmt.setLong(2, roomId);

            log.debug("query = {}", psmt);

            psmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }

        log.info("updateRoomCoverImage() : END");
    }

    // 🆕 채팅방 현재 멤버 수 업데이트 메서드 추가
    public void updateRoomCurrentMembers(Long roomId, Integer currentMembers) {
        log.info("updateRoomCurrentMembers() - START, params : roomId = {}, currentMembers = {}", roomId, currentMembers);

        String query = "UPDATE chat_rooms " +
                "SET current_members = ? " +
                "WHERE id = ? ;";

        try (PreparedStatement psmt = conn.prepareStatement(query)) {
            psmt.setInt(1, currentMembers);
            psmt.setLong(2, roomId);

            log.debug("query = {}", psmt);

            psmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }

        log.info("updateRoomCurrentMembers() : END");
    }

    // 채팅방에 소속된 유저의 id, nickname, profileimage 얻어오기
    // 들어왔다 나간사람도 여기에 포함된다.
    public List<UserData> getMemberData(long roomId) {
        log.debug("getMemberData(long roomId):START - roomId = {}", roomId);
        List<UserData> result = new ArrayList<>();

        // 유저 id, nickname, 프사, 방 입장, 퇴장시간
        String query = "SELECT u.id AS user_id, u.nickname , u.profile_image, ucm.chat_room_id, ucm.entered_at, ucm.exited_at "
                +
                "FROM user_chatroom_map AS ucm " +
                "JOIN users AS u ON ucm.user_id = u.id " +
                "WHERE ucm.entered_at = ( " +
                "    SELECT MAX(entered_at) " +
                "    FROM user_chatroom_map " +
                "    WHERE chat_room_id = ucm.chat_room_id AND user_id = ucm.user_id " +
                ") " +
                "AND ucm.chat_room_id = ? " +
                "ORDER BY ucm.user_id;";

        try {
            PreparedStatement psmt = conn.prepareStatement(query);
            psmt.setLong(1, roomId);

            log.debug("query = {}", psmt);

            ResultSet rs = psmt.executeQuery();

            while (rs.next()) {

                // user 객체 생성
                long userId = rs.getLong("user_id");
                String nickname = rs.getString("nickname");
                String profileImage = rs.getString("profile_image");
                String enteredAt = rs.getString("entered_at");
                String exitedAt = rs.getString("exited_at");

                // 서버 내부 경로 반환시 호스트 경로 추가
                if (profileImage != null && !profileImage.contains("http")) {
                    profileImage = imgHost + profileImage;
                }

                UserData userData = new UserData(userId, nickname, profileImage);

                // 사용자 퇴장 여부 기록
                boolean isExit = true;
                if (exitedAt == null || exitedAt.isEmpty()) {
                    isExit = false;
                }
                userData.isExit = isExit;

                result.add(userData);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        log.debug(" getMemberData(long roomId):END - return = {}", result);
        return result;
    }

    // 채팅방 나가기
    public void exitRoom(long roomId, long userId) {
        log.debug("exitRoom: START - params: roomId={}, userId={}", roomId, userId);

        // exited_at이 null인 가장 최근 레코드의 퇴장시간 업데이트
        String query = "UPDATE user_chatroom_map " +
                "SET exited_at = ? " +
                "WHERE user_id = ? AND exited_at IS NULL AND chat_room_id = ? " +
                "ORDER BY entered_at DESC LIMIT 1";

        try (PreparedStatement psmt = conn.prepareStatement(query)) {
            psmt.setString(1, TimeUtils.getCurrentTimeInUTC());
            psmt.setLong(2, userId);
            psmt.setLong(3, roomId);

            log.debug("query = {}", psmt);

            psmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }

        log.debug("exitRoom: END");
    }

    // 방장 나감 처리
    public void exitHost(Long roomId) {
        log.info("exitHost(): START - params : roomId = {}", roomId);

        // 🆕 방장이 나갈 때 master_user_id를 NULL로 설정 (외래키 제약조건 위반 방지)
        String query = "UPDATE chat_rooms " +
                "SET master_user_id = NULL " +
                "WHERE id = ? ;";

        try (PreparedStatement psmt = conn.prepareStatement(query)) {
            psmt.setLong(1, roomId);

            log.debug("query = {}", psmt);

            psmt.executeUpdate();
            
            log.info("방장 퇴장 처리 완료: roomId={}, master_user_id를 NULL로 설정", roomId);
        } catch (SQLException e) {
            log.error("방장 퇴장 처리 실패: roomId={}, error={}", roomId, e.getMessage(), e);
            e.printStackTrace();
        }

        log.info("exitHost(): END");
    }

    // 채팅방 제거
    public void deleteRoom(long roomId) {
        log.debug("deleteRoom - params: roomI={}", roomId);
        String query = "delete from chat_rooms where id = ?";

        try {
            PreparedStatement psmt = conn.prepareStatement(query);
            psmt.setLong(1, roomId);
            psmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        log.debug("deleteRoom: END");
    }

    // 특정방의 메시지가 작성되었는지 여부를 반환한다.
    public boolean isChatMessageExist(long roomId) {
        log.debug("isChatMessageExist: START - params: roomId={}", roomId);
        boolean result = false;

        String q = "select * from messages where chat_room_id = ? AND sender_id != 0"; // 서버 메시지 개수는 무시

        try {
            PreparedStatement pstmt = conn.prepareStatement(q);
            pstmt.setLong(1, roomId);

            log.debug("query = {}", pstmt);

            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                long count = rs.getLong(1);
                log.debug("roomId={} 의 메시지 개수 : {}", roomId, count);

                // 해당 방의 메시지가 1개 이상 있으면 true 반환
                if (count > 0)
                    result = true;
                else
                    result = false;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        log.debug("isChatMessageExist: END - return: result={}", result);
        return result;
    }

    // 특정방의 채팅 메시지를 가져온다.
    public List<Message> getMessages(long roomId) {
        log.debug("getMessages: START - params: roomId={}", roomId);

        List<Message> ret = new ArrayList<>();

        String q = "select * from messages where chat_room_id = ?";

        // try {
        // PreparedStatement psmt = conn.prepareStatement(q);
        // psmt.setLong(1, roomId);
        // ResultSet rs = psmt.executeQuery();

        // while (rs.next()) {
        // long id = rs.getLong("id");
        // long chatRoomId = rs.getLong("chat_room_id");
        // long senderId = rs.getLong("sender_id");
        // String content = rs.getString("content");
        // String type = rs.getString("type");
        // String sendedAt = rs.getString("sended_at");

        // Message m = new Message(id, chatRoomId, senderId, content, type, sendedAt);
        // ret.add(m);
        // }
        // } catch (Exception e) {
        // e.printStackTrace();
        // }

        log.debug("getMessages: END");
        return ret;
    }

    // 응답 커멘드를 db에 저장
    // 저장된 커맨드의 id 반환
    public Long insertResponseCommand(ResponseCommand command) {
        log.debug("insertCommand: START - params: command={}", command);
        Long lastInsertedId = 0L;

        // 커맨드가 서버에 도착한 시간 기록
        command.createdAT = TimeUtils.getCurrentTimeInUTC();

        String q = "insert into response_commands (action, recipient_id, json, status) values (?,?,?,?)";

        try (PreparedStatement pstmt = conn.prepareStatement(q, PreparedStatement.RETURN_GENERATED_KEYS)) {

            // 값 설정
            pstmt.setString(1, command.action.toString());
            pstmt.setLong(2, command.recipientId);
            pstmt.setString(3, command.toJson());
            pstmt.setString(4, command.transmissionStatus.toString());

            log.debug("query = {}", pstmt);

            int affectedRows = pstmt.executeUpdate();

            // 자동 생성된 키 가져오기
            if (affectedRows > 0) {
                try (ResultSet generatedKeys = pstmt.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        lastInsertedId = generatedKeys.getLong(1); // 첫 번째 열의 값이 자동 생성된 키
                    }
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        log.debug("insertCommand: END - return: lastInsertedId={}", lastInsertedId);
        return Long.valueOf(lastInsertedId);
    }

    /**
     * 메시지를 데이터베이스에 저장
     */
    public long insertMessage(SendMessageCommand command) {
        String operationId = LoggingUtils.generateOperationId();
        PerformanceLogger.Timer timer = PerformanceLogger.startTimer("INSERT_MESSAGE",
                String.format("operationId=%s,roomId=%d,senderId=%d", operationId, command.roomId,
                        command.requesterId));

        log.debug("메시지 저장 시작: operationId={}, roomId={}, senderId={}, messageType={}, contentLength={}",
                operationId, command.roomId, command.requesterId, command.type,
                command.content != null ? command.content.length() : 0);

        String query = "insert into messages (chat_room_id, sender_id, content, type, sended_at) VALUES (?, ?, ?, ?, ?)";
        long messageId = -1;

        try (PreparedStatement pstmt = conn.prepareStatement(query, PreparedStatement.RETURN_GENERATED_KEYS)) {
            // 값 설정
            pstmt.setLong(1, command.roomId);
            pstmt.setLong(2, command.requesterId);
            pstmt.setString(3, command.content);
            pstmt.setString(4, command.type.toString());
            pstmt.setString(5, command.createdAT);

            log.trace("메시지 저장 쿼리 준비 완료: operationId={}, roomId={}, senderId={}",
                    operationId, command.roomId, command.requesterId);

            // SQL 실행
            int insertedRows = pstmt.executeUpdate();

            if (insertedRows > 0) {
                // 삽입된 행의 ID 가져오기
                try (ResultSet generatedKeys = pstmt.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        messageId = generatedKeys.getLong(1);

                        long duration = timer.stop();
                        log.info("메시지 저장 완료: operationId={}, messageId={}, roomId={}, senderId={}, " +
                                "messageType={}, duration={}ms",
                                operationId, messageId, command.roomId, command.requesterId,
                                command.type, duration);

                        // 성능 임계값 체크
                        if (duration > SLOW_QUERY_THRESHOLD_MS) {
                            log.warn("메시지 저장 성능 경고: operationId={}, messageId={}, duration={}ms, threshold={}ms",
                                    operationId, messageId, duration, SLOW_QUERY_THRESHOLD_MS);
                        }
                    } else {
                        timer.stop("ERROR: No generated keys");
                        log.error("메시지 저장 실패 - 생성된 키 없음: operationId={}, roomId={}, senderId={}",
                                operationId, command.roomId, command.requesterId);
                        throw new SQLException("반환된 키 값 없음");
                    }
                }
            } else {
                timer.stop("ERROR: No rows inserted");
                log.error("메시지 저장 실패 - 삽입된 행 없음: operationId={}, roomId={}, senderId={}",
                        operationId, command.roomId, command.requesterId);
                throw new SQLException("행이 삽입되지 않음");
            }

        } catch (SQLException e) {
            timer.stop("ERROR: " + e.getSQLState());
            log.error("메시지 저장 중 데이터베이스 오류: operationId={}, roomId={}, senderId={}, " +
                    "sqlState={}, errorCode={}, error={}",
                    operationId, command.roomId, command.requesterId,
                    e.getSQLState(), e.getErrorCode(), e.getMessage(), e);
            throw new RuntimeException("메시지 저장 실패", e);
        }

        return messageId;
    }

    /**
     * 메시지 읽음 상태를 배치로 삽입 (트랜잭션 처리)
     */
    public void insertMessageReadStatus(SendMessageCommand command) {
        String operationId = LoggingUtils.generateOperationId();
        PerformanceLogger.Timer timer = PerformanceLogger.startTimer("INSERT_MESSAGE_READ_STATUS",
                String.format("operationId=%s,messageId=%d,roomId=%d", operationId, command.messageId, command.roomId));

        log.debug("메시지 읽음 상태 배치 삽입 시작: operationId={}, messageId={}, roomId={}",
                operationId, command.messageId, command.roomId);

        String query = "INSERT INTO message_status (message_id, recipient_id, status, date_time) VALUES (?, ?, ?, ?)";
        boolean originalAutoCommit = true;
        int batchSize = 0;

        try (PreparedStatement pstmt = conn.prepareStatement(query)) {
            // 트랜잭션 시작
            originalAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);

            log.debug("트랜잭션 시작: operationId={}, messageId={}, originalAutoCommit={}",
                    operationId, command.messageId, originalAutoCommit);

            // 멤버목록 불러오기
            List<UserData> memberList = getMemberData(command.roomId);
            log.debug("멤버 목록 조회 완료: operationId={}, messageId={}, memberCount={}",
                    operationId, command.messageId, memberList.size());

            // 배치 준비
            for (UserData recipient : memberList) {
                pstmt.setLong(1, command.messageId);
                pstmt.setLong(2, recipient.id);
                pstmt.setString(3, SendMessageCommand.ReadStatus.UNREAD.toString());
                pstmt.setString(4, command.createdAT);
                pstmt.addBatch();
                batchSize++;

                log.trace("배치 항목 추가: operationId={}, messageId={}, recipientId={}, batchSize={}",
                        operationId, command.messageId, recipient.id, batchSize);
            }

            // 배치 실행
            log.debug("배치 실행 시작: operationId={}, messageId={}, batchSize={}",
                    operationId, command.messageId, batchSize);

            int[] affectedRows = pstmt.executeBatch();

            // 커밋
            conn.commit();

            long duration = timer.stop();
            log.info("메시지 읽음 상태 배치 삽입 완료: operationId={}, messageId={}, roomId={}, " +
                    "batchSize={}, affectedRows={}, duration={}ms",
                    operationId, command.messageId, command.roomId, batchSize, affectedRows.length, duration);

            // 성능 임계값 체크
            if (duration > SLOW_QUERY_THRESHOLD_MS) {
                log.warn("메시지 읽음 상태 배치 삽입 성능 경고: operationId={}, messageId={}, " +
                        "batchSize={}, duration={}ms, threshold={}ms",
                        operationId, command.messageId, batchSize, duration, SLOW_QUERY_THRESHOLD_MS);
            }

        } catch (SQLException e) {
            timer.stop("ERROR: " + e.getSQLState());
            log.error("메시지 읽음 상태 배치 삽입 실패: operationId={}, messageId={}, roomId={}, " +
                    "batchSize={}, sqlState={}, errorCode={}, error={}",
                    operationId, command.messageId, command.roomId, batchSize,
                    e.getSQLState(), e.getErrorCode(), e.getMessage(), e);

            // 롤백 시도
            try {
                if (conn != null) {
                    conn.rollback();
                    log.info("트랜잭션 롤백 완료: operationId={}, messageId={}", operationId, command.messageId);
                }
            } catch (SQLException rollbackEx) {
                log.error("트랜잭션 롤백 실패: operationId={}, messageId={}, rollbackError={}",
                        operationId, command.messageId, rollbackEx.getMessage(), rollbackEx);
            }

            throw new RuntimeException("메시지 읽음 상태 배치 삽입 실패", e);
        } finally {
            // AutoCommit 원복
            try {
                conn.setAutoCommit(originalAutoCommit);
                log.debug("AutoCommit 원복 완료: operationId={}, messageId={}, autoCommit={}",
                        operationId, command.messageId, originalAutoCommit);
            } catch (SQLException e) {
                log.error("AutoCommit 원복 실패: operationId={}, messageId={}, error={}",
                        operationId, command.messageId, e.getMessage(), e);
            }
        }
    }

    public MessageStatus getMessageReadStatus(long msgId) {
        log.debug("isMessageRead: START - params: msgId = {}", msgId);

        String q = "SELECT * FROM message_read_status where id = ?";

        try (PreparedStatement pstmt = conn.prepareStatement(q)) {
            pstmt.setLong(1, msgId);
            ResultSet rs = pstmt.executeQuery();

            // while (rs.next()) {
            // Long id = rs.getLong("id");
            // Long messageId = rs.getLong("message_id");
            // Long userId = rs.getLong("user_id");
            // boolean isRead = rs.getBoolean("is_read");
            // String readAt = rs.getString("read_at");

            // MessageStatus ms = new MessageStatus(id, messageId, userId, isRead, readAt);
            // log.debug("isMessageRead: END - return = {}", ms);
            // return ms;
            // }
        } catch (Exception e) {
            e.printStackTrace();
        }

        log.debug("isMessageRead: END - return = null");
        return null;
    }

    // 커맨드 전송상태 업데이트
    public void updateResponseCommandStatus(Long commandId, TransmissionStatus status) {
        log.debug("updateResponseCommandStatus(): START - params: commandId = {}, status = {}", commandId,
                status.toString());

        String q = "UPDATE response_commands SET status = ? WHERE id = ?";

        try (PreparedStatement pstmt = conn.prepareStatement(q)) {
            pstmt.setString(1, status.toString());
            pstmt.setLong(2, commandId);

            log.debug("query = {}", pstmt);

            pstmt.executeUpdate();

        } catch (Exception e) {
            e.printStackTrace();
        }

        log.debug("updateResponseCommandStatus(): END");
    }

    // 주어진 유저에 대한 NOT_SENT 상태 커맨드 리스트 얻기
    public List<ResponseCommand> getNotSentCommands(Long userId) {
        log.debug("getNotSentCommands() : START - params: userId = {}", userId);
        List<ResponseCommand> ret = new ArrayList<>();

        String q = "SELECT * FROM response_commands where recipient_id = ? AND status = ?";

        try (PreparedStatement pstmt = conn.prepareStatement(q)) {
            pstmt.setLong(1, userId);
            pstmt.setString(2, TransmissionStatus.NOT_SENT.toString());

            log.debug("query = {}", pstmt.toString());

            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                Long id = rs.getLong("id");
                String action = rs.getString("action");
                Long recipientId = rs.getLong("recipient_id");
                String json = rs.getString("json");
                String status = rs.getString("status");

                // action, json으로 커맨드 객체 생성
                ResponseCommand rc = (ResponseCommand) BaseCommand.fromJson(Action.valueOf(action), json);
                rc.id = id;

                // 반환 리스트에 추가
                ret.add(rc);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        log.debug("생성된 ResponseCommand 개수 = {}", ret.size());
        log.debug("getNotSentCommands() : END - params: ret = {}", ret);
        return ret;
    }

    // 유저 정보 얻기
    public UserData getUserDataById(Long userId) {
        log.debug("getUserDataById: START - params: userId={}", userId);

        UserData ret = null;

        String q = "SELECT id, nickname, profile_image FROM users where id = ?";

        try {
            PreparedStatement psmt = conn.prepareStatement(q);
            psmt.setLong(1, userId);

            log.debug("query = {}", psmt);

            ResultSet rs = psmt.executeQuery();

            while (rs.next()) {

                // user 객체 생성
                long id = rs.getLong("id");
                String nickname = rs.getString("nickname");
                String profileImage = rs.getString("profile_image");

                // 서버 내부 경로 반환시 호스트 경로 추가
                if (profileImage != null && !profileImage.contains("http")) {
                    profileImage = imgHost + profileImage;
                }

                ret = new UserData(userId, nickname, profileImage);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        log.debug("getUserDataById: END - return: ret={}", userId);
        return ret;
    }
}
