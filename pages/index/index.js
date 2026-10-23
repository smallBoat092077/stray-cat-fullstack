const { catApi } = require('../../utils/api');

const LIFE_STATUS_MAP = {
  0: { text: '流浪中', class: 'warning' },
  1: { text: '投喂中', class: 'info' },
  2: { text: '待领养', class: 'success' },
  3: { text: '已领养', class: 'success' },
  4: { text: '回访中', class: 'info' }
};

Page({
  data: {
    latitude: 31.230416,
    longitude: 121.473701,
    markers: [],
    nearbyCats: [],
    loading: false
  },

  onLoad() {
    this.getLocation();
  },

  // 获取位置
  getLocation() {
    wx.getLocation({
      type: 'gcj02',
      success: (res) => {
        this.setData({
          latitude: res.latitude,
          longitude: res.longitude
        });
        this.loadNearbyCats();
      },
      fail: () => {
        // 默认上海位置
        this.loadNearbyCats();
      }
    });
  },

  // 加载附近猫咪
  async loadNearbyCats() {
    this.setData({ loading: true });
    try {
      const cats = await catApi.getNearby(
        this.data.latitude,
        this.data.longitude,
        5
      );
      const markers = cats.map(cat => ({
        id: cat.id,
        latitude: cat.latitude,
        longitude: cat.longitude,
        width: 40,
        height: 40,
        callout: {
          content: cat.name,
          color: '#333',
          fontSize: 12,
          borderRadius: 4,
          bgColor: '#fff',
          padding: 6,
          display: 'ALWAYS'
        }
      }));
      const nearbyCats = cats.map(cat => ({
        ...cat,
        lifeStatusText: LIFE_STATUS_MAP[cat.lifeStatus]?.text || '未知'
      }));
      this.setData({ markers, nearbyCats });
    } catch (err) {
      console.error('加载附近猫咪失败:', err);
    } finally {
      this.setData({ loading: false });
    }
  },

  // 点击标记
  onMarkerTap(e) {
    const catId = e.detail.markerId;
    wx.navigateTo({ url: `/pages/cat/detail?id=${catId}` });
  },

  // 附近猫咪按钮
  onNearbyTap() {
    this.loadNearbyCats();
  },

  // 跳转猫咪列表
  goToCatList() {
    wx.switchTab({ url: '/pages/cat/list' });
  },

  // 跳转猫咪详情
  goToCatDetail(e) {
    const id = e.currentTarget.dataset.id;
    wx.navigateTo({ url: `/pages/cat/detail?id=${id}` });
  },

  // 上报流浪猫
  onReportTap() {
    wx.navigateTo({ url: '/pages/cat/report' });
  }
});
