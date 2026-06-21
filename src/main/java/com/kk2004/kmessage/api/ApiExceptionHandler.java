package com.kk2004.kmessage.api;

import com.kk2004.common.exception.BaseBusinessException;
import com.kk2004.common.response.TransDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Arrays;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);
    private final Environment environment;

    public ApiExceptionHandler(Environment environment) {
        this.environment = environment;
    }

    @ExceptionHandler(BaseBusinessException.class)
    public TransDTO<String> handleBusinessException(BaseBusinessException exception) {
        return TransDTO.failure(exception.getCode(), exception.getMessage());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Void> handleNoResourceFound() {
        return ResponseEntity.notFound().build();
    }

    @ExceptionHandler(Exception.class)
    public TransDTO<String> handleException(Exception exception) {
        log.error("Unhandled API exception", exception);
        String message = isNonProd()
                ? exception.getClass().getSimpleName() + ": " + exception.getMessage()
                : "网络异常，请稍后重试";
        return TransDTO.failure(-100, message);
    }

    private boolean isNonProd() {
        return Arrays.stream(environment.getActiveProfiles())
                .anyMatch(profile -> profile.equalsIgnoreCase("local")
                        || profile.equalsIgnoreCase("dev")
                        || profile.equalsIgnoreCase("test"));
    }
}
