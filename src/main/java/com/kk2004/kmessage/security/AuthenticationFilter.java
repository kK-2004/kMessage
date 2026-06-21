package com.kk2004.kmessage.security;

import com.kk2004.kmessage.persistence.*;
import com.kk2004.common.response.TransDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.time.Instant;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class AuthenticationFilter extends OncePerRequestFilter {
    private final ApiCredentialRepository credentials;
    private final CallerRepository callers;
    private final ObjectMapper objectMapper;
    private final CallerRateLimiter rateLimiter;

    public AuthenticationFilter(ApiCredentialRepository credentials, CallerRepository callers, ObjectMapper objectMapper, CallerRateLimiter rateLimiter) {
        this.credentials = credentials; this.callers = callers; this.objectMapper = objectMapper; this.rateLimiter = rateLimiter;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.equals("/api/messages") && !path.startsWith("/api/messages/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String appKey = request.getHeader("X-App-Key");
        String appSecret = request.getHeader("X-App-Secret");
        var credential = appKey == null ? null : credentials.findByAppKeyAndActiveTrue(appKey).orElse(null);
        if (credential == null || appSecret == null || !MessageDigest.isEqual(credential.secretHash.getBytes(StandardCharsets.UTF_8),
                    Hashing.sha256(appSecret).getBytes(StandardCharsets.UTF_8))
                || (credential.expiresAt != null && credential.expiresAt.isBefore(Instant.now()))) {
            response.setStatus(401);
            response.setContentType("application/json;charset=UTF-8");
            objectMapper.writeValue(response.getWriter(), TransDTO.failure(401, "无效 appKey 或 appSecret"));
            return;
        }
        var caller = callers.findById(credential.callerId).filter(c -> c.active).orElse(null);
        if (caller == null) {
            response.setStatus(401);
            response.setContentType("application/json;charset=UTF-8");
            objectMapper.writeValue(response.getWriter(), TransDTO.failure(401, "调用方不可用"));
            return;
        }
        try {
            rateLimiter.check(caller.id);
            CallerContext.set(caller);
            chain.doFilter(request, response);
        } finally {
            CallerContext.clear();
        }
    }
}
