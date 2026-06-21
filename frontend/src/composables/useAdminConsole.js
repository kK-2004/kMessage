import { computed, reactive, ref } from "vue";
import { useTDesignToast } from "@kk-2004/ui-components";
import { adminApi } from "../services/adminApi";

export function useAdminConsole() {
  const authenticated = ref(false);
  const checkingSession = ref(true);
  const loading = ref(false);
  const applications = ref([]);
  const channels = ref([]);
  const channelTypes = ref([]);
  const secret = ref(null);
  const adminUsername = ref("");
  const toast = useTDesignToast();

  const loginForm = reactive({ username: "", password: "" });
  const applicationForm = reactive({ name: "" });
  const channelForm = reactive({
    name: "",
    type: "TELEGRAM",
    credentialRef: "",
  });

  const selectedChannelType = computed(() => channelTypes.value.find((t) => t.type === channelForm.type));
  const channelTypeMap = computed(() => {
    const map = {};
    for (const t of channelTypes.value) map[t.type] = t;
    return map;
  });

  function channelLabel(type) {
    return channelTypeMap.value[type]?.label || type;
  }

  function notify(message, variant = "default") {
    if (variant === "destructive") {
      toast.error(message);
    } else {
      toast.success(message);
    }
  }

  function handleUnauthorized(error) {
    if (error.status === 401) authenticated.value = false;
  }

  async function loadData() {
    loading.value = true;
    try {
      const [applicationData, channelData, typeData] = await Promise.all([
        adminApi.listApplications(),
        adminApi.listChannels(),
        channelTypes.value.length ? Promise.resolve(channelTypes.value) : adminApi.listChannelTypes(),
      ]);
      applications.value = applicationData;
      channels.value = channelData;
      channelTypes.value = typeData;
    } catch (error) {
      handleUnauthorized(error);
      notify(error.message, "destructive");
    } finally {
      loading.value = false;
    }
  }

  async function checkSession() {
    try {
      const user = await adminApi.getSession();
      authenticated.value = Boolean(user);
      adminUsername.value = typeof user === "string" ? user : "";
      if (authenticated.value) await loadData();
    } catch (error) {
      if (error.status === 401) authenticated.value = false;
    } finally {
      checkingSession.value = false;
    }
  }

  async function login() {
    loading.value = true;
    try {
      const user = await adminApi.login(loginForm);
      adminUsername.value = typeof user === "string" ? user : loginForm.username;
      loginForm.password = "";
      authenticated.value = true;
      notify("登录成功");
      await loadData();
    } catch (error) {
      notify(error.message, "destructive");
    } finally {
      loading.value = false;
    }
  }

  async function logout() {
    await adminApi.logout();
    authenticated.value = false;
    adminUsername.value = "";
    secret.value = null;
  }

  async function changePassword(payload) {
    try {
      await adminApi.changePassword(payload);
      notify("密码修改成功");
      return true;
    } catch (error) {
      handleUnauthorized(error);
      notify(error.message, "destructive");
      return false;
    }
  }

  async function createApplication() {
    try {
      secret.value = await adminApi.createApplication(applicationForm);
      applicationForm.name = "";
      notify("应用创建成功");
      await loadData();
    } catch (error) {
      handleUnauthorized(error);
      notify(error.message, "destructive");
    }
  }

  async function rotateApplication(id) {
    try {
      secret.value = await adminApi.rotateApplication(id);
      notify("Secret 已轮换");
      await loadData();
    } catch (error) {
      handleUnauthorized(error);
      notify(error.message, "destructive");
    }
  }

  async function deleteApplication(application) {
    if (!window.confirm(`确定删除应用「${application.name}」吗？删除后该应用凭据将立即失效。`)) return;
    try {
      await adminApi.deleteApplication(application.id);
      secret.value = null;
      notify("应用已删除");
      await loadData();
    } catch (error) {
      handleUnauthorized(error);
      notify(error.message, "destructive");
    }
  }

  async function createChannel() {
    try {
      await adminApi.createChannel({ ...channelForm, enabled: true, configJson: "{}" });
      Object.assign(channelForm, {
        name: "",
        type: channelForm.type,
        credentialRef: "",
      });
      notify("渠道创建成功");
      await loadData();
    } catch (error) {
      handleUnauthorized(error);
      notify(error.message, "destructive");
    }
  }

  async function editChannel(channelId, payload) {
    try {
      await adminApi.updateChannel(channelId, payload);
      notify("渠道已更新");
      await loadData();
      return true;
    } catch (error) {
      handleUnauthorized(error);
      notify(error.message, "destructive");
      return false;
    }
  }

  async function deleteChannel(channel) {
    let preview;
    try {
      preview = await adminApi.deleteChannelPreview(channel.id);
    } catch (error) {
      handleUnauthorized(error);
      notify(error.message, "destructive");
      return false;
    }
    const appList = preview.appNames?.length ? preview.appNames.join("、") : "无";
    const confirmed = window.confirm(
      `确定删除渠道「${channel.name}」吗？\n\n此渠道被 ${preview.grantedAppCount} 个应用授权（${appList}），关联 ${preview.messageCount} 条历史消息。\n删除后将一并清除这些授权与投递记录，且不可恢复。`
    );
    if (!confirmed) return false;
    try {
      await adminApi.deleteChannel(channel.id);
      notify("渠道已删除");
      await loadData();
      return true;
    } catch (error) {
      handleUnauthorized(error);
      notify(error.message, "destructive");
      return false;
    }
  }

  return {
    authenticated,
    checkingSession,
    loading,
    applications,
    channels,
    channelTypes,
    secret,
    adminUsername,
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
  };
}
