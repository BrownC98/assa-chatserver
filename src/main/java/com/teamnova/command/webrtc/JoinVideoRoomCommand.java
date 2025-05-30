package com.teamnova.command.webrtc;

import java.util.List;

import com.teamnova.command.Action;
import com.teamnova.command.ResponseCommand;
import com.teamnova.dto.user.UserData;

/**
 * 영상통화 방 참가 요청 커맨드
 */
public class JoinVideoRoomCommand extends ResponseCommand {

    public String videoRoomId;
    public String profileImage;
    public String nickname;
    public List<UserData> userList; // 전체 멤버리스트(본인포함)
    public boolean videoEnabled;
    public boolean audioEnabled;

    public JoinVideoRoomCommand(String videoRoomId) {
        super(Action.JOIN_VIDEO_ROOM, 0L);
        this.videoRoomId = videoRoomId;
    }

    public static JoinVideoRoomCommand fromJson(String json) throws Exception {
        return fromJson(json, JoinVideoRoomCommand.class);
    }
}