<script setup>
import { computed } from "vue";
import VChart from "vue-echarts";
import { use } from "echarts/core";
import { CanvasRenderer } from "echarts/renderers";
import { PieChart, LineChart } from "echarts/charts";
import {
  TooltipComponent,
  LegendComponent,
  GridComponent,
  TitleComponent,
} from "echarts/components";

use([CanvasRenderer, PieChart, LineChart, TooltipComponent, LegendComponent, GridComponent, TitleComponent]);

const props = defineProps({
  stats: { type: Object, default: null },
});

// 状态中文名 + 配色（与环形图一致）
const STATUS_META = {
  ACCEPTED: { label: "待发送", color: "#94a3b8" },
  DELIVERING: { label: "投递中", color: "#3b82f6" },
  RETRYING: { label: "重试中", color: "#f59e0b" },
  DELIVERED: { label: "已送达", color: "#10b981" },
  FAILED: { label: "失败", color: "#ef4444" },
};
const meta = (status) => STATUS_META[status] || { label: status, color: "#94a3b8" };

const hasData = computed(() => props.stats && props.stats.total > 0);

// 环形图（状态分布）
const pieOption = computed(() => {
  if (!hasData.value) return {};
  const data = (props.stats.statusCounts || []).map((item) => ({
    name: meta(item.status).label,
    value: item.count,
    itemStyle: { color: meta(item.status).color },
  }));
  return {
    tooltip: { trigger: "item", formatter: "{b}: {c} ({d}%)" },
    legend: { bottom: 0, icon: "circle", textStyle: { fontSize: 12 } },
    series: [
      {
        type: "pie",
        radius: ["45%", "70%"],
        center: ["50%", "42%"],
        avoidLabelOverlap: true,
        label: { show: false },
        emphasis: {
          label: { show: true, fontSize: 16, fontWeight: "bold" },
        },
        data,
      },
    ],
  };
});

// 折线图（近 7 天趋势）
const lineOption = computed(() => {
  if (!hasData.value) return {};
  const trend = props.stats.dailyTrend || [];
  return {
    tooltip: { trigger: "axis" },
    legend: { bottom: 0, icon: "circle", textStyle: { fontSize: 12 } },
    grid: { left: 40, right: 20, top: 20, bottom: 40, containLabel: true },
    xAxis: {
      type: "category",
      data: trend.map((d) => d.date),
      axisTick: { show: false },
      axisLine: { lineStyle: { color: "#e2e8f0" } },
      axisLabel: { color: "#64748b", fontSize: 12 },
    },
    yAxis: {
      type: "value",
      minInterval: 1,
      axisLine: { show: false },
      axisTick: { show: false },
      splitLine: { lineStyle: { color: "#f1f5f9" } },
      axisLabel: { color: "#64748b", fontSize: 12 },
    },
    series: [
      {
        name: "成功",
        type: "line",
        smooth: true,
        symbol: "circle",
        symbolSize: 6,
        itemStyle: { color: "#10b981" },
        lineStyle: { width: 2.5 },
        areaStyle: { opacity: 0.12 },
        data: trend.map((d) => d.delivered),
      },
      {
        name: "失败",
        type: "line",
        smooth: true,
        symbol: "circle",
        symbolSize: 6,
        itemStyle: { color: "#ef4444" },
        lineStyle: { width: 2.5 },
        areaStyle: { opacity: 0.12 },
        data: trend.map((d) => d.failed),
      },
    ],
  };
});
</script>

<template>
  <section v-if="hasData" class="stats-section">
    <div class="stats-metric-row">
      <article class="stats-metric-card">
        <span class="stats-metric-label">消息总量</span>
        <strong class="stats-metric-value">{{ stats.total }}</strong>
      </article>
      <article class="stats-metric-card stats-metric-delivered">
        <span class="stats-metric-label">成功</span>
        <strong class="stats-metric-value">{{ stats.delivered }}</strong>
      </article>
      <article class="stats-metric-card stats-metric-failed">
        <span class="stats-metric-label">失败</span>
        <strong class="stats-metric-value">{{ stats.failed }}</strong>
      </article>
      <article class="stats-metric-card stats-metric-rate">
        <span class="stats-metric-label">成功率</span>
        <strong class="stats-metric-value">{{ stats.successRate }}%</strong>
      </article>
    </div>

    <div class="stats-charts">
      <article class="stats-chart-card">
        <h3 class="stats-chart-title">状态分布</h3>
        <VChart class="stats-chart" :option="pieOption" autoresize />
      </article>
      <article class="stats-chart-card">
        <h3 class="stats-chart-title">近 7 天趋势</h3>
        <VChart class="stats-chart" :option="lineOption" autoresize />
      </article>
    </div>
  </section>

  <section v-else class="stats-empty overview-panel">
    <p class="overview-empty">暂无消息数据</p>
  </section>
</template>
