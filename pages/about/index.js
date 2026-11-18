const { aboutApi } = require('../../utils/api');

// 后端配置异常时的兜底展示，保证页面永远有内容
const FALLBACK = {
  appName: '猫遇猫舍',
  version: 'v1.1.0',
  slogan: '遇一只猫 · 安一个家',
  mottoMain: '让领养代替购买\n爱不流浪',
  mottoSub: '遇一只猫，安一个家',
  phone: '17671710525',
  email: '1343076057@qq.com',
  contactTip: '如有领养咨询、流浪猫求助或合作意向，欢迎在工作时间（9:00–21:00）与我们联系。',
  copyright: '猫遇猫舍 · 用心善待每一只小猫\n© 2026 CatMeet Cattery'
};

Page({
  data: {
    about: FALLBACK,
    mottoLines: FALLBACK.mottoMain.split('\n'),
    copyrightLines: FALLBACK.copyright.split('\n')
  },

  onLoad() {
    this.loadAbout();
  },

  loadAbout() {
    aboutApi.getAbout().then((res) => {
      const about = res && res.appName ? res : FALLBACK;
      this.setData({
        about,
        mottoLines: String(about.mottoMain || '').split('\n').filter(Boolean),
        copyrightLines: String(about.copyright || '').split('\n').filter(Boolean)
      });
    }).catch(() => {
      // 拉取失败保持兜底内容，不打扰用户
    });
  },

  callAdmin() {
    const phone = this.data.about.phone;
    if (!phone) {
      return;
    }
    wx.makePhoneCall({ phoneNumber: phone });
  },

  copyEmail() {
    const email = this.data.about.email;
    if (!email) {
      return;
    }
    wx.setClipboardData({
      data: email,
      success: () => {
        wx.showToast({ title: '邮箱已复制', icon: 'success' });
      }
    });
  }
});
