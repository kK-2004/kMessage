package com.kk2004.kmessage.security;

import com.kk2004.common.exception.BusinessException;
import com.kk2004.kmessage.config.KMessageProperties;
import com.kk2004.kmessage.domain.Entities.AdminCredential;
import com.kk2004.kmessage.persistence.AdminCredentialRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Service
public class AdminCredentialService {
    // Single shared row: a fixed id keeps the table a logical singleton.
    public static final String SINGLETON_ID = "admin";

    private final KMessageProperties properties;
    private final AdminCredentialRepository overrides;

    public AdminCredentialService(KMessageProperties properties, AdminCredentialRepository overrides) {
        this.properties = properties; this.overrides = overrides;
    }

    /** Constant-time comparison against the currently effective password (override or config default). */
    public boolean verify(String candidate) {
        if (candidate == null) return false;
        String effective = effectiveHash();
        String candidateHash = Hashing.sha256(candidate);
        return MessageDigest.isEqual(
                effective.getBytes(StandardCharsets.UTF_8),
                candidateHash.getBytes(StandardCharsets.UTF_8));
    }

    @Transactional
    public void changePassword(String oldPassword, String newPassword) {
        if (newPassword == null || newPassword.isBlank()) {
            throw new BusinessException("新密码不能为空");
        }
        if (newPassword.length() < 6) {
            throw new BusinessException("新密码长度至少 6 位");
        }
        if (!verify(oldPassword)) {
            throw new BusinessException(401, "原密码不正确");
        }
        // Changing to the same value is allowed but pointless; reject to give clearer feedback.
        if (MessageDigest.isEqual(
                oldPassword.getBytes(StandardCharsets.UTF_8),
                newPassword.getBytes(StandardCharsets.UTF_8))) {
            throw new BusinessException("新密码不能与原密码相同");
        }
        AdminCredential credential = overrides.findById(SINGLETON_ID).orElseGet(AdminCredential::new);
        credential.id = SINGLETON_ID;
        credential.passwordHash = Hashing.sha256(newPassword);
        credential.updatedAt = java.time.Instant.now();
        overrides.save(credential);
    }

    private String effectiveHash() {
        return overrides.findById(SINGLETON_ID)
                .map(c -> c.passwordHash)
                .orElseGet(() -> Hashing.sha256(properties.admin().password()));
    }
}
