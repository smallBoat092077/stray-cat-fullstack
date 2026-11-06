import axios from 'axios';
import { ElMessage } from 'element-plus';

export const AUTH_CODES = [1004, 1005, 1006];

const http = axios.create({
  baseURL: '/api',
  timeout: 20000
});

http.interceptors.request.use((config) => {
  const token = localStorage.getItem('admin-token');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

/**
 * 统一剥壳：成功返回 Result.data，失败弹提示并 reject，页面只管 catch
 */
http.interceptors.response.use(
  (response) => {
    const body = response.data;
    if (body && body.code === 200) {
      return body.data;
    }
    const message = (body && body.message) || '请求失败';
    if (body && AUTH_CODES.includes(body.code)) {
      localStorage.removeItem('admin-token');
      localStorage.removeItem('admin-user');
      ElMessage.error('登录已失效，请重新登录');
      import('../router').then((m) => m.default.replace('/login'));
    } else if (body && body.code === 1007) {
      ElMessage.error('当前账号没有该操作权限');
    } else {
      ElMessage.error(message);
    }
    return Promise.reject(new Error(message));
  },
  (error) => {
    const status = error.response && error.response.status;
    ElMessage.error(status === 404 ? '接口不存在，检查后端是否启动' : '网络异常或服务未启动');
    return Promise.reject(error);
  }
);

export default http;
