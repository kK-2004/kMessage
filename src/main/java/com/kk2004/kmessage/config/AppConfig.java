package com.kk2004.kmessage.config;

import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.*;
import org.springframework.web.client.RestClient;

@Configuration
public class AppConfig {
    @Bean
    RestClient restClient(RestClient.Builder builder) {
        return builder.build();
    }
}
