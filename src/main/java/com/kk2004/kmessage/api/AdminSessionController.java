package com.kk2004.kmessage.api;

import com.kk2004.common.response.TransDTO;
import com.kk2004.kmessage.config.KMessageProperties;
import com.kk2004.kmessage.security.AdminCredentialService;
import com.kk2004.kmessage.security.AdminSessionFilter;
import jakarta.servlet.http.*;
import org.springframework.web.bind.annotation.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@RestController
@RequestMapping("/api/admin/session")
public class AdminSessionController {
    private final KMessageProperties properties;
    private final AdminCredentialService credentials;
    public AdminSessionController(KMessageProperties properties, AdminCredentialService credentials) {
        this.properties = properties; this.credentials = credentials;
    }

    @PostMapping("/login")
    public TransDTO<String> login(HttpServletRequest http, @RequestBody Login request) {
        if (!same(properties.admin().username(), request.username()) || !credentials.verify(request.password())) {
            return TransDTO.failure(401, "用户名或密码错误");
        }
        http.getSession(true).setAttribute(AdminSessionFilter.ADMIN_SESSION, true);
        return TransDTO.success(properties.admin().username());
    }

    @PostMapping("/logout")
    public TransDTO<Void> logout(HttpServletRequest http) {
        HttpSession session = http.getSession(false);
        if (session != null) session.invalidate();
        return TransDTO.success();
    }

    @GetMapping
    public TransDTO<String> current() { return TransDTO.success(properties.admin().username()); }

    @PutMapping("/password")
    public TransDTO<Void> changePassword(@RequestBody ChangePassword request) {
        credentials.changePassword(request.oldPassword(), request.newPassword());
        return TransDTO.success();
    }

    private boolean same(String expected, String actual) {
        return actual != null && MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8));
    }
    public record Login(String username, String password) {}
    public record ChangePassword(String oldPassword, String newPassword) {}
}
