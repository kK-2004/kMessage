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
  KDialogTitle,
  KEmptyState,
  KInput,
  KMaskedValueDisplay,
  KMultiSelect,
  KStatusBadge,
} from "@kk-2004/ui-components";
import { ref, computed } from "vue";
import { maskValue } from "../../utils/format";
import { adminApi } from "../../services/adminApi";

const props = defineProps({
  applications: { type: Array, default: () => [] },
  form: { type: Object, required: true },
  channels: { type: Array, default: () => [] },
  channelLabel: { type: Function, required: true },
});

const emit = defineEmits(["create", "manage-groups", "rotate", "delete"]);

const createDialogOpen = ref(false);
const bindDialogOpen = ref(false);
const bindApp = ref(null);
const selectedChannelIds = ref([]);
const bindLoading = ref(false);
const canCreate = computed(() => Boolean((props.form.name || "").trim()));

const channelOptions = computed(() =>
  props.channels.map((c) => ({
    value: c.id,
    label: `${c.name}（${props.channelLabel(c.channelType)}）`,
  }))
);

function openCreateDialog() {
  createDialogOpen.value = true;
}

function submitCreate() {
  if (!canCreate.value) return;
  emit("create");
  createDialogOpen.value = false;
}

function openGroups(application) {
  emit("manage-groups", application);
}

async function openBindDialog(application) {
  bindApp.value = application;
  bindDialogOpen.value = true;
  bindLoading.value = true;
  try {
    const granted = await adminApi.listApplicationChannels(application.id);
    selectedChannelIds.value = [...granted];
  } catch {
    selectedChannelIds.value = [];
  } finally {
    bindLoading.value = false;
  }
}

async function saveBindings() {
  if (!bindApp.value) return;
  bindLoading.value = true;
  try {
    const current = await adminApi.listApplicationChannels(bindApp.value.id);
    const toGrant = selectedChannelIds.value.filter((id) => !current.includes(id));
    const toRevoke = current.filter((id) => !selectedChannelIds.value.includes(id));
    await Promise.all([
      ...toGrant.map((id) => adminApi.changeGrant(bindApp.value.id, id, "PUT")),
      ...toRevoke.map((id) => adminApi.changeGrant(bindApp.value.id, id, "DELETE")),
    ]);
    bindDialogOpen.value = false;
  } catch {
    // error handled by api layer
  } finally {
    bindLoading.value = false;
  }
}
</script>

<template>
  <KCard class="management-card application-panel">
    <KCardHeader>
      <div class="panel-heading">
        <div>
          <KCardTitle>应用管理</KCardTitle>
          <KCardDescription>管理业务应用、调用凭据及可使用渠道。</KCardDescription>
        </div>
        <div class="panel-heading-actions">
          <KStatusBadge :label="`${applications.length} 个应用`" variant="blue" />
          <KButton size="sm" @click="openCreateDialog">创建应用</KButton>
        </div>
      </div>
    </KCardHeader>
    <KCardContent>
      <div v-if="applications.length" class="resource-list application-list">
        <article
          v-for="application in applications"
          :key="application.id"
          class="resource-card app-resource-card"
          role="button"
          tabindex="0"
          :aria-label="`进入 ${application.name} 的用户分组`"
          @click="openGroups(application)"
          @keydown.enter.prevent="openGroups(application)"
          @keydown.space.prevent="openGroups(application)"
        >
          <div class="resource-accent"></div>
          <div class="resource-main">
            <div class="resource-title-row">
              <div>
                <h3>{{ application.name }}</h3>
                <p>点击应用进入该应用的用户分组；独立 appKey 适用于业务侧 API 调用。</p>
              </div>
              <KStatusBadge label="已激活" variant="success" />
            </div>

            <div class="credential-row" @click.stop>
              <span>appKey</span>
              <KMaskedValueDisplay
                v-if="application.appKey"
                label="appKey"
                :full-value="application.appKey"
                :masked-value="maskValue(application.appKey)"
                copy-tooltip="复制 appKey"
              />
              <span v-else class="muted-value">暂未返回</span>
            </div>

            <div class="resource-actions">
              <KButton size="sm" @click.stop="openGroups(application)">用户分组</KButton>
              <KButton variant="outline" size="sm" @click.stop="openBindDialog(application)">绑定渠道</KButton>
              <KButton variant="outline" size="sm" @click.stop="$emit('rotate', application.id)">轮换 Secret</KButton>
              <KButton variant="destructive" size="sm" @click.stop="$emit('delete', application)">删除</KButton>
            </div>
          </div>
        </article>
      </div>
      <KEmptyState v-else title="暂无应用" description="创建第一个业务应用以获得调用凭据。" />
    </KCardContent>
  </KCard>

  <KDialog v-model:open="createDialogOpen">
    <KDialogContent>
      <KDialogHeader>
        <KDialogTitle>创建应用</KDialogTitle>
        <KDialogDescription>创建后会返回 appKey 与一次性 AppSecret，请立即保存。</KDialogDescription>
      </KDialogHeader>
      <form class="test-form" @submit.prevent="submitCreate">
        <label>
          应用名称
          <KInput v-model="form.name" placeholder="例如：billing-service" />
        </label>
      </form>
      <KDialogFooter>
        <KButton variant="outline" @click="createDialogOpen = false">取消</KButton>
        <KButton :disabled="!canCreate" @click="submitCreate">创建</KButton>
      </KDialogFooter>
    </KDialogContent>
  </KDialog>

  <KDialog v-model:open="bindDialogOpen">
    <KDialogContent>
      <KDialogHeader>
        <KDialogTitle>绑定渠道 — {{ bindApp?.name }}</KDialogTitle>
        <KDialogDescription>选择该应用可使用的渠道实例。</KDialogDescription>
      </KDialogHeader>
      <KMultiSelect
        v-model="selectedChannelIds"
        :options="channelOptions"
        :searchable="true"
        placeholder="搜索并选择渠道..."
      />
      <KDialogFooter>
        <KButton variant="outline" @click="bindDialogOpen = false">取消</KButton>
        <KButton :disabled="bindLoading" @click="saveBindings">
          {{ bindLoading ? "保存中..." : "保存" }}
        </KButton>
      </KDialogFooter>
    </KDialogContent>
  </KDialog>
</template>
