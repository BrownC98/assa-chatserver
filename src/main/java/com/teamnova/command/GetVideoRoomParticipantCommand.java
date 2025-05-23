package com.teamnova.command;

import java.util.List;

import com.teamnova.UserData;

/**
 * 영상통화 방 참가 요청 커맨드
 */
public class GetVideoRoomParticipantCommand extends ResponseCommand {

    public String videoRoomId;
    public String profileImage;
    public String nickname;
    public List<UserData> userList;   // 전체 멤버리스트(본인포함)

    public GetVideoRoomParticipantCommand(String videoRoomId) {
        super(Action.GET_VIDEO_ROOM_PARTICIPANT, 0L);
        this.videoRoomId = videoRoomId;
    }

    public static GetVideoRoomParticipantCommand fromJson(String json) throws Exception {
        return fromJson(json, GetVideoRoomParticipantCommand.class);
    }
}