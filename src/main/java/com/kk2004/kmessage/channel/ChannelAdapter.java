package com.kk2004.kmessage.channel;

import com.kk2004.kmessage.domain.*;
import com.kk2004.kmessage.domain.Entities.*;
import java.util.List;

public interface ChannelAdapter {
    ChannelType type();
    DeliveryResult send(ChannelInstance channel, Message message);
    default void validate(String target, String extensionJson) {
        if (target == null || target.isBlank()) throw new IllegalArgumentException("target 不能为空");
    }
    default void validate(String target, MessageContentType contentType, String extensionJson) {
        validate(target, extensionJson);
    }

    /**
     * Enumerate selectable send targets for the admin "send message" dialog.
     * Adapters that can list recent chats/groups override this; the default
     * returns an empty list, in which case the UI falls back to manual entry.
     */
    default List<ContactOption> listContacts(ChannelInstance channel) {
        return List.of();
    }

    /**
     * Resolve channel target ids from phone numbers / emails (e.g. Feishu batch_get_id).
     * Returns only the successfully resolved users; callers handle the unmatched inputs.
     * The default returns an empty list for channels without a contacts API.
     */
    default List<ResolvedUser> lookupUsers(ChannelInstance channel, List<String> mobiles, List<String> emails) {
        return List.of();
    }

    /**
     * Fetch the channel provider's organizational structure (e.g. Feishu department tree with
     * users) so the console can offer an org-based member picker for groups. The default returns
     * an empty list for channels without an org API.
     */
    default List<OrgNode> listOrgStructure(ChannelInstance channel) {
        return List.of();
    }
}
