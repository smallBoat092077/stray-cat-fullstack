import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';

// 后台只和同源路径打交道：/api 交给本地 Spring Boot（context-path 就是 /api，所以不重写路径）
export default defineConfig({
  base: '/admin/',
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://127.0.0.1:8080',
        changeOrigin: true
      }
    }
  }
});
