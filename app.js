App({
  globalData: {
    userInfo: null,
    token: null,
    focusCatId: null,
    // 域名侧 nginx 443 + Let's Encrypt 已就绪，备案已通过，启用 HTTPS 域名
    baseUrl: 'https://straycat.fun/api'
    //baseUrl: 'http://47.95.149.170/api'
    //baseUrl: 'http://47.97.253.32:8080/api'
    //baseUrl: 'http://192.168.1.6:8080/api'
  },

  onLaunch() {
    this.initPrivacy();
    // 检查登录状态
    this.checkLogin();
  },

  // 微信要求隐私接口调用前取得用户授权；未注册该监听时 getLocation/chooseLocation 在正式版会直接失败
  initPrivacy() {
    if (!wx.onNeedPrivacyAuthorization) {
      return;
    }
    wx.onNeedPrivacyAuthorization((resolve) => {
      wx.showModal({
        title: '用户隐私保护指引',
        content: '位置信息用于展示附近的流浪猫与投喂点打卡，相册权限用于上传猫咪照片。详情请查看《隐私政策》。',
        confirmText: '同意',
        cancelText: '查看政策',
        success: (res) => {
          if (res.confirm) {
            resolve({ event: 'agreePrivacyAuthorization' });
          } else {
            resolve({ event: 'disagree' });
            wx.navigateTo({ url: '/pages/privacy/index' });
          }
        },
        fail: () => resolve({ event: 'disagree' })
      });
    });
  },

  checkLogin() {
    const token = wx.getStorageSync('token');
    if (token) {
      this.globalData.token = token;
      this.getUserInfo();
    }
  },

  // 微信登录
  login() {
    return new Promise((resolve, reject) => {
      wx.login({
        success: (res) => {
          if (res.code) {
            wx.request({
              url: `${this.globalData.baseUrl}/user/login?code=${res.code}`,
              method: 'POST',
              header: {
                'ngrok-skip-browser-warning': 'true'
              },
              success: (loginRes) => {
                if (loginRes.data.code === 200) {
                  const { token, user } = loginRes.data.data;
                  this.globalData.token = token;
                  this.globalData.userInfo = user;
                  wx.setStorageSync('token', token);
                  wx.setStorageSync('userInfo', user);
                  resolve(user);
                } else {
                  reject(new Error(loginRes.data.message));
                }
              },
              fail: reject
            });
          }
        },
        fail: reject
      });
    });
  },

  // 获取用户信息
  getUserInfo() {
    const userInfo = wx.getStorageSync('userInfo');
    if (userInfo) {
      this.globalData.userInfo = userInfo;
    }
  },

  // 角色可能已被管理员调整，本地缓存要能被服务端覆盖
  refreshUserInfo() {
    return this.request({ url: '/user/current' }).then((user) => {
      this.globalData.userInfo = user;
      wx.setStorageSync('userInfo', user);
      return user;
    });
  },

  // 保存登录态
  saveSession(token, user) {
    this.globalData.token = token;
    this.globalData.userInfo = user;
    wx.setStorageSync('token', token);
    wx.setStorageSync('userInfo', user);
  },

  clearSession() {
    this.globalData.token = null;
    this.globalData.userInfo = null;
    wx.removeStorageSync('token');
    wx.removeStorageSync('userInfo');
  },

  // 需要登录的动作统一入口：未登录时引导去登录页并返回 false
  ensureLogin() {
    if (this.globalData.token && this.globalData.userInfo) {
      return true;
    }
    this.goLogin();
    return false;
  },

  // 并发请求同时失效时只推一次登录页，避免栈里叠出好几个
  goLogin() {
    if (this.redirectingToLogin) {
      return;
    }
    const pages = getCurrentPages();
    const current = pages.length ? pages[pages.length - 1].route : '';
    if (current === 'pages/login/index') {
      return;
    }
    this.redirectingToLogin = true;
    wx.navigateTo({
      url: '/pages/login/index',
      complete: () => setTimeout(() => { this.redirectingToLogin = false; }, 800)
    });
  },

  // 账号密码登录
  passwordLogin(username, password) {
    return new Promise((resolve, reject) => {
      wx.request({
        url: `${this.globalData.baseUrl}/user/login/password`,
        method: 'POST',
        header: {
          'Content-Type': 'application/json',
          'ngrok-skip-browser-warning': 'true'
        },
        data: { username, password },
        success: (res) => {
          if (res.data.code === 200) {
            const { token, user } = res.data.data;
            this.saveSession(token, user);
            resolve(user);
          } else {
            reject(new Error(res.data.message || '登录失败'));
          }
        },
        fail: () => reject(new Error('网络错误'))
      });
    });
  },

  // 账号密码注册（成功即登录）
  register(username, password) {
    return new Promise((resolve, reject) => {
      wx.request({
        url: `${this.globalData.baseUrl}/user/register`,
        method: 'POST',
        header: {
          'Content-Type': 'application/json',
          'ngrok-skip-browser-warning': 'true'
        },
        data: { username, password },
        success: (res) => {
          if (res.data.code === 200) {
            const { token, user } = res.data.data;
            this.saveSession(token, user);
            resolve(user);
          } else {
            reject(new Error(res.data.message || '注册失败'));
          }
        },
        fail: () => reject(new Error('网络错误'))
      });
    });
  },

  // 封装请求
  request(options) {
    return new Promise((resolve, reject) => {
      wx.request({
        url: `${this.globalData.baseUrl}${options.url}`,
        method: options.method || 'GET',
        data: options.data || {},
        header: {
          'Content-Type': 'application/json',
          'Authorization': this.globalData.token ? `Bearer ${this.globalData.token}` : '',
          'ngrok-skip-browser-warning': 'true'
        },
        success: (res) => {
          if (res.data.code === 200) {
            resolve(res.data.data);
          } else if (res.data.code === 1004 || res.data.code === 1005 || res.data.code === 1006) {
            // 不能在这里自动改走微信登录：账号密码注册的用户没有 openid，静默重登会把 TA 登成另一个账号
            this.clearSession();
            wx.showToast({ title: '登录已过期，请重新登录', icon: 'none' });
            this.goLogin();
            reject(res.data);
          } else {
            wx.showToast({ title: res.data.message || '请求失败', icon: 'none' });
            reject(res.data);
          }
        },
        fail: (err) => {
          wx.showToast({ title: '网络错误', icon: 'none' });
          reject(err);
        }
      });
    });
  }
});
