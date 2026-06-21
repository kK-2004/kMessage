<script setup>
import {
  KButton,
  KCard,
  KCardContent,
  KCardDescription,
  KCardHeader,
  KCardTitle,
  KDialog,
  KDialogContent,
  KDialogDescription,
  KDialogFooter,
  KDialogHeader,
  KDialogOverlay,
  KDialogTitle,
  KEmptyState,
  KInput,
  KStatusBadge,
} from "@kk-2004/ui-components";
import { ElSelect, ElOption, ElMessage } from "element-plus";
import { ref, reactive, computed, watch } from "vue";
import { adminApi } from "../../services/adminApi";
import GroupTreeSelect from "./GroupTreeSelect.vue";
import MemberTreePicker from "./MemberTreePicker.vue";

const props = defineProps({
  applications: { type: Array, default: () => [] },
  channels: { type: Array, default: () => [] },
  channelLabel: { type: Function, required: true },
  reloadKey: { type: Number, default: 0 },
});

// ---------- Available users used by group members ----------

const users = ref([]);
const usersLoading = ref(false);

const selectableChannels = computed(() => props.channels.filter((c) => c.enabled));

async function loadUsers(channelId) {
  if (!channelId) {
    users.value = [];
    return;
  }
  usersLoading.value = true;
  try {
    users.value = await adminApi.listAppUsers(channelId);
  } catch {
    users.value = [];
  } finally {
    usersLoading.value = false;
  }
}

// ---------- App groups (app+channel scoped) ----------

const selectedAppId = ref("");
const selectedChannelId = ref("");
const groups = ref([]);
const groupsLoading = ref(false);
const selectedGroupId = ref("");
const groupMembers = ref([]);
const membersSaving = ref(false);
const memberKeyword = ref("");

async function loadScope() {
  if (!selectedAppId.value || !selectedChannelId.value) {
    groups.value = [];
    selectedGroupId.value = "";
    groupMembers.value = [];
    return;
  }
  groupsLoading.value = true;
  try {
    groups.value = await adminApi.listGroups(selectedAppId.value, selectedChannelId.value);
    if (selectedGroupId.value && groups.value.some((group) => group.id === selectedGroupId.value)) {
      await selectGroup({ id: selectedGroupId.value });
    } else {
      selectedGroupId.value = "";
      groupMembers.value = [];
    }
  } catch {
    groups.value = [];
    selectedGroupId.value = "";
    groupMembers.value = [];
  } finally {
    groupsLoading.value = false;
  }
}

watch([selectedAppId, selectedChannelId], async () => {
  await loadScope();
  await loadUsers(selectedChannelId.value);
});
watch(() => props.reloadKey, async () => {
  await loadScope();
  await loadUsers(selectedChannelId.value);
});

// Build the tree-selector node structure from the flat group list.
// Members of the currently selected group are injected as leaf child nodes.
const groupTree = computed(() => attachMembers(buildTree(groups.value)));
function buildTree(list) {
  const byId = {};
  list.forEach((g) => (byId[g.id] = { ...g, children: [] }));
  const roots = [];
  list.forEach((g) => {
    const node = byId[g.id];
    if (g.parentId && byId[g.parentId]) byId[g.parentId].children.push(node);
    else roots.push(node);
  });
  return roots;
}
function attachMembers(nodes) {
  return nodes.map((node) => {
    const next = { ...node };
    if (node.children?.length) next.children = attachMembers(node.children);
    if (node.id === selectedGroupId.value && groupMemberUsers.value.length) {
      const memberChildren = groupMemberUsers.value.map((user) => ({
        id: `member:${node.id}:${user.id}`,
        name: userLabel(user),
        kind: "member",
        userId: user.id,
        meta: userMeta(user),
        avatarColor: colorFor(userLabel(user)),
        avatarText: initials(userLabel(user)),
      }));
      next.children = [...memberChildren, ...(next.children || [])];
    }
    return next;
  });
}

const parentGroupTree = computed(() => removeNode(groupTree.value, groupForm.id));
const selectedGroup = computed(() => groups.value.find((group) => group.id === selectedGroupId.value));
const userById = computed(() => new Map(users.value.map((user) => [user.id, user])));
const groupMemberIdSet = computed(() => new Set(groupMembers.value));
const groupMemberUsers = computed(() =>
  groupMembers.value.map((id) => userById.value.get(id) || fallbackUser(id))
);
const availableMemberUsers = computed(() =>
  users.value.filter((user) => !groupMemberIdSet.value.has(user.id))
);
const filteredAvailableMemberUsers = computed(() => filterUsers(availableMemberUsers.value, memberKeyword.value));

// ---------- Org structure (Feishu): real-time department tree as user source ----------

const selectedChannelType = computed(() => {
  const ch = props.channels.find((c) => c.id === selectedChannelId.value);
  return ch?.channelType || "";
});
const isOrgChannel = computed(() => selectedChannelType.value === "FEISHU");
const memberSource = ref("manual");
const isOrgSource = computed(() => isOrgChannel.value && memberSource.value === "org");
const orgTree = ref([]);
const orgLoading = ref(false);
const orgError = ref("");
const orgCheckedTargets = ref([]); // selected {targetId, name} from the org picker

async function loadOrgStructure(channelId) {
  if (!channelId || !isOrgChannel.value) {
    orgTree.value = [];
    return;
  }
  orgLoading.value = true;
  orgError.value = "";
  try {
    orgTree.value = await adminApi.listOrgStructure(channelId);
  } catch (e) {
    orgError.value = e.message || "组织架构拉取失败";
    orgTree.value = [];
  } finally {
    orgLoading.value = false;
  }
}

watch([selectedAppId, selectedChannelId], async () => {
  orgCheckedTargets.value = [];
  await loadOrgStructure(selectedChannelId.value);
});
watch(
  selectedChannelType,
  (type) => {
    memberSource.value = type === "FEISHU" ? "org" : "manual";
    orgCheckedTargets.value = [];
  },
  { immediate: true }
);
watch(memberSource, () => {
  orgCheckedTargets.value = [];
});
watch(() => props.reloadKey, async () => {
  await loadOrgStructure(selectedChannelId.value);
});

// Collect all user leaves under a node (recursive) — used by department checkbox select-all.
function collectUserLeaves(node) {
  const out = [];
  if (!node.department && node.targetId) out.push({ targetId: node.targetId, name: node.name });
  for (const child of node.children || []) out.push(...collectUserLeaves(child));
  return out;
}

function onOrgSelectionChange(targets) {
  orgCheckedTargets.value = targets;
}

async function addOrgMembersToGroup() {
  if (!orgCheckedTargets.value.length || !selectedGroupId.value) return;
  membersSaving.value = true;
  try {
    await adminApi.addOrgMembers(
      selectedAppId.value,
      selectedChannelId.value,
      selectedGroupId.value,
      { targets: orgCheckedTargets.value }
    );
    orgCheckedTargets.value = [];
    await selectGroup({ id: selectedGroupId.value });
    await loadUsers(selectedChannelId.value);
  } catch (e) {
    ElMessage.error(e.message || "加入分组失败");
  } finally {
    membersSaving.value = false;
  }
}

function removeNode(nodes, excludedId) {
  if (!excludedId) return nodes;
  return nodes
    .filter((node) => node.id !== excludedId)
    .map((node) => ({
      ...node,
      children: removeNode(node.children || [], excludedId),
    }));
}

// ---------- Group CRUD ----------

const groupDialogOpen = ref(false);
const groupForm = reactive({ id: "", name: "", parentId: "" });
const groupEditing = ref(false);

function openCreateGroup(parentId = "") {
  groupEditing.value = false;
  groupForm.id = "";
  groupForm.name = "";
  groupForm.parentId = parentId;
  groupDialogOpen.value = true;
}

function openEditGroup(node) {
  groupEditing.value = true;
  groupForm.id = node.id;
  groupForm.name = node.name;
  groupForm.parentId = node.parentId || "";
  groupDialogOpen.value = true;
}

async function saveGroup() {
  const payload = { name: groupForm.name, parentId: groupForm.parentId || null };
  try {
    let saved;
    if (groupEditing.value) {
      saved = await adminApi.updateGroup(selectedAppId.value, selectedChannelId.value, groupForm.id, payload);
    } else {
      saved = await adminApi.createGroup(selectedAppId.value, selectedChannelId.value, payload);
    }
    groupDialogOpen.value = false;
    await loadScope();
    if (saved?.id) await selectGroup(saved);
  } catch (e) {
    ElMessage.error(e.message || "保存失败");
  }
}

async function deleteGroupNode(node) {
  if (!confirm(`确定删除分组「${node.name}」？子分组会被移到上级，成员关系解除。`)) return;
  try {
    await adminApi.deleteGroup(selectedAppId.value, selectedChannelId.value, node.id);
    if (selectedGroupId.value === node.id) {
      selectedGroupId.value = "";
      groupMembers.value = [];
    }
    await loadScope();
  } catch (e) {
    ElMessage.error(e.message || "删除失败");
  }
}

async function selectGroup(node) {
  selectedGroupId.value = node.id;
  try {
    groupMembers.value = await adminApi.listGroupMembers(
      selectedAppId.value, selectedChannelId.value, node.id
    );
    updateGroupMemberCount(node.id, groupMembers.value.length);
  } catch {
    groupMembers.value = [];
  }
}

function updateGroupMemberCount(groupId, memberCount) {
  groups.value = groups.value.map((group) =>
    group.id === groupId ? { ...group, memberCount } : group
  );
}

function addGroupMember(userId) {
  if (!userId || groupMemberIdSet.value.has(userId)) return;
  syncGroupMembers([...groupMembers.value, userId]);
}

function removeGroupMember(userId) {
  syncGroupMembers(groupMembers.value.filter((id) => id !== userId));
}

async function syncGroupMembers(nextIds) {
  if (!selectedGroupId.value) return;
  const nextUniqueIds = Array.from(new Set(nextIds));
  const previousIds = [...groupMembers.value];
  const currentSet = new Set(previousIds);
  const nextSet = new Set(nextUniqueIds);
  const toAdd = nextUniqueIds.filter((id) => !currentSet.has(id));
  const toRemove = previousIds.filter((id) => !nextSet.has(id));

  if (!toAdd.length && !toRemove.length) return;

  groupMembers.value = nextUniqueIds;
  membersSaving.value = true;
  try {
    if (toAdd.length) {
      await adminApi.addGroupMembers(
        selectedAppId.value,
        selectedChannelId.value,
        selectedGroupId.value,
        { userIds: toAdd }
      );
    }
    await Promise.all(
      toRemove.map((userId) =>
        adminApi.removeGroupMember(
          selectedAppId.value,
          selectedChannelId.value,
          selectedGroupId.value,
          userId
        )
      )
    );
    await selectGroup({ id: selectedGroupId.value });
  } catch (e) {
    groupMembers.value = previousIds;
    ElMessage.error(e.message || "保存成员失败");
    await selectGroup({ id: selectedGroupId.value });
  } finally {
    membersSaving.value = false;
  }
}

const palette = ["#2563eb", "#0f9f6e", "#d97706", "#dc2626", "#7c3aed", "#0891b2", "#be185d"];

function colorFor(name) {
  let hash = 0;
  for (let i = 0; i < name.length; i += 1) hash = (hash * 31 + name.charCodeAt(i)) >>> 0;
  return palette[hash % palette.length];
}

function initials(name) {
  return name.trim().slice(0, 2).toUpperCase() || "用";
}

function userLabel(user) {
  return user?.name || user?.phone || user?.email || user?.targetId || "未命名用户";
}

function userMeta(user) {
  if (user?.missing) return "用户记录不可用";
  const parts = [user?.phone, user?.email].filter(Boolean);
  return parts.length ? parts.join(" · ") : user?.targetId || "无附加信息";
}

function fallbackUser(id) {
  return { id, targetId: id, name: "未知用户", missing: true };
}

function filterUsers(list, keyword) {
  const term = keyword.trim().toLowerCase();
  if (!term) return list;
  return list.filter((user) =>
    [userLabel(user), userMeta(user), user?.targetId].some((value) =>
      String(value || "").toLowerCase().includes(term)
    )
  );
}
</script>

<template>
  <KCard class="management-card user-group-panel">
    <KCardHeader>
      <div class="panel-heading">
        <div>
          <KCardTitle>用户分组管理</KCardTitle>
          <KCardDescription>分组属应用与渠道组合。选择作用域后维护分组树，并把已导入用户加入分组。</KCardDescription>
        </div>
        <KStatusBadge label="多组成员" variant="violet" />
      </div>
    </KCardHeader>
    <KCardContent>
      <div class="sub-section" style="border-top: none; padding-top: 0;">
        <div class="sub-section-head">
          <span class="section-title">应用分组</span>
          <div class="scope-selector-inline">
            <ElSelect v-model="selectedAppId" placeholder="选择应用" :class="{ 'full-width': true }">
              <ElOption v-for="app in applications" :key="app.id" :value="app.id" :label="app.name" />
            </ElSelect>
            <ElSelect v-model="selectedChannelId" placeholder="选择渠道" :disabled="!selectedAppId" :class="{ 'full-width': true }">
              <ElOption v-for="ch in selectableChannels" :key="ch.id" :value="ch.id" :label="`${ch.name}（${channelLabel(ch.channelType)}）`" />
            </ElSelect>
            <KButton size="sm" :disabled="!selectedAppId || !selectedChannelId" @click="openCreateGroup('')">新建根分组</KButton>
          </div>
        </div>
        <template v-if="selectedAppId && selectedChannelId">
          <p v-if="groupsLoading" class="inline-loading">正在加载分组树...</p>
          <div v-else-if="groups.length" class="group-layout">
            <div class="group-tree-wrap">
              <GroupTreeSelect
                v-model="selectedGroupId"
                :data="groupTree"
                placeholder="搜索分组名称"
                empty-text="没有匹配的分组"
                @select="selectGroup"
                @sub-create="(node) => openCreateGroup(node.id)"
                @edit="openEditGroup"
                @delete="deleteGroupNode"
                @remove-member="removeGroupMember"
              />
            </div>
            <div class="group-members">
              <template v-if="selectedGroupId">
                <div class="sub-section-head member-section-head">
                  <span class="section-title">可加入用户{{ selectedGroup ? ` - ${selectedGroup.name}` : "" }}</span>
                  <span v-if="membersSaving" class="inline-saving">正在保存...</span>
                </div>
                <p v-if="!isOrgSource && usersLoading" class="inline-loading">正在加载可加入用户...</p>
                <div v-else class="member-content">
                  <div class="member-content-toolbar">
                    <div v-if="isOrgChannel" class="member-source-toggle" aria-label="选择用户来源">
                      <button
                        type="button"
                        :class="{ active: memberSource === 'org' }"
                        @click="memberSource = 'org'"
                      >
                        飞书组织架构
                      </button>
                      <button
                        type="button"
                        :class="{ active: memberSource === 'manual' }"
                        @click="memberSource = 'manual'"
                      >
                        手动添加用户
                      </button>
                    </div>
                    <div v-if="!isOrgSource" class="member-search">
                      <svg viewBox="0 0 20 20" fill="none" aria-hidden="true">
                        <circle cx="9" cy="9" r="6.5" stroke="currentColor" stroke-width="1.6" />
                        <path d="M14 14L18 18" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" />
                      </svg>
                      <input v-model="memberKeyword" type="text" placeholder="搜索用户名称、手机号或邮箱" />
                      <button v-if="memberKeyword" type="button" aria-label="清除搜索" @click="memberKeyword = ''">
                        <svg viewBox="0 0 14 14" fill="none" aria-hidden="true">
                          <path d="M2 2L12 12M12 2L2 12" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" />
                        </svg>
                      </button>
                    </div>
                    <span class="member-count">
                      {{ isOrgSource ? `已选 ${orgCheckedTargets.length} 人` : `${availableMemberUsers.length} / ${users.length} 人` }}
                    </span>
                  </div>

                  <section class="member-content-panel member-content-panel--single">
                    <header>
                      <span>{{ isOrgSource ? "飞书组织架构" : "手动添加用户" }}</span>
                      <small v-if="isOrgSource">从部门树选择用户加入分组</small>
                      <small v-else>{{ filteredAvailableMemberUsers.length }} / {{ availableMemberUsers.length }}</small>
                    </header>

                    <!-- Feishu: org structure tree -->
                    <template v-if="isOrgSource">
                      <p v-if="orgLoading" class="inline-loading">正在拉取组织架构...</p>
                      <p v-else-if="orgError" class="inline-error">{{ orgError }}</p>
                      <template v-else>
                        <MemberTreePicker
                          :org-tree="orgTree"
                          :selected-target-ids="groupMembers.map(id => (userById.get(id)?.targetId)).filter(Boolean)"
                          root-label="组织架构"
                          empty-text="暂无组织架构数据，请检查飞书通讯录权限范围"
                          @selection-change="onOrgSelectionChange"
                        />
                        <div class="org-actions">
                          <KButton
                            size="sm"
                            :disabled="!orgCheckedTargets.length || membersSaving"
                            @click="addOrgMembersToGroup"
                          >
                            {{ membersSaving ? "加入中..." : `加入分组 (${orgCheckedTargets.length})` }}
                          </KButton>
                        </div>
                      </template>
                    </template>

                    <!-- Imported/manual users: shown for all channels, including Feishu. -->
                    <template v-else>
                      <div v-if="filteredAvailableMemberUsers.length" class="member-list">
                        <article v-for="user in filteredAvailableMemberUsers" :key="user.id" class="member-list-item">
                          <span class="member-avatar" :style="{ background: colorFor(userLabel(user)) }">
                            {{ initials(userLabel(user)) }}
                          </span>
                          <span class="member-text">
                            <strong>{{ userLabel(user) }}</strong>
                            <small>{{ userMeta(user) }}</small>
                          </span>
                          <KButton
                            size="sm"
                            :disabled="membersSaving"
                            @click="addGroupMember(user.id)"
                          >
                            加入
                          </KButton>
                        </article>
                      </div>
                      <div v-else class="member-empty">
                        <strong>{{ memberKeyword ? "没有匹配用户" : "暂无可加入用户" }}</strong>
                        <span>{{ memberKeyword ? "换个关键词再试。" : "当前渠道用户都已在该分组中。" }}</span>
                      </div>
                    </template>
                  </section>
                </div>
              </template>
              <KEmptyState v-else title="未选择分组" description="点击左侧分组节点查看与管理成员。" />
            </div>
          </div>
          <KEmptyState v-else title="该应用在此渠道暂无分组" description="新建根分组开始组织用户。" />
        </template>
        <KEmptyState v-else title="请先选择应用与渠道" description="选择已授权的渠道后，即可维护该作用域下的分组。" />
      </div>
    </KCardContent>
  </KCard>

  <!-- Group create/edit dialog -->
  <KDialog v-model:open="groupDialogOpen">
    <KDialogOverlay />
    <KDialogContent>
      <KDialogHeader>
        <KDialogTitle>{{ groupEditing ? "编辑分组" : "新建分组" }}</KDialogTitle>
        <KDialogDescription>可选父分组，留空为根节点。</KDialogDescription>
      </KDialogHeader>
      <div class="test-form">
        <label>
          分组名称
          <KInput v-model="groupForm.name" placeholder="分组名称" />
        </label>
        <label>
          父分组
          <div class="parent-picker">
            <button
              type="button"
              class="parent-root-option"
              :class="{ active: !groupForm.parentId }"
              @click="groupForm.parentId = ''"
            >
              根节点
            </button>
            <GroupTreeSelect
              v-if="parentGroupTree.length"
              v-model="groupForm.parentId"
              :data="parentGroupTree"
              compact
              placeholder="搜索父分组"
              empty-text="没有可选父分组"
            />
          </div>
        </label>
      </div>
      <KDialogFooter>
        <KButton variant="outline" @click="groupDialogOpen = false">取消</KButton>
        <KButton :disabled="!groupForm.name" @click="saveGroup">保存</KButton>
      </KDialogFooter>
    </KDialogContent>
  </KDialog>
</template>
