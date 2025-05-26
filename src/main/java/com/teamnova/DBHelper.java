package com.teamnova;

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

import com.teamnova.command.Action;
import com.teamnova.command.BaseCommand;
import com.teamnova.command.CreateRoomCommand;
import com.teamnova.command.CreateRoomCommand.RoomType;
import com.teamnova.command.ResponseCommand;
import com.teamnova.command.ResponseCommand.TransmissionStatus;
import com.teamnova.command.SendMessageCommand;
import com.teamnova.config.PropertiesManager;
import com.teamnova.dto.chat.Message;
import com.teamnova.dto.chat.MessageStatus;
import com.teamnova.dto.chat.RoomData;
import com.teamnova.dto.user.UserData;
import com.teamnova.utils.TimeUtils;

/**
 * db 접속 및 사용을 편리하게하는 메소드
 */
public class DBHelper {

    private static Logger log = LogManager.getLogger(DBHelper.class.getName());

    private static DBHelper instance = null;

    private Connection conn;
    private Statement statement;
    private ResultSet resultSet;

    private String imgHost; // 이미지 호스트 경로

    public static DBHelper getInstance() {
        if (instance == null) {
            instance = new DBHelper();
        }

        return instance;
    }

    private DBHelper() {
        this.imgHost = PropertiesManager.getProperty("IMG_HOST");
        connect();
    }

    // db 연결
    private void connect() {
        try {
            // 드라이버 로딩
            Class.forName("com.mysql.cj.jdbc.Driver");

            // db 접속 정보 가져오기
            String dbUrl = PropertiesManager.getProperty("DB_URL");
            String dbUser = PropertiesManager.getProperty("DB_USER");
            String dbPw = PropertiesManager.getProperty("DB_PW");

            // connection 객체 얻기
            conn = DriverManager.getConnection(dbUrl, dbUser, dbPw);
            log.debug("db 커넥션 객체 획득");

            // statement 객체 얻기
            statement = conn.createStatement();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // db에 저장된 방목록, 방에 소속돈 멤버 정보를 map 형태로 반환
    public Map<Long, ChatRoom> getServerData() {
        Map<Long, ChatRoom> resultMap = new HashMap<>();
        List<Long> roomIds = new ArrayList<>();

        // 유저 id, nickname, 프사, 소속 방 번호 순으로 출력됨
        String query = "select users.id as user_id, chat_room_id from users, user_chatroom_map where users.id = user_chatroom_map.user_id";

        try {
            PreparedStatement psmt = conn.prepareStatement(query);
            ResultSet rs = psmt.executeQuery();

            while (rs.next()) {

                long roomId = rs.getLong("chat_room_id");

                if (!resultMap.containsKey(roomId)) {
                    // 처음 보는 방번호인 경우 key 생성
                    ChatRoom room = new ChatRoom();
                    room.id = roomId;
                    resultMap.put(roomId, room);
                }

                // user 객체 생성
                long userId = rs.getLong("user_id");
                User user = new User(userId);

                // 매칭되는 방에 유저 추가
                resultMap.get(roomId).userList.add(user);
            }
        } catch (SQLException e) {
            e.printStackTrace();
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
        log.debug("insertRoom(): START");

        String sql = "";
        if (roomType == RoomType.NORMAL) {
            sql = "insert into chat_rooms() values()";
        } else if (roomType == RoomType.OPEN) {
            sql = "insert into chat_rooms(room_name, description, room_type, master_user_id) values('"
                    + roomName + "', '"
                    + description + "', '"
                    + roomType.toString() + "', '"
                    + masterUserId
                    + "')";
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
                // String exitedAt = rs.getString("exited_at");

                log.debug("roomName={}, description={}, roomType={}, masterId={}",
                        roomName, description, roomType, masterId);
                ret = new RoomData(roomId, roomName, description, roomType, masterId);
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
                if (!profileImage.contains("http")) {
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
        log.info("exitHost(): START - params : roomId = {}" + roomId);

        // exited_at이 null인 가장 최근 레코드의 퇴장시간 업데이트
        String query = "UPDATE chat_rooms " +
                "SET master_user_id = 0 " +
                "WHERE id = ? ;";

        try (PreparedStatement psmt = conn.prepareStatement(query)) {
            psmt.setLong(1, roomId);

            log.debug("query = {}", psmt);

            psmt.executeUpdate();
        } catch (SQLException e) {
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

    // 메시지를 db에 저장
    public long insertMessage(SendMessageCommand command) {
        log.debug("insertMessage: START - params: command={}", command);

        String q = "insert into messages (chat_room_id, sender_id, content, type, sended_at) VALUES (?, ? ,?, ?, ?)";
        long ret = -1;

        try (PreparedStatement pstmt = conn.prepareStatement(q,
                PreparedStatement.RETURN_GENERATED_KEYS)) {

            // 값 설정
            pstmt.setLong(1, command.roomId);
            pstmt.setLong(2, command.requesterId);
            pstmt.setString(3, command.content);
            pstmt.setString(4, command.type.toString());
            pstmt.setString(5, command.createdAT);

            log.debug("query = {}", pstmt);

            // SQL 실행
            int insertedRows = pstmt.executeUpdate();

            if (insertedRows > 0) {
                // 삽입된 행의 ID 가져오기
                try (ResultSet generatedKeys = pstmt.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        long generatedId = generatedKeys.getLong(1);
                        log.debug("삽입된 행의 ID: " + generatedId);

                        // 디버깅 로그
                        log.debug("Message가 DB에 추가되었습니다. 삽입된 행의 ID: " + generatedId);
                        ret = generatedId;
                    } else {
                        log.debug("반환된 키 값 없음");
                        throw new SQLException("반환된 키 값 없음");
                    }
                }
            } else {
                log.debug("행이 삽입되지 않음");
                throw new SQLException("행이 삽입되지 않음");
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }

        log.debug("insertMessge: END");
        return ret;
    }

    // 메시지 읽음 상태 테이블 insert
    public void insertMessageReadStatus(SendMessageCommand command) {
        log.debug("insertMessageReadStatus: START - parms: command");

        String q = "INSERT INTO message_status (message_id, recipient_id, status, date_time) VALUES (?, ?, ?, ?)";

        try (PreparedStatement pstmt = conn.prepareStatement(q)) {
            // 배치처리로 한꺼번에 처리
            conn.setAutoCommit(false);

            // 멤버목록 불러오기
            List<UserData> memberList = getMemberData(command.roomId);

            for (UserData recipient : memberList) {
                pstmt.setLong(1, command.messageId);
                pstmt.setLong(2, recipient.id);
                pstmt.setString(3, SendMessageCommand.ReadStatus.UNREAD.toString());
                pstmt.setString(4, command.createdAT);
                pstmt.addBatch();
            }

            // 모든 배치 실행
            int[] affectedRows = pstmt.executeBatch();

            // 커밋
            conn.commit();
            log.debug("{} 개의 행이 삽입됨", affectedRows.length);

            conn.setAutoCommit(true);

        } catch (SQLException e) {
            e.printStackTrace();
            // 롤백
            try {
                if (conn != null) {
                    conn.rollback();
                }
            } catch (SQLException rollbackEx) {
                rollbackEx.printStackTrace();
            }
        }

        log.debug("insertMessagerReadStatus: END");
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
                if (!profileImage.contains("http")) {
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
