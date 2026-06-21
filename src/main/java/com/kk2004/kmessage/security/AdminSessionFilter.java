package com.kk2004.kmessage.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kk2004.common.response.TransDTO;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class AdminSessionFilter extends OncePerRequestFilter {
    public static final String ADMIN_SESSION = "kmessageAdmin";
    private final ObjectMapper mapper;
    public AdminSessionFilter(ObjectMapper mapper) { this.mapper = mapper; }

    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/admin");
    }

    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        if (path.equals("/api/admin/session/login")) { chain.doFilter(request, response); return; }
        HttpSession session = request.getSession(false);
        if (session != null && Boolean.TRUE.equals(session.getAttribute(ADMIN_SESSION))) { chain.doFilter(request, response); return; }
        response.setStatus(401); response.setContentType("application/json;charset=UTF-8");
        mapper.writeValue(response.getWriter(), TransDTO.failure(401, "请先登录管理后台"));
    }
}
