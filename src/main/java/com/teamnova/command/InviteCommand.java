package com.teamnova.command;

import java.util.List;

import com.teamnova.dto.user.UserData;

// 요청시 필요한 데이터 - 방 id,  요청자 id, 피초대자 id 들 
// 응답시 필요한 데이터 - roomInfo, 메시지 id, 요청자 기본정보, 피초대자 기본정보
// 멤버리스트에 요청자, 피초대자 모두 들어간다.
// 기본정보 = id, nickname, profileImage
public class InviteCommand extends RoomInfoCommand {

    public Long messageId; // 문구를 출력하기 위한 id
    public List<Long> invitedIdList; // 초대받은 사람 id 리스트
    public boolean isNewOpenChatMember = false; // 외부 인원이 검색을 통해 오픈채팅에 입장할 때만 true

    public InviteCommand(Long recipientId, Long roomId, List<UserData> memberList, List<Long> invitedIdList,
            Long messageId) {
        super(Action.INVITE, recipientId, roomId, memberList);
        this.messageId = messageId;
        this.invitedIdList = invitedIdList;
    }

    public static InviteCommand fromJson(String json) throws Exception {
        return fromJson(json, InviteCommand.class);
    }

}
