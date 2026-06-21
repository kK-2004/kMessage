<script setup>
import {
  KButton,
  KCard,
  KCardContent,
  KCardDescription,
  KCardHeader,
  KCardTitle,
  KInput,
  KPasswordInput,
} from "@kk-2004/ui-components";

defineProps({
  form: { type: Object, required: true },
  loading: { type: Boolean, default: false },
});

defineEmits(["submit"]);
</script>

<template>
  <main class="login-page">
    <div class="login-shell">
      <KCard class="login-card">
        <KCardHeader>
          <div class="login-brand">
            <span class="brand-mark login-brand-mark">K</span>
          </div>
          <div class="login-heading">
            <KCardTitle>欢迎回来</KCardTitle>
            <KCardDescription>登录以管理您的消息平台</KCardDescription>
          </div>
        </KCardHeader>
        <KCardContent>
          <form class="login-form" @submit.prevent="$emit('submit')">
            <label class="field-stack">
              <span>用户名</span>
              <KInput v-model="form.username" placeholder="请输入用户名" />
            </label>
            <label class="field-stack">
              <span>密码</span>
              <KPasswordInput v-model="form.password" placeholder="请输入密码" />
            </label>
            <KButton type="submit" size="lg" :loading="loading" :disabled="!form.username || !form.password">
              登录
            </KButton>
          </form>
        </KCardContent>
      </KCard>
      <div class="login-footnote">
        <span>kMessage</span>
        <span>内部统一消息平台</span>
      </div>
    </div>
  </main>
</template>
