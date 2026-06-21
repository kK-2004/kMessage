<script setup>
import {
  KAlert,
  KAlertDescription,
  KAlertTitle,
  KButton,
  KCard,
  KCardContent,
  KCardDescription,
  KCardHeader,
  KCardTitle,
  KCopyButton,
  KDialog,
  KDialogContent,
  KDialogDescription,
  KDialogFooter,
  KDialogHeader,
  KDialogTitle,
  KEmptyState,
  KInput,
  KLongText,
  KStatusBadge,
  KSwitch,
  KTextarea,
} from "@kk-2004/ui-components";
import { ElSelect, ElOption, ElMessage, ElTable, ElTableColumn, ElPagination } from "element-plus";
import { ref, computed, reactive, watch } from "vue";
import { adminApi } from "../../services/adminApi";

const props = defineProps({
  applications: { type: Array, default: () => [] },
  channels: { type: Array, default: () => [] },
  channelTypes: { type: Array, default: () => [] },
  form: { type: Object, required: true },
  selectedChannelType: { type: Object, default: null },
  channelLabel: { type: Function, required: true },
});

const emit = defineEmits(["create", "edit", "delete", "users-imported"]);

const channelTypeMap = computed(() => {
  const map = {};
  for (const t of props.channelTypes) map[t.type] = t;
  return map;
});

// Selectable channel types: implemented adapters only.
const selectableTypes = computed(() => props.channelTypes.filter((t) => t.implemented));

// If the current form type becomes unavailable, fall back to the first selectable type.
watch(
  selectableTypes,
  (list) => {
    if (list.length && !list.some((t) => t.type === props.form.type)) {
      props.form.type = list[0].type;
    }
  },
  { immediate: true }
);

const MANUAL_ENTRY = "__manual__";

// Send message dialog state
const sendDialogOpen = ref(false);
const sendChannel = ref(null);
const contacts = ref([]);
const contactsLoading = ref(false);
const contactsFailed = ref(false);
const sendForm = reactive({ target: "", manualTarget: "", text: "测试消息" });
const sendSending = ref(false);
const sendResult = ref(null);

async function openSendDialog(channel) {
  sendChannel.value = channel;
  contacts.value = [];
  contactsFailed.value = false;
  sendForm.target = "";
  sendForm.manualTarget = "";
  sendForm.text = "测试消息";
  sendResult.value = null;
  sendDialogOpen.value = true;
  contactsLoading.value = true;
  try {
    contacts.value = await adminApi.listChannelContacts(channel.id);
    sendForm.target = contacts.value.length ? contacts.value[0].id : MANUAL_ENTRY;
  } catch {
    contactsFailed.value = true;
    sendForm.target = MANUAL_ENTRY;
  } finally {
    contactsLoading.value = false;
  }
}

const sendTargetHint = computed(() => {
  if (!sendChannel.value) return "";
  return channelTypeMap.value[sendChannel.value.channelType]?.targetHint || "";
});

const useManualTarget = computed(() => sendForm.target === MANUAL_ENTRY);
const effectiveTarget = computed(() =>
  useManualTarget.value ? sendForm.manualTarget : sendForm.target
);
const canSend = computed(() => Boolean(effectiveTarget.value) && Boolean(sendForm.text));

async function doSend() {
  sendSending.value = true;
  sendResult.value = null;
  try {
    sendResult.value = await adminApi.sendMessage(sendChannel.value.id, {
      target: effectiveTarget.value,
      text: sendForm.text,
    });
  } catch (e) {
    sendResult.value = { type: "ERROR", errorCode: "REQUEST_FAILED", diagnostic: e.message };
  } finally {
    sendSending.value = false;
  }
}

const canCreate = computed(
  () => Boolean(props.form.name) && Boolean(props.form.credentialRef) && props.form.type
);

// Channel user viewer
const usersDialogOpen = ref(false);
const usersChannel = ref(null);
const channelUsers = ref([]);
const channelUsersLoading = ref(false);
const deletingUserId = ref("");
const userPageSizes = [10, 20, 50, 100];
const userPage = reactive({ pageNum: 1, pageSize: 10, total: 0 });

async function openUsersDialog(channel) {
  usersChannel.value = channel;
  channelUsers.value = [];
  userPage.pageNum = 1;
  userPage.pageSize = 10;
  userPage.total = 0;
  await loadChannelUsers(channel);
  usersDialogOpen.value = true;
}

async function loadChannelUsers(channel) {
  if (!channel) return;
  channelUsersLoading.value = true;
  try {
    const page = await adminApi.listAppUsers(channel.id, {
      pageNum: userPage.pageNum,
      pageSize: userPage.pageSize,
    });
    channelUsers.value = page.records || [];
    userPage.pageNum = Number(page.pageNum || userPage.pageNum);
    userPage.pageSize = Math.max(Number(page.pageSize || userPage.pageSize), 10);
    userPage.total = Number(page.total || 0);
  } catch (e) {
    channelUsers.value = [];
    userPage.total = 0;
    ElMessage.error(e.message || "用户加载失败");
  } finally {
    channelUsersLoading.value = false;
  }
}

async function changeUserPage(pageNum) {
  userPage.pageNum = pageNum;
  await loadChannelUsers(usersChannel.value);
}

async function changeUserPageSize(pageSize) {
  userPage.pageSize = Math.max(Number(pageSize), 10);
  userPage.pageNum = 1;
  await loadChannelUsers(usersChannel.value);
}

async function deleteChannelUser(row) {
  if (!usersChannel.value || !row?.id) return;
  const label = displayUserName(row) === "未命名用户" ? row.targetId || row.id : displayUserName(row);
  if (!window.confirm(`确定从渠道用户池删除「${label}」吗？该用户也会从相关分组中移除。`)) return;
  deletingUserId.value = row.id;
  try {
    await adminApi.deleteAppUser(usersChannel.value.id, row.id);
    ElMessage.success("用户已删除");
    await loadChannelUsers(usersChannel.value);
    if (!channelUsers.value.length && userPage.total > 0 && userPage.pageNum > 1) {
      userPage.pageNum -= 1;
      await loadChannelUsers(usersChannel.value);
    }
    emit("users-imported", { channelId: usersChannel.value.id });
  } catch (e) {
    ElMessage.error(e.message || "删除失败");
  } finally {
    deletingUserId.value = "";
  }
}

function displayUserName(row) {
  const name = row?.name?.trim();
  if (name && name !== row.targetId) return name;
  return "未命名用户";
}

function formatTime(value) {
  if (!value) return "-";
  return new Intl.DateTimeFormat("zh-CN", {
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
  }).format(new Date(value));
}

// Phone/email columns only make sense for Feishu (which imports via phone/email).
// Other channels (Telegram) never populate them, so hide the columns there.
const showContactColumns = computed(() => usersChannel.value?.channelType === "FEISHU");

// Feishu-only user import. Users are stored under a channel and shared by applications
// that bind to the channel.
const importDialogOpen = ref(false);
const importChannel = ref(null);
const importText = ref("");
const importResult = ref(null);
const importing = ref(false);

function canBatchImport(channel) {
  return channel.enabled && channel.channelType === "FEISHU";
}

async function openImportDialog(channel) {
  importChannel.value = channel;
  importText.value = "";
  importResult.value = null;
  importDialogOpen.value = true;
}

// 从「查看用户」模态框右上角进入批量导入：先关掉查看用户框，再打开导入框，避免两层模态叠加。
function openImportFromUsers(channel) {
  usersDialogOpen.value = false;
  openImportDialog(channel);
}

function parseLines(text) {
  return text
    .split(/[\n,]/)
    .map((s) => s.trim())
    .filter(Boolean);
}

async function doImportUsers() {
  if (!importChannel.value) return;
  const lines = parseLines(importText.value);
  // Accept mobile numbers with optional + and country code (e.g. +8613..., 138...).
  const mobiles = lines.filter((l) => /^\+?\d{7,}$/.test(l));
  const emails = lines.filter((l) => l.includes("@") && /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(l));
  importing.value = true;
  try {
    importResult.value = await adminApi.importUsers(importChannel.value.id, { mobiles, emails });
    if (usersChannel.value?.id === importChannel.value.id) {
      await loadChannelUsers(importChannel.value);
    }
    emit("users-imported", { channelId: importChannel.value.id });
  } catch (e) {
    ElMessage.error(e.message || "导入失败");
  } finally {
    importing.value = false;
  }
}

// Edit dialog state
const editDialogOpen = ref(false);
const editChannelRef = ref(null);
const editForm = reactive({ name: "", enabled: false, credentialRef: "", configJson: "{}" });

function openEditDialog(channel) {
  editChannelRef.value = channel;
  editForm.name = channel.name;
  editForm.enabled = channel.enabled;
  editForm.credentialRef = ""; // leave blank = unchanged (credential is redacted server-side)
  editForm.configJson = channel.configJson || "{}";
  editDialogOpen.value = true;
}

const editCredentialHint = computed(() => {
  if (!editChannelRef.value) return "";
  return channelTypeMap.value[editChannelRef.value.channelType]?.credentialHint || "";
});

function submitEdit() {
  if (!editChannelRef.value) return;
  // Only send credentialRef when the user typed a new value; blank means "unchanged".
  const payload = {
    name: editForm.name,
    enabled: editForm.enabled,
    configJson: editForm.configJson,
  };
  if (editForm.credentialRef.trim()) payload.credentialRef = editForm.credentialRef.trim();
  emit("edit", editChannelRef.value.id, payload);
  editDialogOpen.value = false;
}
</script>

<template>
  <KCard class="management-card channel-panel">
    <KCardHeader>
      <div class="panel-heading">
        <div>
          <KCardTitle>渠道管理</KCardTitle>
          <KCardDescription>配置消息渠道、凭据引用、启用状态和测试发送。</KCardDescription>
        </div>
        <KStatusBadge :label="`${channels.length} 个渠道`" variant="cyan" />
      </div>
    </KCardHeader>
    <KCardContent>
      <form class="channel-form" @submit.prevent="canCreate && emit('create')">
        <div class="field">
          <label>渠道名称</label>
          <KInput v-model="form.name" placeholder="渠道名称" />
        </div>
        <div class="field">
          <label>渠道类型</label>
          <ElSelect v-model="form.type" placeholder="选择渠道类型">
            <ElOption
              v-for="ct in channelTypes"
              :key="ct.type"
              :value="ct.type"
              :label="ct.label + (ct.implemented ? '' : '（暂未开放）')"
              :disabled="!ct.implemented"
            />
          </ElSelect>
        </div>
        <div class="field">
          <label>凭据引用</label>
          <KInput
            v-model="form.credentialRef"
            :placeholder="selectedChannelType?.credentialHint || 'env:SECRET_NAME'"
          />
        </div>
        <div class="form-actions">
          <KButton type="submit" :disabled="!canCreate">创建渠道</KButton>
        </div>
      </form>

      <details v-if="selectedChannelType" :key="selectedChannelType.type" class="setup-guide">
        <summary class="setup-guide-summary">
          <span class="setup-guide-title">{{ selectedChannelType.label }} 接入指南</span>
          <span class="setup-guide-toggle" aria-hidden="true"></span>
        </summary>
        <div class="setup-guide-body">
          <p class="setup-guide-description">{{ selectedChannelType.description }}</p>
          <p class="setup-steps" v-html="selectedChannelType.setupGuide.replace(/\n/g, '<br/>')"></p>
        </div>
      </details>

      <div v-if="channels.length" class="channel-grid">
        <article v-for="channel in channels" :key="channel.id" class="channel-tile">
          <div class="channel-tile-head">
            <div class="channel-icon" :class="{ disabled: !channel.enabled }">
              {{ channelLabel(channel.channelType).slice(0, 1) }}
            </div>
            <div>
              <h3>{{ channel.name }}</h3>
              <p>{{ channelLabel(channel.channelType) }}</p>
            </div>
            <KStatusBadge
              :label="channel.enabled ? '启用' : '停用'"
              :variant="channel.enabled ? 'success' : 'neutral'"
              :pulse="channel.enabled"
            />
          </div>
          <dl class="channel-facts">
            <div>
              <dt>凭据</dt>
              <dd><KLongText :max-length="36" class="credential-text">{{ channel.credential }}</KLongText></dd>
            </div>
            <div>
              <dt>渠道类型</dt>
              <dd><KStatusBadge :label="channelLabel(channel.channelType)" variant="blue" /></dd>
            </div>
          </dl>
          <div class="resource-actions">
            <KButton v-if="channel.enabled" size="sm" @click="openSendDialog(channel)">发消息</KButton>
            <KButton variant="outline" size="sm" @click="openUsersDialog(channel)">查看用户</KButton>
            <KButton variant="outline" size="sm" @click="openEditDialog(channel)">编辑</KButton>
            <KButton variant="destructive" size="sm" @click="$emit('delete', channel)">删除</KButton>
          </div>
        </article>
      </div>
      <KEmptyState v-else title="暂无渠道" description="创建渠道实例后即可授权给业务应用。" />
    </KCardContent>
  </KCard>

  <KDialog v-model:open="sendDialogOpen">
    <KDialogContent>
      <KDialogHeader>
        <KDialogTitle>发送消息 — {{ sendChannel?.name }}</KDialogTitle>
        <KDialogDescription>从渠道拉取的聊天或已导入用户中选择目标，或手动输入。</KDialogDescription>
      </KDialogHeader>
      <div class="test-form">
        <dl class="test-info">
          <dt>channelInstanceId</dt>
          <dd><code>{{ sendChannel?.id }}</code> <KCopyButton :value="sendChannel?.id" tooltip="复制 ID" /></dd>
        </dl>

        <label>
          目标
          <span v-if="contactsLoading" class="hint">正在拉取联系人列表...</span>
          <span v-else-if="contactsFailed" class="hint">联系人拉取失败，请手动输入目标。</span>
          <span v-else-if="contacts.length === 0" class="hint">未拉取到联系人或已导入用户，请手动输入目标。</span>
          <span v-else class="hint">共 {{ contacts.length }} 个可选目标。</span>
          <ElSelect
            v-if="!contactsLoading && (contacts.length || contactsFailed)"
            v-model="sendForm.target"
            placeholder="选择目标"
            :class="{ 'full-width': true }"
          >
            <ElOption
              v-for="opt in contacts"
              :key="opt.id"
              :value="opt.id"
              :label="`${opt.label}（${opt.type}）`"
            />
            <ElOption :value="MANUAL_ENTRY" label="手动输入" />
          </ElSelect>
        </label>

        <label v-if="useManualTarget">
          <span>手动输入 target</span>
          <span class="hint">{{ sendTargetHint }}</span>
          <KInput v-model="sendForm.manualTarget" :placeholder="sendTargetHint" />
        </label>

        <label>
          消息内容
          <KTextarea v-model="sendForm.text" placeholder="输入消息内容" auto-resize />
        </label>

        <div v-if="sendResult" class="test-result">
          <KAlert :variant="sendResult.type === 'SUCCESS' ? 'success' : 'destructive'">
            <KAlertTitle>{{ sendResult.type === 'SUCCESS' ? '发送成功' : '发送失败' }}</KAlertTitle>
            <KAlertDescription v-if="sendResult.type !== 'SUCCESS'">
              {{ sendResult.errorCode }}：{{ sendResult.diagnostic }}
            </KAlertDescription>
          </KAlert>
        </div>
      </div>
      <KDialogFooter>
        <KButton variant="outline" @click="sendDialogOpen = false">关闭</KButton>
        <KButton :disabled="!canSend || sendSending" @click="doSend">
          {{ sendSending ? "发送中..." : "发送" }}
        </KButton>
      </KDialogFooter>
    </KDialogContent>
  </KDialog>

  <KDialog v-model:open="usersDialogOpen">
    <KDialogContent class="dialog-wide dialog-channel-users">
      <div class="channel-users-header">
        <KDialogHeader class="channel-users-header-titles">
          <KDialogTitle>渠道用户 — {{ usersChannel?.name }}</KDialogTitle>
          <KDialogDescription>查看该渠道已同步或导入的用户，分组页可从这里的用户池中选择成员。</KDialogDescription>
        </KDialogHeader>
        <KButton
          v-if="canBatchImport(usersChannel)"
          variant="outline"
          size="sm"
          class="channel-users-import-btn"
          @click="openImportFromUsers(usersChannel)"
        >
          批量添加用户
        </KButton>
      </div>
      <div class="channel-users-body">
        <p v-if="channelUsersLoading" class="inline-loading channel-users-loading">正在加载渠道用户...</p>
        <ElTable
          v-else-if="channelUsers.length"
          :data="channelUsers"
          class="k-table channel-users-table"
          stripe
          size="small"
          height="100%"
        >
          <ElTableColumn label="名称" width="160">
            <template #default="{ row }">
              <span class="cell-name">{{ displayUserName(row) }}</span>
            </template>
          </ElTableColumn>
          <ElTableColumn label="targetId" min-width="360">
            <template #default="{ row }">
              <span class="target-cell">
                <KLongText :max-length="44" class="credential-text">{{ row.targetId }}</KLongText>
                <KCopyButton :value="row.targetId" tooltip="复制 targetId" />
              </span>
            </template>
          </ElTableColumn>
          <ElTableColumn v-if="showContactColumns" label="手机号" width="140">
            <template #default="{ row }">{{ row.phone || "-" }}</template>
          </ElTableColumn>
          <ElTableColumn v-if="showContactColumns" label="邮箱" min-width="180">
            <template #default="{ row }">{{ row.email || "-" }}</template>
          </ElTableColumn>
          <ElTableColumn label="加入时间" width="128">
            <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
          </ElTableColumn>
          <ElTableColumn label="操作" width="104" fixed="right" align="right">
            <template #default="{ row }">
              <KButton
                variant="destructive"
                size="sm"
                :disabled="deletingUserId === row.id"
                @click="deleteChannelUser(row)"
              >
                {{ deletingUserId === row.id ? "删除中..." : "删除" }}
              </KButton>
            </template>
          </ElTableColumn>
        </ElTable>
        <KEmptyState
          v-else
          class="channel-users-empty"
          title="暂无用户"
          description="飞书渠道可通过批量添加用户导入；其他渠道会在联系人拉取后同步到用户池。"
        />
        <ElPagination
          v-if="!channelUsersLoading && userPage.total > 0"
          class="channel-users-pagination"
          background
          :current-page="userPage.pageNum"
          :page-size="userPage.pageSize"
          :page-sizes="userPageSizes"
          :total="userPage.total"
          layout="total, sizes, prev, pager, next, jumper"
          @current-change="changeUserPage"
          @size-change="changeUserPageSize"
        />
      </div>
      <KDialogFooter>
        <KButton variant="outline" @click="usersDialogOpen = false">关闭</KButton>
      </KDialogFooter>
    </KDialogContent>
  </KDialog>

  <KDialog v-model:open="importDialogOpen">
    <KDialogContent>
      <KDialogHeader>
        <KDialogTitle>批量添加用户 — {{ importChannel?.name }}</KDialogTitle>
        <KDialogDescription>仅飞书渠道支持。每行一个手机号或邮箱，将解析为飞书 open_id 并加入该渠道用户池。</KDialogDescription>
      </KDialogHeader>
      <div class="test-form">
        <label>
          手机号 / 邮箱（每行一个）
          <KTextarea v-model="importText" placeholder="13800000001&#10;user@example.com" auto-resize />
        </label>
        <div v-if="importResult" class="import-result">
          <KStatusBadge :label="`成功 ${importResult.importedCount}`" variant="success" />
          <KStatusBadge v-if="importResult.unresolved.length" :label="`失败 ${importResult.unresolved.length}`" variant="danger" />
          <p v-if="importResult.error" class="hint import-error">{{ importResult.error }}</p>
          <p v-if="importResult.unresolved.length" class="hint">未解析：{{ importResult.unresolved.join("、") }}</p>
        </div>
      </div>
      <KDialogFooter>
        <KButton variant="outline" @click="importDialogOpen = false">关闭</KButton>
        <KButton :disabled="!importText.trim() || importing" @click="doImportUsers">
          {{ importing ? "添加中..." : "添加用户" }}
        </KButton>
      </KDialogFooter>
    </KDialogContent>
  </KDialog>

  <KDialog v-model:open="editDialogOpen">
    <KDialogContent>
      <KDialogHeader>
        <KDialogTitle>编辑渠道 — {{ editChannelRef?.name }}</KDialogTitle>
        <KDialogDescription>修改渠道名称、启用状态、凭据或高级配置。</KDialogDescription>
      </KDialogHeader>
      <div class="test-form">
        <label>
          渠道名称
          <KInput v-model="editForm.name" placeholder="渠道名称" />
        </label>
        <label class="switch-row">
          <span>启用状态</span>
          <KSwitch v-model="editForm.enabled" />
          <span class="hint">{{ editForm.enabled ? "启用" : "停用" }}</span>
        </label>
        <label>
          凭据引用
          <span class="hint">留空表示不修改（凭据已脱敏存储）</span>
          <KInput v-model="editForm.credentialRef" :placeholder="editCredentialHint" />
        </label>
        <label>
          高级配置 (configJson)
          <span class="hint">JSON 格式，如飞书 {"receive_id_type":"user_id"}</span>
          <KTextarea v-model="editForm.configJson" placeholder="{}" auto-resize />
        </label>
      </div>
      <KDialogFooter>
        <KButton variant="outline" @click="editDialogOpen = false">取消</KButton>
        <KButton :disabled="!editForm.name" @click="submitEdit">保存</KButton>
      </KDialogFooter>
    </KDialogContent>
  </KDialog>
</template>
