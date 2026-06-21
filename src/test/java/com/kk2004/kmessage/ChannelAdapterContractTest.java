package com.kk2004.kmessage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kk2004.kmessage.channel.*;
import com.kk2004.kmessage.domain.*;
import com.kk2004.kmessage.domain.Entities.*;
import java.util.List;
import org.junit.jupiter.api.*;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class ChannelAdapterContractTest {
    RestClient.Builder builder;
    MockRestServiceServer server;
    ObjectMapper mapper = new ObjectMapper();
    SecretProvider secret = reference -> {
        if (reference.equals("env:TOKEN")) return "test-token";
        if (reference.equals("env:FEISHU_CREDS")) return "test-app-id:test-app-secret";
        return "https://provider.test/webhook";
    };
    Message message;
    ChannelInstance channel;

    // Feishu returns `expire` as an ABSOLUTE epoch-second expiry timestamp; use a far-future value.
    static final long FEISHU_EXPIRE_AT = java.time.Instant.now().getEpochSecond() + 7200;
    static final String FEISHU_TOKEN_SUCCESS = "{\"code\":0,\"msg\":\"ok\",\"tenant_access_token\":\"t-test-token\",\"expire\":" + FEISHU_EXPIRE_AT + "}";
    static final String FEISHU_MSG_SUCCESS = "{\"code\":0,\"msg\":\"success\",\"data\":{\"message_id\":\"om_test123\"}}";

    @BeforeEach
    void setup() {
        builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        message = new Message(); message.targetValue = "123"; message.contentText = "hello";
        channel = new ChannelInstance(); channel.credentialRef = "env:WEBHOOK";
    }

    @Test
    void classifiesSuccessfulRequestsForAllAdapters() {
        message.targetValue = "ou_test-open-id";
        server.expect(once(), requestTo("https://api.telegram.org/bottest-token/sendMessage")).andExpect(method(HttpMethod.POST)).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo("https://open.feishu.cn/open-apis/auth/v3/tenant_access_token/internal")).andExpect(method(HttpMethod.POST)).andRespond(withSuccess(FEISHU_TOKEN_SUCCESS, MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo("https://open.feishu.cn/open-apis/im/v1/messages?receive_id_type=open_id")).andExpect(method(HttpMethod.POST)).andRespond(withSuccess(FEISHU_MSG_SUCCESS, MediaType.APPLICATION_JSON));
        RestClient client = builder.build();
        channel.credentialRef = "env:TOKEN";
        Assertions.assertEquals(DeliveryResult.Type.SUCCESS, new HttpChannelAdapters.Telegram(client, secret, mapper).send(channel, message).type());
        channel.credentialRef = "env:FEISHU_CREDS";
        Assertions.assertEquals(DeliveryResult.Type.SUCCESS, new HttpChannelAdapters.Feishu(client, secret, mapper).send(channel, message).type());
        server.verify();
    }

    @Test
    void classifiesHttpFailures() {
        message.targetValue = "ou_test-open-id";
        server.expect(once(), requestTo("https://open.feishu.cn/open-apis/auth/v3/tenant_access_token/internal")).andExpect(method(HttpMethod.POST)).andRespond(withSuccess(FEISHU_TOKEN_SUCCESS, MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo("https://open.feishu.cn/open-apis/im/v1/messages?receive_id_type=open_id")).andExpect(method(HttpMethod.POST)).andRespond(withTooManyRequests());
        RestClient client = builder.build();
        channel.credentialRef = "env:FEISHU_CREDS";
        Assertions.assertEquals(DeliveryResult.Type.TRANSIENT_FAILURE, new HttpChannelAdapters.Feishu(client, secret, mapper).send(channel, message).type());
        server.verify();
    }

    @Test
    void feishuCachesTokenAcrossSends() {
        message.targetValue = "ou_test-open-id";
        server.expect(once(), requestTo("https://open.feishu.cn/open-apis/auth/v3/tenant_access_token/internal")).andExpect(method(HttpMethod.POST)).andRespond(withSuccess(FEISHU_TOKEN_SUCCESS, MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo("https://open.feishu.cn/open-apis/im/v1/messages?receive_id_type=open_id")).andExpect(method(HttpMethod.POST)).andRespond(withSuccess(FEISHU_MSG_SUCCESS, MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo("https://open.feishu.cn/open-apis/im/v1/messages?receive_id_type=open_id")).andExpect(method(HttpMethod.POST)).andRespond(withSuccess(FEISHU_MSG_SUCCESS, MediaType.APPLICATION_JSON));
        RestClient client = builder.build();
        channel.credentialRef = "env:FEISHU_CREDS";
        HttpChannelAdapters.Feishu feishu = new HttpChannelAdapters.Feishu(client, secret, mapper);
        Assertions.assertEquals(DeliveryResult.Type.SUCCESS, feishu.send(channel, message).type());
        Assertions.assertEquals(DeliveryResult.Type.SUCCESS, feishu.send(channel, message).type());
        server.verify();
    }

    @Test
    void feishuRetriesOnTokenExpired() {
        message.targetValue = "ou_test-open-id";
        String expiredResponse = "{\"code\":99991401,\"msg\":\"token expired\"}";
        server.expect(once(), requestTo("https://open.feishu.cn/open-apis/auth/v3/tenant_access_token/internal")).andExpect(method(HttpMethod.POST)).andRespond(withSuccess(FEISHU_TOKEN_SUCCESS, MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo("https://open.feishu.cn/open-apis/im/v1/messages?receive_id_type=open_id")).andExpect(method(HttpMethod.POST)).andRespond(withSuccess(expiredResponse, MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo("https://open.feishu.cn/open-apis/auth/v3/tenant_access_token/internal")).andExpect(method(HttpMethod.POST)).andRespond(withSuccess(FEISHU_TOKEN_SUCCESS, MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo("https://open.feishu.cn/open-apis/im/v1/messages?receive_id_type=open_id")).andExpect(method(HttpMethod.POST)).andRespond(withSuccess(FEISHU_MSG_SUCCESS, MediaType.APPLICATION_JSON));
        RestClient client = builder.build();
        channel.credentialRef = "env:FEISHU_CREDS";
        Assertions.assertEquals(DeliveryResult.Type.SUCCESS, new HttpChannelAdapters.Feishu(client, secret, mapper).send(channel, message).type());
        server.verify();
    }

    @Test
    void feishuDerivesReceiveIdTypeFromTargetForm() {
        RestClient client = builder.build();
        HttpChannelAdapters.Feishu feishu = new HttpChannelAdapters.Feishu(client, secret, mapper);
        Assertions.assertEquals("open_id", feishu.resolveReceiveIdType(channel, "ou_user"));
        Assertions.assertEquals("union_id", feishu.resolveReceiveIdType(channel, "on_user"));
        Assertions.assertEquals("chat_id", feishu.resolveReceiveIdType(channel, "oc_group"));
        Assertions.assertEquals("chat_id", feishu.resolveReceiveIdType(channel, "123456"));
        Assertions.assertEquals("email", feishu.resolveReceiveIdType(channel, "user@example.com"));
        Assertions.assertEquals("user_id", feishu.resolveReceiveIdType(channel, "internal-id"));
    }

    @Test
    void feishuConfigReceiveIdTypeOverridesDerivation() {
        channel.configJson = "{\"receive_id_type\":\"user_id\"}";
        RestClient client = builder.build();
        HttpChannelAdapters.Feishu feishu = new HttpChannelAdapters.Feishu(client, secret, mapper);
        Assertions.assertEquals("user_id", feishu.resolveReceiveIdType(channel, "ou_user"));
    }

    @Test
    void feishuSendsWithChatIdForGroupTarget() {
        message.targetValue = "oc_test-group";
        server.expect(once(), requestTo("https://open.feishu.cn/open-apis/auth/v3/tenant_access_token/internal")).andExpect(method(HttpMethod.POST)).andRespond(withSuccess(FEISHU_TOKEN_SUCCESS, MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo("https://open.feishu.cn/open-apis/im/v1/messages?receive_id_type=chat_id")).andExpect(method(HttpMethod.POST)).andRespond(withSuccess(FEISHU_MSG_SUCCESS, MediaType.APPLICATION_JSON));
        RestClient client = builder.build();
        channel.credentialRef = "env:FEISHU_CREDS";
        Assertions.assertEquals(DeliveryResult.Type.SUCCESS, new HttpChannelAdapters.Feishu(client, secret, mapper).send(channel, message).type());
        server.verify();
    }

    @Test
    void feishuSendsInteractiveCardMessages() throws Exception {
        message.targetValue = "ou_test-open-id";
        message.contentType = MessageContentType.CARD;
        String cardJson = mapper.writeValueAsString(java.util.Map.of(
                "config", java.util.Map.of("wide_screen_mode", true),
                "header", java.util.Map.of(
                        "template", "blue",
                        "title", java.util.Map.of("tag", "plain_text", "content", "通知标题")),
                "elements", java.util.List.of(java.util.Map.of(
                        "tag", "div",
                        "text", java.util.Map.of("tag", "lark_md", "content", "**状态**：成功")))));
        message.contentJson = cardJson;
        String expectedBody = mapper.writeValueAsString(java.util.Map.of(
                "receive_id", "ou_test-open-id",
                "msg_type", "interactive",
                "content", cardJson));
        server.expect(once(), requestTo("https://open.feishu.cn/open-apis/auth/v3/tenant_access_token/internal")).andExpect(method(HttpMethod.POST)).andRespond(withSuccess(FEISHU_TOKEN_SUCCESS, MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo("https://open.feishu.cn/open-apis/im/v1/messages?receive_id_type=open_id"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json(expectedBody))
                .andRespond(withSuccess(FEISHU_MSG_SUCCESS, MediaType.APPLICATION_JSON));
        RestClient client = builder.build();
        channel.credentialRef = "env:FEISHU_CREDS";
        Assertions.assertEquals(DeliveryResult.Type.SUCCESS, new HttpChannelAdapters.Feishu(client, secret, mapper).send(channel, message).type());
        server.verify();
    }

    @Test
    void feishuTreatsExpireAsAbsoluteTimestampAndRefreshes() {
        // Feishu's `expire` is an ABSOLUTE epoch-second expiry. A past value must force a token
        // re-fetch on every send (proving it is NOT cached for ~54 years as plusSeconds would do).
        long pastExpire = java.time.Instant.now().getEpochSecond() - 60;
        String expiredTokenResponse = "{\"code\":0,\"msg\":\"ok\",\"tenant_access_token\":\"t-old\",\"expire\":" + pastExpire + "}";
        message.targetValue = "ou_test-open-id";
        server.expect(once(), requestTo("https://open.feishu.cn/open-apis/auth/v3/tenant_access_token/internal")).andExpect(method(HttpMethod.POST)).andRespond(withSuccess(expiredTokenResponse, MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo("https://open.feishu.cn/open-apis/im/v1/messages?receive_id_type=open_id")).andExpect(method(HttpMethod.POST)).andRespond(withSuccess(FEISHU_MSG_SUCCESS, MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo("https://open.feishu.cn/open-apis/auth/v3/tenant_access_token/internal")).andExpect(method(HttpMethod.POST)).andRespond(withSuccess(expiredTokenResponse, MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo("https://open.feishu.cn/open-apis/im/v1/messages?receive_id_type=open_id")).andExpect(method(HttpMethod.POST)).andRespond(withSuccess(FEISHU_MSG_SUCCESS, MediaType.APPLICATION_JSON));
        RestClient client = builder.build();
        channel.credentialRef = "env:FEISHU_CREDS";
        HttpChannelAdapters.Feishu feishu = new HttpChannelAdapters.Feishu(client, secret, mapper);
        Assertions.assertEquals(DeliveryResult.Type.SUCCESS, feishu.send(channel, message).type());
        Assertions.assertEquals(DeliveryResult.Type.SUCCESS, feishu.send(channel, message).type());
        server.verify();
    }

    @Test
    void telegramAcceptsNumericAndUsernameTargets() {
        RestClient client = builder.build();
        HttpChannelAdapters.Telegram telegram = new HttpChannelAdapters.Telegram(client, secret, mapper);
        Assertions.assertTrue(HttpChannelAdapters.Telegram.isSupportedTarget("123"));
        Assertions.assertTrue(HttpChannelAdapters.Telegram.isSupportedTarget("-1001234567890"));
        Assertions.assertTrue(HttpChannelAdapters.Telegram.isSupportedTarget("@mychannel"));
        Assertions.assertFalse(HttpChannelAdapters.Telegram.isSupportedTarget("not-a-chat-id"));
        Assertions.assertFalse(HttpChannelAdapters.Telegram.isSupportedTarget("@ab"));
        Assertions.assertDoesNotThrow(() -> telegram.validate("123", "{}"));
        Assertions.assertDoesNotThrow(() -> telegram.validate("@mychannel", "{}"));
        Assertions.assertThrows(IllegalArgumentException.class, () -> telegram.validate("not-a-chat-id", "{}"));
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> telegram.validate("123", MessageContentType.CARD, "{}"));
    }

    @Test
    void telegramRejectsOversizedText() {
        message.contentText = "a".repeat(4097);
        RestClient client = builder.build();
        DeliveryResult result = new HttpChannelAdapters.Telegram(client, secret, mapper).send(channel, message);
        Assertions.assertEquals(DeliveryResult.Type.PERMANENT_FAILURE, result.type());
        Assertions.assertEquals("TEXT_TOO_LONG", result.errorCode());
    }

    @Test
    void telegramListsAndDeduplicatesContacts() {
        channel.credentialRef = "env:TOKEN";
        // Two updates from the same chat + one private chat + one group; the group appears twice.
        String updates = "{\"ok\":true,\"result\":["
                + "{\"message\":{\"chat\":{\"id\":111,\"type\":\"private\",\"first_name\":\"Alice\",\"username\":\"alice\"}}},"
                + "{\"message\":{\"chat\":{\"id\":111,\"type\":\"private\",\"first_name\":\"Alice\",\"username\":\"alice\"}}},"
                + "{\"message\":{\"chat\":{\"id\":-222,\"type\":\"group\",\"title\":\"Ops\"}}},"
                + "{\"my_chat_member\":{\"chat\":{\"id\":-222,\"type\":\"group\",\"title\":\"Ops\"}}}"
                + "]}";
        server.expect(once(), requestTo("https://api.telegram.org/bottest-token/getUpdates?offset=-1"))
                .andExpect(method(HttpMethod.GET)).andRespond(withSuccess(updates, MediaType.APPLICATION_JSON));
        RestClient client = builder.build();
        List<ContactOption> contacts = new HttpChannelAdapters.Telegram(client, secret, mapper).listContacts(channel);
        server.verify();
        Assertions.assertEquals(2, contacts.size());
        Assertions.assertEquals("111", contacts.get(0).id());
        Assertions.assertEquals("Alice", contacts.get(0).label());
        Assertions.assertEquals("user", contacts.get(0).type());
        Assertions.assertEquals("-222", contacts.get(1).id());
        Assertions.assertEquals("Ops", contacts.get(1).label());
        Assertions.assertEquals("group", contacts.get(1).type());
    }

    @Test
    void feishuListsBotGroups() {
        channel.credentialRef = "env:FEISHU_CREDS";
        String chats = "{\"code\":0,\"msg\":\"success\",\"data\":{\"items\":["
                + "{\"chat_id\":\"oc_group1\",\"name\":\"告警群\"},"
                + "{\"chat_id\":\"oc_group2\",\"name\":\"运维群\"}"
                + "]}}";
        server.expect(once(), requestTo("https://open.feishu.cn/open-apis/auth/v3/tenant_access_token/internal")).andExpect(method(HttpMethod.POST)).andRespond(withSuccess(FEISHU_TOKEN_SUCCESS, MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo("https://open.feishu.cn/open-apis/im/v1/chats?user_id_type=open_id&page_size=50")).andExpect(method(HttpMethod.GET)).andRespond(withSuccess(chats, MediaType.APPLICATION_JSON));
        RestClient client = builder.build();
        List<ContactOption> contacts = new HttpChannelAdapters.Feishu(client, secret, mapper).listContacts(channel);
        server.verify();
        Assertions.assertEquals(2, contacts.size());
        Assertions.assertEquals("oc_group1", contacts.get(0).id());
        Assertions.assertEquals("告警群", contacts.get(0).label());
        Assertions.assertEquals("group", contacts.get(0).type());
    }

    @Test
    void defaultListContactsIsEmptyForUnknownAdapters() {
        // Base adapter has no listContacts override; default returns empty.
        ChannelAdapter stub = new ChannelAdapter() {
            public ChannelType type() { return ChannelType.TELEGRAM; }
            public DeliveryResult send(ChannelInstance c, Message m) { return DeliveryResult.success("ok"); }
        };
        Assertions.assertTrue(stub.listContacts(channel).isEmpty());
    }

    @Test
    void feishuNormalizesMobileToE164ForLookup() {
        // Feishu requires E.164 (with country code). Local numbers get +86.
        Assertions.assertEquals("+8613800000001", HttpChannelAdapters.Feishu.normalizeMobile("13800000001"));
        Assertions.assertEquals("+8613800000001", HttpChannelAdapters.Feishu.normalizeMobile("8613800000001"));
        Assertions.assertEquals("+8613800000001", HttpChannelAdapters.Feishu.normalizeMobile("008613800000001"));
        Assertions.assertEquals("+8613800000001", HttpChannelAdapters.Feishu.normalizeMobile("+86 138 0000 0001"));
        // Non-China numbers keep their explicit + prefix.
        Assertions.assertEquals("+15551234567", HttpChannelAdapters.Feishu.normalizeMobile("+15551234567"));
    }

    @Test
    void feishuLookupUsersResolvesMobilesAndEmails() {
        channel.credentialRef = "env:FEISHU_CREDS";
        // Feishu returns the ID under "user_id" (its value is the open_id when user_id_type=open_id),
        // NOT "open_id". Unmatched inputs return an empty user_id.
        String resp = "{\"code\":0,\"msg\":\"success\",\"data\":{\"user_list\":["
                + "{\"mobile\":\"+8613800000001\",\"user_id\":\"ou_alice\"},"
                + "{\"mobile\":\"+8613800000002\",\"user_id\":\"\"}" // unmatched → empty
                + "]}}";
        server.expect(once(), requestTo("https://open.feishu.cn/open-apis/auth/v3/tenant_access_token/internal")).andExpect(method(HttpMethod.POST)).andRespond(withSuccess(FEISHU_TOKEN_SUCCESS, MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://open.feishu.cn/open-apis/contact/v3/users/batch_get_id?user_id_type=open_id")).andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(resp, MediaType.APPLICATION_JSON));
        RestClient client = builder.build();
        List<ResolvedUser> resolved = new HttpChannelAdapters.Feishu(client, secret, mapper)
                .lookupUsers(channel, java.util.List.of("13800000001", "13800000002"), null);
        server.verify();
        Assertions.assertEquals(1, resolved.size());
        Assertions.assertEquals("ou_alice", resolved.get(0).targetId());
    }

    @Test
    void feishuLookupUsersSurfacesProviderErrorInsteadOfSilentEmpty() {
        // Non-zero code (e.g. permission denied 99991672) must be thrown, not swallowed into an empty list.
        channel.credentialRef = "env:FEISHU_CREDS";
        String errResp = "{\"code\":99991672,\"msg\":\"permission denied: contact:user.id:readonly required\"}";
        server.expect(once(), requestTo("https://open.feishu.cn/open-apis/auth/v3/tenant_access_token/internal")).andExpect(method(HttpMethod.POST)).andRespond(withSuccess(FEISHU_TOKEN_SUCCESS, MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://open.feishu.cn/open-apis/contact/v3/users/batch_get_id?user_id_type=open_id")).andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(errResp, MediaType.APPLICATION_JSON));
        RestClient client = builder.build();
        com.kk2004.common.exception.BusinessException ex = Assertions.assertThrows(com.kk2004.common.exception.BusinessException.class, () ->
                new HttpChannelAdapters.Feishu(client, secret, mapper)
                        .lookupUsers(channel, java.util.List.of("13800000001"), null));
        server.verify();
        Assertions.assertTrue(ex.getMessage().contains("FEISHU_99991672"));
        Assertions.assertTrue(ex.getMessage().contains("contact:user.id:readonly"));
    }

    @Test
    void feishuListOrgStructureAssemblesDepartmentTreeWithUsers() {
        channel.credentialRef = "env:FEISHU_CREDS";
        // Two departments: "总公司" (root, id=D1) and "技术部" (child, id=D2, parent D1).
        String rootUsers = "{\"code\":0,\"msg\":\"success\",\"data\":{\"items\":[],\"has_more\":false}}";
        String rootChildren = "{\"code\":0,\"msg\":\"success\",\"data\":{\"items\":["
                + "{\"department_id\":\"D1\",\"name\":\"总公司\",\"parent_department_id\":\"0\"}"
                + "],\"has_more\":false}}";
        String d1Children = "{\"code\":0,\"msg\":\"success\",\"data\":{\"items\":["
                + "{\"department_id\":\"D2\",\"name\":\"技术部\",\"parent_department_id\":\"D1\"}"
                + "],\"has_more\":false}}";
        String d2Children = "{\"code\":0,\"msg\":\"success\",\"data\":{\"items\":[],\"has_more\":false}}";
        // Members of D1 and D2.
        String d1Users = "{\"code\":0,\"msg\":\"success\",\"data\":{\"items\":["
                + "{\"user_id\":\"ou_ceo\",\"name\":\"张总\"}],\"has_more\":false}}";
        String d2Users = "{\"code\":0,\"msg\":\"success\",\"data\":{\"items\":["
                + "{\"user_id\":\"ou_dev\",\"name\":\"小李\"}],\"has_more\":false}}";
        server.expect(once(), requestTo("https://open.feishu.cn/open-apis/auth/v3/tenant_access_token/internal")).andExpect(method(HttpMethod.POST)).andRespond(withSuccess(FEISHU_TOKEN_SUCCESS, MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo(allOf(startsWith("https://open.feishu.cn/open-apis/contact/v3/users/find_by_department?department_id=0"), containsString("department_id_type=department_id")))).andExpect(method(HttpMethod.GET)).andRespond(withSuccess(rootUsers, MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo(allOf(startsWith("https://open.feishu.cn/open-apis/contact/v3/departments/0/children?"), containsString("department_id_type=department_id")))).andExpect(method(HttpMethod.GET)).andRespond(withSuccess(rootChildren, MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo(allOf(startsWith("https://open.feishu.cn/open-apis/contact/v3/users/find_by_department?department_id=D1"), containsString("department_id_type=department_id")))).andExpect(method(HttpMethod.GET)).andRespond(withSuccess(d1Users, MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo(allOf(startsWith("https://open.feishu.cn/open-apis/contact/v3/departments/D1/children?"), containsString("department_id_type=department_id")))).andExpect(method(HttpMethod.GET)).andRespond(withSuccess(d1Children, MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo(allOf(startsWith("https://open.feishu.cn/open-apis/contact/v3/users/find_by_department?department_id=D2"), containsString("department_id_type=department_id")))).andExpect(method(HttpMethod.GET)).andRespond(withSuccess(d2Users, MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo(allOf(startsWith("https://open.feishu.cn/open-apis/contact/v3/departments/D2/children?"), containsString("department_id_type=department_id")))).andExpect(method(HttpMethod.GET)).andRespond(withSuccess(d2Children, MediaType.APPLICATION_JSON));
        // Org structure now also fetches the bot's joined chats; return none so the tree is unchanged.
        String chatsEmpty = "{\"code\":0,\"msg\":\"success\",\"data\":{\"items\":[],\"has_more\":false}}";
        server.expect(once(), requestTo(startsWith("https://open.feishu.cn/open-apis/im/v1/chats?"))).andExpect(method(HttpMethod.GET)).andRespond(withSuccess(chatsEmpty, MediaType.APPLICATION_JSON));
        RestClient client = builder.build();
        List<OrgNode> roots = new HttpChannelAdapters.Feishu(client, secret, mapper).listOrgStructure(channel);
        server.verify();
        Assertions.assertEquals(1, roots.size());
        OrgNode root = roots.get(0);
        Assertions.assertEquals("D1", root.id());
        Assertions.assertTrue(root.department());
        // Root has one user leaf + one child department.
        Assertions.assertEquals(2, root.children().size());
        OrgNode childDept = root.children().stream().filter(OrgNode::department).findFirst().orElseThrow();
        Assertions.assertEquals("D2", childDept.id());
        Assertions.assertEquals("技术部", childDept.name());
        // Child department has one user.
        OrgNode childUser = childDept.children().stream().filter(n -> !n.department()).findFirst().orElseThrow();
        Assertions.assertEquals("ou_dev", childUser.targetId());
        Assertions.assertEquals("小李", childUser.name());
        Assertions.assertFalse(childUser.department());
    }

    @Test
    void feishuListOrgStructureAcceptsConfiguredPlainDepartmentId() {
        channel.credentialRef = "env:FEISHU_CREDS";
        channel.configJson = "{\"org_root_department_id\":\"8d1ge45gd261de51\",\"org_root_department_name\":\"总办\"}";
        String empty = "{\"code\":0,\"msg\":\"success\",\"data\":{\"items\":[],\"has_more\":false}}";
        server.expect(once(), requestTo("https://open.feishu.cn/open-apis/auth/v3/tenant_access_token/internal")).andExpect(method(HttpMethod.POST)).andRespond(withSuccess(FEISHU_TOKEN_SUCCESS, MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo(allOf(startsWith("https://open.feishu.cn/open-apis/contact/v3/users/find_by_department?department_id=8d1ge45gd261de51"), containsString("department_id_type=department_id")))).andExpect(method(HttpMethod.GET)).andRespond(withSuccess(empty, MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo(allOf(startsWith("https://open.feishu.cn/open-apis/contact/v3/departments/8d1ge45gd261de51/children?"), containsString("department_id_type=department_id")))).andExpect(method(HttpMethod.GET)).andRespond(withSuccess(empty, MediaType.APPLICATION_JSON));
        // Org structure now also fetches the bot's joined chats; return none so the tree is unchanged.
        String chatsEmpty = "{\"code\":0,\"msg\":\"success\",\"data\":{\"items\":[],\"has_more\":false}}";
        server.expect(once(), requestTo(startsWith("https://open.feishu.cn/open-apis/im/v1/chats?"))).andExpect(method(HttpMethod.GET)).andRespond(withSuccess(chatsEmpty, MediaType.APPLICATION_JSON));
        RestClient client = builder.build();
        List<OrgNode> roots = new HttpChannelAdapters.Feishu(client, secret, mapper).listOrgStructure(channel);
        server.verify();
        Assertions.assertEquals(1, roots.size());
        Assertions.assertEquals("8d1ge45gd261de51", roots.get(0).id());
        Assertions.assertEquals("总办", roots.get(0).name());
        Assertions.assertTrue(roots.get(0).department());
        Assertions.assertTrue(roots.get(0).children().isEmpty());
    }

    @Test
    void feishuListOrgStructureMergesBotJoinedChats() {
        channel.credentialRef = "env:FEISHU_CREDS";
        // Empty department tree + two joined chats. Each chat becomes a SELECTABLE leaf whose
        // targetId is the chat_id (sending to the whole group), NOT expanded into its members.
        String empty = "{\"code\":0,\"msg\":\"success\",\"data\":{\"items\":[],\"has_more\":false}}";
        String chats = "{\"code\":0,\"msg\":\"success\",\"data\":{\"items\":["
                + "{\"chat_id\":\"oc_alert\",\"name\":\"告警群\"},"
                + "{\"chat_id\":\"oc_ops\",\"name\":\"运维群\"}"
                + "],\"has_more\":false}}";
        server.expect(once(), requestTo("https://open.feishu.cn/open-apis/auth/v3/tenant_access_token/internal")).andExpect(method(HttpMethod.POST)).andRespond(withSuccess(FEISHU_TOKEN_SUCCESS, MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo(allOf(startsWith("https://open.feishu.cn/open-apis/contact/v3/users/find_by_department?department_id=0"), containsString("department_id_type=department_id")))).andExpect(method(HttpMethod.GET)).andRespond(withSuccess(empty, MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo(allOf(startsWith("https://open.feishu.cn/open-apis/contact/v3/departments/0/children?"), containsString("department_id_type=department_id")))).andExpect(method(HttpMethod.GET)).andRespond(withSuccess(empty, MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo(startsWith("https://open.feishu.cn/open-apis/im/v1/chats?"))).andExpect(method(HttpMethod.GET)).andRespond(withSuccess(chats, MediaType.APPLICATION_JSON));
        RestClient client = builder.build();
        List<OrgNode> roots = new HttpChannelAdapters.Feishu(client, secret, mapper).listOrgStructure(channel);
        server.verify();
        // The only top-level node is the virtual "机器人群聊" parent.
        Assertions.assertEquals(1, roots.size());
        OrgNode chatsRoot = roots.get(0);
        Assertions.assertTrue(chatsRoot.department());
        Assertions.assertEquals("机器人群聊", chatsRoot.name());
        Assertions.assertEquals(2, chatsRoot.children().size());
        // Each chat is a selectable leaf (department=false) targeting the whole group via chat_id.
        OrgNode chat = chatsRoot.children().stream().filter(n -> "oc_alert".equals(n.id())).findFirst().orElseThrow();
        Assertions.assertEquals("告警群", chat.name());
        Assertions.assertFalse(chat.department());
        Assertions.assertEquals("oc_alert", chat.targetId());
        Assertions.assertTrue(chat.children().isEmpty());
    }
}
