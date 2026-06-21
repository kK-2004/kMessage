package com.kk2004.kmessage.security;

import com.kk2004.common.exception.BusinessException;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class CallerRateLimiter {
    private static final int DEFAULT_LIMIT_PER_MINUTE = 600;
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    public void check(String callerId) {
        long minute = Instant.now().getEpochSecond() / 60;
        Window window = windows.compute(callerId, (key, current) ->
                current == null || current.minute != minute ? new Window(minute) : current);
        if (window.count.incrementAndGet() > DEFAULT_LIMIT_PER_MINUTE) throw new BusinessException(429, "调用频率超过限制");
    }

    private static final class Window {
        final long minute;
        final AtomicInteger count = new AtomicInteger();
        Window(long minute) { this.minute = minute; }
    }
}
