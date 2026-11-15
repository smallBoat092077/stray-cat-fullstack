import { defineStore } from 'pinia';

export const THEMES = [
  { id: 'midnight', label: '午夜', preview: 'linear-gradient(135deg, #0d1b2a 0%, #1a1040 50%, #2d1530 100%)' },
  { id: 'vibrant',  label: '多彩', preview: 'linear-gradient(135deg, #1e0a3c 0%, #c040ff 60%, #ff2896 100%)' },
  { id: 'graphite', label: '石墨', preview: 'linear-gradient(160deg, #18181b 0%, #27272a 100%)' },
  { id: 'sun',      label: '日光', preview: 'linear-gradient(160deg, #fbfbfd 0%, #e8e8ed 100%)' },
  { id: 'dawn',     label: '晨曦', preview: 'linear-gradient(145deg, #fdf3e0 0%, #fce8d0 50%, #fde2df 100%)' }
];

const STORAGE_KEY = 'wb_theme';

let _hydrated = false;

export const useThemeStore = defineStore('theme', {
  state: () => ({
    current: _hydrated ? localStorage.getItem(STORAGE_KEY) || 'midnight' : 'midnight'
  }),
  actions: {
    setTheme(id) {
      this.current = id;
      localStorage.setItem(STORAGE_KEY, id);
      document.documentElement.setAttribute('data-theme', id);
    },
    // main.js 挂载前调用，绕过响应式初始化时序问题
    __hydrate(id) {
      this.current = id;
      _hydrated = true;
    }
  }
});
