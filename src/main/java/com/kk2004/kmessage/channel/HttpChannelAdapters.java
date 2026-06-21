package com.kk2004.kmessage.channel;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.kk2004.kmessage.domain.*;
import com.kk2004.kmessage.domain.Entities.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.*;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public final class HttpChannelAdapters {
    private HttpChannelAdapters() {}

    abstract static class Base implements ChannelAdapter {
        final RestClient http;
        final SecretProvider secrets;
        final ObjectMapper mapper;
        Base(RestClient http, SecretProvider secrets, ObjectMapper mapper) {
            this.http = http; this.secrets = secrets; this.mapper = mapper;
        }
        @Override public void validate(String target, String extensionJson) {
            ChannelAdapter.super.validate(target, extensionJson);
            try {
                JsonNode extensions = mapper.readTree(extensionJson);
                if (!extensions.isObject() || !extensions.isEmpty()) throw new IllegalArgumentException("当前渠道暂不支持扩展参数");
            } catch (JsonProcessingException e) {
                throw new IllegalArgumentException("扩展参数格式错误");
            }
        }
        DeliveryResult post(String url, Object body) {
            try {
                String response = http.post().uri(url).contentType(MediaType.APPLICATION_JSON)
                        .body(body).retrieve().body(String.class);
                return DeliveryResult.success(response == null ? null : Integer.toHexString(response.hashCode()));
            } catch (HttpStatusCodeException e) {
                int status = e.getStatusCode().value();
                if (status == 408 || status == 429 || status >= 500) return DeliveryResult.transientFailure("HTTP_" + status, e.getStatusText());
                return DeliveryResult.permanentFailure("HTTP_" + status, e.getStatusText());
            } catch (ResourceAccessException e) {
                return DeliveryResult.transientFailure("NETWORK", e.getClass().getSimpleName());
            } catch (Exception e) {
                return DeliveryResult.permanentFailure("ADAPTER_ERROR", e.getClass().getSimpleName());
            }
        }
    }

    @Component
    public static class Telegram extends Base {
        // Telegram Bot API: chat_id is either a signed integer or a @channelusername (5-32 chars).
        private static final Pattern NUMERIC_CHAT_ID = Pattern.compile("-?\\d+");
        private static final Pattern CHANNEL_USERNAME = Pattern.compile("@[A-Za-z0-9_]{5,32}");
        // Telegram Bot API caps message.text at 4096 UTF-16 code units.
        private static final int MAX_TEXT_LENGTH = 4096;

        public Telegram(RestClient h, SecretProvider s, ObjectMapper m) { super(h, s, m); }
        public ChannelType type() { return ChannelType.TELEGRAM; }
        @Override public void validate(String target, String extensionJson) {
            super.validate(target, extensionJson);
            if (!isSupportedTarget(target))
                throw new IllegalArgumentException("Telegram target 必须是 chat id 或 @channelusername");
        }
        @Override public void validate(String target, MessageContentType contentType, String extensionJson) {
            validate(target, extensionJson);
            if (contentType != MessageContentType.TEXT)
                throw new IllegalArgumentException("Telegram 暂不支持卡片消息");
        }
        public static boolean isSupportedTarget(String target) {
            return NUMERIC_CHAT_ID.matcher(target).matches() || CHANNEL_USERNAME.matcher(target).matches();
        }
        @Override public DeliveryResult send(ChannelInstance c, Message m) {
            if (m.contentType != MessageContentType.TEXT)
                return DeliveryResult.permanentFailure("UNSUPPORTED_MESSAGE_TYPE", "Telegram 暂不支持卡片消息");
            if (m.contentText != null && m.contentText.length() > MAX_TEXT_LENGTH)
                return DeliveryResult.permanentFailure("TEXT_TOO_LONG", "消息超过 4096 字符上限");
            String token = secrets.resolve(c.credentialRef);
            return post("https://api.telegram.org/bot" + token + "/sendMessage",
                    Map.of("chat_id", m.targetValue, "text", m.contentText));
        }

        @Override public List<ContactOption> listContacts(ChannelInstance c) {
            String token = secrets.resolve(c.credentialRef);
            // offset=-1 returns updates from the last 48 hours WITHOUT consuming them
            // (Telegram does not advance the offset for negative values), so this is safe
            // to call repeatedly and does not interfere with a potential webhook/polling consumer.
            String json = http.get().uri("https://api.telegram.org/bot" + token + "/getUpdates?offset=-1")
                    .retrieve().body(String.class);
            LinkedHashMap<String, ContactOption> dedup = new LinkedHashMap<>();
            try {
                JsonNode results = mapper.readTree(json).path("result");
                for (JsonNode update : results) {
                    JsonNode chat = update.path("message").path("chat");
                    if (!chat.has("id")) chat = update.path("my_chat_member").path("chat");
                    if (!chat.has("id")) continue;
                    String id = chat.path("id").asText();
                    if (dedup.containsKey(id)) continue;
                    String type = chat.path("type").asText("private");
                    String category = type.startsWith("group") || "supergroup".equals(type) ? "group"
                            : "channel".equals(type) ? "channel" : "user";
                    String title = chat.path("title").asText(null);
                    if (title == null || title.isBlank()) {
                        String first = chat.path("first_name").asText("");
                        String last = chat.path("last_name").asText("");
                        String username = chat.path("username").asText("");
                        title = (first + " " + last).trim();
                        if (title.isBlank()) title = username.isBlank() ? id : "@" + username;
                    }
                    dedup.put(id, new ContactOption(id, title, category));
                }
            } catch (Exception e) {
                return List.of();
            }
            return List.copyOf(dedup.values());
        }
    }

    @Component
    public static class Feishu extends Base {
        private static final Logger log = LoggerFactory.getLogger("kmessage.feishu");
        private static final String TOKEN_URL = "https://open.feishu.cn/open-apis/auth/v3/tenant_access_token/internal";
        private static final String MESSAGE_URL = "https://open.feishu.cn/open-apis/im/v1/messages";
        private static final String CHATS_URL = "https://open.feishu.cn/open-apis/im/v1/chats";
        private static final int TOKEN_REFRESH_BUFFER = 300;
        // Optional configJson key forcing a single receive_id_type instead of target-based derivation.
        private static final String CONFIG_RECEIVE_ID_TYPE = "receive_id_type";
        private static final Pattern EMAIL_SHAPE = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

        private final ConcurrentHashMap<String, CachedToken> tokenCache = new ConcurrentHashMap<>();

        record CachedToken(String token, Instant expiresAt) {}

        public Feishu(RestClient h, SecretProvider s, ObjectMapper m) { super(h, s, m); }
        public ChannelType type() { return ChannelType.FEISHU; }

        String[] resolveCredentials(String credentialRef) {
            String raw = secrets.resolve(credentialRef);
            String[] parts = raw.split(":", 2);
            if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank())
                throw new IllegalArgumentException("飞书凭据格式错误，需要 app_id:app_secret");
            return parts;
        }

        CachedToken fetchToken(String appId, String appSecret) {
            String json = http.post().uri(TOKEN_URL).contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("app_id", appId, "app_secret", appSecret))
                    .retrieve().body(String.class);
            try {
                JsonNode root = mapper.readTree(json);
                if (root.path("code").asInt(-1) != 0)
                    throw new IllegalStateException("飞书 token 获取失败: " + root.path("msg").asText("unknown"));
                String token = root.path("tenant_access_token").asText();
                // Feishu returns `expire` as an ABSOLUTE epoch-second expiry timestamp.
                long expire = root.path("expire").asLong();
                Instant expiresAt = expire > 0 ? Instant.ofEpochSecond(expire) : Instant.now().plusSeconds(7200);
                return new CachedToken(token, expiresAt);
            } catch (JsonProcessingException e) {
                throw new IllegalStateException("飞书 token 响应解析失败", e);
            }
        }

        String getOrCreateToken(String credentialRef) {
            return tokenCache.compute(credentialRef, (ref, cached) -> {
                if (cached != null && Instant.now().isBefore(cached.expiresAt().minusSeconds(TOKEN_REFRESH_BUFFER)))
                    return cached;
                String[] creds = resolveCredentials(ref);
                return fetchToken(creds[0], creds[1]);
            }).token();
        }

        void evictToken(String credentialRef) {
            tokenCache.remove(credentialRef);
        }

        String sendMessage(String token, String receiveIdType, Object body) {
            return http.post().uri(MESSAGE_URL + "?receive_id_type=" + receiveIdType)
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body).retrieve().body(String.class);
        }

        /**
         * Derive the Feishu receive_id_type from the target form, unless the channel
         * instance config explicitly pins one via {@code configJson.receive_id_type}.
         */
        public String resolveReceiveIdType(ChannelInstance c, String target) {
            String override = readConfigReceiveIdType(c.configJson);
            if (override != null && !override.isBlank()) return override;
            if (target == null) return "open_id";
            if (target.startsWith("ou_")) return "open_id";
            if (target.startsWith("on_")) return "union_id";
            if (target.startsWith("oc_")) return "chat_id";
            if (EMAIL_SHAPE.matcher(target).matches()) return "email";
            if (target.matches("-?\\d+")) return "chat_id";
            return "user_id";
        }

        String readConfigReceiveIdType(String configJson) {
            if (configJson == null || configJson.isBlank()) return null;
            try {
                JsonNode node = mapper.readTree(configJson);
                JsonNode value = node.path(CONFIG_RECEIVE_ID_TYPE);
                return value.isTextual() ? value.asText() : null;
            } catch (JsonProcessingException e) {
                return null;
            }
        }

        DeliveryResult classifyResponse(String json) {
            try {
                JsonNode root = mapper.readTree(json);
                int code = root.path("code").asInt(-1);
                if (code == 0)
                    return DeliveryResult.success(root.path("data").path("message_id").asText());
                return DeliveryResult.permanentFailure("FEISHU_" + code, root.path("msg").asText());
            } catch (Exception e) {
                return DeliveryResult.permanentFailure("PARSE_ERROR", e.getMessage());
            }
        }

        @Override public DeliveryResult send(ChannelInstance c, Message m) {
            String receiveIdType = resolveReceiveIdType(c, m.targetValue);
            String token = getOrCreateToken(c.credentialRef);
            String msgType;
            String content;
            try {
                if (m.contentType == MessageContentType.CARD) {
                    if (m.contentJson == null || m.contentJson.isBlank())
                        return DeliveryResult.permanentFailure("EMPTY_CARD", "卡片内容不能为空");
                    msgType = "interactive";
                    content = m.contentJson;
                } else {
                    msgType = "text";
                    content = mapper.writeValueAsString(Map.of("text", m.contentText));
                }
            } catch (JsonProcessingException e) {
                return DeliveryResult.permanentFailure("SERIALIZE_ERROR", e.getMessage());
            }
            Map<String, String> body = Map.of(
                    "receive_id", m.targetValue,
                    "msg_type", msgType,
                    "content", content);
            try {
                String json = sendMessage(token, receiveIdType, body);
                DeliveryResult result = classifyResponse(json);
                if (result.type() == DeliveryResult.Type.PERMANENT_FAILURE
                        && "FEISHU_99991401".equals(result.errorCode())) {
                    evictToken(c.credentialRef);
                    token = getOrCreateToken(c.credentialRef);
                    json = sendMessage(token, receiveIdType, body);
                    return classifyResponse(json);
                }
                return result;
            } catch (HttpStatusCodeException e) {
                int status = e.getStatusCode().value();
                if (status == 408 || status == 429 || status >= 500) return DeliveryResult.transientFailure("HTTP_" + status, e.getStatusText());
                return DeliveryResult.permanentFailure("HTTP_" + status, e.getStatusText());
            } catch (ResourceAccessException e) {
                return DeliveryResult.transientFailure("NETWORK", e.getClass().getSimpleName());
            } catch (Exception e) {
                return DeliveryResult.permanentFailure("ADAPTER_ERROR", e.getClass().getSimpleName());
            }
        }

        @Override public List<ContactOption> listContacts(ChannelInstance c) {
            // Feishu enumerates groups the bot has joined via im/v1/chats. Listing individual
            // users requires extra contacts-scope permissions, so the picker offers groups only;
            // private targets fall back to manual open_id entry in the dialog.
            String token = getOrCreateToken(c.credentialRef);
            try {
                String json = http.get().uri(CHATS_URL + "?user_id_type=open_id&page_size=50")
                        .header("Authorization", "Bearer " + token)
                        .retrieve().body(String.class);
                JsonNode items = mapper.readTree(json).path("data").path("items");
                java.util.List<ContactOption> contacts = new java.util.ArrayList<>();
                for (JsonNode item : items) {
                    String chatId = item.path("chat_id").asText("");
                    if (chatId.isBlank()) continue;
                    String name = item.path("name").asText(chatId);
                    contacts.add(new ContactOption(chatId, name, "group"));
                }
                return contacts;
            } catch (Exception e) {
                return List.of();
            }
        }

        @Override public List<ResolvedUser> lookupUsers(ChannelInstance c, List<String> mobiles, List<String> emails) {
            // Feishu: POST contact/v3/users/batch_get_id?user_id_type=open_id with {mobiles, emails}.
            // Requires contact:user.id:readonly (+ phone/email readonly) and the app's contacts scope.
            // Feishu expects E.164 mobile numbers with country code (e.g. +8613...).
            if ((mobiles == null || mobiles.isEmpty()) && (emails == null || emails.isEmpty())) return List.of();
            String token = getOrCreateToken(c.credentialRef);
            java.util.Map<String, Object> body = new java.util.HashMap<>();
            if (mobiles != null) body.put("mobiles", mobiles.stream().map(Feishu::normalizeMobile).toList());
            if (emails != null) body.put("emails", emails);
            String json;
            try {
                json = http.post()
                        .uri("https://open.feishu.cn/open-apis/contact/v3/users/batch_get_id?user_id_type=open_id")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body).retrieve().body(String.class);
            } catch (Exception e) {
                // Surface transport/HTTP errors so the admin knows the request itself failed.
                throw new com.kk2004.common.exception.BusinessException("飞书用户解析请求失败：" + e.getMessage());
            }
            // Debug-log the raw response so operators can see exactly what Feishu returned
            // (e.g. permission scope issues, or a user not in the contacts visibility range).
            log.info("batch_get_id request body={}, raw response={}", body, json);
            try {
                JsonNode root = checkFeishuCode(json);
                JsonNode userList = root.path("data").path("user_list");
                java.util.List<ResolvedUser> resolved = new java.util.ArrayList<>();
                for (JsonNode u : userList) {
                    // The ID field is ALWAYS named "user_id" in the response regardless of the
                    // user_id_type query param (its VALUE is the open_id when type=open_id).
                    String targetId = u.path("user_id").asText("");
                    if (targetId.isBlank()) continue;
                    resolved.add(new ResolvedUser(
                            targetId,
                            u.path("mobile").asText(null),
                            u.path("email").asText(null),
                            u.path("name").asText(null)));
                }
                return resolved;
            } catch (com.kk2004.common.exception.BusinessException e) {
                throw e;
            } catch (Exception e) {
                throw new com.kk2004.common.exception.BusinessException("飞书用户解析响应解析失败：" + e.getMessage());
            }
        }

        @Override public List<OrgNode> listOrgStructure(ChannelInstance c) {
            // Feishu: build the enterprise org tree from contact/v3/departments (fetch_child=true)
            // and attach each department's members via contact/v3/users/find_by_department.
            // Requires contact:department.base:readonly + contact:user.base:readonly and the
            // app's contacts permission range set to "all members" to see the root department.
            String token = getOrCreateToken(c.credentialRef);
            // 1. Fetch all departments (fetch_child returns the whole subtree in pages).
            // department_id=0 means start from the root; fetch_child=true recurses the whole tree.
            java.util.List<java.util.Map<String, String>> deptItems = new java.util.ArrayList<>();
            String deptPageToken = "";
            try {
                do {
                    String url = "https://open.feishu.cn/open-apis/contact/v3/departments?department_id=0&fetch_child=true&user_id_type=open_id&page_size=50"
                            + (deptPageToken.isBlank() ? "" : "&page_token=" + deptPageToken);
                    String json = http.get().uri(url).header("Authorization", "Bearer " + token).retrieve().body(String.class);
                    JsonNode root = checkFeishuCode(json);
                    for (JsonNode d : root.path("data").path("items")) {
                        deptItems.add(java.util.Map.of(
                                "id", d.path("department_id").asText(""),
                                "name", d.path("name").asText(d.path("department_id").asText("")),
                                "parent", d.path("parent_id").asText("")));
                    }
                    deptPageToken = root.path("data").path("page_token").asText("");
                    if (!root.path("data").path("has_more").asBoolean(false)) break;
                } while (!deptPageToken.isBlank());
            } catch (com.kk2004.common.exception.BusinessException e) {
                throw e;
            } catch (Exception e) {
                throw new com.kk2004.common.exception.BusinessException("飞书部门拉取请求失败：" + e.getMessage());
            }
            // 2. Build department nodes and assemble tree by parent_id.
            java.util.Map<String, OrgNode> deptNodes = new LinkedHashMap<>();
            for (java.util.Map<String, String> d : deptItems) {
                String id = d.get("id");
                if (!id.isBlank()) deptNodes.put(id, new OrgNode(id, d.get("name"), d.get("parent"), true, null, null, new java.util.ArrayList<>()));
            }
            java.util.List<OrgNode> roots = new java.util.ArrayList<>();
            for (OrgNode dept : deptNodes.values()) {
                String pid = dept.parentId();
                if (pid == null || pid.isBlank() || "0".equals(pid) || !deptNodes.containsKey(pid)) {
                    roots.add(dept);
                } else {
                    ((java.util.ArrayList<OrgNode>) deptNodes.get(pid).children()).add(dept);
                }
            }
            // 3. For each department, fetch its direct members and attach as user leaves.
            for (OrgNode dept : deptNodes.values()) {
                String deptId = dept.id();
                String userPageToken = "";
                try {
                    do {
                        String url = "https://open.feishu.cn/open-apis/contact/v3/users/find_by_department?department_id=" + deptId
                                + "&user_id_type=open_id&page_size=50"
                                + (userPageToken.isBlank() ? "" : "&page_token=" + userPageToken);
                        String json = http.get().uri(url).header("Authorization", "Bearer " + token).retrieve().body(String.class);
                        JsonNode root = checkFeishuCode(json);
                        for (JsonNode u : root.path("data").path("items")) {
                            String targetId = u.path("user_id").asText("");
                            if (targetId.isBlank()) continue;
                            String name = u.path("name").asText(targetId);
                            ((java.util.ArrayList<OrgNode>) dept.children()).add(
                                    new OrgNode(targetId, name, deptId, false, targetId, targetId, java.util.List.of()));
                        }
                        userPageToken = root.path("data").path("page_token").asText("");
                        if (!root.path("data").path("has_more").asBoolean(false)) break;
                    } while (!userPageToken.isBlank());
                } catch (com.kk2004.common.exception.BusinessException e) {
                    throw e;
                } catch (Exception e) {
                    throw new com.kk2004.common.exception.BusinessException("飞书部门用户拉取失败 [dept=" + deptId + "]: " + e.getMessage());
                }
            }
            log.info("org-structure: {} departments, {} roots", deptNodes.size(), roots.size());
            return roots;
        }

        /** Parse a Feishu response, throwing on non-zero code. Returns the root JSON node. */
        private JsonNode checkFeishuCode(String json) throws com.fasterxml.jackson.core.JsonProcessingException {
            JsonNode root = mapper.readTree(json);
            int code = root.path("code").asInt(-1);
            if (code != 0)
                throw new com.kk2004.common.exception.BusinessException("飞书请求失败 [FEISHU_" + code + "]：" + root.path("msg").asText("未知错误"));
            return root;
        }

        /** Normalize a mobile to E.164: assume China (+86) when no country code is present. */
        public static String normalizeMobile(String raw) {
            if (raw == null) return null;
            String digits = raw.replaceAll("[\\s+]", "");
            if (digits.startsWith("0086")) return "+86" + digits.substring(4);
            if (digits.startsWith("86")) return "+" + digits;
            if (raw.trim().startsWith("+")) return "+" + digits;
            return "+86" + digits;
        }
    }
}
