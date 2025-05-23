package com.teamnova;

public class UserData {
    public long id;
    public String nickname;
    public String profileImage;
    public Boolean isExit;

    public UserData(long id, String nickname, String profileImage) {
        this.id = id;
        this.nickname = nickname;
        this.profileImage = profileImage;
    }
}
