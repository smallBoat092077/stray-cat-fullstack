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

// 用户相关（微信登录统一走 app.login()，这里删掉把 code 塞 JSON body 的失效封装）
const userApi = {
  getCurrent: () => request({ url: '/user/current' }),
  getInfo: (userId) => request({ url: '/user/info', data: { userId } }),
  updateInfo: (data) => request({ url: '/user/info', method: 'PUT', data }),
  deactivate: () => request({ url: '/user/deactivate', method: 'POST' })
};

// 猫咪相关
const catApi = {
  getList: (params) => request({ url: '/cat/list', data: params }),
  getStats: () => request({ url: '/cat/stats' }),
  getDetail: (id) => request({ url: `/cat/${id}` }),
  getMine: () => request({ url: '/cat/mine' }),
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

// 海报相关
const posterApi = {
  getList: () => request({ url: '/poster/list' })
};

// 路线规划（服务端代理腾讯位置服务，key 不落在小程序包里）
const geoApi = {
  getRoute: (catId, fromLatitude, fromLongitude) =>
    request({ url: '/geo/route', data: { catId, fromLatitude, fromLongitude } }),
  // 坐标 -> 行政区（区域划分需求 R3/D2/D5）；未登录或后端未配 key 时返回降级结构
  reverse: (latitude, longitude) =>
    request({ url: '/geo/reverse', data: { latitude, longitude } })
};

// 行政区域字典（区域划分需求 R1，region 为只读字典，免登 GET）
const regionApi = {
  children: (parentCode) =>
    request({ url: '/region/children', data: parentCode ? { parentCode } : {} })
};

// 首页天气卡片（天气功能，免登 GET；后端按 adcode 缓存 30min）
const weatherApi = {
  get: (adcode) => request({ url: '/weather', data: adcode ? { adcode } : {} })
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
  // 识别灰度配置（匿名 GET）：打卡页据 enabled 决定是否进入识别模式
  recognizeConfig: () => request({ url: '/feeding/recognize/config' }),
  // 拍照识别猫咪
  recognizeCat: (photoPath, catId, topK) => {
    return new Promise((resolve, reject) => {
      const url = `${app.globalData.baseUrl}/feeding/recognize`
        + (catId ? `?catId=${catId}` : '')
        + (topK ? `${catId ? '&' : '?'}topK=${topK}` : '');
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
    request({ url: '/feeding/record/review', method: 'POST', data: { id, status, comment } }),
  // 志愿者等级
  volunteerStats: () => request({ url: '/feeding/volunteer/stats' }),
  volunteerLeaderboard: (limit = 10) =>
    request({ url: '/feeding/volunteer/leaderboard', data: { limit } })
};

// 领养相关
const adoptionApi = {
  apply: (data) => request({ url: '/adoption/apply', method: 'POST', data }),
  getApplication: (id) => request({ url: `/adoption/application/${id}` }),
  getApplicationList: (params) => request({ url: '/adoption/application/list', data: params }),
  getFollowUpList: (params) => request({ url: '/adoption/followup/list', data: params }),
  submitFollowUp: (data) => request({ url: '/adoption/followup/submit', method: 'POST', data }),
  review: (id, status, comment) =>
    request({ url: '/adoption/review', method: 'POST', data: { id, status, comment } }),
  // 共同领养：后端这几个参数是 @RequestParam，必须走查询串
  inviteCoAdopter: (applicationId, keyword) =>
    request({
      url: `/adoption/application/${applicationId}/co-adopter?keyword=${encodeURIComponent(keyword)}`,
      method: 'POST'
    }),
  getCoAdopters: (applicationId) => request({ url: `/adoption/application/${applicationId}/co-adopters` }),
  cancelCoAdopter: (id) => request({ url: `/adoption/co-adopter/${id}`, method: 'DELETE' }),
  myCoAdoptions: () => request({ url: '/adoption/co-adoption/mine' }),
  respondCoAdoption: (id, accept) =>
    request({ url: `/adoption/co-adoption/${id}/respond?accept=${accept}`, method: 'POST' })
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

// 意见反馈（必须登录，后端 24 小时内限 3 条）
const feedbackApi = {
  submit: (data) => request({ url: '/feedback/submit', method: 'POST', data }),
  mine: (pageNum = 1, pageSize = 20) => request({ url: '/feedback/my', data: { pageNum, pageSize } })
};

// 密码找回申请（免登提交，后端白名单放行；账号+昵称+手机号+说明，管理员线下核实重置）
const forgotPasswordApi = {
  apply: (data) => request({ url: '/user/forgot-password/apply', method: 'POST', data })
};

// 账号安全：自主改密 / 安全状态 / 邮箱绑定管理（均需登录）
const accountApi = {
  changePassword: (data) => request({ url: '/user/change-password', method: 'POST', data }),
  securityStatus: () => request({ url: '/user/security-status' }),
  sendEmailCode: (data) => request({ url: '/user/email/send-code', method: 'POST', data }),
  bindEmail: (data) => request({ url: '/user/email/bind', method: 'POST', data }),
  unbindEmail: (data) => request({ url: '/user/email/unbind', method: 'POST', data }),
  resetPasswordByCode: (data) => request({ url: '/user/reset-password-by-code', method: 'POST', data })
};

// 「关于我们」页面配置（公开接口，无需登录）
const aboutApi = {
  getAbout: () => request({ url: '/about' })
};

// 反馈处理（管理员专用，后端 @RequireRole(3)，消息中心「收到新的用户反馈」直达这里）
const feedbackAdminApi = {
  page: (params) => request({ url: '/admin/feedback', data: params }),
  handle: (id, status, reply) =>
    request({ url: '/admin/feedback/handle', method: 'POST', data: { id, status, reply } })
};

// 渠道短码。页面预生成分享码请用 attribution.createLink（失败静默、不弹 toast）
const shareApi = {
  link: (data) => request({ url: '/share/link', method: 'POST', data }),
  resolve: (sid) => request({ url: '/share/resolve', data: { sid } })
};

// 营销活动（免登），活动页落地用
const campaignApi = {
  detail: (params) => request({ url: '/campaign/detail', data: params })
};

// 首页聚合（GET 免登）：首屏一次拿热点 / 通告 / 三榜 / 模块开关。
// 路径严格对齐方案 5.1，写操作（点击记数）的 token 由 app.request 统一注入。
const homeApi = {
  bootstrap: () => request({ url: '/home/bootstrap' }),
  leaderboard: (params) => request({ url: '/home/leaderboard', data: params }),
  myRank: (params) => request({ url: '/home/leaderboard/mine', data: params }),
  noticeList: (params) => request({ url: '/home/notice/list', data: params }),
  noticeDetail: (id) => request({ url: `/home/notice/${id}` }),
  contentClick: (id) => request({ url: `/home/content/${id}/click`, method: 'POST' })
};

// 寻猫启事：列表 / 详情免登，发布 / 我的 / 标记已找到需登录。
const lostCatApi = {
  list: (params) => request({ url: '/lost-cat/list', data: params }),
  detail: (id) => request({ url: `/lost-cat/detail/${id}` }),
  create: (data) => request({ url: '/lost-cat/create', method: 'POST', data }),
  mine: () => request({ url: '/lost-cat/mine' }),
  found: (id) => request({ url: `/lost-cat/${id}/found`, method: 'POST' }),
  // 线索上报：提交需登录；详情 / 我的可见性由服务端校验
  createClue: (data) => request({ url: '/lost-cat/clue/create', method: 'POST', data }),
  clueDetail: (id) => request({ url: `/lost-cat/clue/detail/${id}` }),
  clueMine: () => request({ url: '/lost-cat/clue/mine' })
};

// 爱猫搬家：列表 / 详情免登，发布 / 我的 / 申请 / 管理需登录。
// 手机号只出现在受控接口；公开列表与详情由服务端过滤，不在前端处理。
const transferApi = {
  // 列表（在架）：省 / 市 / 区 + 品种 + 关键词
  list: (params) => request({ url: '/transfer/posts', data: params }),
  // 详情：浏览量 +1，不含手机号
  detail: (id) => request({ url: `/transfer/post/${id}` }),
  // 发布转让
  create: (data) => request({ url: '/transfer/post', method: 'POST', data }),
  // 我发布的（全部状态）
  mine: () => request({ url: '/transfer/mine' }),
  // 下架 / 标记已转让
  offline: (id) => request({ url: `/transfer/post/${id}/offline`, method: 'POST' }),
  complete: (id) => request({ url: `/transfer/post/${id}/complete`, method: 'POST' }),
  // 提交接猫申请
  apply: (data) => request({ url: '/transfer/apply', method: 'POST', data }),
  // 我的申请
  myApplies: () => request({ url: '/transfer/my-applies' }),
  // 某条发布的接猫申请（仅发布人，含手机号）
  applies: (postId) => request({ url: `/transfer/applies/${postId}` }),
  // 更新申请处理状态：后端 @RequestParam，必须走查询串
  applyStatus: (id, status) =>
    request({ url: `/transfer/apply/${id}/status?status=${status}`, method: 'POST' }),
  // 举报：后端 @RequestParam，必须走查询串
  report: (postId, reason) =>
    request({
      url: `/transfer/report?postId=${postId}&reason=${encodeURIComponent(reason || '')}`,
      method: 'POST'
    })
};

// 猫咪百科：分类 / 题目 / 文章 / 提交测评免登；我的测评需登录。
// 题目选项由服务端打乱，不返回正确答案，前端只提交 {questionId, optionIndex}。
const wikiApi = {
  categories: () => request({ url: '/wiki/categories' }),
  questions: (limit = 10) => request({ url: '/wiki/questions', data: { limit } }),
  submitQuiz: (answers) => request({ url: '/wiki/quiz/submit', method: 'POST', data: { answers } }),
  articles: (params) => request({ url: '/wiki/articles', data: params }),
  article: (id) => request({ url: `/wiki/article/${id}` }),
  myQuiz: () => request({ url: '/wiki/my-quiz' })
};

module.exports = {
  resolveImageUrl,
  transferApi,
  wikiApi,
  aboutApi,
  userApi,
  shareApi,
  campaignApi,
  homeApi,
  lostCatApi,
  catApi,
  geoApi,
  regionApi,
  weatherApi,
  feedingApi,
  adoptionApi,
  coinApi,
  tipApi,
  rechargeApi,
  messageApi,
  feedbackApi,
  feedbackAdminApi,
  forgotPasswordApi,
  accountApi,
  posterApi,
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
