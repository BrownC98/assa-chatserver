package com.teamnova.command;

import com.teamnova.command.chat.CheckReceiveCommand;
import com.teamnova.command.chat.CreateRoomCommand;
import com.teamnova.command.chat.ExitRoomCommand;
import com.teamnova.command.chat.InviteCommand;
import com.teamnova.command.chat.RoomInfoCommand;
import com.teamnova.command.chat.SendMessageCommand;
import com.teamnova.command.user.ConnectCommand;
import com.teamnova.command.webrtc.CreateVideoRoomCommand;
import com.teamnova.command.webrtc.ExitVideoRoomCommand;
import com.teamnova.command.webrtc.GetVideoRoomParticipantCommand;
import com.teamnova.command.webrtc.IceCandidateCommand;
import com.teamnova.command.webrtc.JoinVideoRoomCommand;
import com.teamnova.command.webrtc.MediaStatusCommand;
import com.teamnova.command.webrtc.SDPCommand;

// 명령 액션 enum

public enum Action {
    CONNECT(ConnectCommand.class), // 소켓 접속
    DISCONNECT(null),
    CREATE_ROOM(CreateRoomCommand.class), // 채팅방 생성
    EXIT_ROOM(ExitRoomCommand.class),
    ROOM_INFO(RoomInfoCommand.class),
    SEND_MESSAGE(SendMessageCommand.class),
    CHECK_RECEIVE(CheckReceiveCommand.class), INVITE(InviteCommand.class),
    CREATE_VIDEO_ROOM(CreateVideoRoomCommand.class), SDP(SDPCommand.class), ICE_CANDIDATE(IceCandidateCommand.class),
    JOIN_VIDEO_ROOM(JoinVideoRoomCommand.class),
    VIDEO_ROOM(VideoRoomCommand.class), EXIT_VIDEO_ROOM(ExitVideoRoomCommand.class),
    MEDIA_STATUS(MediaStatusCommand.class), GET_VIDEO_ROOM_PARTICIPANT(GetVideoRoomParticipantCommand.class);

    private final Class<? extends BaseCommand> commandClass;

    Action(Class<? extends BaseCommand> commandClass) {
        this.commandClass = commandClass;
    }

    public Class<? extends BaseCommand> getCommandClass() {
        return commandClass;
    }

}
