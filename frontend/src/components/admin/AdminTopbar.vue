<script setup>
import { KButton } from "@kk-2004/ui-components";

defineProps({
  loading: { type: Boolean, default: false },
  pageTitle: { type: String, default: "kMessage" },
  pageEyebrow: { type: String, default: "管理控制台" },
});

defineEmits(["refresh", "logout"]);
</script>

<template>
  <header class="topbar">
    <div class="topbar-title">
      <div>
        <span class="topbar-eyebrow">{{ pageEyebrow }}</span>
        <span class="topbar-heading">{{ pageTitle }}</span>
      </div>
    </div>
    <div class="topbar-actions">
      <KButton size="sm" variant="ghost" :loading="loading" @click="$emit('refresh')">刷新</KButton>
      <KButton size="sm" variant="outline" @click="$emit('logout')">退出登录</KButton>
    </div>
  </header>
</template>
