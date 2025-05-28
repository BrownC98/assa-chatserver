package com.teamnova.utils;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * ASSA Chat Server 성능 측정 및 로깅 헬퍼 클래스
 * 
 * 이 클래스는 메서드 실행 시간 측정, 성능 임계값 체크,
 * 자동 성능 로깅 등의 기능을 제공합니다.
 */
public class PerformanceLogger {

    private static final Logger log = LogManager.getLogger(PerformanceLogger.class);
    private static final Logger performanceLog = LogManager.getLogger("PERFORMANCE");

    // 성능 통계를 위한 메트릭 저장소
    private static final ConcurrentHashMap<String, PerformanceMetric> metrics = new ConcurrentHashMap<>();

    /**
     * 성능 메트릭 정보를 저장하는 내부 클래스
     */
    private static class PerformanceMetric {
        private final AtomicLong totalExecutions = new AtomicLong(0);
        private final AtomicLong totalDuration = new AtomicLong(0);
        private final AtomicLong maxDuration = new AtomicLong(0);
        private final AtomicLong minDuration = new AtomicLong(Long.MAX_VALUE);

        public void addExecution(long duration) {
            totalExecutions.incrementAndGet();
            totalDuration.addAndGet(duration);

            // 최대값 업데이트
            long currentMax = maxDuration.get();
            while (duration > currentMax && !maxDuration.compareAndSet(currentMax, duration)) {
                currentMax = maxDuration.get();
            }

            // 최소값 업데이트
            long currentMin = minDuration.get();
            while (duration < currentMin && !minDuration.compareAndSet(currentMin, duration)) {
                currentMin = minDuration.get();
            }
        }

        public double getAverageDuration() {
            long executions = totalExecutions.get();
            return executions > 0 ? (double) totalDuration.get() / executions : 0.0;
        }

        public long getTotalExecutions() {
            return totalExecutions.get();
        }

        public long getTotalDuration() {
            return totalDuration.get();
        }

        public long getMaxDuration() {
            return maxDuration.get();
        }

        public long getMinDuration() {
            long min = minDuration.get();
            return min == Long.MAX_VALUE ? 0 : min;
        }
    }

    /**
     * 메서드 실행 시간을 측정하고 로깅하는 클래스
     */
    public static class Timer {
        private final String operationName;
        private final String context;
        private final long startTime;
        private final long warnThreshold;
        private final long errorThreshold;

        private Timer(String operationName, String context, long warnThreshold, long errorThreshold) {
            this.operationName = operationName;
            this.context = context;
            this.startTime = System.currentTimeMillis();
            this.warnThreshold = warnThreshold;
            this.errorThreshold = errorThreshold;

            log.debug("성능 측정 시작: operation={}, context={}, startTime={}",
                    operationName, context, startTime);
        }

        /**
         * 타이머를 종료하고 성능 로그를 기록합니다.
         * 
         * @return 실행 시간 (밀리초)
         */
        public long stop() {
            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;

            // 메트릭 업데이트
            String metricKey = operationName + (context != null ? ":" + context : "");
            metrics.computeIfAbsent(metricKey, k -> new PerformanceMetric()).addExecution(duration);

            // 로그 레벨 결정
            if (duration >= errorThreshold) {
                log.error("성능 임계값 초과 (ERROR): operation={}, context={}, duration={}ms, errorThreshold={}ms",
                        operationName, context, duration, errorThreshold);
                performanceLog.error("PERFORMANCE_ERROR: operation={}, context={}, duration={}ms",
                        operationName, context, duration);
            } else if (duration >= warnThreshold) {
                log.warn("성능 임계값 초과 (WARN): operation={}, context={}, duration={}ms, warnThreshold={}ms",
                        operationName, context, duration, warnThreshold);
                performanceLog.warn("PERFORMANCE_WARN: operation={}, context={}, duration={}ms",
                        operationName, context, duration);
            } else {
                log.debug("성능 측정 완료: operation={}, context={}, duration={}ms",
                        operationName, context, duration);
                performanceLog.debug("PERFORMANCE_OK: operation={}, context={}, duration={}ms",
                        operationName, context, duration);
            }

            return duration;
        }

        /**
         * 타이머를 종료하고 결과와 함께 성능 로그를 기록합니다.
         * 
         * @param result 작업 결과
         * @return 실행 시간 (밀리초)
         */
        public long stop(Object result) {
            long duration = stop();
            log.debug("성능 측정 완료 (결과 포함): operation={}, context={}, duration={}ms, result={}",
                    operationName, context, duration, result);
            return duration;
        }
    }

    /**
     * 기본 임계값으로 성능 측정을 시작합니다.
     * 
     * @param operationName 작업 이름
     * @return Timer 인스턴스
     */
    public static Timer startTimer(String operationName) {
        return startTimer(operationName, null, 1000L, 5000L);
    }

    /**
     * 컨텍스트와 함께 성능 측정을 시작합니다.
     * 
     * @param operationName 작업 이름
     * @param context       컨텍스트 정보
     * @return Timer 인스턴스
     */
    public static Timer startTimer(String operationName, String context) {
        return startTimer(operationName, context, 1000L, 5000L);
    }

    /**
     * 사용자 정의 임계값으로 성능 측정을 시작합니다.
     * 
     * @param operationName  작업 이름
     * @param context        컨텍스트 정보
     * @param warnThreshold  경고 임계값 (밀리초)
     * @param errorThreshold 에러 임계값 (밀리초)
     * @return Timer 인스턴스
     */
    public static Timer startTimer(String operationName, String context, long warnThreshold, long errorThreshold) {
        return new Timer(operationName, context, warnThreshold, errorThreshold);
    }

    /**
     * 메시지 처리용 타이머를 시작합니다.
     * 
     * @param context 컨텍스트 정보
     * @return Timer 인스턴스
     */
    public static Timer startMessageTimer(String context) {
        return startTimer("MESSAGE_PROCESSING", context,
                LoggingConstants.PERFORMANCE_MESSAGE_PROCESSING_WARN_MS,
                LoggingConstants.PERFORMANCE_MESSAGE_PROCESSING_ERROR_MS);
    }

    /**
     * 데이터베이스 쿼리용 타이머를 시작합니다.
     * 
     * @param queryType 쿼리 타입
     * @param table     테이블 이름
     * @return Timer 인스턴스
     */
    public static Timer startDatabaseTimer(String queryType, String table) {
        String context = String.format("%s:%s", queryType, table);
        return startTimer("DATABASE_QUERY", context,
                LoggingConstants.PERFORMANCE_DB_QUERY_WARN_MS,
                LoggingConstants.PERFORMANCE_DB_QUERY_ERROR_MS);
    }

    /**
     * WebRTC 시그널링용 타이머를 시작합니다.
     * 
     * @param signalType 시그널 타입 (SDP, ICE_CANDIDATE 등)
     * @return Timer 인스턴스
     */
    public static Timer startWebRTCTimer(String signalType) {
        return startTimer("WEBRTC_SIGNALING", signalType,
                LoggingConstants.PERFORMANCE_SDP_SIGNALING_WARN_MS,
                LoggingConstants.PERFORMANCE_SDP_SIGNALING_ERROR_MS);
    }

    /**
     * 소켓 통신용 타이머를 시작합니다.
     * 
     * @param operation 소켓 작업 (SEND, RECEIVE 등)
     * @return Timer 인스턴스
     */
    public static Timer startSocketTimer(String operation) {
        return startTimer("SOCKET_COMMUNICATION", operation,
                LoggingConstants.PERFORMANCE_SOCKET_RESPONSE_WARN_MS,
                LoggingConstants.PERFORMANCE_SOCKET_RESPONSE_ERROR_MS);
    }

    /**
     * 함수형 인터페이스를 사용하여 코드 블록의 실행 시간을 측정합니다.
     * 
     * @param operationName 작업 이름
     * @param supplier      실행할 코드 블록
     * @param <T>           반환 타입
     * @return 코드 블록의 실행 결과
     */
    public static <T> T measureTime(String operationName, Supplier<T> supplier) {
        Timer timer = startTimer(operationName);
        try {
            T result = supplier.get();
            timer.stop(result);
            return result;
        } catch (Exception e) {
            timer.stop("ERROR: " + e.getMessage());
            throw e;
        }
    }

    /**
     * 함수형 인터페이스를 사용하여 코드 블록의 실행 시간을 측정합니다. (반환값 없음)
     * 
     * @param operationName 작업 이름
     * @param runnable      실행할 코드 블록
     */
    public static void measureTime(String operationName, Runnable runnable) {
        Timer timer = startTimer(operationName);
        try {
            runnable.run();
            timer.stop("SUCCESS");
        } catch (Exception e) {
            timer.stop("ERROR: " + e.getMessage());
            throw e;
        }
    }

    /**
     * 특정 작업의 성능 통계를 조회합니다.
     * 
     * @param operationName 작업 이름
     * @return 성능 통계 문자열
     */
    public static String getPerformanceStats(String operationName) {
        PerformanceMetric metric = metrics.get(operationName);
        if (metric == null) {
            return String.format("성능 통계 없음: operation=%s", operationName);
        }

        return String.format(
                "성능 통계: operation=%s, executions=%d, avgDuration=%.2fms, " +
                        "minDuration=%dms, maxDuration=%dms, totalDuration=%dms",
                operationName, metric.getTotalExecutions(), metric.getAverageDuration(),
                metric.getMinDuration(), metric.getMaxDuration(), metric.getTotalDuration());
    }

    /**
     * 모든 성능 통계를 로그로 출력합니다.
     */
    public static void logAllPerformanceStats() {
        if (metrics.isEmpty()) {
            performanceLog.info("성능 통계 없음");
            return;
        }

        performanceLog.info("=== 성능 통계 요약 ===");
        metrics.forEach((operationName, metric) -> {
            performanceLog.info(getPerformanceStats(operationName));
        });
        performanceLog.info("=== 성능 통계 요약 종료 ===");
    }

    /**
     * 성능 통계를 초기화합니다.
     */
    public static void clearPerformanceStats() {
        metrics.clear();
        performanceLog.info("성능 통계 초기화 완료");
    }

    /**
     * 메모리 사용량을 체크하고 로깅합니다.
     */
    public static void checkMemoryUsage() {
        Runtime runtime = Runtime.getRuntime();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;
        long maxMemory = runtime.maxMemory();

        long usedMemoryMB = usedMemory / (1024 * 1024);
        long totalMemoryMB = totalMemory / (1024 * 1024);
        long maxMemoryMB = maxMemory / (1024 * 1024);

        double usagePercentage = (double) usedMemory / maxMemory * 100;

        String memoryInfo = String.format(
                "메모리 사용량: used=%dMB, total=%dMB, max=%dMB, usage=%.1f%%",
                usedMemoryMB, totalMemoryMB, maxMemoryMB, usagePercentage);

        if (usedMemoryMB >= LoggingConstants.PERFORMANCE_MEMORY_USAGE_ERROR_MB) {
            log.error("메모리 사용량 위험 수준: {}", memoryInfo);
            performanceLog.error("MEMORY_ERROR: {}", memoryInfo);
        } else if (usedMemoryMB >= LoggingConstants.PERFORMANCE_MEMORY_USAGE_WARN_MB) {
            log.warn("메모리 사용량 경고 수준: {}", memoryInfo);
            performanceLog.warn("MEMORY_WARN: {}", memoryInfo);
        } else {
            log.debug("메모리 사용량 정상: {}", memoryInfo);
            performanceLog.debug("MEMORY_OK: {}", memoryInfo);
        }
    }

    /**
     * 시스템 리소스 정보를 로깅합니다.
     */
    public static void logSystemResources() {
        // 메모리 정보
        checkMemoryUsage();

        // 스레드 정보
        ThreadGroup rootGroup = Thread.currentThread().getThreadGroup();
        while (rootGroup.getParent() != null) {
            rootGroup = rootGroup.getParent();
        }
        int activeThreads = rootGroup.activeCount();

        performanceLog.info("시스템 리소스: activeThreads={}, availableProcessors={}",
                activeThreads, Runtime.getRuntime().availableProcessors());
    }

    /**
     * 성능 모니터링을 위한 주기적 로깅을 시작합니다.
     * 
     * @param intervalMinutes 로깅 간격 (분)
     */
    public static void startPeriodicLogging(int intervalMinutes) {
        Thread monitoringThread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(intervalMinutes * 60 * 1000L);
                    logSystemResources();
                    logAllPerformanceStats();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });

        monitoringThread.setName("PerformanceMonitor");
        monitoringThread.setDaemon(true);
        monitoringThread.start();

        log.info("성능 모니터링 시작: interval={}분", intervalMinutes);
    }
}