const app = getApp();

// API 基础地址
const BASE_URL = app.globalData.baseUrl;

/**
 * 封装请求
 */
function request(options) {
  return app.request(options);
}

// 用户相关
const userApi = {
  login: (code) => request({ url: '/user/login', method: 'POST', data: { code } }),
  getInfo: (userId) => request({ url: '/user/info', data: { userId } }),
  updateInfo: (data) => request({ url: '/user/info', method: 'PUT', data })
};

// 猫咪相关
const catApi = {
  getList: (params) => request({ url: '/cat/list', data: params }),
  getDetail: (id) => request({ url: `/cat/${id}` }),
  report: (data) => request({ url: '/cat/report', method: 'POST', data }),
  getNearby: (latitude, longitude, radius = 5) =>
    request({ url: '/cat/nearby', data: { latitude, longitude, radius } }),
  update: (id, data) => request({ url: `/cat/${id}`, method: 'PUT', data })
};

// 投喂相关
const feedingApi = {
  createTask: (data) => request({ url: '/feeding/task', method: 'POST', data }),
  acceptTask: (taskId, volunteerId) =>
    request({ url: `/feeding/task/${taskId}/accept`, method: 'POST', data: { volunteerId } }),
  checkIn: (taskId, latitude, longitude, images) =>
    request({
      url: `/feeding/task/${taskId}/checkin`,
      method: 'POST',
      data: { latitude, longitude, images }
    }),
  getTaskList: (params) => request({ url: '/feeding/task/list', data: params }),
  getTaskDetail: (id) => request({ url: `/feeding/task/${id}` })
};

// 领养相关
const adoptionApi = {
  apply: (data) => request({ url: '/adoption/apply', method: 'POST', data }),
  getApplication: (id) => request({ url: `/adoption/application/${id}` }),
  getFollowUpList: (params) => request({ url: '/adoption/followup/list', data: params }),
  submitFollowUp: (data) => request({ url: '/adoption/followup/submit', method: 'POST', data })
};

module.exports = {
  userApi,
  catApi,
  feedingApi,
  adoptionApi
};
