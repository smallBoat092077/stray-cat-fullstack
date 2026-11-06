import http from './http';

export const adminApi = {
  stats: () => http.get('/admin/stats'),
  users: (params) => http.get('/admin/users', { params }),
  ledger: (params) => http.get('/admin/ledger', { params }),
  tips: (params) => http.get('/admin/tips', { params })
};

export const adoptionApi = {
  list: (params) => http.get('/adoption/application/list', { params }),
  review: (id, status, comment) => http.post('/adoption/review', { id, status, comment })
};

export const feedingApi = {
  taskList: (params) => http.get('/feeding/task/list', { params }),
  taskDetail: (id) => http.get(`/feeding/task/${id}`),
  createTask: (data) => http.post('/feeding/task', data),
  recordList: (params) => http.get('/feeding/record/list', { params }),
  reviewRecord: (id, status, comment) => http.post('/feeding/record/review', { id, status, comment })
};

export const catApi = {
  list: (params) => http.get('/cat/list', { params }),
  update: (id, data) => http.put(`/cat/${id}`, data),
  updateLifeStatus: (id, lifeStatus) => http.put(`/cat/${id}/life-status`, { lifeStatus })
};

export const userApi = {
  grantRole: (userId, role) => http.put(`/user/${userId}/role`, { role })
};

export const posterApi = {
  getList: () => http.get('/poster/list'),
  upload: (file) => {
    const fd = new FormData();
    fd.append('file', file);
    return http.post('/poster/upload', fd, { headers: { 'Content-Type': 'multipart/form-data' } });
  },
  delete: (id) => http.delete(`/poster/${id}`)
};
