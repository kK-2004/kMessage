package com.kk2004.kmessage.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

public final class Entities {
    private Entities() {}

    @MappedSuperclass
    public abstract static class Base {
        @Id public String id = UUID.randomUUID().toString();
    }

    @Entity @Table(name = "callers")
    public static class Caller extends Base {
        public String name;
        public boolean admin;
        public boolean active = true;
        public Instant createdAt = Instant.now();
        public Caller() {}
        public Caller(String name, boolean admin) { this.name = name; this.admin = admin; }
    }

    @Entity @Table(name = "api_credentials")
    public static class ApiCredential extends Base {
        public String callerId;
        public String appKey;
        public String secretHash;
        public boolean active = true;
        public Instant createdAt = Instant.now();
        public Instant expiresAt;
        public ApiCredential() {}
    }

    @Entity @Table(name = "channel_instances")
    public static class ChannelInstance extends Base {
        public String name;
        @Enumerated(EnumType.STRING) public ChannelType channelType;
        public boolean enabled;
        public String credentialRef;
        @Column(columnDefinition = "text") public String configJson = "{}";
        public Instant createdAt = Instant.now();
        public Instant updatedAt = Instant.now();
        public ChannelInstance() {}
    }

    @Entity @Table(name = "channel_contacts",
            uniqueConstraints = @UniqueConstraint(columnNames = {"channelInstanceId", "targetId"}))
    public static class ChannelContact extends Base {
        public String channelInstanceId;
        public String targetId;
        public String label;
        public String contactType;
        public Instant firstSeenAt = Instant.now();
        public Instant lastSeenAt = Instant.now();
        public ChannelContact() {}
    }

    @Entity @Table(name = "app_users",
            uniqueConstraints = @UniqueConstraint(columnNames = {"channelInstanceId", "targetId"}))
    public static class AppUser extends Base {
        public String channelInstanceId;
        public String targetId;
        public String name;
        public String phone;
        public String email;
        public Instant createdAt = Instant.now();
        public AppUser() {}
    }

    @Entity @Table(name = "user_groups")
    public static class UserGroup extends Base {
        public String callerId;
        public String channelInstanceId;
        public String parentId;
        public String name;
        public Instant createdAt = Instant.now();
        public UserGroup() {}
    }

    @Entity @Table(name = "user_group_members")
    @IdClass(UserGroupMemberId.class)
    public static class UserGroupMember {
        @Id public String groupId;
        @Id public String appUserId;
        public UserGroupMember() {}
        public UserGroupMember(String groupId, String appUserId) {
            this.groupId = groupId; this.appUserId = appUserId;
        }
    }

    public static class UserGroupMemberId implements java.io.Serializable {
        public String groupId;
        public String appUserId;
        public UserGroupMemberId() {}
        public UserGroupMemberId(String groupId, String appUserId) {
            this.groupId = groupId; this.appUserId = appUserId;
        }
        @Override public boolean equals(Object o) {
            if (!(o instanceof UserGroupMemberId m)) return false;
            return java.util.Objects.equals(groupId, m.groupId) && java.util.Objects.equals(appUserId, m.appUserId);
        }
        @Override public int hashCode() { return java.util.Objects.hash(groupId, appUserId); }
    }

    @Entity @Table(name = "caller_channel_grants")
    @IdClass(GrantId.class)
    public static class Grant {
        @Id public String callerId;
        @Id public String channelInstanceId;
        public Grant() {}
        public Grant(String callerId, String channelInstanceId) {
            this.callerId = callerId; this.channelInstanceId = channelInstanceId;
        }
    }

    public static class GrantId implements java.io.Serializable {
        public String callerId;
        public String channelInstanceId;
        public GrantId() {}
        public GrantId(String callerId, String channelInstanceId) {
            this.callerId = callerId; this.channelInstanceId = channelInstanceId;
        }
        public boolean equals(Object o) { return o instanceof GrantId g && java.util.Objects.equals(callerId, g.callerId) && java.util.Objects.equals(channelInstanceId, g.channelInstanceId); }
        public int hashCode() { return java.util.Objects.hash(callerId, channelInstanceId); }
    }

    @Entity @Table(name = "messages")
    public static class Message extends Base {
        public String callerId;
        public String channelInstanceId;
        public String targetValue;
        @Enumerated(EnumType.STRING) public MessageContentType contentType = MessageContentType.TEXT;
        @Column(columnDefinition = "text") public String contentText;
        @Column(columnDefinition = "text") public String contentJson;
        @Column(columnDefinition = "text") public String extensionJson = "{}";
        public String idempotencyKey;
        public String requestHash;
        public String traceId;
        @Enumerated(EnumType.STRING) public MessageStatus status = MessageStatus.ACCEPTED;
        public Instant createdAt = Instant.now();
        public Instant updatedAt = Instant.now();
        public Message() {}
    }

    @Entity(name = "DeliveryTask") @Table(name = "delivery_tasks")
    public static class DeliveryTask extends Base {
        public String messageId;
        public int attemptCount;
        public Instant nextAttemptAt = Instant.now();
        public Instant leaseUntil;
        public String workerId;
        public Instant createdAt = Instant.now();
        public Instant updatedAt = Instant.now();
        public DeliveryTask() {}
    }

    @Entity @Table(name = "delivery_attempts")
    public static class DeliveryAttempt extends Base {
        public String messageId;
        public int attemptNumber;
        @Enumerated(EnumType.STRING) public DeliveryResult.Type resultType;
        public String providerReference;
        public String errorCode;
        public String diagnostic;
        public Instant startedAt;
        public Instant finishedAt;
        public DeliveryAttempt() {}
    }

    // Singleton row (fixed id) overriding the configured admin password once changed at runtime.
    @Entity @Table(name = "admin_credentials")
    public static class AdminCredential extends Base {
        public String passwordHash;
        public Instant updatedAt = Instant.now();
        public AdminCredential() {}
    }
}
