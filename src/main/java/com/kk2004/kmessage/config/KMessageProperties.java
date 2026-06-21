package com.kk2004.kmessage.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("kmessage")
public record KMessageProperties(String runtimeRole, Admin admin, Worker worker) {
    public record Admin(String username, String password) {}
    public record Worker(long pollDelayMs, long leaseSeconds, int maxAttempts) {}
}
