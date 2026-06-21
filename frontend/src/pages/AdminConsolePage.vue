<script setup>
import { computed, onMounted, onUnmounted, ref } from "vue";
import { KEmptyState } from "@kk-2004/ui-components";
import AdminTopbar from "../components/admin/AdminTopbar.vue";
import ApplicationPanel from "../components/admin/ApplicationPanel.vue";
import ChangePasswordDialog from "../components/admin/ChangePasswordDialog.vue";
import ChannelPanel from "../components/admin/ChannelPanel.vue";
import ConsoleHero from "../components/admin/ConsoleHero.vue";
import SecretAlert from "../components/admin/SecretAlert.vue";
import StatsDashboard from "../components/admin/StatsDashboard.vue";
import UserGroupPanel from "../components/admin/UserGroupPanel.vue";

const props = defineProps({
  loading: { type: Boolean, default: false },
  applications: { type: Array, default: () => [] },
  channels: { type: Array, default: () => [] },
  channelTypes: { type: Array, default: () => [] },
  secret: { type: Object, default: null },
  adminUsername: { type: String, default: "" },
  stats: { type: Object, default: null },
  applicationForm: { type: Object, required: true },
  channelForm: { type: Object, required: true },
  selectedChannelType: { type: Object, default: null },
  channelLabel: { type: Function, required: true },
  onChangePassword: { type: Function, required: true },
});

const enabledChannelCount = computed(() => props.channels.filter((channel) => channel.enabled).length);
const unavailableTypeCount = computed(() => props.channelTypes.filter((type) => !type.implemented).length);
const userImportRevision = ref(0);
const changePasswordOpen = ref(false);
const pages = [
  { id: "overview", label: "概览", eyebrow: "Dashboard", index: "01" },
  { id: "applications", label: "应用", eyebrow: "Applications", index: "02" },
  { id: "channels", label: "渠道", eyebrow: "Channels", index: "03" },
];
const pageIds = new Set([...pages.map((page) => page.id), "application-groups"]);
const activePage = ref("overview");
const activeApplicationId = ref("");
const activeApplication = computed(() =>
  props.applications.find((application) => application.id === activeApplicationId.value) || null
);
const currentPage = computed(() => {
  if (activePage.value === "application-groups") {
    return {
      id: "application-groups",
      label: activeApplication.value ? activeApplication.value.name : "用户分组",
      eyebrow: "应用 / 用户分组",
    };
  }
  return pages.find((page) => page.id === activePage.value) || pages[0];
});
const recentApplications = computed(() => props.applications.slice(0, 4));
const recentChannels = computed(() => props.channels.slice(0, 4));
const adminInitial = computed(() => {
  const name = (props.adminUsername || "").trim();
  return name ? name.charAt(0).toUpperCase() : "A";
});
// Vite base 是 /admin/，public 资源需拼接 base 才能正确解析。
const logoUrl = `${import.meta.env.BASE_URL}logo.png`;

function locationState() {
  if (typeof window === "undefined") return { page: "overview", applicationId: "" };
  const hash = window.location.hash.replace(/^#/, "");
  const appGroupsMatch = hash.match(/^applications\/([^/]+)\/groups$/);
  if (appGroupsMatch) {
    return {
      page: "application-groups",
      applicationId: decodeURIComponent(appGroupsMatch[1]),
    };
  }
  return pageIds.has(hash) && hash !== "application-groups"
    ? { page: hash, applicationId: "" }
    : { page: "overview", applicationId: "" };
}

function syncPageFromHistory() {
  const state = locationState();
  activePage.value = state.page;
  activeApplicationId.value = state.applicationId;
}

function switchPage(id) {
  activePage.value = id;
  activeApplicationId.value = "";
  if (typeof window !== "undefined" && window.location.hash !== `#${id}`) {
    window.history.pushState({}, "", `#${id}`);
  }
}

function openApplicationGroups(application) {
  activePage.value = "application-groups";
  activeApplicationId.value = application.id;
  const targetHash = `#applications/${encodeURIComponent(application.id)}/groups`;
  if (typeof window !== "undefined" && window.location.hash !== targetHash) {
    window.history.pushState({}, "", targetHash);
  }
}

onMounted(() => {
  syncPageFromHistory();
  window.addEventListener("popstate", syncPageFromHistory);
  window.addEventListener("hashchange", syncPageFromHistory);
});

onUnmounted(() => {
  window.removeEventListener("popstate", syncPageFromHistory);
  window.removeEventListener("hashchange", syncPageFromHistory);
});

const emit = defineEmits([
  "refresh",
  "logout",
  "create-application",
  "rotate-application",
  "delete-application",
  "create-channel",
  "edit-channel",
  "delete-channel",
]);
</script>

<template>
  <div class="console-shell">
    <aside class="side-nav" aria-label="管理后台导航">
      <button type="button" class="side-nav-brand" @click="switchPage('overview')">
        <img class="brand-mark brand-logo" :src="logoUrl" alt="kMessage logo" />
        <span>
          <strong>kMessage</strong>
          <small>统一消息平台</small>
        </span>
      </button>

      <nav class="side-nav-menu">
        <button
          v-for="page in pages"
          :key="page.id"
          type="button"
          class="side-nav-link"
          :class="{ active: activePage === page.id }"
          @click="switchPage(page.id)"
        >
          <span class="nav-icon">{{ page.index }}</span>
          <span>{{ page.label }}</span>
        </button>
      </nav>

      <div class="side-nav-footer">
        <span class="admin-avatar">{{ adminInitial }}</span>
        <span>
          <strong>管理员控制台</strong>
          <small>{{ adminUsername || "未登录" }}</small>
        </span>
      </div>
      <button type="button" class="side-nav-footer-action" @click="changePasswordOpen = true">
        修改密码
      </button>
    </aside>

    <div class="console-main">
      <AdminTopbar
        :loading="loading"
        :page-title="currentPage.label"
        :page-eyebrow="currentPage.eyebrow"
        @refresh="$emit('refresh')"
        @logout="$emit('logout')"
      />

      <main class="workspace">
        <div class="workspace-inner">
          <section v-if="activePage === 'overview'" class="workspace-page">
            <ConsoleHero
              :application-count="applications.length"
              :channel-count="channels.length"
              :enabled-channel-count="enabledChannelCount"
              :unavailable-type-count="unavailableTypeCount"
            />

            <StatsDashboard :stats="stats" />

            <div class="overview-board">
              <article class="overview-panel">
                <div class="overview-panel-head">
                  <span class="overview-label">应用概况</span>
                  <strong>{{ applications.length }}</strong>
                </div>
                <div v-if="recentApplications.length" class="overview-list">
                  <div v-for="application in recentApplications" :key="application.id" class="overview-list-item">
                    <span>{{ application.name }}</span>
                    <small>{{ application.appKey ? "已发放凭据" : "待生成凭据" }}</small>
                  </div>
                </div>
                <p v-else class="overview-empty">暂无应用</p>
              </article>

              <article class="overview-panel">
                <div class="overview-panel-head">
                  <span class="overview-label">渠道状态</span>
                  <strong>{{ enabledChannelCount }}/{{ channels.length }}</strong>
                </div>
                <div v-if="recentChannels.length" class="overview-list">
                  <div v-for="channel in recentChannels" :key="channel.id" class="overview-list-item">
                    <span>{{ channel.name }}</span>
                    <small :class="{ healthy: channel.enabled }">{{ channel.enabled ? "启用" : "停用" }}</small>
                  </div>
                </div>
                <p v-else class="overview-empty">暂无渠道</p>
              </article>

              <article class="overview-panel">
                <div class="overview-panel-head">
                  <span class="overview-label">渠道类型</span>
                  <strong>{{ channelTypes.length }}</strong>
                </div>
                <div v-if="channelTypes.length" class="overview-list">
                  <div v-for="type in channelTypes.slice(0, 4)" :key="type.type" class="overview-list-item">
                    <span>{{ type.label }}</span>
                    <small :class="{ healthy: type.implemented }">{{ type.implemented ? "可用" : "暂未开放" }}</small>
                  </div>
                </div>
                <p v-else class="overview-empty">暂无类型</p>
              </article>
            </div>
          </section>

          <section v-else-if="activePage === 'applications'" class="workspace-page">
            <SecretAlert v-if="secret" :secret="secret" />
            <ApplicationPanel
              :applications="applications"
              :form="applicationForm"
              :channels="channels"
              :channel-label="channelLabel"
              @create="$emit('create-application')"
              @manage-groups="openApplicationGroups"
              @rotate="$emit('rotate-application', $event)"
              @delete="$emit('delete-application', $event)"
            />
          </section>

          <section v-else-if="activePage === 'application-groups'" class="workspace-page">
            <UserGroupPanel
              v-if="activeApplication"
              :key="activeApplication.id"
              :application="activeApplication"
              :channels="channels"
              :channel-label="channelLabel"
              :reload-key="userImportRevision"
              @back="switchPage('applications')"
            />
            <KEmptyState
              v-else
              title="未找到应用"
              description="该应用不存在或已被删除，请返回应用列表重新选择。"
            />
          </section>

          <section v-else-if="activePage === 'channels'" class="workspace-page">
            <ChannelPanel
              :applications="applications"
              :channels="channels"
              :channel-types="channelTypes"
              :form="channelForm"
              :selected-channel-type="selectedChannelType"
              :channel-label="channelLabel"
              @create="$emit('create-channel')"
              @edit="(channelId, payload) => $emit('edit-channel', channelId, payload)"
              @delete="$emit('delete-channel', $event)"
              @users-imported="userImportRevision += 1"
            />
          </section>
        </div>
      </main>
    </div>

    <nav class="mobile-bottom-nav" aria-label="移动端导航">
      <button
        v-for="page in pages"
        :key="page.id"
        type="button"
        :class="{ active: activePage === page.id }"
        @click="switchPage(page.id)"
      >
        {{ page.label }}
      </button>
    </nav>

    <ChangePasswordDialog
      v-model:open="changePasswordOpen"
      :on-submit="onChangePassword"
    />
  </div>
</template>
