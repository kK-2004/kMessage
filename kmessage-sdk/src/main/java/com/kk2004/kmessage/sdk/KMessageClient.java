package com.kk2004.kmessage.sdk;

import com.fasterxml.jackson.databind.*;
import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.Map;

public final class KMessageClient {
    private final URI endpoint; private final String appKey; private final String appSecret;
    private final HttpClient http; private final ObjectMapper mapper;
    public KMessageClient(String endpoint, String appKey, String appSecret) {
        this(endpoint, appKey, appSecret, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build(), new ObjectMapper());
    }
    KMessageClient(String endpoint, String appKey, String appSecret, HttpClient http, ObjectMapper mapper) {
        this.endpoint=URI.create(endpoint.replaceAll("/$",""));this.appKey=appKey;this.appSecret=appSecret;this.http=http;this.mapper=mapper;
    }
    public MessageResult send(SendMessage message) { return request("/api/messages","POST",message,MessageResult.class); }
    /** Send a typed message entity to a raw channel target. */
    public MessageResult send(String channelInstanceId, String target, MessageEntity message, String idempotencyKey) {
        return send(new SendTypedMessage(channelInstanceId, target, null, null, message, idempotencyKey, Map.of()));
    }
    /** Send a typed message request that returns one message result; use sendToGroup for fan-out. */
    public MessageResult send(SendTypedMessage message) { return request("/api/messages","POST",message,MessageResult.class); }
    /** Send to all members of a group; the server fans out to N messages and returns a batch. */
    public MessageBatchResult sendToGroup(String channelInstanceId, String groupId, String text, String idempotencyKey) {
        return request("/api/messages","POST",
                new SendTargetMessage(channelInstanceId, null, groupId, null, text, idempotencyKey, Map.of()),
                MessageBatchResult.class);
    }
    /** Send a typed message entity to all members of a group. */
    public MessageBatchResult sendToGroup(String channelInstanceId, String groupId, MessageEntity message, String idempotencyKey) {
        return request("/api/messages","POST",
                new SendTypedMessage(channelInstanceId, null, groupId, null, message, idempotencyKey, Map.of()),
                MessageBatchResult.class);
    }
    /** Send to a single registered app-user (resolved by the server to the channel target). */
    public MessageResult sendToUser(String channelInstanceId, String userId, String text, String idempotencyKey) {
        return request("/api/messages","POST",
                new SendTargetMessage(channelInstanceId, null, null, userId, text, idempotencyKey, Map.of()),
                MessageResult.class);
    }
    /** Send a typed message entity to a single registered app-user. */
    public MessageResult sendToUser(String channelInstanceId, String userId, MessageEntity message, String idempotencyKey) {
        return request("/api/messages","POST",
                new SendTypedMessage(channelInstanceId, null, null, userId, message, idempotencyKey, Map.of()),
                MessageResult.class);
    }
    public JsonNode status(String messageId) { return request("/api/messages/"+messageId,"GET",null,JsonNode.class); }
    private <T> T request(String path,String method,Object body,Class<T> type) {
        try {
            HttpRequest.Builder b=HttpRequest.newBuilder(endpoint.resolve(path)).timeout(Duration.ofSeconds(15)).header("X-App-Key",appKey).header("X-App-Secret",appSecret).header("Content-Type","application/json");
            b.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)));
            HttpResponse<String> r=http.send(b.build(),HttpResponse.BodyHandlers.ofString());
            JsonNode envelope=mapper.readTree(r.body());if(!envelope.path("success").asBoolean())throw new KMessageException(envelope.path("message").asText(),r.statusCode());
            return mapper.treeToValue(envelope.get("data"),type);
        } catch(IOException e){throw new KMessageException("消息平台请求失败",e);}catch(InterruptedException e){Thread.currentThread().interrupt();throw new KMessageException("消息平台请求被中断",e);}
    }
    public record SendMessage(String channelInstanceId,String target,String text,String idempotencyKey,Map<String,Object> extensions){}
    /** Carries either a raw target, a groupId, or a userId (mutually exclusive server-side). */
    public record SendTargetMessage(String channelInstanceId,String target,String groupId,String userId,String text,String idempotencyKey,Map<String,Object> extensions){}
    /** Carries either a raw target, a groupId, or a userId with a typed message entity. */
    public record SendTypedMessage(String channelInstanceId,String target,String groupId,String userId,MessageEntity message,String idempotencyKey,Map<String,Object> extensions){}
    public interface MessageEntity {}
    public record NormalMessage(String type, String text) implements MessageEntity {
        public NormalMessage(String text) { this("TEXT", text); }
        public NormalMessage {
            if (text == null || text.isBlank()) throw new IllegalArgumentException("text 不能为空");
            type = "TEXT";
        }
        public static NormalMessage of(String text) { return new NormalMessage(text); }
    }
    public record CardMessage(String type, Map<String,Object> card) implements MessageEntity {
        public CardMessage(Map<String,Object> card) { this("CARD", card); }
        public CardMessage {
            if (card == null || card.isEmpty()) throw new IllegalArgumentException("card 不能为空");
            type = "CARD";
        }
        public static CardMessage of(Map<String,Object> card) { return new CardMessage(card); }
    }
    public record MessageResult(String id,String channelInstanceId,String status,String createdAt,String updatedAt){}
    public record MessageBatchResult(int totalMessages,java.util.List<MessageResult> messages){}
    public static class KMessageException extends RuntimeException { public final int status; public KMessageException(String m,int s){super(m);status=s;}public KMessageException(String m,Throwable c){super(m,c);status=0;} }
}
