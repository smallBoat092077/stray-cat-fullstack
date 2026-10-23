const { catApi } = require('../../utils/api');

const LIFE_STATUS_MAP = {
  0: { text: '流浪中', class: 'warning' },
  1: { text: '投喂中', class: 'info' },
  2: { text: '待领养', class: 'success' },
  3: { text: '已领养', class: 'success' },
  4: { text: '回访中', class: 'info' }
};

const GENDER_MAP = { 0: '母', 1: '公', 2: '未知' };

const HEALTH_STATUS_MAP = {
  0: [],
  1: ['已检查'],
  2: ['已检查', '已绝育'],
  3: ['已检查', '已疫苗'],
  4: ['已检查', '已驱虫']
};

Page({
  data: {
    cat: null
  },

  onLoad(options) {
    if (options.id) {
      this.loadCatDetail(options.id);
    }
  },

  async loadCatDetail(id) {
    try {
      const cat = await catApi.getDetail(id);
      const statusInfo = LIFE_STATUS_MAP[cat.lifeStatus] || { text: '未知', class: 'info' };
      this.setData({
        cat: {
          ...cat,
          lifeStatusText: statusInfo.text,
          lifeStatusClass: statusInfo.class,
          genderText: GENDER_MAP[cat.gender] || '未知',
          healthTags: HEALTH_STATUS_MAP[cat.healthStatus] || []
        }
      });
    } catch (err) {
      wx.showToast({ title: '加载失败', icon: 'none' });
    }
  },

  onDonate() {
    wx.showToast({ title: '打赏功能开发中', icon: 'none' });
  },

  onAdopt() {
    wx.navigateTo({ url: `/pages/adoption/apply?catId=${this.data.cat.id}` });
  },

  onShare() {
    // 微信小程序分享
  },

  onShareAppMessage() {
    return {
      title: `来看看${this.data.cat.name}，${this.data.cat.description}`,
      path: `/pages/cat/detail?id=${this.data.cat.id}`
    };
  }
});
