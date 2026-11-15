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
app
  .use(createPinia())
  .use(router)
  .use(ElementPlus, { locale: zhCn })
  .mount('#app');

// 将已保存的主题同步进 Pinia store（供 ThemeSwitcher 等组件读取）
useThemeStore.__hydrate(saved);
