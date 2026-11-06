import { defineStore } from 'pinia';
import http from '../api/http';

const TOKEN_KEY = 'admin-token';
const USER_KEY = 'admin-user';

export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: localStorage.getItem(TOKEN_KEY) || '',
    user: JSON.parse(localStorage.getItem(USER_KEY) || 'null')
  }),
  getters: {
    logged: (state) => !!state.token,
    // 后台只给管理员用；role 3 才有审核打卡和授权限的接口
    isAdmin: (state) => state.user && state.user.role === 3
  },
  actions: {
    async login(username, password) {
      const data = await http.post('/user/login/password', { username, password });
      this.token = data.token;
      this.user = data.user;
      localStorage.setItem(TOKEN_KEY, data.token);
      localStorage.setItem(USER_KEY, JSON.stringify(data.user));
      return data.user;
    },
    async refresh() {
      const user = await http.get('/user/current');
      this.user = user;
      localStorage.setItem(USER_KEY, JSON.stringify(user));
    },
    logout() {
      this.token = '';
      this.user = null;
      localStorage.removeItem(TOKEN_KEY);
      localStorage.removeItem(USER_KEY);
    }
  }
});
