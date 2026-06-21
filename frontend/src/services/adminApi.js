export async function adminRequest(path, options = {}) {
  const response = await fetch(`/api/admin${path}`, {
    headers: { "Content-Type": "application/json", ...(options.headers || {}) },
    ...options,
  });

  if (response.status === 401) {
    const error = new Error("登录状态已失效");
    error.status = 401;
    throw error;
  }

  const text = await response.text();
  if (!text) {
    if (response.ok) return null;
    throw new Error("服务暂不可用");
  }

  const body = JSON.parse(text);
  if (!body.success) throw new Error(body.message || "请求失败");
  return body.data;
}

export const adminApi = {
  getSession() {
    return adminRequest("/session");
  },
  login(credentials) {
    return adminRequest("/session/login", {
      method: "POST",
      body: JSON.stringify(credentials),
    });
  },
  logout() {
    return adminRequest("/session/logout", { method: "POST" });
  },
  changePassword(payload) {
    return adminRequest("/session/password", {
      method: "PUT",
      body: JSON.stringify(payload),
    });
  },
  listApplications() {
    return adminRequest("/applications");
  },
  createApplication(payload) {
    return adminRequest("/applications", {
      method: "POST",
      body: JSON.stringify(payload),
    });
  },
  rotateApplication(id) {
    return adminRequest(`/applications/${id}/rotate`, { method: "POST" });
  },
  deleteApplication(id) {
    return adminRequest(`/applications/${id}`, { method: "DELETE" });
  },
  listChannels() {
    return adminRequest("/channels");
  },
  listChannelTypes() {
    return adminRequest("/channel-types");
  },
  createChannel(payload) {
    return adminRequest("/channels", {
      method: "POST",
      body: JSON.stringify(payload),
    });
  },
  updateChannel(channelId, payload) {
    return adminRequest(`/channels/${channelId}`, {
      method: "PUT",
      body: JSON.stringify(payload),
    });
  },
  listChannelContacts(channelId) {
    return adminRequest(`/channels/${channelId}/contacts`);
  },
  sendMessage(channelId, payload) {
    return adminRequest(`/channels/${channelId}/send-message`, {
      method: "POST",
      body: JSON.stringify(payload),
    });
  },
  deleteChannelPreview(channelId) {
    return adminRequest(`/channels/${channelId}/delete-preview`);
  },
  deleteChannel(channelId) {
    return adminRequest(`/channels/${channelId}`, { method: "DELETE" });
  },
  importUsers(channelId, payload) {
    return adminRequest(`/channels/${channelId}/users/import`, {
      method: "POST",
      body: JSON.stringify(payload),
    });
  },
  listAppUsers(channelId, params = null) {
    const query = params ? `?${new URLSearchParams(params).toString()}` : "";
    return adminRequest(`/channels/${channelId}/users${query}`);
  },
  deleteAppUser(channelId, userId) {
    return adminRequest(`/channels/${channelId}/users/${userId}`, { method: "DELETE" });
  },
  listGroups(applicationId, channelId) {
    return adminRequest(`/applications/${applicationId}/channels/${channelId}/groups`);
  },
  createGroup(applicationId, channelId, payload) {
    return adminRequest(`/applications/${applicationId}/channels/${channelId}/groups`, {
      method: "POST",
      body: JSON.stringify(payload),
    });
  },
  updateGroup(applicationId, channelId, groupId, payload) {
    return adminRequest(`/applications/${applicationId}/channels/${channelId}/groups/${groupId}`, {
      method: "PUT",
      body: JSON.stringify(payload),
    });
  },
  deleteGroup(applicationId, channelId, groupId) {
    return adminRequest(`/applications/${applicationId}/channels/${channelId}/groups/${groupId}`, { method: "DELETE" });
  },
  listGroupMembers(applicationId, channelId, groupId) {
    return adminRequest(`/applications/${applicationId}/channels/${channelId}/groups/${groupId}/members`);
  },
  addGroupMembers(applicationId, channelId, groupId, payload) {
    return adminRequest(`/applications/${applicationId}/channels/${channelId}/groups/${groupId}/members`, {
      method: "POST",
      body: JSON.stringify(payload),
    });
  },
  removeGroupMember(applicationId, channelId, groupId, userId) {
    return adminRequest(`/applications/${applicationId}/channels/${channelId}/groups/${groupId}/members/${userId}`, { method: "DELETE" });
  },
  addOrgMembers(applicationId, channelId, groupId, payload) {
    return adminRequest(`/applications/${applicationId}/channels/${channelId}/groups/${groupId}/org-members`, {
      method: "POST",
      body: JSON.stringify(payload),
    });
  },
  listOrgStructure(channelId) {
    return adminRequest(`/channels/${channelId}/org-structure`);
  },
  changeGrant(applicationId, channelId, method) {
    return adminRequest(`/applications/${applicationId}/channels/${channelId}`, { method });
  },
  listApplicationChannels(applicationId) {
    return adminRequest(`/applications/${applicationId}/channels`);
  },
};
