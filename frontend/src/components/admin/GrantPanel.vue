<script setup>
import {
  KButton,
  KCard,
  KCardContent,
  KCardDescription,
  KCardHeader,
  KCardTitle,
} from "@kk-2004/ui-components";
import { ElSelect, ElOption } from "element-plus";

defineProps({
  applications: { type: Array, default: () => [] },
  channels: { type: Array, default: () => [] },
  form: { type: Object, required: true },
  canManageGrant: { type: Boolean, default: false },
  channelLabel: { type: Function, required: true },
});

defineEmits(["change"]);
</script>

<template>
  <KCard>
    <KCardHeader>
      <KCardTitle>应用渠道授权</KCardTitle>
      <KCardDescription>选择应用与渠道后授予或撤销发送权限。</KCardDescription>
    </KCardHeader>
    <KCardContent>
      <div class="channel-form">
        <div class="field">
          <label>应用</label>
          <ElSelect v-model="form.applicationId" placeholder="选择应用">
            <ElOption value="" label="选择应用" disabled />
            <ElOption
              v-for="application in applications"
              :key="application.id"
              :value="application.id"
              :label="application.name"
            />
          </ElSelect>
        </div>
        <div class="field">
          <label>渠道</label>
          <ElSelect v-model="form.channelId" placeholder="选择渠道">
            <ElOption value="" label="选择渠道" disabled />
            <ElOption
              v-for="channel in channels"
              :key="channel.id"
              :value="channel.id"
              :label="`${channel.name}（${channelLabel(channel.channelType)}）`"
            />
          </ElSelect>
        </div>
        <div class="form-actions">
          <KButton :disabled="!canManageGrant" @click="$emit('change', 'PUT')">授权</KButton>
        </div>
        <div class="form-actions">
          <KButton variant="outline" :disabled="!canManageGrant" @click="$emit('change', 'DELETE')">撤销</KButton>
        </div>
      </div>
    </KCardContent>
  </KCard>
</template>
