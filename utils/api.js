const app = getApp();

// API 基础地址
const BASE_URL = app.globalData.baseUrl;

/**
 * 封装请求
 */
function request(options) {
  return app.request(options);
}

// 后端返回的 /uploads/xxx 是相对路径，小程序 <image> 需要完整地址
function resolveImageUrl(path) {
  if (!path) return '';
  if (path.indexOf('http') === 0 || path.indexOf('/images/') === 0) {
    return path;
  }
  return `${app.globalData.baseUrl}${path}`;
}

// 用户相关
const userApi = {
  login: (code) => request({ url: '/user/login', method: 'POST', data: { code } }),
  getCurrent: () => request({ url: '/user/current' }),
  getInfo: (userId) => request({ url: '/user/info', data: { userId } }),
  updateInfo: (data) => request({ url: '/user/info', method: 'PUT', data }),
  deactivate: () => request({ url: '/user/deactivate', method: 'POST' })
};

// 猫咪相关
const catApi = {
  getList: (params) => request({ url: '/cat/list', data: params }),
  getDetail: (id) => request({ url: `/cat/${id}` }),
  report: (data) => request({ url: '/cat/report', method: 'POST', data }),
  getNearby: (latitude, longitude, radius = 5) =>
    request({ url: '/cat/nearby', data: { latitude, longitude, radius } }),
  getPopular: (limit = 3) => request({ url: '/cat/popular', data: { limit } }),
  toggleLike: (id) => request({ url: `/cat/${id}/like`, method: 'POST' }),
  getComments: (id, params) => request({ url: `/cat/${id}/comments`, data: params }),
  getCommentReplies: (commentId, params) =>
    request({ url: `/cat/comments/${commentId}/replies`, data: params }),
  createComment: (id, data) => request({ url: `/cat/${id}/comments`, method: 'POST', data }),
  deleteComment: (commentId) => request({ url: `/cat/comments/${commentId}`, method: 'DELETE' }),
  update: (id, data) => request({ url: `/cat/${id}`, method: 'PUT', data })
};

// 路线规划（服务端代理腾讯位置服务，key 不落在小程序包里）
const geoApi = {
  getRoute: (catId, fromLatitude, fromLongitude) =>
    request({ url: '/geo/route', data: { catId, fromLatitude, fromLongitude } })
};

// 投喂相关
const feedingApi = {
  createTask: (data) => request({ url: '/feeding/task', method: 'POST', data }),
  acceptTask: (taskId) =>
    request({ url: `/feeding/task/${taskId}/accept`, method: 'POST' }),
  checkIn: (taskId, latitude, longitude, images) =>
    request({
      // 后端这几个参数是 @RequestParam，必须走查询串；发 JSON body 会绑不上
      url: `/feeding/task/${taskId}/checkin?latitude=${latitude}&longitude=${longitude}`
        + `&images=${encodeURIComponent(images || '')}`,
      method: 'POST'
    }),
  // 拍照识别猫咪
  recognizeCat: (photoPath, catId) => {
    return new Promise((resolve, reject) => {
      const url = `${app.globalData.baseUrl}/feeding/recognize`
        + (catId ? `?catId=${catId}` : '');
      wx.uploadFile({
        url,
        filePath: photoPath,
        name: 'photo',
        header: {
          'Authorization': app.globalData.token ? `Bearer ${app.globalData.token}` : '',
          'ngrok-skip-browser-warning': 'true'
        },
        success: (res) => {
          const data = JSON.parse(res.data);
          if (data.code === 200) {
            resolve(data.data);
          } else {
            reject(new Error(data.message || '识别失败'));
          }
        },
        fail: reject
      });
    });
  },
  // 带识别的打卡
  checkinWithRecognize: (taskId, photoPath, catId, latitude, longitude, address, portions, remark) => {
    return new Promise((resolve, reject) => {
      const url = `${app.globalData.baseUrl}/feeding/checkin-with-recognize`
        + `?taskId=${taskId}&catId=${catId}&latitude=${latitude}&longitude=${longitude}`
        + (address ? `&address=${encodeURIComponent(address)}` : '')
        + (portions ? `&portions=${portions}` : '')
        + (remark ? `&remark=${encodeURIComponent(remark)}` : '');
      wx.uploadFile({
        url,
        filePath: photoPath,
        name: 'photo',
        header: {
          'Authorization': app.globalData.token ? `Bearer ${app.globalData.token}` : '',
          'ngrok-skip-browser-warning': 'true'
        },
        success: (res) => {
          const data = JSON.parse(res.data);
          if (data.code === 200) {
            resolve(data.data);
          } else {
            reject(new Error(data.message || '打卡失败'));
          }
        },
        fail: reject
      });
    });
  },
  getTaskList: (params) => request({ url: '/feeding/task/list', data: params }),
  getTaskDetail: (id) => request({ url: `/feeding/task/${id}` }),
  listRecordsByCat: (catId) => request({ url: `/feeding/record/cat/${catId}` }),
  listMyRecords: (params) => request({ url: '/feeding/record/mine', data: params }),
  listRecords: (status, limit = 20) =>
    request({
      url: '/feeding/record/list',
      data: status === null || status === undefined ? { limit } : { status, limit }
    }),
  reviewRecord: (id, status, comment) =>
    request({ url: '/feeding/record/review', method: 'POST', data: { id, status, comment } })
};

// 领养相关
const adoptionApi = {
  apply: (data) => request({ url: '/adoption/apply', method: 'POST', data }),
  getApplication: (id) => request({ url: `/adoption/application/${id}` }),
  getApplicationList: (params) => request({ url: '/adoption/application/list', data: params }),
  getFollowUpList: (params) => request({ url: '/adoption/followup/list', data: params }),
  submitFollowUp: (data) => request({ url: '/adoption/followup/submit', method: 'POST', data }),
  review: (id, status, comment) =>
    request({ url: '/adoption/review', method: 'POST', data: { id, status, comment } })
};

// 猫粮（站内虚拟积分，无现金价值）
const coinApi = {
  getSummary: () => request({ url: '/coin/summary' }),
  getTransactions: (params) => request({ url: '/coin/transactions', data: params }),
  signIn: () => request({ url: '/coin/signin', method: 'POST' })
};

// 送猫粮
const tipApi = {
  create: (catId, coinAmount, message) =>
    request({ url: '/tip', method: 'POST', data: { catId, coinAmount, message } }),
  listByCat: (catId, params) => request({ url: `/tip/cat/${catId}`, data: params }),
  listMine: (params) => request({ url: '/tip/my', data: params })
};

// 猫粮充值（微信虚拟支付 - 道具直购）
const rechargeApi = {
  getPacks: () => request({ url: '/recharge/packs' }),
  createOrder: (packCode) => request({ url: '/recharge/order', method: 'POST', data: { packCode } }),
  confirm: (outTradeNo) => request({ url: '/recharge/confirm', method: 'POST', data: { outTradeNo } }),
  getStatus: (outTradeNo) => request({ url: '/recharge/status', data: { outTradeNo } }),
  getOrders: (pageNum = 1, pageSize = 20) => request({ url: '/recharge/orders', data: { pageNum, pageSize } })
};

// 消息中心（审核结果、接单、回访提醒都先落这里，订阅消息只是补充）
const messageApi = {
  list: (pageNum = 1, pageSize = 20) => request({ url: '/message/list', data: { pageNum, pageSize } }),
  unreadCount: () => request({ url: '/message/unread-count' }),
  readAll: () => request({ url: '/message/read', method: 'POST' }),
  templates: () => request({ url: '/message/templates' })
};

module.exports = {
  resolveImageUrl,
  userApi,
  catApi,
  geoApi,
  feedingApi,
  adoptionApi,
  coinApi,
  tipApi,
  rechargeApi,
  messageApi,
  uploadFile: (filePath) => {
    return new Promise((resolve, reject) => {
      wx.uploadFile({
        url: `${app.globalData.baseUrl}/common/upload`,
        filePath,
        name: 'file',
        header: {
          'Authorization': app.globalData.token ? `Bearer ${app.globalData.token}` : '',
          'ngrok-skip-browser-warning': 'true'
        },
        success: (res) => {
          const data = JSON.parse(res.data);
          if (data.code === 200) {
            resolve(data.data);
          } else {
            reject(new Error(data.message || '上传失败'));
          }
        },
        fail: reject
      });
    });
  }
};
