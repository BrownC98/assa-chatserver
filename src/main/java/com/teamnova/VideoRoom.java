package com.teamnova;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class VideoRoom {

    public String id;
    public Long hostId;
    public List<User> userList;
    // key - 유저 id, value - ice 후보를 보낸 유저 목록
    public Map<Long, List<Long>> receivedIceCandidateMap = new HashMap<>();

    public VideoRoom() {
        this.id = UUID.randomUUID().toString();
        this.userList = new ArrayList<>();
    }

    public void addUser(User user){
        userList.add(user);
        // receivedIceCandidateMap.put(user.id, new ArrayList<Long>());
    }

    public User getUserById(Long userId){

        for(User user : userList){
            if(user.id == userId){
                return user;
            }
        }

        return null;
    }
}
