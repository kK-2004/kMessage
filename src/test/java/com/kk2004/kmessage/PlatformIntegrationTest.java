package com.kk2004.kmessage;

import com.fasterxml.jackson.databind.*;
import com.kk2004.kmessage.channel.*;
import com.kk2004.kmessage.delivery.DeliveryService;
import com.kk2004.kmessage.domain.*;
import com.kk2004.kmessage.domain.Entities.*;
import com.kk2004.kmessage.persistence.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.mock.web.MockHttpSession;
import java.util.Map;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:kmessage;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "kmessage.admin.username=test-admin",
        "kmessage.admin.password=test-password",
        "kmessage.runtime-role=api",
        "kmessage.worker.max-attempts=2"
})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
@ExtendWith(OutputCaptureExtension.class)
class PlatformIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired CallerRepository callers;
    @Autowired ChannelRepository channels;
    @Autowired GrantRepository grants;
    @Autowired TaskRepository tasks;
    @Autowired AttemptRepository attempts;
    @Autowired MessageRepository messages;
    @Autowired ChannelContactRepository contacts;
    @Autowired UserGroupMemberRepository groupMembers;
    @Autowired UserGroupRepository userGroups;
    @Autowired AppUserRepository appUsers;
    @Autowired ApiCredentialRepository credentials;
    @Autowired DeliveryService delivery;
    @Autowired com.kk2004.kmessage.channel.UserGroupService userGroupService;
    @MockBean ChannelAdapterRegistry adapters;

    ChannelAdapter adapter;
    String appKey;
    String appSecret;
    String callerId;
    String channelId;
    MockHttpSession adminSession;

    @BeforeEach
    void setup() throws Exception {
        attempts.deleteAll();
        tasks.deleteAll();
        messages.deleteAll();
        groupMembers.deleteAll();
        // user_groups has a self-referential parent_id FK: null out parents before delete to avoid constraint violations.
        userGroups.findAll().forEach(g -> { g.parentId = null; userGroups.save(g); });
        userGroups.deleteAll();
        appUsers.deleteAll();
        grants.deleteAll();
        contacts.deleteAll();
        channels.deleteAll();
        credentials.deleteAll();
        callers.deleteAll();
        adapter = mock(ChannelAdapter.class);
        doNothing().when(adapter).validate(anyString(), anyString());
        when(adapter.send(any(), any())).thenReturn(DeliveryResult.success("provider-ref"));
        when(adapters.require(any())).thenReturn(adapter);

        adminSession = (MockHttpSession) mvc.perform(post("/api/admin/session/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"test-admin\",\"password\":\"test-password\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
                .andReturn().getRequest().getSession(false);
        String body = mvc.perform(post("/api/admin/applications").session(adminSession)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"test-caller\"}"))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            JsonNode data = mapper.readTree(body).get("data");
            appKey = data.get("appKey").asText(); appSecret = data.get("appSecret").asText(); callerId = data.get("applicationId").asText();

            String channelBody = mvc.perform(post("/api/admin/channels").session(adminSession)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"type\":\"TELEGRAM\",\"name\":\"test-telegram\",\"enabled\":true,\"credentialRef\":\"env:TEST_TOKEN\",\"configJson\":\"{}\"}"))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            channelId = mapper.readTree(channelBody).get("data").get("id").asText();
            mvc.perform(put("/api/admin/applications/{caller}/channels/{channel}", callerId, channelId).session(adminSession))
                    .andExpect(status().isOk());
    }

    @Test
    void rejectsUnauthorizedAdminAndEnabledEmail() throws Exception {
        mvc.perform(get("/")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/admin/"));
        mvc.perform(get("/favicon.ico")).andExpect(status().isNotFound());
        mvc.perform(get("/api/admin/channels")).andExpect(status().isUnauthorized())
                .andExpect(content().contentType("application/json;charset=UTF-8"))
                .andExpect(jsonPath("$.message").value("请先登录管理后台"));
        mvc.perform(get("/admin").session(adminSession))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/admin/"));
        mvc.perform(get("/admin/")).andExpect(status().isOk());
        mvc.perform(get("/admin/").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("kMessage 管理后台")));
        mvc.perform(post("/api/admin/channels").session(adminSession).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"EMAIL\",\"name\":\"email\",\"enabled\":true,\"credentialRef\":\"env:EMAIL\",\"configJson\":\"{}\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void exposesChannelTypeMetadataPerOfficialDocs() throws Exception {
        String body = mvc.perform(get("/api/admin/channel-types").session(adminSession))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode types = mapper.readTree(body).get("data");
        java.util.Map<String, JsonNode> byType = new java.util.HashMap<>();
        for (JsonNode t : types) byType.put(t.get("type").asText(), t);

        // Only Telegram and Feishu are implemented; Email is reserved; DingTalk is gone entirely.
        Assertions.assertTrue(byType.containsKey("TELEGRAM"));
        Assertions.assertTrue(byType.containsKey("FEISHU"));
        Assertions.assertTrue(byType.containsKey("EMAIL"));
        Assertions.assertFalse(byType.containsKey("DINGTALK"));
        Assertions.assertTrue(byType.get("TELEGRAM").get("implemented").asBoolean());
        Assertions.assertTrue(byType.get("FEISHU").get("implemented").asBoolean());
        Assertions.assertFalse(byType.get("EMAIL").get("implemented").asBoolean());

        // Feishu setup guide references the official permission scope and multi-id targets.
        Assertions.assertTrue(byType.get("FEISHU").get("setupGuide").asText().contains("im:message:send_as_bot"));
        Assertions.assertTrue(byType.get("FEISHU").get("setupGuide").asText().contains("contact:user.employee_id:readonly"));
        Assertions.assertTrue(byType.get("FEISHU").get("setupGuide").asText().contains("contact:department.base:readonly"));
        Assertions.assertTrue(byType.get("FEISHU").get("targetHint").asText().contains("chat_id"));
        // Telegram setup guide accepts @channelusername.
        Assertions.assertTrue(byType.get("TELEGRAM").get("setupGuide").asText().contains("@channelusername"));
    }

    @Test
    void redactsCredentialAndSupportsIdempotentSubmissionAndDelivery() throws Exception {
        mvc.perform(get("/api/admin/channels").session(adminSession))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].credential").value(org.hamcrest.Matchers.startsWith("redacted:")));

        String request = mapper.writeValueAsString(Map.of("channelInstanceId", channelId, "target", "123", "text", "hello", "idempotencyKey", "idem-1"));
        String first = mvc.perform(post("/api/messages").header("X-App-Key", appKey).header("X-App-Secret", appSecret).contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isAccepted()).andExpect(jsonPath("$.data.status").value("ACCEPTED"))
                .andReturn().getResponse().getContentAsString();
        String messageId = mapper.readTree(first).get("data").get("id").asText();
        mvc.perform(post("/api/messages").header("X-App-Key", appKey).header("X-App-Secret", appSecret).contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isAccepted()).andExpect(jsonPath("$.data.id").value(messageId));
        Assertions.assertEquals(1, tasks.count());

        String taskId = delivery.claim(1).get(0);
        delivery.deliver(taskId);
        mvc.perform(get("/api/messages/{id}", messageId).header("X-App-Key", appKey).header("X-App-Secret", appSecret))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.message.status").value("DELIVERED"))
                .andExpect(jsonPath("$.data.attempts[0].result").value("SUCCESS"));
    }

    @Test
    void acceptsTypedCardMessagesFromApplications() throws Exception {
        String request = mapper.writeValueAsString(Map.of(
                "channelInstanceId", channelId,
                "target", "123",
                "message", Map.of(
                        "type", "CARD",
                        "card", Map.of(
                                "header", Map.of("title", Map.of("tag", "plain_text", "content", "告警")),
                                "elements", java.util.List.of(Map.of(
                                        "tag", "div",
                                        "text", Map.of("tag", "lark_md", "content", "**状态**：失败"))))),
                "idempotencyKey", "card-1"));
        String body = mvc.perform(post("/api/messages").header("X-App-Key", appKey).header("X-App-Secret", appSecret)
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        String messageId = mapper.readTree(body).get("data").get("id").asText();
        Message stored = messages.findById(messageId).orElseThrow();
        Assertions.assertEquals(MessageContentType.CARD, stored.contentType);
        Assertions.assertEquals("告警", stored.contentText);
        Assertions.assertTrue(stored.contentJson.contains("\"elements\""));
        verify(adapter).validate(eq("123"), eq(MessageContentType.CARD), anyString());
    }

    @Test
    void sendMessageAndCascadeDeleteChannel() throws Exception {
        // The admin "send message" endpoint drives the adapter directly (mocked here).
        mvc.perform(post("/api/admin/channels/{id}/send-message", channelId).session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"target\":\"123\",\"text\":\"hello from console\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.type").value("SUCCESS"));

        // Persist one message through the normal API + deliver it, so delete has history to clear.
        String request = mapper.writeValueAsString(Map.of("channelInstanceId", channelId, "target", "123", "text", "hi", "idempotencyKey", "del-1"));
        String body = mvc.perform(post("/api/messages").header("X-App-Key", appKey).header("X-App-Secret", appSecret)
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        String messageId = mapper.readTree(body).get("data").get("id").asText();
        delivery.deliver(delivery.claim(1).get(0));

        // delete-preview reports the granted app + the persisted message.
        String preview = mvc.perform(get("/api/admin/channels/{id}/delete-preview", channelId).session(adminSession))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode previewData = mapper.readTree(preview).get("data");
        Assertions.assertEquals(1, previewData.get("grantedAppCount").asInt());
        Assertions.assertTrue(previewData.get("appNames").get(0).asText().equals("test-caller"));
        Assertions.assertEquals(1, previewData.get("messageCount").asLong());

        // Hard delete cascades grants, messages, tasks, attempts, and contacts.
        mvc.perform(delete("/api/admin/channels/{id}", channelId).session(adminSession))
                .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true));
        Assertions.assertTrue(channels.findById(channelId).isEmpty());
        Assertions.assertEquals(0, grants.findByChannelInstanceId(channelId).size());
        Assertions.assertTrue(messages.findById(messageId).isEmpty());
        Assertions.assertEquals(0, tasks.count());
        Assertions.assertTrue(attempts.findByMessageIdOrderByAttemptNumberAsc(messageId).isEmpty());
        Assertions.assertEquals(0, contacts.findByChannelInstanceIdOrderByLastSeenAtDesc(channelId).size());
    }

    @Test
    void syncsContactsIncrementallyAndEditsChannel() throws Exception {
        // First fetch: adapter returns two contacts → both persisted, returned merged.
        when(adapter.listContacts(any())).thenReturn(java.util.List.of(
                new ContactOption("111", "Alice", "user"),
                new ContactOption("-222", "Ops", "group")));
        String firstBody = mvc.perform(get("/api/admin/channels/{id}/contacts", channelId).session(adminSession))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        Assertions.assertEquals(2, mapper.readTree(firstBody).get("data").size());
        Assertions.assertEquals(2, contacts.findByChannelInstanceIdOrderByLastSeenAtDesc(channelId).size());

        // Second fetch: one known (label changed) + one new → no duplicate, label updated, count grows to 3.
        when(adapter.listContacts(any())).thenReturn(java.util.List.of(
                new ContactOption("111", "Alice Renamed", "user"),
                new ContactOption("333", "Bob", "user")));
        String secondBody = mvc.perform(get("/api/admin/channels/{id}/contacts", channelId).session(adminSession))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        Assertions.assertEquals(3, mapper.readTree(secondBody).get("data").size());
        Assertions.assertEquals(3, contacts.findByChannelInstanceIdOrderByLastSeenAtDesc(channelId).size());
        Assertions.assertEquals("Alice Renamed",
                contacts.findByChannelInstanceIdAndTargetId(channelId, "111").orElseThrow().label);

        // Adapter returns nothing on a later call → persisted history still served (beyond 48h window).
        when(adapter.listContacts(any())).thenReturn(java.util.List.of());
        String thirdBody = mvc.perform(get("/api/admin/channels/{id}/contacts", channelId).session(adminSession))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        Assertions.assertEquals(3, mapper.readTree(thirdBody).get("data").size());

        // Edit channel: rename succeeds.
        mvc.perform(put("/api/admin/channels/{id}", channelId).session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"renamed-channel\",\"enabled\":true,\"configJson\":\"{}\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.name").value("renamed-channel"));
        Assertions.assertTrue(channels.findById(channelId).orElseThrow().enabled);

        // Create a second channel, then renaming the first to its name is rejected.
        mvc.perform(post("/api/admin/channels").session(adminSession).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"TELEGRAM\",\"name\":\"other\",\"enabled\":false,\"credentialRef\":\"env:OTHER\",\"configJson\":\"{}\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true));
        mvc.perform(put("/api/admin/channels/{id}", channelId).session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"other\",\"enabled\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(false));

        // Edit with credentialRef omitted (null) → existing credential unchanged.
        String originalCredential = channels.findById(channelId).orElseThrow().credentialRef;
        mvc.perform(put("/api/admin/channels/{id}", channelId).session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.enabled").value(false));
        Assertions.assertEquals(originalCredential, channels.findById(channelId).orElseThrow().credentialRef);
    }

    @Test
    void importsUsersManagesGroupsAndFansOutGroupSend() throws Exception {
        // Import users: mock the Feishu-style lookupUsers to resolve 2 of 3 phones.
        when(adapter.lookupUsers(any(), any(), any())).thenReturn(java.util.List.of(
                new ResolvedUser("ou_alice", "13800000001", null, "Alice"),
                new ResolvedUser("ou_bob", "13800000002", null, "Bob")));

        // Users are channel-level: importing on a non-Feishu channel is rejected.
        mvc.perform(post("/api/admin/channels/{c}/users/import", channelId)
                        .session(adminSession).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mobiles\":[\"13800000001\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("仅飞书渠道")));

        String feishuChannelBody = mvc.perform(post("/api/admin/channels").session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"FEISHU\",\"name\":\"test-feishu\",\"enabled\":true,\"credentialRef\":\"env:FEISHU_TOKEN\",\"configJson\":\"{}\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String feishuChannelId = mapper.readTree(feishuChannelBody).get("data").get("id").asText();

        // Grant the app to the Feishu channel (needed for sending, not for user import).
        mvc.perform(put("/api/admin/applications/{caller}/channels/{channel}", callerId, feishuChannelId).session(adminSession))
                .andExpect(status().isOk());

        String importBody = mvc.perform(post("/api/admin/channels/{c}/users/import", feishuChannelId)
                        .session(adminSession).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mobiles\":[\"13800000001\",\"13800000002\",\"13800000003\"]}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode importData = mapper.readTree(importBody).get("data");
        Assertions.assertEquals(2, importData.get("importedCount").asInt());
        Assertions.assertEquals(1, importData.get("unresolved").size());
        Assertions.assertEquals("13800000003", importData.get("unresolved").get(0).asText());
        // app_users persisted at the channel level (no callerId).
        Assertions.assertEquals(2, appUsers.findByChannelInstanceId(feishuChannelId).size());

        // The admin send picker merges imported Feishu users even when Feishu chat listing
        // returns no bot groups.
        when(adapter.listContacts(any())).thenReturn(java.util.List.of());
        JsonNode sendTargets = mapper.readTree(mvc.perform(get("/api/admin/channels/{c}/contacts", feishuChannelId)
                        .session(adminSession))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("data");
        java.util.Map<String, JsonNode> sendTargetsById = new java.util.HashMap<>();
        for (JsonNode target : sendTargets) sendTargetsById.put(target.get("id").asText(), target);
        Assertions.assertEquals("Alice", sendTargetsById.get("ou_alice").get("label").asText());
        Assertions.assertEquals("user", sendTargetsById.get("ou_alice").get("type").asText());
        Assertions.assertEquals("Bob", sendTargetsById.get("ou_bob").get("label").asText());

        // Two resolved user ids.
        JsonNode users = mapper.readTree(mvc.perform(get("/api/admin/channels/{c}/users", feishuChannelId)
                        .session(adminSession)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("data");
        String aliceId = users.get(0).get("id").asText();
        String bobId = users.get(1).get("id").asText();

        // Group tree: root "全员" + child "核心".
        String rootBody = mvc.perform(post("/api/admin/applications/{a}/channels/{c}/groups", callerId, feishuChannelId)
                        .session(adminSession).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"全员\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String rootId = mapper.readTree(rootBody).get("data").get("id").asText();
        String childBody = mvc.perform(post("/api/admin/applications/{a}/channels/{c}/groups", callerId, feishuChannelId)
                        .session(adminSession).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"核心\",\"parentId\":\"" + rootId + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String childId = mapper.readTree(childBody).get("data").get("id").asText();

        // Add both users to child group; only Alice to root.
        mvc.perform(post("/api/admin/applications/{a}/channels/{c}/groups/{g}/members", callerId, feishuChannelId, childId)
                        .session(adminSession).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userIds\":[\"" + aliceId + "\",\"" + bobId + "\"]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true));
        mvc.perform(post("/api/admin/applications/{a}/channels/{c}/groups/{g}/members", callerId, feishuChannelId, rootId)
                        .session(adminSession).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userIds\":[\"" + aliceId + "\"]}"))
                .andExpect(status().isOk());

        // Cycle prevention: moving root under its own child is rejected.
        mvc.perform(put("/api/admin/applications/{a}/channels/{c}/groups/{g}", callerId, feishuChannelId, rootId)
                        .session(adminSession).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"parentId\":\"" + childId + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(false));

        // Send to child group (2 members) → fans out to 2 messages.
        String groupSend = mapper.writeValueAsString(Map.of(
                "channelInstanceId", feishuChannelId, "groupId", childId,
                "text", "hi-group", "idempotencyKey", "grp-1"));
        String groupBody = mvc.perform(post("/api/messages").header("X-App-Key", appKey).header("X-App-Secret", appSecret)
                        .contentType(MediaType.APPLICATION_JSON).content(groupSend))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        JsonNode batch = mapper.readTree(groupBody).get("data");
        Assertions.assertTrue(batch.has("totalMessages"));
        Assertions.assertEquals(2, batch.get("totalMessages").asInt());
        Assertions.assertEquals(2, batch.get("messages").size());
        // Two delivery tasks created, each message target is one of the open_ids.
        Assertions.assertEquals(2, tasks.count());

        // Send to a single user → one message (single-target shape, not batch).
        String userSend = mapper.writeValueAsString(Map.of(
                "channelInstanceId", feishuChannelId, "userId", aliceId,
                "text", "hi-alice", "idempotencyKey", "usr-1"));
        String userBody = mvc.perform(post("/api/messages").header("X-App-Key", appKey).header("X-App-Secret", appSecret)
                        .contentType(MediaType.APPLICATION_JSON).content(userSend))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        Assertions.assertTrue(mapper.readTree(userBody).get("data").has("status"));
        Assertions.assertEquals(3, tasks.count());
    }

    @Test
    void addOrgMembersUpsertsUsersAndAddsToGroup() throws Exception {
        // Create a group for the app+channel.
        String groupBody = mvc.perform(post("/api/admin/applications/{a}/channels/{c}/groups", callerId, channelId)
                        .session(adminSession).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"org-group\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String groupId = mapper.readTree(groupBody).get("data").get("id").asText();

        // Add two org users (not yet in app_users) via the org-members endpoint.
        mvc.perform(post("/api/admin/applications/{a}/channels/{c}/groups/{g}/org-members", callerId, channelId, groupId)
                        .session(adminSession).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targets\":[{\"targetId\":\"ou_org1\",\"name\":\"王五\"},{\"targetId\":\"ou_org2\",\"name\":\"赵六\"}]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true));

        // Both users were upserted into app_users at the channel level.
        Assertions.assertEquals(2, appUsers.findByChannelInstanceId(channelId).size());
        // The group now has 2 members, expandable to their target ids.
        Assertions.assertEquals(2, userGroupService.expandGroup(callerId, channelId, groupId).size());
    }

    @Test
    void channelContactsAutoMergeIntoAppUserList() throws Exception {
        // Simulate getUpdates/im-chats having persisted two channel-level contacts for this channel.
        Entities.ChannelContact c1 = new Entities.ChannelContact();
        c1.channelInstanceId = channelId; c1.targetId = "999"; c1.label = "Telegram User"; c1.contactType = "user";
        Entities.ChannelContact c2 = new Entities.ChannelContact();
        c2.channelInstanceId = channelId; c2.targetId = "-888"; c2.label = "Ops Group"; c2.contactType = "group";
        contacts.save(c1); contacts.save(c2);

        // Channel-level user list merges channel contacts into app_users (no application binding needed).
        String body = mvc.perform(get("/api/admin/channels/{c}/users", channelId)
                        .session(adminSession))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode users = mapper.readTree(body).get("data");
        Assertions.assertEquals(2, users.size());
        // Contacts are merged into app_users at the channel level and deduplicated on repeat.
        mvc.perform(get("/api/admin/channels/{c}/users", channelId).session(adminSession))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(2));
        Assertions.assertEquals(2, appUsers.findByChannelInstanceId(channelId).size());
    }
    @Test
    void rejectsMissingAppCredentialsAndConflictingIdempotency() throws Exception {
        mvc.perform(post("/api/messages").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value("无效 appKey 或 appSecret"));
        mvc.perform(post("/api/messages").header("X-App-Key", appKey).header("X-App-Secret", "wrong")
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized());
        String first = mapper.writeValueAsString(Map.of("channelInstanceId", channelId, "target", "1", "text", "one", "idempotencyKey", "conflict"));
        String second = mapper.writeValueAsString(Map.of("channelInstanceId", channelId, "target", "1", "text", "two", "idempotencyKey", "conflict"));
        mvc.perform(post("/api/messages").header("X-App-Key", appKey).header("X-App-Secret", appSecret).contentType(MediaType.APPLICATION_JSON).content(first)).andExpect(status().isAccepted());
        mvc.perform(post("/api/messages").header("X-App-Key", appKey).header("X-App-Secret", appSecret).contentType(MediaType.APPLICATION_JSON).content(second))
                .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void rotatesAppSecretAndInvalidatesPreviousSecret() throws Exception {
        String rotated = mvc.perform(post("/api/admin/applications/{id}/rotate", callerId).session(adminSession))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode value = mapper.readTree(rotated).get("data");
        String request = mapper.writeValueAsString(Map.of("channelInstanceId", channelId, "target", "1", "text", "one", "idempotencyKey", "rotate"));
        mvc.perform(post("/api/messages").header("X-App-Key", appKey).header("X-App-Secret", appSecret)
                .contentType(MediaType.APPLICATION_JSON).content(request)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/messages").header("X-App-Key", value.get("appKey").asText()).header("X-App-Secret", value.get("appSecret").asText())
                .contentType(MediaType.APPLICATION_JSON).content(request)).andExpect(status().isAccepted());
    }

    @Test
    void deletesApplicationAndInvalidatesCredentials() throws Exception {
        mvc.perform(delete("/api/admin/applications/{id}", callerId).session(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
        mvc.perform(get("/api/admin/applications").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
        mvc.perform(post("/api/messages").header("X-App-Key", appKey).header("X-App-Secret", appSecret)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/admin/applications").session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"test-caller\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void propagatesTraceAndExhaustsTransientRetries() throws Exception {
        when(adapter.send(any(), any())).thenAnswer(invocation -> {
            Assertions.assertEquals("trace-123", com.kk2004.common.web.RequestContext.getTraceId());
            return DeliveryResult.transientFailure("HTTP_429", "rate limited");
        });
        String request = mapper.writeValueAsString(Map.of("channelInstanceId", channelId, "target", "123", "text", "hello", "idempotencyKey", "retry"));
        String body = mvc.perform(post("/api/messages").header("X-App-Key", appKey).header("X-App-Secret", appSecret).header("X-Trace-Id", "trace-123")
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        String messageId = mapper.readTree(body).get("data").get("id").asText();
        delivery.deliver(delivery.claim(1).get(0));
        Assertions.assertEquals(MessageStatus.RETRYING, messages.findById(messageId).orElseThrow().status);
        DeliveryTask task = tasks.findAll().get(0);
        task.nextAttemptAt = java.time.Instant.now().minusSeconds(1);
        tasks.save(task);
        delivery.deliver(delivery.claim(1).get(0));
        Assertions.assertEquals(MessageStatus.FAILED, messages.findById(messageId).orElseThrow().status);
        Assertions.assertEquals(2, attempts.findByMessageIdOrderByAttemptNumberAsc(messageId).size());
    }

    @Test
    void isolatesMessageQueriesAndValidatesTelegramTarget() throws Exception {
        doThrow(new IllegalArgumentException("Telegram target 必须是 chat id"))
                .when(adapter).validate(eq("not-a-chat-id"), any(), anyString());
        String request = mapper.writeValueAsString(Map.of("channelInstanceId", channelId, "target", "123", "text", "hello", "idempotencyKey", "isolation"));
        String body = mvc.perform(post("/api/messages").header("X-App-Key", appKey).header("X-App-Secret", appSecret).contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        String messageId = mapper.readTree(body).get("data").get("id").asText();

        String otherBody = mvc.perform(post("/api/admin/applications").session(adminSession).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"other-caller\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode other = mapper.readTree(otherBody).get("data");
        mvc.perform(get("/api/messages/{id}", messageId).header("X-App-Key", other.get("appKey").asText())
                        .header("X-App-Secret", other.get("appSecret").asText()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(false));

        String invalid = mapper.writeValueAsString(Map.of("channelInstanceId", channelId, "target", "not-a-chat-id", "text", "hello", "idempotencyKey", "invalid"));
        mvc.perform(post("/api/messages").header("X-App-Key", appKey).header("X-App-Secret", appSecret).contentType(MediaType.APPLICATION_JSON).content(invalid))
                .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void preventsDuplicateClaimsAndRecoversExpiredLease() throws Exception {
        String request = mapper.writeValueAsString(Map.of("channelInstanceId", channelId, "target", "123", "text", "hello", "idempotencyKey", "lease"));
        mvc.perform(post("/api/messages").header("X-App-Key", appKey).header("X-App-Secret", appSecret).contentType(MediaType.APPLICATION_JSON).content(request)).andExpect(status().isAccepted());
        String taskId = delivery.claim(1).get(0);
        Assertions.assertTrue(delivery.claim(1).isEmpty());
        DeliveryTask task = tasks.findById(taskId).orElseThrow();
        task.workerId = "dead-worker"; task.leaseUntil = java.time.Instant.now().minusSeconds(1);
        tasks.save(task);
        Assertions.assertEquals(taskId, delivery.claim(1).get(0));
    }

    @Test
    void permanentFailureDoesNotLeakSensitiveData(CapturedOutput output) throws Exception {
        when(adapter.send(any(), any())).thenReturn(DeliveryResult.permanentFailure("HTTP_400", "sanitized-provider-error"));
        String request = mapper.writeValueAsString(Map.of("channelInstanceId", channelId, "target", "123", "text", "super-secret-content", "idempotencyKey", "permanent"));
        String body = mvc.perform(post("/api/messages").header("X-App-Key", appKey).header("X-App-Secret", appSecret).contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        String messageId = mapper.readTree(body).get("data").get("id").asText();
        delivery.deliver(delivery.claim(1).get(0));
        Assertions.assertEquals(MessageStatus.FAILED, messages.findById(messageId).orElseThrow().status);
        DeliveryAttempt attempt = attempts.findByMessageIdOrderByAttemptNumberAsc(messageId).get(0);
        Assertions.assertEquals("sanitized-provider-error", attempt.diagnostic);
        Assertions.assertFalse(output.getAll().contains("super-secret-content"));
        Assertions.assertFalse(output.getAll().contains("env:TEST_TOKEN"));
    }
}
