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
  updateLifeStatus: (id, lifeStatus) => http.put(`/cat/${id}/life-status`, { lifeStatus }),
  // 猫咪头像：从该猫上报图片里挑选（服务端校验 imageUrl 必须属于该猫本体图）
  updateCover: (id, imageUrl) => http.post(`/cat/${id}/cover`, { imageUrl }),
  // 猫咪头像：管理员自行上传（multipart POST，服务端落盘 uploads/cover/{catId}/）
  uploadCover: (id, file) => {
    const fd = new FormData();
    fd.append('file', file);
    return http.post(`/cat/${id}/cover/upload`, fd, { headers: { 'Content-Type': 'multipart/form-data' } });
  },
  // 归属区域修正（区域划分需求 R5/D4），body 传 provinceCode/cityCode/districtCode，空串 = 清除
  updateRegion: (id, data) => http.post(`/cat/${id}/region`, data)
};

// 行政区域字典（区域划分需求 R1，只读）
export const regionApi = {
  tree: () => http.get('/region/tree'),
  children: (parentCode) => http.get('/region/children', { params: parentCode ? { parentCode } : {} }),
  stats: () => http.get('/admin/region/stats'),
  sync: () => http.post('/admin/region/sync')
};

export const userApi = {
  grantRole: (userId, role) => http.put(`/user/${userId}/role`, { role })
};

// 后台用户管理 · 新增用户 / 重置密码 / 操作日志 / 密码找回申请（后端 @RequireRole(3)）
// 注意：create 走 body；action/status/remark 等筛选项与操作参数走 query（params），与 campaignApi/lostCatApi 同口径。
export const adminUserApi = {
  // 新增用户：body { username, password, nickname, role }
  create: (data) => http.post('/admin/user/create', data),
  // 重置密码：服务端生成临时密码，明文仅响应一次
  resetPassword: (id, data) => http.post(`/admin/user/${id}/reset-password`, data || {}),
  // 操作日志分页：GET query { action, keyword, pageNum, pageSize }
  operationLog: (params) => http.get('/admin/user/operation-log', { params }),
  // 密码找回申请分页：GET query { status, pageNum, pageSize }
  resetRequests: (params) => http.get('/admin/user/reset-requests', { params }),
  // 核实/忽略找回申请：status=1 重置 / status=2 忽略，参数走 query
  handleResetRequest: (id, params) => http.post(`/admin/user/reset-request/${id}/handle`, null, { params }),
  // 解除账号锁定：只清安全状态，不改密码、不恢复管理员手动禁用
  unlock: (id) => http.post(`/admin/user/${id}/unlock`),
  // 安全事件分页：GET query { userId, eventType, pageNum, pageSize }
  securityEvents: (params) => http.get('/admin/user/security-events', { params })
};

export const posterApi = {
  getList: () => http.get('/poster/list'),
  upload: (file) => {
    const fd = new FormData();
    fd.append('file', file);
    return http.post('/poster/upload', fd, { headers: { 'Content-Type': 'multipart/form-data' } });
  },
  delete: (id) => http.delete(`/poster/${id}`),
  setLink: (id, linkUrl) => http.post(`/poster/${id}/link`, null, { params: { linkUrl } })
};

export const feedbackApi = {
  list: (params) => http.get('/admin/feedback', { params }),
  handle: (id, status, reply) => http.post('/admin/feedback/handle', { id, status, reply })
};

// P0 渠道归因看板（后端 @RequireRole(3)）
export const channelApi = {
  stats: (days = 30) => http.get('/admin/stats/channel', { params: { days } }),
  links: (days = 30) => http.get('/admin/share/links', { params: { days } }),
  createCode: (sid, envVersion = 'release') => http.post('/wxa/code', { sid, envVersion })
};

// P1 活动管理（后端 @RequireRole(3)）
export const campaignApi = {
  list: (params) => http.get('/admin/campaign/list', { params }),
  save: (data) => http.post('/admin/campaign/save', data),
  status: (id, status) => http.post(`/admin/campaign/status`, null, { params: { id, status } }),
  remind: (id, content) => http.post(`/admin/campaign/${id}/remind`, null, { params: { content } })
};

// 首页改造 · 首页内容管理（后端 @RequireRole(3)）
export const homeApi = {
  contentList: (params) => http.get('/admin/home/content/list', { params }),
  saveContent: (data) => http.post('/admin/home/content/save', data),
  setContentStatus: (id, status) => http.post(`/admin/home/content/${id}/status`, null, { params: { status } }),
  deleteContent: (id) => http.delete(`/admin/home/content/${id}`)
};

// 首页改造 · 寻猫审核（后端 @RequireRole(3)）
export const lostCatApi = {
  list: (params) => http.get('/admin/lost-cat/list', { params }),
  // 后端 AdminLostCatController.audit 用 @RequestParam 取 auditStatus / auditRemark，
  // 必须走 query，不能用 body，否则审核参数丢失。
  audit: (id, data) => http.post(`/admin/lost-cat/${id}/audit`, null, { params: data }),
  // 归属区域修正（区域划分需求 R5/D4），body 传 provinceCode/cityCode/districtCode，空串 = 清除
  updateRegion: (id, data) => http.post(`/admin/lost-cat/${id}/region`, data)
};

// 二期 · 寻猫线索审核（后端 @RequireRole(3)）
// 审核接口用 @RequestParam 取 auditStatus / auditRemark，必须走 params（query 串），
// 不能用 body，否则参数静默丢失（与 lostCatApi.audit 同口径）。
export const lostCatClueApi = {
  list: (params) => http.get('/admin/lost-cat/clue/list', { params }),
  audit: (id, data) => http.post('/admin/lost-cat/clue/' + id + '/audit', null, { params: data })
};

// 花纹识别一期 · 管理侧（后端 @RequireRole(3)）
// 字段名口径：/stats 与 /register 返回驼峰；/samples 返回 snake_case Map
// （feature_id / cat_id / image_url / created_at）；register 的 multipart 字段是 photo(文件) + catId(普通字段)。
export const recognizerApi = {
  config: () => http.get('/feeding/recognize/config'),
  stats: () => http.get('/admin/recognize/stats'),
  samples: (catId) => http.get('/admin/recognize/samples', {
    params: catId !== undefined && catId !== null && catId !== '' ? { catId } : {}
  }),
  register: (catId, file) => {
    const fd = new FormData();
    fd.append('photo', file);
    fd.append('catId', catId);
    // 显式覆盖，避免被任何全局 JSON Content-Type 默认值影响（与 CampaignList.vue 上传写法一致）
    return http.post('/admin/recognize/register', fd, { headers: { 'Content-Type': 'multipart/form-data' } });
  },
  // 三期：删除一条基准样本（后端转发 Python /delete 并重建索引）
  removeSample: (featureId) => http.post('/admin/recognize/samples/' + featureId + '/delete')
};

// 首页三大功能 · 爱猫搬家审核（后端 @RequireRole(3)）
export const transferApi = {
  list: (params) => http.get('/admin/transfer/posts', { params }),
  // 审核接口用 @RequestParam 取 status / remark，必须走 params（query 串），
  // 不能用 body，否则参数静默丢失（与 lostCatApi.audit 同口径）。
  audit: (id, params) => http.post(`/admin/transfer/post/${id}/audit`, null, { params })
};

// 首页三大功能 · 猫咪百科维护（后端 @RequireRole(3)）
export const wikiApi = {
  questions: () => http.get('/admin/wiki/questions'),
  saveQuestion: (data) => http.post('/admin/wiki/questions', data),
  deleteQuestion: (id) => http.delete(`/admin/wiki/questions/${id}`),
  articles: () => http.get('/admin/wiki/articles'),
  saveArticle: (data) => http.post('/admin/wiki/articles', data),
  deleteArticle: (id) => http.delete(`/admin/wiki/articles/${id}`)
};

// 「关于我们」页面配置（后端 @RequireRole(3)；保存后客户端即时生效）
export const aboutApi = {
  get: () => http.get('/admin/about'),
  save: (data) => http.put('/admin/about', data)
};
