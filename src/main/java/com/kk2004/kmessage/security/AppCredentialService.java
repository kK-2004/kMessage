package com.kk2004.kmessage.security;

import com.kk2004.common.exception.BusinessException;
import com.kk2004.kmessage.domain.Entities.*;
import com.kk2004.kmessage.persistence.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;

@Service
public class AppCredentialService {
    private final CallerRepository callers;
    private final ApiCredentialRepository credentials;
    private final GrantRepository grants;
    private final SecureRandom random = new SecureRandom();

    public AppCredentialService(CallerRepository callers, ApiCredentialRepository credentials, GrantRepository grants) {
        this.callers = callers; this.credentials = credentials; this.grants = grants;
    }

    @Transactional
    public CreatedCredential create(String applicationName) {
        if (callers.findByName(applicationName).filter(c -> c.active).isPresent()) throw new BusinessException(409, "应用名称已存在");
        return issue(callers.save(new Caller(applicationName, false)), "app_" + random(12));
    }

    @Transactional
    public CreatedCredential rotate(String callerId) {
        Caller caller = callers.findById(callerId).orElseThrow(() -> new BusinessException(404, "应用不存在"));
        ApiCredential current = credentials.findAll().stream().filter(c -> c.callerId.equals(callerId) && c.active).findFirst()
                .orElseThrow(() -> new BusinessException(404, "应用凭据不存在"));
        current.active = false;
        return issue(caller, current.appKey);
    }

    public List<ApplicationView> list() {
        Map<String, ApiCredential> active = new HashMap<>();
        credentials.findAll().stream().filter(c -> c.active).forEach(c -> active.put(c.callerId, c));
        return callers.findAll().stream().filter(c -> c.active).map(c -> new ApplicationView(c.id, c.name, c.active,
                Optional.ofNullable(active.get(c.id)).map(a -> a.appKey).orElse(null), c.createdAt)).toList();
    }

    @Transactional
    public void delete(String callerId) {
        Caller caller = callers.findById(callerId).orElseThrow(() -> new BusinessException(404, "应用不存在"));
        caller.active = false;
        String namePrefix = caller.name.length() > 70 ? caller.name.substring(0, 70) : caller.name;
        caller.name = namePrefix + "__deleted__" + caller.id;
        credentials.findByCallerId(callerId).forEach(credential -> credential.active = false);
        grants.deleteByCallerId(callerId);
    }

    private CreatedCredential issue(Caller caller, String appKey) {
        String appSecret = "secret_" + random(32);
        ApiCredential credential = new ApiCredential();
        credential.callerId = caller.id; credential.appKey = appKey; credential.secretHash = Hashing.sha256(appSecret);
        credentials.save(credential);
        return new CreatedCredential(caller.id, caller.name, appKey, appSecret, credential.createdAt);
    }

    private String random(int bytes) {
        byte[] value = new byte[bytes]; random.nextBytes(value);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    public record CreatedCredential(String applicationId, String applicationName, String appKey, String appSecret, Instant createdAt) {}
    public record ApplicationView(String id, String name, boolean active, String appKey, Instant createdAt) {}
}
