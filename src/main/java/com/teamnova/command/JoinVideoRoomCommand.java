package com.teamnova.command;

import java.util.List;

import com.teamnova.dto.user.UserData;

/**
 * 영상통화 방 참가 요청 커맨드
 */
public class JoinVideoRoomCommand extends ResponseCommand {

    public Long roomId;
    public String videoRoomId;
    public String profileImage;
    public String nickname;
    public List<UserData> userList; // 전체 멤버리스트(본인포함)
    public boolean videoEnabled;
    public boolean audioEnabled;

    public JoinVideoRoomCommand(Long roomId, Long recipientId) {
        super(Action.JOIN_VIDEO_ROOM, recipientId);
        this.roomId = roomId;
    }

    public static JoinVideoRoomCommand fromJson(String json) throws Exception {
        return fromJson(json, JoinVideoRoomCommand.class);
    }
}
