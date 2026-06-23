package com.kk2004.kmessage.channel;

import com.kk2004.common.exception.BusinessException;
import com.kk2004.common.response.PageResponse;
import com.kk2004.kmessage.domain.ChannelType;
import com.kk2004.kmessage.domain.Entities.*;
import com.kk2004.kmessage.domain.ResolvedUser;
import com.kk2004.kmessage.persistence.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
public class UserGroupService {
    private final AppUserRepository appUsers;
    private final UserGroupRepository groups;
    private final UserGroupMemberRepository members;
    private final ChannelRepository channels;
    private final ChannelAdapterRegistry adapters;
    private final ChannelContactRepository contacts;

    public UserGroupService(AppUserRepository appUsers, UserGroupRepository groups,
                             UserGroupMemberRepository members, ChannelRepository channels,
                             ChannelAdapterRegistry adapters, ChannelContactRepository contacts) {
        this.appUsers = appUsers; this.groups = groups; this.members = members;
        this.channels = channels; this.adapters = adapters; this.contacts = contacts;
    }

    // ---------- Channel-level users ----------

    /**
     * Resolve phones/emails to channel target ids via the adapter, then upsert channel-level
     * app_users. Users belong to the channel and are shared by all applications bound to it.
     * Returns the resolved users plus the inputs that could not be resolved.
     */
    @Transactional
    public ImportResult importUsers(String channelInstanceId, List<String> mobiles, List<String> emails) {
        ChannelInstance channel = channels.findById(channelInstanceId)
                .orElseThrow(() -> new BusinessException(404, "渠道实例不存在"));
        if (channel.channelType != ChannelType.FEISHU)
            throw new BusinessException("仅飞书渠道支持通过手机号/邮箱批量添加用户");
        List<ResolvedUser> resolved;
        try {
            resolved = adapters.require(channel.channelType).lookupUsers(channel, mobiles, emails);
        } catch (RuntimeException e) {
            // Provider/transport error: report it to the admin instead of silently failing.
            // All inputs are returned as unresolved so the operator sees what was attempted.
            List<String> allInputs = new ArrayList<>();
            if (mobiles != null) allInputs.addAll(mobiles);
            if (emails != null) allInputs.addAll(emails);
            return new ImportResult(0, allInputs, List.of(), e.getMessage());
        }
        List<AppUserView> saved = new ArrayList<>();
        for (ResolvedUser u : resolved) {
            java.util.Optional<AppUser> existing = appUsers.findByChannelInstanceIdAndTargetId(channelInstanceId, u.targetId());
            AppUser au = existing.orElseGet(() -> {
                AppUser n = new AppUser();
                n.channelInstanceId = channelInstanceId;
                n.targetId = u.targetId();
                return n;
            });
            if (u.phone() != null && !u.phone().isBlank()) au.phone = u.phone();
            if (u.email() != null && !u.email().isBlank()) au.email = u.email();
            if (u.name() != null && !u.name().isBlank()) au.name = u.name();
            au = appUsers.save(au);
            saved.add(view(au));
        }
        // Compute unresolved inputs: phones/emails not present in any resolved record.
        // Mobile comparison uses E.164 normalization (matches Feishu's echo format).
        Set<String> resolvedPhones = new HashSet<>();
        Set<String> resolvedEmails = new HashSet<>();
        for (ResolvedUser u : resolved) {
            if (u.phone() != null) resolvedPhones.add(stripPhone(u.phone()));
            if (u.email() != null) resolvedEmails.add(u.email().toLowerCase());
        }
        List<String> unresolved = new ArrayList<>();
        if (mobiles != null) for (String m : mobiles)
            if (!resolvedPhones.contains(stripPhone(m))) unresolved.add(m);
        if (emails != null) for (String e : emails)
            if (!resolvedEmails.contains(e.toLowerCase())) unresolved.add(e);
        // When the provider accepted the request (code 0) but resolved nobody, give the admin
        // an actionable hint instead of an empty result. The log in lookupUsers shows the raw body.
        String hint = null;
        if (saved.isEmpty() && !unresolved.isEmpty()) {
            hint = "飞书未匹配到任何用户。请确认：(1) 已开通通讯录权限 contact:user.id:readonly / contact:user.phone:readonly / contact:user.email:readonly 并发布生效；(2) 目标用户在应用的「通讯录权限范围」内；(3) 手机号需为该用户在飞书注册的手机号。详见后端日志（kmessage.feishu）中 batch_get_id 的原始响应。";
        }
        return new ImportResult(saved.size(), unresolved, saved, hint);
    }

    /**
     * List channel-level users. Auto-merges the channel's known contacts (from listContacts /
     * getUpdates / im chats) into app_users on first sight, so every channel's resolved contacts
     * are manageable as users regardless of which application views them.
     */
    @Transactional
    public List<AppUserView> listUsers(String channelInstanceId) {
        syncChannelContacts(channelInstanceId);
        return appUsers.findByChannelInstanceId(channelInstanceId).stream()
                .map(this::view).toList();
    }

    @Transactional
    public PageResponse<AppUserView> listUsersPage(String channelInstanceId, int pageNum, int pageSize) {
        syncChannelContacts(channelInstanceId);
        int safePageNum = Math.max(pageNum, 1);
        int safePageSize = Math.max(pageSize, 10);
        Page<AppUser> page = appUsers.findByChannelInstanceId(
                channelInstanceId,
                PageRequest.of(safePageNum - 1, safePageSize, Sort.by(Sort.Direction.DESC, "createdAt")));
        return PageResponse.of(
                safePageNum,
                safePageSize,
                page.getTotalElements(),
                page.getContent().stream().map(this::view).toList());
    }

    private void syncChannelContacts(String channelInstanceId) {
        for (ChannelContact c : contacts.findByChannelInstanceIdOrderByLastSeenAtDesc(channelInstanceId)) {
            if (appUsers.findByChannelInstanceIdAndTargetId(channelInstanceId, c.targetId).isEmpty()) {
                AppUser u = new AppUser();
                u.channelInstanceId = channelInstanceId;
                u.targetId = c.targetId;
                u.name = c.label;
                appUsers.save(u);
            }
        }
    }

    @Transactional
    public void deleteUser(String channelInstanceId, String userId) {
        AppUser u = appUsers.findById(userId)
                .orElseThrow(() -> new BusinessException(404, "用户不存在"));
        if (!Objects.equals(u.channelInstanceId, channelInstanceId))
            throw new BusinessException(403, "用户不属于该渠道");
        members.deleteByAppUserId(userId);
        contacts.deleteByChannelInstanceIdAndTargetId(channelInstanceId, u.targetId);
        appUsers.delete(u);
    }

    // ---------- Group tree CRUD ----------

    @Transactional
    public GroupView createGroup(String callerId, String channelInstanceId, String name, String parentId) {
        validateScope(callerId, channelInstanceId);
        if (name == null || name.isBlank()) throw new BusinessException("分组名称不能为空");
        if (parentId != null && !parentId.isBlank()) {
            UserGroup parent = requireGroup(parentId);
            if (!Objects.equals(parent.callerId, callerId) || !Objects.equals(parent.channelInstanceId, channelInstanceId))
                throw new BusinessException(403, "无权操作该父分组");
            parentId = parent.id;
        } else {
            parentId = null;
        }
        UserGroup g = new UserGroup();
        g.callerId = callerId;
        g.channelInstanceId = channelInstanceId;
        g.parentId = parentId;
        g.name = name;
        g = groups.save(g);
        return view(g, 0);
    }

    @Transactional
    public GroupView updateGroup(String callerId, String channelInstanceId, String groupId, String name, String parentId) {
        UserGroup g = requireGroup(groupId);
        if (!Objects.equals(g.callerId, callerId) || !Objects.equals(g.channelInstanceId, channelInstanceId))
            throw new BusinessException(403, "无权操作该分组");
        if (name != null && !name.isBlank()) g.name = name;
        if (parentId != null) {
            if (parentId.isBlank()) {
                g.parentId = null;
            } else {
                UserGroup newParent = requireGroup(parentId);
                if (!Objects.equals(newParent.callerId, callerId) || !Objects.equals(newParent.channelInstanceId, channelInstanceId))
                    throw new BusinessException(403, "无权操作该父分组");
                if (createsCycle(g, newParent.id))
                    throw new BusinessException("不能将分组移动到自身或其子树下");
                g.parentId = newParent.id;
            }
        }
        g = groups.save(g);
        return view(g, memberCount(g.id));
    }

    @Transactional
    public void deleteGroup(String callerId, String channelInstanceId, String groupId) {
        UserGroup g = requireGroup(groupId);
        if (!Objects.equals(g.callerId, callerId) || !Objects.equals(g.channelInstanceId, channelInstanceId))
            throw new BusinessException(403, "无权操作该分组");
        // Re-parent children to this group's parent (orphan them to grandparent / root).
        for (UserGroup child : groups.findByParentId(groupId)) {
            child.parentId = g.parentId;
            groups.save(child);
        }
        members.deleteByGroupId(groupId);
        groups.delete(g);
    }

    /** Full tree for an app+channel scope, with member counts. */
    public List<GroupView> listGroups(String callerId, String channelInstanceId) {
        List<UserGroup> all = groups.findByCallerIdAndChannelInstanceId(callerId, channelInstanceId);
        Map<String, Long> counts = new HashMap<>();
        for (UserGroup g : all) counts.put(g.id, memberCount(g.id));
        return all.stream().map(g -> view(g, counts.getOrDefault(g.id, 0L))).toList();
    }

    // ---------- Membership ----------

    @Transactional
    public void addMembers(String callerId, String channelInstanceId, String groupId, List<String> userIds) {
        UserGroup g = requireGroup(groupId);
        if (!Objects.equals(g.callerId, callerId) || !Objects.equals(g.channelInstanceId, channelInstanceId))
            throw new BusinessException(403, "无权操作该分组");
        for (String uid : userIds) {
            AppUser u = appUsers.findById(uid).orElseThrow(() -> new BusinessException(404, "用户不存在: " + uid));
            // Users are channel-level: only the channel must match the group's channel.
            if (!Objects.equals(u.channelInstanceId, channelInstanceId))
                throw new BusinessException(403, "用户不属于该渠道");
            members.save(new UserGroupMember(groupId, uid));
        }
    }

    /**
     * Add org-structure users to a group. Unlike {@link #addMembers}, the users may not yet exist
     * in app_users (they come straight from the provider org tree), so they are upserted by
     * (channel, targetId) before the membership row is created.
     */
    @Transactional
    public void addOrgMembers(String callerId, String channelInstanceId, String groupId, List<OrgMemberRef> targets) {
        UserGroup g = requireGroup(groupId);
        if (!Objects.equals(g.callerId, callerId) || !Objects.equals(g.channelInstanceId, channelInstanceId))
            throw new BusinessException(403, "无权操作该分组");
        for (OrgMemberRef ref : targets) {
            AppUser u = appUsers.findByChannelInstanceIdAndTargetId(channelInstanceId, ref.targetId())
                    .orElseGet(() -> {
                        AppUser n = new AppUser();
                        n.channelInstanceId = channelInstanceId;
                        n.targetId = ref.targetId();
                        n.name = ref.name();
                        return n;
                    });
            if (ref.name() != null && !ref.name().isBlank() && (u.name == null || u.name.isBlank())) u.name = ref.name();
            u = appUsers.save(u);
            members.save(new UserGroupMember(groupId, u.id));
        }
    }

    @Transactional
    public void removeMember(String callerId, String channelInstanceId, String groupId, String userId) {
        UserGroup g = requireGroup(groupId);
        if (!Objects.equals(g.callerId, callerId) || !Objects.equals(g.channelInstanceId, channelInstanceId))
            throw new BusinessException(403, "无权操作该分组");
        members.deleteById(new UserGroupMemberId(groupId, userId));
    }

    public List<String> memberUserIds(String groupId) {
        return members.findAppUserIdsByGroupId(groupId);
    }

    /**
     * Resolve a group to its members' channel target ids (for message fan-out). Recursively
     * aggregates direct members of the group AND all its descendant sub-groups, deduplicated
     * by channel target id so a user appearing in multiple sub-groups receives one message.
     * A parent group with no direct members but populated sub-groups still expands to those members.
     */
    public List<GroupMember> expandGroup(String callerId, String channelInstanceId, String groupId) {
        UserGroup g = requireGroup(groupId);
        if (!Objects.equals(g.callerId, callerId) || !Objects.equals(g.channelInstanceId, channelInstanceId))
            throw new BusinessException(403, "无权使用该分组");
        // Collect this group + all descendant group ids (BFS over the parent_id edges).
        List<String> groupIds = collectDescendantGroupIds(groupId);
        List<String> appUserIds = members.findAppUserIdsByGroupIdIn(groupIds);
        if (appUserIds.isEmpty()) return List.of();
        // Users are channel-level; no caller filter. Only the group's channel is authoritative.
        // Dedup by targetId so overlapping sub-groups don't produce duplicate deliveries.
        LinkedHashMap<String, String> byTarget = new LinkedHashMap<>();
        for (AppUser u : appUsers.findAllById(appUserIds)) {
            byTarget.putIfAbsent(u.targetId, u.id);
        }
        return byTarget.entrySet().stream()
                .map(e -> new GroupMember(e.getValue(), e.getKey()))
                .toList();
    }

    /** BFS over parent_id to gather this group and every descendant group id. */
    private List<String> collectDescendantGroupIds(String rootGroupId) {
        List<String> result = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        List<String> frontier = new ArrayList<>(List.of(rootGroupId));
        while (!frontier.isEmpty()) {
            List<String> next = new ArrayList<>();
            for (String id : frontier) {
                if (!visited.add(id)) continue; // guard against cycles
                result.add(id);
                for (UserGroup child : groups.findByParentId(id)) next.add(child.id);
            }
            frontier = next;
        }
        return result;
    }

    /** Resolve a group to its owning channel instance id (for send requests that omit channelInstanceId). */
    public String groupChannel(String groupId) {
        return requireGroup(groupId).channelInstanceId;
    }

    /** Resolve a channel-level user to its owning channel instance id (for send requests that omit channelInstanceId). */
    public String userChannel(String userId) {
        return appUsers.findById(userId).orElseThrow(() -> new BusinessException(404, "用户不存在")).channelInstanceId;
    }

    /** Resolve a channel-level user to its channel target id (for single-user send). */
    public AppUser resolveUser(String channelInstanceId, String userId) {
        AppUser u = appUsers.findById(userId).orElseThrow(() -> new BusinessException(404, "用户不存在"));
        if (!Objects.equals(u.channelInstanceId, channelInstanceId))
            throw new BusinessException(403, "用户不属于该渠道");
        return u;
    }

    // ---------- helpers ----------

    private void validateScope(String callerId, String channelInstanceId) {
        if (!channels.existsById(channelInstanceId))
            throw new BusinessException(404, "渠道实例不存在");
    }

    private UserGroup requireGroup(String id) {
        return groups.findById(id).orElseThrow(() -> new BusinessException(404, "分组不存在"));
    }

    private long memberCount(String groupId) {
        return members.countByGroupId(groupId);
    }

    /** True if making {@code group} a child of {@code newParentId} would create a cycle. */
    private boolean createsCycle(UserGroup group, String newParentId) {
        if (Objects.equals(group.id, newParentId)) return true;
        String cursor = newParentId;
        Set<String> visited = new HashSet<>();
        while (cursor != null && visited.add(cursor)) {
            if (Objects.equals(cursor, group.id)) return true;
            UserGroup p = groups.findById(cursor).orElse(null);
            cursor = (p == null) ? null : p.parentId;
        }
        return false;
    }

    /** Reduce a mobile to its bare digit sequence for comparison (drops +, spaces, leading 86/0086). */
    private String stripPhone(String p) {
        if (p == null) return "";
        String digits = p.replaceAll("[\\s+]", "");
        if (digits.startsWith("0086")) return digits.substring(4);
        if (digits.startsWith("86") && digits.length() > 11) return digits.substring(2);
        return digits;
    }

    private AppUserView view(AppUser u) {
        return new AppUserView(u.id, u.targetId, u.name, u.phone, u.email, u.createdAt);
    }

    private GroupView view(UserGroup g, long memberCount) {
        return new GroupView(g.id, g.parentId, g.name, memberCount, g.createdAt);
    }

    // ---------- DTOs ----------

    public record AppUserView(String id, String targetId, String name, String phone, String email, Instant createdAt) {}
    public record GroupView(String id, String parentId, String name, long memberCount, Instant createdAt) {}
    public record GroupMember(String appUserId, String targetId) {}
    public record OrgMemberRef(String targetId, String name) {}
    public record ImportResult(int importedCount, List<String> unresolved, List<AppUserView> users, String error) {}
}
