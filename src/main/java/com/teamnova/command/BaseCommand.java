package com.teamnova.command;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.teamnova.utils.TimeUtils;

public abstract class BaseCommand {

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .create();

    public Long id;
    public Action action;
    public Long requesterId; // 요청자
    public String createdAT; // 커맨드가 생성된 시간

    public BaseCommand(Action action) {
        this.action = action;
        this.requesterId = 0L; // 서버의 id는 0 이다.
        this.createdAT = TimeUtils.getCurrentTimeInUTC();
    }

    public String toJson() {
        return new Gson().toJson(this);
    }

    protected static <T extends BaseCommand> T fromJson(String json, Class<T> clazz) throws Exception {
        try {
            return GSON.fromJson(json, clazz);
        } catch (Exception e) {
            throw new Exception("Failed to parse JSON to " + clazz.getSimpleName(), e);
        }
    }

    // action에 맞는 커맨드 객체 생성
    public static BaseCommand fromJson(Action action, String json) throws Exception {
        return fromJson(json, action.getCommandClass());
    }
}
