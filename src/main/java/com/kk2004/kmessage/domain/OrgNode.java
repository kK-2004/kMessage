package com.kk2004.kmessage.domain;

import java.util.List;

/**
 * A node in a channel's organizational structure (e.g. a Feishu department tree).
 *
 * @param id           unique node id (department_id for departments; targetId for users)
 * @param name         display name
 * @param parentId     parent node id (department parent_id; null for users/roots)
 * @param department   true for a department node, false for a user leaf
 * @param userId       for user nodes: the app_user reference key (same as targetId for Feishu open_id)
 * @param targetId     for user nodes: the channel send target id (e.g. open_id); null for departments
 * @param children     nested child nodes
 */
public record OrgNode(String id, String name, String parentId, boolean department,
                      String userId, String targetId, List<OrgNode> children) {}
