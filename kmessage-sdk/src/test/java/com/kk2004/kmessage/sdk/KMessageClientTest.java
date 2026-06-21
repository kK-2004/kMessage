package com.kk2004.kmessage.sdk;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

class KMessageClientTest {
    HttpServer server;
    AtomicReference<String> appKey = new AtomicReference<>();
    AtomicReference<String> appSecret = new AtomicReference<>();
    AtomicReference<String> requestBody = new AtomicReference<>();

    @BeforeEach void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api/messages", exchange -> {
            appKey.set(exchange.getRequestHeaders().getFirst("X-App-Key"));
            appSecret.set(exchange.getRequestHeaders().getFirst("X-App-Secret"));
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            requestBody.set(body);
            String response;
            if (body.contains("\"groupId\":") && !body.contains("\"groupId\":null")) {
                response = "{\"success\":true,\"data\":{\"totalMessages\":2,\"messages\":[{\"id\":\"m1\",\"channelInstanceId\":\"c1\",\"status\":\"ACCEPTED\",\"createdAt\":\"now\",\"updatedAt\":\"now\"},{\"id\":\"m2\",\"channelInstanceId\":\"c1\",\"status\":\"ACCEPTED\",\"createdAt\":\"now\",\"updatedAt\":\"now\"}]}}";
            } else {
                response = "{\"success\":true,\"data\":{\"id\":\"m1\",\"channelInstanceId\":\"c1\",\"status\":\"ACCEPTED\",\"createdAt\":\"now\",\"updatedAt\":\"now\"}}";
            }
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(202, bytes.length); exchange.getResponseBody().write(bytes); exchange.close();
        });
        server.start();
    }
    @AfterEach void stop() { server.stop(0); }

    @Test void sendsAppCredentialsAndMessage() {
        KMessageClient client = new KMessageClient("http://localhost:" + server.getAddress().getPort(), "app-key", "app-secret");
        KMessageClient.MessageResult result = client.send(new KMessageClient.SendMessage("c1", "target", "hello", "idem", Map.of()));
        Assertions.assertEquals("m1", result.id());
        Assertions.assertEquals("app-key", appKey.get());
        Assertions.assertEquals("app-secret", appSecret.get());
    }

    @Test void sendToGroupReturnsBatch() {
        KMessageClient client = new KMessageClient("http://localhost:" + server.getAddress().getPort(), "app-key", "app-secret");
        KMessageClient.MessageBatchResult batch = client.sendToGroup("c1", "g1", "hello-group", "idem-group");
        Assertions.assertEquals(2, batch.totalMessages());
        Assertions.assertEquals(2, batch.messages().size());
        Assertions.assertEquals("m1", batch.messages().get(0).id());
    }

    @Test void sendToUserReturnsSingleResult() {
        KMessageClient client = new KMessageClient("http://localhost:" + server.getAddress().getPort(), "app-key", "app-secret");
        KMessageClient.MessageResult result = client.sendToUser("c1", "u1", "hello-user", "idem-user");
        Assertions.assertEquals("m1", result.id());
    }

    @Test void sendsTypedNormalMessageEntity() {
        KMessageClient client = new KMessageClient("http://localhost:" + server.getAddress().getPort(), "app-key", "app-secret");
        KMessageClient.MessageResult result = client.send("c1", "target",
                new KMessageClient.NormalMessage("hello-normal"), "idem-normal");
        Assertions.assertEquals("m1", result.id());
        Assertions.assertTrue(requestBody.get().contains("\"message\":{\"type\":\"TEXT\",\"text\":\"hello-normal\"}"));
    }

    @Test void sendsTypedCardMessageEntity() {
        KMessageClient client = new KMessageClient("http://localhost:" + server.getAddress().getPort(), "app-key", "app-secret");
        KMessageClient.MessageResult result = client.sendToUser("c1", "u1",
                new KMessageClient.CardMessage(Map.of(
                        "header", Map.of("title", Map.of("tag", "plain_text", "content", "告警")),
                        "elements", java.util.List.of(Map.of(
                                "tag", "div",
                                "text", Map.of("tag", "lark_md", "content", "**状态**：失败"))))),
                "idem-card");
        Assertions.assertEquals("m1", result.id());
        Assertions.assertTrue(requestBody.get().contains("\"message\":{\"type\":\"CARD\",\"card\":"));
        Assertions.assertTrue(requestBody.get().contains("\"content\":\"告警\""));
    }
}
