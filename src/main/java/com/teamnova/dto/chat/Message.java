package com.teamnova.dto.chat;

import com.google.gson.JsonObject;

public class Message {
    public Long id;
    public Long chatRoomId;
    public Long senderId;
    public String content;
    public TYPE type;
    public String sendedAt;

    public Message(Long id, Long chatRoomId, Long senderId, String content, TYPE type, String sendedAt) {
        this.id = id;
        this.chatRoomId = chatRoomId;
        this.senderId = senderId;
        this.content = content;
        this.type = type;
        this.sendedAt = sendedAt;
    }

    public Message() {
    }

    public String getAsJson(){
        JsonObject ret = new JsonObject();

        ret.addProperty("messageId", id);
        ret.addProperty("chatRoomId", chatRoomId);
        ret.addProperty("senderId", senderId);
        ret.addProperty("content", content);
        ret.addProperty("sendedAt", sendedAt);
        ret.addProperty("type", type.toString());

        return ret.toString();
    }

    public enum TYPE {
        TEXT,
        IMAGE,
        VIDEO
    }
}
