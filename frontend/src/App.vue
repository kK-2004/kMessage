<script setup>
import { onMounted } from "vue";
import { KLoadingState } from "@kk-2004/ui-components";
import { useAdminConsole } from "./composables/useAdminConsole";
import AdminConsolePage from "./pages/AdminConsolePage.vue";
import LoginPage from "./pages/LoginPage.vue";

const {
  authenticated,
  checkingSession,
  loading,
  applications,
  channels,
  channelTypes,
  secret,
  adminUsername,
  stats,
  loginForm,
  applicationForm,
  channelForm,
  selectedChannelType,
  channelLabel,
  loadData,
  checkSession,
  login,
  logout,
  changePassword,
  createApplication,
  rotateApplication,
  deleteApplication,
  createChannel,
  editChannel,
  deleteChannel,
} = useAdminConsole();

onMounted(checkSession);
</script>

<template>
  <KLoadingState v-if="checkingSession" message="正在检查登录状态" />

  <LoginPage
    v-else-if="!authenticated"
    :form="loginForm"
    :loading="loading"
    @submit="login"
  />

  <AdminConsolePage
    v-else
    :loading="loading"
    :applications="applications"
    :channels="channels"
    :channel-types="channelTypes"
    :secret="secret"
    :admin-username="adminUsername"
    :stats="stats"
    :application-form="applicationForm"
    :channel-form="channelForm"
    :selected-channel-type="selectedChannelType"
    :channel-label="channelLabel"
    :on-change-password="changePassword"
    @refresh="loadData"
    @logout="logout"
    @create-application="createApplication"
    @rotate-application="rotateApplication"
    @delete-application="deleteApplication"
    @create-channel="createChannel"
    @edit-channel="editChannel"
    @delete-channel="deleteChannel"
  />
</template>
