package com.kk2004.kmessage.delivery;

import com.kk2004.kmessage.config.KMessageProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class DeliveryWorker {
    private final DeliveryService delivery;
    private final KMessageProperties properties;

    public DeliveryWorker(DeliveryService delivery, KMessageProperties properties) {
        this.delivery = delivery; this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${kmessage.worker.poll-delay-ms:1000}")
    public void poll() {
        if ("api".equalsIgnoreCase(properties.runtimeRole())) return;
        delivery.claim(20).forEach(delivery::deliver);
    }
}
