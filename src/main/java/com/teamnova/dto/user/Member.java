package com.teamnova.dto.user;

import com.teamnova.dto.user.UserData;

public class Member {
    public Long roomId;
    public UserData userData;
    public String enteredAt;
    public String exitedAt;

    public Member(Long roomId, UserData userData, String enteredAt, String exitedAt) {
        this.roomId = roomId;
        this.userData = userData;
        this.enteredAt = enteredAt;
        this.exitedAt = exitedAt;
    }
}
