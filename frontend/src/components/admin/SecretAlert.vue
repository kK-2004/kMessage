<script setup>
import { KAlert, KAlertDescription, KAlertTitle, KCopyButton } from "@kk-2004/ui-components";

defineProps({
  secret: { type: Object, required: true },
});
</script>

<template>
  <KAlert variant="warning" class="secret-alert">
    <KAlertTitle>请立即保存新凭据</KAlertTitle>
    <KAlertDescription>AppSecret 仅在本次展示，页面刷新后不再显示。</KAlertDescription>
    <dl>
      <dt>appKey</dt>
      <dd><code>{{ secret.appKey }}</code><KCopyButton :value="secret.appKey" /></dd>
      <dt>appSecret</dt>
      <dd><code>{{ secret.appSecret }}</code><KCopyButton :value="secret.appSecret" /></dd>
    </dl>
  </KAlert>
</template>
