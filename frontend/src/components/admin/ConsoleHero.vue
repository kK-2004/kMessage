<script setup>
defineProps({
  applicationCount: { type: Number, default: 0 },
  channelCount: { type: Number, default: 0 },
  enabledChannelCount: { type: Number, default: 0 },
  unavailableTypeCount: { type: Number, default: 0 },
});
</script>

<template>
  <section class="hero-card">
    <div class="hero-text">
      <p class="eyebrow">Console</p>
      <h1>管理消息发送身份与渠道</h1>
      <p>创建业务应用、配置消息渠道、维护用户分组，并控制应用可使用的发送范围。</p>
    </div>
    <div class="metrics">
      <div class="metric-card primary-metric">
        <span>业务应用</span>
        <strong>{{ applicationCount }}</strong>
      </div>
      <div class="metric-card">
        <span>渠道实例</span>
        <strong>{{ channelCount }}</strong>
      </div>
      <div class="metric-card">
        <span>启用渠道</span>
        <strong>{{ enabledChannelCount }}</strong>
      </div>
      <div class="metric-card muted-metric">
        <span>未开放类型</span>
        <strong>{{ unavailableTypeCount }}</strong>
      </div>
    </div>
  </section>
</template>
