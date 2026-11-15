<template>
  <div class="switcher-wrap">
    <el-popover
      placement="bottom-end"
      :width="260"
      trigger="click"
      popper-class="theme-popover"
      :show-arrow="false"
    >
      <template #reference>
        <button class="icon-btn" title="外观">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
            <circle cx="12" cy="12" r="3"/>
            <path d="M12 1v2M12 21v2M4.22 4.22l1.42 1.42M18.36 18.36l1.42 1.42M1 12h2M21 12h2M4.22 19.78l1.42-1.42M18.36 5.64l1.42-1.42"/>
          </svg>
        </button>
      </template>
      <div class="popover-inner">
        <p class="pop-label">选择主题</p>
        <div class="grid">
          <button
            v-for="t in THEMES"
            :key="t.id"
            class="swatch"
            :class="{ active: themeStore.current === t.id }"
            @click="themeStore.setTheme(t.id)"
          >
            <span class="preview" :style="{ background: t.preview }"></span>
            <span class="label">{{ t.label }}</span>
          </button>
        </div>
      </div>
    </el-popover>
  </div>
</template>

<script setup>
import { THEMES } from '../stores/theme';
import { useThemeStore } from '../stores/theme';

const themeStore = useThemeStore();
</script>

<style scoped>
.switcher-wrap {
  display: flex;
  align-items: center;
}

.icon-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border-radius: 8px;
  border: 0.5px solid var(--border, rgba(255,255,255,0.15));
  background: var(--glass, rgba(255,255,255,0.08));
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  color: var(--fg, #f5f5f7);
  cursor: pointer;
  transition: background 0.2s ease;
  flex-shrink: 0;
}

.icon-btn:hover {
  background: var(--glass-hi, rgba(255,255,255,0.15));
}

.popover-inner {
  padding: 4px 0;
}

.pop-label {
  margin: 4px 12px 10px;
  font-size: 11px;
  font-weight: 500;
  color: var(--fg-muted, rgba(245,245,247,0.5));
  letter-spacing: 0.06em;
  text-transform: uppercase;
}

.grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
  padding: 0 8px 8px;
}

.swatch {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  border-radius: 10px;
  border: 0.5px solid transparent;
  background: transparent;
  cursor: pointer;
  transition: background 0.2s ease, border-color 0.2s ease;
}

.swatch:hover {
  background: var(--glass, rgba(255,255,255,0.06));
}

.swatch.active {
  border-color: var(--accent, #8b9cf7);
  background: var(--glass, rgba(255,255,255,0.08));
}

.preview {
  width: 28px;
  height: 18px;
  border-radius: 4px;
  flex-shrink: 0;
  box-shadow: 0 1px 4px rgba(0,0,0,0.25);
}

.label {
  font-size: 13px;
  color: var(--fg, #f5f5f7);
  font-weight: 500;
}

/* Popover background override for dark themes */
:deep(.theme-popover) {
  background: var(--glass, rgba(20, 20, 30, 0.85));
  backdrop-filter: blur(24px);
  -webkit-backdrop-filter: blur(24px);
  border: 0.5px solid var(--border, rgba(255,255,255,0.10));
  box-shadow: var(--shadow, 0 12px 40px rgba(0,0,0,0.5));
  border-radius: 14px;
  padding: 0;
}
</style>
