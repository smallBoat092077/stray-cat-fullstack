<template>
  <div class="trend-chart" @mousemove="onMove" @mouseleave="hover = null">
    <svg v-if="points.length" :viewBox="`0 0 ${W} ${H}`" preserveAspectRatio="none" class="svg">
      <!-- 网格线（3 条横线） -->
      <line v-for="g in gridLines" :key="g" x1="40" :y1="g" x2="20" :y2="g" class="grid" />
      <!-- 区域底色（PV 折线下方淡淡一层） -->
      <polygon v-if="pvPath.area" :points="pvPath.area" class="area" />
      <!-- PV 折线（墨绿实线） -->
      <polyline :points="pvPath.line" class="line line-pv" />
      <!-- 新用户折线（金色虚线） -->
      <polyline :points="newPath.line" class="line line-new" />
      <!-- 数据点 -->
      <circle v-for="(p, i) in pvPoints" :key="'pv' + i" :cx="p.x" :cy="p.y" r="2.5" class="dot dot-pv" />
      <circle v-for="(p, i) in newPoints" :key="'nw' + i" :cx="p.x" :cy="p.y" r="2.5" class="dot dot-new" />
      <!-- Y 轴刻度 -->
      <text v-for="(g, i) in gridLines" :key="'t' + i" x="4" :y="g + 4" class="tick">{{ yLabels[i] }}</text>
      <!-- X 轴：首/中/尾三个日期 -->
      <text :x="xTicks[0] && xTicks[0].x" :y="H - 6" class="tick">{{ xTicks[0] && xTicks[0].label }}</text>
      <text :x="xTicks[1] && xTicks[1].x" :y="H - 6" text-anchor="middle" class="tick">{{ xTicks[1] && xTicks[1].label }}</text>
      <text :x="xTicks[2] && xTicks[2].x" :y="H - 6" text-anchor="end" class="tick">{{ xTicks[2] && xTicks[2].label }}</text>
    </svg>
    <div v-else class="empty">暂无访问数据</div>
    <div v-if="points.length" class="legend">
      <span class="legend-item"><i class="swatch swatch-pv" />访问 PV</span>
      <span class="legend-item"><i class="swatch swatch-new" />新用户</span>
      <span class="legend-item muted">悬浮查看数值</span>
    </div>
    <div v-if="hover" class="tooltip" :style="{ left: hover.left + 'px', top: hover.top + 'px' }">
      <div class="tip-day">{{ hover.day }}</div>
      <div>访问 {{ hover.pv }} · 新用户 {{ hover.newUsers }}</div>
    </div>
  </div>
</template>

<script setup>
/**
 * 自绘 SVG 双折线趋势图（零依赖）。选择自绘而不是引 ECharts：
 * 只有「30 天 PV / 新用户」两条线，~350KB 的图表库不值当。
 */
import { computed, ref } from 'vue';

const props = defineProps({
  rows: { type: Array, default: () => [] },
  days: { type: Number, default: 30 }
});

const W = 760;
const H = 220;
const PAD_L = 40;
const PAD_R = 10;
const PAD_T = 14;
const PAD_B = 24;

// 后端只返回有数据的天，这里把周期内每一天都补上（无数据补 0），折线才不断档
const points = computed(() => {
  if (!props.rows || !props.rows.length) return [];
  const map = new Map();
  props.rows.forEach((row) => {
    const day = String(row.day).slice(0, 10);
    map.set(day, { pv: Number(row.pv || 0), newUsers: Number(row.newUsers || 0) });
  });
  const list = [];
  const start = new Date();
  start.setHours(0, 0, 0, 0);
  start.setDate(start.getDate() - (props.days - 1));
  for (let i = 0; i < props.days; i++) {
    const d = new Date(start);
    d.setDate(start.getDate() + i);
    const key = d.toISOString().slice(0, 10);
    const hit = map.get(key);
    list.push({ day: key, pv: hit ? hit.pv : 0, newUsers: hit ? hit.newUsers : 0 });
  }
  return list;
});

const maxValue = computed(() => {
  let max = 0;
  points.value.forEach((p) => { if (p.pv > max) max = p.pv; });
  return Math.max(max, 4);
});

const gridLines = computed(() => {
  const step = (H - PAD_T - PAD_B) / 3;
  return [0, 1, 2, 3].map((i) => PAD_T + step * i);
});

const yLabels = computed(() => {
  const step = maxValue.value / 3;
  return [3, 2, 1, 0].map((i) => Math.round(step * i));
});

function xOf(i) {
  const n = points.value.length;
  if (n <= 1) return PAD_L;
  const span = W - PAD_L - PAD_R;
  return PAD_L + (span * i) / (n - 1);
}

function yOf(v) {
  const span = H - PAD_T - PAD_B;
  return PAD_T + span * (1 - v / maxValue.value);
}

const pvPoints = computed(() => points.value.map((p, i) => ({ x: xOf(i), y: yOf(p.pv), ...p })));
const newPoints = computed(() => points.value.map((p, i) => ({ x: xOf(i), y: yOf(p.newUsers), ...p })));

function polylineOf(pts) {
  return pts.map((p) => `${p.x.toFixed(1)},${p.y.toFixed(1)}`).join(' ');
}

const pvPath = computed(() => {
  const pts = pvPoints.value;
  if (!pts.length) return { line: '', area: '' };
  const line = polylineOf(pts);
  const base = (H - PAD_B).toFixed(1);
  const area = `${PAD_L},${base} ${line} ${W - PAD_R},${base}`;
  return { line, area };
});

const newPath = computed(() => ({ line: polylineOf(newPoints.value) }));

const xTicks = computed(() => {
  const n = points.value.length;
  if (!n) return [];
  const pick = (i) => {
    const idx = n <= 1 ? 0 : Math.round((i * (n - 1)) / 2);
    const p = points.value[idx];
    return { x: xOf(idx), label: p.day.slice(5) };
  };
  return [pick(0), pick(1), pick(2)];
});

// 悬浮数值：按鼠标 x 坐标找最近的数据点，提示框摆在点上方
const hover = ref(null);

function onMove(e) {
  const rect = e.currentTarget.getBoundingClientRect();
  const x = ((e.clientX - rect.left) / rect.width) * W;
  const n = points.value.length;
  if (!n) return;
  const span = W - PAD_L - PAD_R;
  const ratio = Math.min(1, Math.max(0, (x - PAD_L) / span));
  const idx = n <= 1 ? 0 : Math.round(ratio * (n - 1));
  const p = points.value[idx];
  hover.value = {
    day: p.day,
    pv: p.pv,
    newUsers: p.newUsers,
    left: (x / W) * rect.width,
    top: (yOf(p.pv) / H) * rect.height
  };
}
</script>

<style scoped>
.trend-chart {
  position: relative;
  background: #fff;
  border: 1px solid #e6e3da;
  border-radius: 8px;
  padding: 8px 4px 4px;
}

.svg {
  display: block;
  width: 100%;
  height: 220px;
  cursor: crosshair;
}

.grid {
  stroke: #eeeae0;
  stroke-width: 1;
}

.area {
  fill: rgba(20, 73, 60, 0.08);
  stroke: none;
}

.line-pv {
  fill: none;
  stroke: #14493c;
  stroke-width: 2;
}

.line-new {
  fill: none;
  stroke: #c3a15f;
  stroke-width: 2;
  stroke-dasharray: 5 4;
}

.dot-pv { fill: #14493c; }
.dot-new { fill: #c3a15f; }

.tick {
  font-size: 11px;
  fill: #9a968a;
}

.empty {
  height: 180px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #9a968a;
  font-size: 13px;
}

.legend {
  display: flex;
  gap: 18px;
  justify-content: center;
  font-size: 12px;
  color: #60635f;
  padding-bottom: 6px;
}

.legend-item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.legend-item.muted { color: #b6b2a6; }

.swatch {
  width: 14px;
  height: 3px;
  border-radius: 2px;
  display: inline-block;
}

.swatch-pv { background: #14493c; }
.swatch-new { background: repeating-linear-gradient(90deg, #c3a15f 0 5px, transparent 5px 9px); }

.tooltip {
  position: absolute;
  background: rgba(12, 51, 41, 0.92);
  color: #fffdf7;
  font-size: 12px;
  padding: 6px 10px;
  border-radius: 6px;
  pointer-events: none;
  transform: translate(-50%, -115%);
  white-space: nowrap;
  z-index: 2;
}

.tip-day { font-weight: 600; margin-bottom: 2px; }
</style>
