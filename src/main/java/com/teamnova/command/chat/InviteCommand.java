package com.teamnova.command.chat;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;
import com.teamnova.command.Action;
import com.teamnova.dto.user.UserData;

// 요청시 필요한 데이터 - 방 id,  요청자 id, 피초대자 id 들 
// 응답시 필요한 데이터 - roomInfo, 메시지 id, 요청자 기본정보, 피초대자 기본정보
// 멤버리스트에 요청자, 피초대자 모두 들어간다.
// 기본정보 = id, nickname, profileImage
public class InviteCommand extends RoomInfoCommand {

    private static final String TAG = InviteCommand.class.getSimpleName();

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

    public SendMessageCommand getMessageCommand() {
        System.out.println(TAG + " getMessageCommand: START");

        SendMessageCommand c = null;
        // requesterId 가 제거된 리스트 (있는 경우)
        List<Long> notRequesterIds = new ArrayList<>(this.invitedIdList);
        notRequesterIds.remove(this.requesterId);
        System.out.println(TAG + " getMessageCommand: notRequesterIds = " + notRequesterIds);
        String invitedIdsJson = new Gson().toJson(notRequesterIds);

        if (roomType == CreateRoomCommand.RoomType.NORMAL) {

            String content = "INVITE:" + this.requesterId + ":" + invitedIdsJson;

            c = new SendMessageCommand(roomId, content, SendMessageCommand.Type.TEXT);
            c.messageId = this.messageId;
            c.requesterId = 0L;
            c.recipientId = this.recipientId;
            c.createdAT = this.createdAT;

        } else if (roomType == CreateRoomCommand.RoomType.OPEN) {
            // 오픈 채팅방의 경우

            if (isNewOpenChatMember) {
                System.out.println(TAG + " getMessageCommand: 오픈 채팅방에 신규 입장한 경우의 메시지 생성");

                if (notRequesterIds.size() > 0) {
                    System.out.println(TAG + " getMessageCommand: 기존 멤버의 의해 초대받음");

                    String content = "INVITE:" + this.requesterId + ":" + invitedIdsJson;
                    System.out.println(TAG + " getMessageCommand: content = " + content);
                    c = new SendMessageCommand(roomId, content, SendMessageCommand.Type.TEXT);
                } else {
                    c = new SendMessageCommand(roomId, "ENTER:" + this.requesterId, SendMessageCommand.Type.TEXT);
                }
            } else {
                System.out.println(TAG + " getMessageCommand: 오픈 채팅방을 생성한 경우의 메시지 생성");
                c = new SendMessageCommand(roomId, "오픈 채팅방이 생성되었습니다.", SendMessageCommand.Type.TEXT);
            }
            c.messageId = this.messageId;
            c.requesterId = 0L;
            c.recipientId = this.recipientId;
            c.createdAT = this.createdAT;
        }

        System.out.println(TAG + " getMessageCommand: return - 생성된 메시지 커맨드 = " + c.toJson());
        return c;
    }

}