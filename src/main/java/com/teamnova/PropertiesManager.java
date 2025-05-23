package com.teamnova;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Properties 관리 클래스
 */
public class PropertiesManager {

    private static Logger log = LogManager.getLogger(PropertiesManager.class.getName());

    private static final String CONFIG_FILE = "config.properties";
    private static Properties props = new Properties();

    static {
        try (InputStream input = PropertiesManager.class.getClassLoader().getResourceAsStream(CONFIG_FILE)) {
            props.load(input);
        } catch (IOException e) {
            log.error("설정 파일 로드 실패", e);
            throw new RuntimeException("서버 시작 불가", e);
        }
    }

    public static String getProperty(String key) {
        return props.getProperty(key);
    }
}
