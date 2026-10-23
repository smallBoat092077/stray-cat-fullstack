App({
  globalData: {
    userInfo: null,
    token: null,
    baseUrl: 'http://localhost:8080/api'
  },

  onLaunch() {
    // 检查登录状态
    this.checkLogin();
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
              url: `${this.globalData.baseUrl}/user/login`,
              method: 'POST',
              data: { code: res.code },
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

  // 封装请求
  request(options) {
    return new Promise((resolve, reject) => {
      wx.request({
        url: `${this.globalData.baseUrl}${options.url}`,
        method: options.method || 'GET',
        data: options.data || {},
        header: {
          'Content-Type': 'application/json',
          'Authorization': this.globalData.token ? `Bearer ${this.globalData.token}` : ''
        },
        success: (res) => {
          if (res.data.code === 200) {
            resolve(res.data.data);
          } else if (res.data.code === 1004 || res.data.code === 1005) {
            // Token过期，重新登录
            this.login().then(() => {
              // 重试请求
              return this.request(options);
            }).then(resolve).catch(reject);
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
