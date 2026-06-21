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

const emit = defineEmits(["create", "rotate", "delete"]);

const bindDialogOpen = ref(false);
const bindApp = ref(null);
const selectedChannelIds = ref([]);
const bindLoading = ref(false);

const channelOptions = computed(() =>
  props.channels.map((c) => ({
    value: c.id,
    label: `${c.name}（${props.channelLabel(c.channelType)}）`,
  }))
);

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
        <KStatusBadge :label="`${applications.length} 个应用`" variant="blue" />
      </div>
    </KCardHeader>
    <KCardContent>
      <form class="create-strip" @submit.prevent="$emit('create')">
        <div class="field-stack">
          <span>应用名称</span>
          <KInput v-model="form.name" placeholder="例如：billing-service" />
        </div>
        <KButton type="submit" size="lg" :disabled="!form.name">创建新应用</KButton>
      </form>

      <div v-if="applications.length" class="resource-list application-list">
        <article v-for="application in applications" :key="application.id" class="resource-card app-resource-card">
          <div class="resource-accent"></div>
          <div class="resource-main">
            <div class="resource-title-row">
              <div>
                <h3>{{ application.name }}</h3>
                <p>独立 appKey 与 appSecret，适用于业务侧 API 调用。</p>
              </div>
              <KStatusBadge label="已激活" variant="success" />
            </div>

            <div class="credential-row">
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
              <KButton size="sm" @click="openBindDialog(application)">绑定渠道</KButton>
              <KButton variant="outline" size="sm" @click="$emit('rotate', application.id)">轮换 Secret</KButton>
              <KButton variant="destructive" size="sm" @click="$emit('delete', application)">删除</KButton>
            </div>
          </div>
        </article>
      </div>
      <KEmptyState v-else title="暂无应用" description="创建第一个业务应用以获得调用凭据。" />
    </KCardContent>
  </KCard>

  <KDialog v-model:open="bindDialogOpen">
    <KDialogOverlay />
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
