package com.kk2004.kmessage.channel;

import com.kk2004.common.exception.BusinessException;
import org.springframework.stereotype.Component;

public interface SecretProvider {
    String resolve(String reference);

    @Component
    class EnvironmentSecretProvider implements SecretProvider {
        @Override public String resolve(String reference) {
            if (reference == null || reference.isBlank()) throw new BusinessException("渠道凭据不可用");
            return reference;
        }
    }
}
