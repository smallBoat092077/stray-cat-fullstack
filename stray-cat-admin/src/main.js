import { createApp } from 'vue';
import ElementPlus from 'element-plus';
import 'element-plus/dist/index.css';
import zhCn from 'element-plus/es/locale/lang/zh-cn';
import { createPinia } from 'pinia';
import App from './App.vue';
import router from './router';
import './styles.css';
import { useThemeStore } from './stores/theme';

// 在 Vue 挂载前设置 data-theme，避免初始闪烁
const saved = localStorage.getItem('wb_theme') || 'midnight';
document.documentElement.setAttribute('data-theme', saved);

const app = createApp(App);
const pinia = createPinia();
app
  .use(pinia)
  .use(router)
  .use(ElementPlus, { locale: zhCn })
  .mount('#app');

// 将已保存的主题同步进 Pinia store（供 ThemeSwitcher 等组件读取）
// useThemeStore 是 defineStore 返回的定义函数，必须先调用它拿到 store 实例，才能访问 actions 里的 __hydrate
useThemeStore(pinia).__hydrate(saved);
