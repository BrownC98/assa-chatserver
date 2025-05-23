package com.teamnova.demo;

import org.apache.logging.log4j.*;

/**
 * LoggingDemo
 */
public class LoggingDemo {

    private static Logger demoLogger = LogManager.getLogger(LoggingDemo.class.getName());

    public static void main(String[] args) {
        System.out.println("this is syso");

        demoLogger.trace("this is trace");
        demoLogger.debug("this is debug");
        demoLogger.info("this is info");
        demoLogger.error("this is error");
    }    
}