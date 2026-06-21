<script setup>
import {
  KButton,
  KDialog,
  KDialogContent,
  KDialogDescription,
  KDialogFooter,
  KDialogHeader,
  KDialogTitle,
  KPasswordInput,
} from "@kk-2004/ui-components";
import { computed, reactive, ref, watch } from "vue";

const props = defineProps({
  open: { type: Boolean, default: false },
  // Async handler resolving to a boolean (true on success). Kept on the parent so the
  // dialog stays a stateless form that does not import the API layer directly.
  onSubmit: { type: Function, required: true },
});

const emit = defineEmits(["update:open"]);

const form = reactive({ oldPassword: "", newPassword: "", confirmPassword: "" });
const submitting = ref(false);

const newPasswordValid = computed(() => (form.newPassword || "").length >= 6);
const confirmPasswordValid = computed(() => form.newPassword === form.confirmPassword);
const canSubmit = computed(() =>
  Boolean(form.oldPassword) && newPasswordValid.value && confirmPasswordValid.value
);

// Reset fields whenever the dialog is (re)opened so prior input never lingers.
watch(
  () => props.open,
  (open) => {
    if (open) {
      form.oldPassword = "";
      form.newPassword = "";
      form.confirmPassword = "";
    }
  }
);

function close() {
  if (submitting.value) return;
  emit("update:open", false);
}

async function submit() {
  if (!canSubmit.value || submitting.value) return;
  submitting.value = true;
  try {
    const ok = await props.onSubmit({
      oldPassword: form.oldPassword,
      newPassword: form.newPassword,
    });
    if (ok) emit("update:open", false);
  } finally {
    submitting.value = false;
  }
}
</script>

<template>
  <KDialog :open="open" @update:open="emit('update:open', $event)">
    <KDialogContent>
      <KDialogHeader>
        <KDialogTitle>修改管理员密码</KDialogTitle>
        <KDialogDescription>修改后下次登录请使用新密码，旧密码将立即失效。</KDialogDescription>
      </KDialogHeader>
      <form class="test-form" @submit.prevent="submit">
        <label>
          原密码
          <KPasswordInput v-model="form.oldPassword" placeholder="请输入当前密码" autocomplete="current-password" />
        </label>
        <label>
          新密码
          <KPasswordInput v-model="form.newPassword" placeholder="至少 6 位" autocomplete="new-password" />
          <span v-if="form.newPassword && !newPasswordValid" class="hint" style="color: var(--km-danger, #dc2626);">
            新密码长度至少 6 位
          </span>
        </label>
        <label>
          确认新密码
          <KPasswordInput v-model="form.confirmPassword" placeholder="再次输入新密码" autocomplete="new-password" />
          <span v-if="form.confirmPassword && !confirmPasswordValid" class="hint" style="color: var(--km-danger, #dc2626);">
            两次输入的新密码不一致
          </span>
        </label>
      </form>
      <KDialogFooter>
        <KButton variant="outline" :disabled="submitting" @click="close">取消</KButton>
        <KButton :disabled="!canSubmit || submitting" @click="submit">
          {{ submitting ? "提交中..." : "确认修改" }}
        </KButton>
      </KDialogFooter>
    </KDialogContent>
  </KDialog>
</template>
