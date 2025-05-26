package com.teamnova.utils;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

public class TimeUtils {

    // 현재 UTC 시간 구하기
    public static String getCurrentTimeInUTC() {
        ZonedDateTime utcTime = ZonedDateTime.now(ZoneOffset.UTC);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        return utcTime.format(formatter);
    }
}
