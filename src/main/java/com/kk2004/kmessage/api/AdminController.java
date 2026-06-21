package com.kk2004.kmessage.api;

import com.kk2004.common.response.TransDTO;
import com.kk2004.common.response.PageResponse;
import com.kk2004.kmessage.channel.ChannelAdapterRegistry;
import com.kk2004.kmessage.channel.ChannelService;
import com.kk2004.kmessage.channel.UserGroupService;
import com.kk2004.kmessage.domain.ChannelType;
import com.kk2004.kmessage.domain.ContactOption;
import com.kk2004.kmessage.domain.OrgNode;
import com.kk2004.kmessage.domain.DeliveryResult;
import com.kk2004.kmessage.domain.Entities;
import com.kk2004.kmessage.persistence.GrantRepository;
import com.kk2004.kmessage.security.AppCredentialService;
import com.kk2004.kmessage.stats.StatsService;
import com.kk2004.kmessage.stats.StatsView;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private final AppCredentialService credentials;
    private final ChannelService channels;
    private final ChannelAdapterRegistry adapterRegistry;
    private final GrantRepository grants;
    private final UserGroupService userGroups;
    private final StatsService stats;
    public AdminController(AppCredentialService credentials, ChannelService channels,
                           ChannelAdapterRegistry adapterRegistry, GrantRepository grants,
                           UserGroupService userGroups, StatsService stats) {
        this.credentials = credentials; this.channels = channels;
        this.adapterRegistry = adapterRegistry; this.grants = grants;
        this.userGroups = userGroups;
        this.stats = stats;
    }

    @PostMapping("/applications")
    public TransDTO<AppCredentialService.CreatedCredential> createApplication(@RequestBody CreateApplication request) {
        return TransDTO.success(credentials.create(request.name()));
    }
    @GetMapping("/applications")
    public TransDTO<List<AppCredentialService.ApplicationView>> applications() {
        return TransDTO.success(credentials.list());
    }
    @PostMapping("/applications/{id}/rotate")
    public TransDTO<AppCredentialService.CreatedCredential> rotate(@PathVariable String id) {
        return TransDTO.success(credentials.rotate(id));
    }
    @DeleteMapping("/applications/{id}")
    public TransDTO<Void> deleteApplication(@PathVariable String id) {
        credentials.delete(id); return TransDTO.success();
    }
    @PostMapping("/channels")
    public TransDTO<ChannelService.ChannelView> createChannel(@RequestBody CreateChannel request) {
        return TransDTO.success(channels.create(request.type(), request.name(), request.enabled(), request.credentialRef(), request.configJson()));
    }
    @PutMapping("/channels/{id}")
    public TransDTO<ChannelService.ChannelView> updateChannel(@PathVariable String id, @RequestBody UpdateChannel request) {
        return TransDTO.success(channels.update(id, request.name(), request.enabled(), request.credentialRef(), request.configJson()));
    }
    @GetMapping("/channels")
    public TransDTO<List<ChannelService.ChannelView>> listChannels() {
        return TransDTO.success(channels.list());
    }
    @GetMapping("/channels/{id}/contacts")
    public TransDTO<List<ContactOption>> channelContacts(@PathVariable String id) {
        Entities.ChannelInstance channel = channels.require(id);
        if (!channel.enabled) throw new com.kk2004.common.exception.BusinessException("渠道实例未启用");
        List<ContactOption> fetched = adapterRegistry.require(channel.channelType).listContacts(channel);
        // Transparent incremental sync: persist fetched targets so the picker can offer
        // contacts beyond the provider's 48-hour window on future calls.
        return TransDTO.success(channels.syncContacts(id, fetched));
    }
    @GetMapping("/channel-types")
    public TransDTO<List<ChannelTypeView>> channelTypes() {
        return TransDTO.success(java.util.Arrays.stream(ChannelType.values())
                .map(t -> new ChannelTypeView(t.name(), t.getLabel(), t.getCredentialHint(),
                        t.getDescription(), t.getSetupGuide(), t.getTargetHint(), t.implemented()))
                .toList());
    }
    @GetMapping("/stats")
    public TransDTO<StatsView> stats() {
        return TransDTO.success(stats.load());
    }
    @PutMapping("/applications/{applicationId}/channels/{channelId}")
    public TransDTO<Void> grant(@PathVariable String applicationId, @PathVariable String channelId) {
        channels.grant(applicationId, channelId); return TransDTO.success();
    }
    @DeleteMapping("/applications/{applicationId}/channels/{channelId}")
    public TransDTO<Void> revoke(@PathVariable String applicationId, @PathVariable String channelId) {
        channels.revoke(applicationId, channelId); return TransDTO.success();
    }
    @GetMapping("/applications/{id}/channels")
    public TransDTO<List<String>> listApplicationChannels(@PathVariable String id) {
        return TransDTO.success(grants.findByCallerId(id).stream().map(g -> g.channelInstanceId).toList());
    }
    @PostMapping("/channels/{id}/send-message")
    public TransDTO<DeliveryResult> sendMessage(@PathVariable String id, @RequestBody SendMessageRequest request) {
        Entities.ChannelInstance channel = channels.require(id);
        if (!channel.enabled) throw new com.kk2004.common.exception.BusinessException("渠道实例未启用");
        Entities.Message msg = new Entities.Message();
        msg.targetValue = request.target();
        msg.contentText = request.text();
        DeliveryResult result = adapterRegistry.require(channel.channelType).send(channel, msg);
        return TransDTO.success(result);
    }
    @GetMapping("/channels/{id}/delete-preview")
    public TransDTO<ChannelService.DeletePreview> deletePreview(@PathVariable String id) {
        return TransDTO.success(channels.previewDelete(id));
    }
    @DeleteMapping("/channels/{id}")
    public TransDTO<Void> deleteChannel(@PathVariable String id) {
        channels.delete(id); return TransDTO.success();
    }

    // ---------- Channel users (channel-level) & app+channel groups ----------

    @PostMapping("/channels/{channelId}/users/import")
    public TransDTO<UserGroupService.ImportResult> importUsers(@PathVariable String channelId,
                                                               @RequestBody ImportUsersRequest request) {
        return TransDTO.success(userGroups.importUsers(channelId, request.mobiles(), request.emails()));
    }
    @GetMapping("/channels/{channelId}/users")
    public TransDTO<?> listUsers(@PathVariable String channelId,
                                 @RequestParam(required = false) Integer pageNum,
                                 @RequestParam(required = false) Integer pageSize) {
        if (pageNum != null || pageSize != null) {
            int safePageNum = pageNum == null ? 1 : pageNum;
            int safePageSize = pageSize == null ? 10 : pageSize;
            PageResponse<UserGroupService.AppUserView> page = userGroups.listUsersPage(channelId, safePageNum, safePageSize);
            return TransDTO.success(page);
        }
        return TransDTO.success(userGroups.listUsers(channelId));
    }
    @DeleteMapping("/channels/{channelId}/users/{userId}")
    public TransDTO<Void> deleteUser(@PathVariable String channelId, @PathVariable String userId) {
        userGroups.deleteUser(channelId, userId); return TransDTO.success();
    }
    @GetMapping("/channels/{channelId}/org-structure")
    public TransDTO<List<OrgNode>> orgStructure(@PathVariable String channelId) {
        Entities.ChannelInstance channel = channels.require(channelId);
        if (!channel.enabled) throw new com.kk2004.common.exception.BusinessException("渠道实例未启用");
        return TransDTO.success(adapterRegistry.require(channel.channelType).listOrgStructure(channel));
    }

    @GetMapping("/applications/{appId}/channels/{channelId}/groups")
    public TransDTO<List<UserGroupService.GroupView>> listGroups(@PathVariable String appId, @PathVariable String channelId) {
        return TransDTO.success(userGroups.listGroups(appId, channelId));
    }
    @PostMapping("/applications/{appId}/channels/{channelId}/groups")
    public TransDTO<UserGroupService.GroupView> createGroup(@PathVariable String appId, @PathVariable String channelId,
                                                            @RequestBody CreateGroupRequest request) {
        return TransDTO.success(userGroups.createGroup(appId, channelId, request.name(), request.parentId()));
    }
    @PutMapping("/applications/{appId}/channels/{channelId}/groups/{groupId}")
    public TransDTO<UserGroupService.GroupView> updateGroup(@PathVariable String appId, @PathVariable String channelId,
                                                            @PathVariable String groupId, @RequestBody CreateGroupRequest request) {
        return TransDTO.success(userGroups.updateGroup(appId, channelId, groupId, request.name(), request.parentId()));
    }
    @DeleteMapping("/applications/{appId}/channels/{channelId}/groups/{groupId}")
    public TransDTO<Void> deleteGroup(@PathVariable String appId, @PathVariable String channelId, @PathVariable String groupId) {
        userGroups.deleteGroup(appId, channelId, groupId); return TransDTO.success();
    }
    @GetMapping("/applications/{appId}/channels/{channelId}/groups/{groupId}/members")
    public TransDTO<List<String>> listMembers(@PathVariable String appId, @PathVariable String channelId, @PathVariable String groupId) {
        return TransDTO.success(userGroups.memberUserIds(groupId));
    }
    @PostMapping("/applications/{appId}/channels/{channelId}/groups/{groupId}/members")
    public TransDTO<Void> addMembers(@PathVariable String appId, @PathVariable String channelId, @PathVariable String groupId,
                                     @RequestBody AddMembersRequest request) {
        userGroups.addMembers(appId, channelId, groupId, request.userIds()); return TransDTO.success();
    }
    @PostMapping("/applications/{appId}/channels/{channelId}/groups/{groupId}/org-members")
    public TransDTO<Void> addOrgMembers(@PathVariable String appId, @PathVariable String channelId, @PathVariable String groupId,
                                        @RequestBody AddOrgMembersRequest request) {
        userGroups.addOrgMembers(appId, channelId, groupId, request.targets()); return TransDTO.success();
    }
    @DeleteMapping("/applications/{appId}/channels/{channelId}/groups/{groupId}/members/{userId}")
    public TransDTO<Void> removeMember(@PathVariable String appId, @PathVariable String channelId,
                                       @PathVariable String groupId, @PathVariable String userId) {
        userGroups.removeMember(appId, channelId, groupId, userId); return TransDTO.success();
    }

    public record CreateApplication(String name) {}
    public record CreateChannel(ChannelType type, String name, boolean enabled, String credentialRef, String configJson) {}
    public record UpdateChannel(String name, boolean enabled, String credentialRef, String configJson) {}
    public record SendMessageRequest(String target, String text) {}
    public record ImportUsersRequest(List<String> mobiles, List<String> emails) {}
    public record CreateGroupRequest(String name, String parentId) {}
    public record AddMembersRequest(List<String> userIds) {}
    public record AddOrgMembersRequest(List<UserGroupService.OrgMemberRef> targets) {}
    public record ChannelTypeView(String type, String label, String credentialHint, String description, String setupGuide, String targetHint, boolean implemented) {}
}
