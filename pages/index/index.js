const { catApi, resolveImageUrl } = require('../../utils/api');
const { LIFE_STATUS_MAP, GENDER_MAP, HEALTH_STATUS_MAP } = require('../../utils/constants');

// 两张素材的宽高比略有差别，分别给尺寸，免得图钉被拉变形
const MARKER_VARIANTS = {
  normal: { iconPath: '/images/marker-cat.png', width: 40, height: 53 },
  adopt: { iconPath: '/images/marker-cat-adopt.png', width: 40, height: 52 }
};

// 附近没有猫时展示的人气猫咪数量
const POPULAR_LIMIT = 3;

Page({
  data: {
    latitude: 31.230416,
    longitude: 121.473701,
    markers: [],
    nearbyCats: [],
    popularCats: [],
    // nearby = 附近有猫，popular = 附近没猫、抽屉换成人气榜
    mode: 'nearby',
    selectedCat: null,
    cardVisible: false,
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

  // 加载附近猫咪，空了就退到人气榜
  async loadNearbyCats() {
    this.setData({ loading: true });
    try {
      const cats = await catApi.getNearby(
        this.data.latitude,
        this.data.longitude,
        5
      );
      const nearbyCats = cats.map((cat) => this.decorateCat(cat));
      if (nearbyCats.length > 0) {
        this.setData({
          mode: 'nearby',
          nearbyCats,
          popularCats: [],
          markers: this.buildMarkers(nearbyCats, null)
        });
        return;
      }
      await this.loadPopularCats();
    } catch (err) {
      console.error('加载附近猫咪失败:', err);
    } finally {
      this.setData({ loading: false });
    }
  },

  // 人气猫咪：只做抽屉卡片，不上地图——上地图就得缩放到全市，用户会误判距离
  async loadPopularCats() {
    const cats = await catApi.getPopular(POPULAR_LIMIT);
    this.setData({
      mode: 'popular',
      nearbyCats: [],
      popularCats: cats.map((cat) => this.decorateCat(cat)),
      markers: []
    });
  },

  decorateCat(cat) {
    const life = LIFE_STATUS_MAP[cat.lifeStatus] || {};
    const health = HEALTH_STATUS_MAP[cat.healthStatus] || {};
    return {
      ...cat,
      coverImage: resolveImageUrl(cat.coverImage),
      lifeStatusText: life.text || '未知',
      lifeStatusClass: life.class || 'info',
      healthText: health.text || '未检查',
      healthClass: health.class || 'warning',
      metaText: [
        GENDER_MAP[cat.gender] || '未知',
        cat.ageEstimate || '年龄未知',
        cat.color || '花色未知'
      ].join(' · ')
    };
  },

  buildMarkers(cats, selectedId) {
    return cats.map((cat) => {
      const variant = cat.lifeStatus === 2 ? MARKER_VARIANTS.adopt : MARKER_VARIANTS.normal;
      const active = selectedId === cat.id;
      return {
        id: cat.id,
        latitude: Number(cat.latitude),
        longitude: Number(cat.longitude),
        iconPath: variant.iconPath,
        width: active ? variant.width + 6 : variant.width,
        height: active ? variant.height + 8 : variant.height,
        // 图钉尖端落在坐标上，不然选中的猫会“飘”在点位上方
        anchor: { x: 0.5, y: 1 },
        zIndex: active ? 20 : 1,
        label: {
          content: cat.name,
          color: active ? '#0C3329' : '#14493C',
          fontSize: 11,
          borderRadius: 14,
          borderWidth: 1,
          borderColor: '#C3A15F',
          bgColor: '#FFFDF7',
          padding: 5,
          x: 0,
          y: 8,
          display: 'ALWAYS'
        }
      };
    });
  },

  // 点击标记：就地弹出猫咪卡片
  onMarkerTap(e) {
    // 基础库不同版本把 markerId 放在 detail 或事件本体上，两边都接
    const markerId = e.detail ? e.detail.markerId : e.markerId;
    const cat = this.data.nearbyCats.find((item) => item.id === markerId);
    if (cat) {
      this.openCard(cat);
    }
  },

  // 抽屉卡片：人气态弹 3D 卡，附近态仍进详情页
  onSheetCatTap(e) {
    const id = Number(e.currentTarget.dataset.id);
    console.log('[map] onSheetCatTap id=', id, 'mode=', this.data.mode);
    if (this.data.mode !== 'popular') {
      wx.navigateTo({ url: `/pages/cat/detail?id=${id}` });
      return;
    }
    const cat = this.data.popularCats.find((item) => item.id === id);
    if (cat) {
      this.openCard(cat);
    }
  },

  openCard(cat) {
    getApp().globalData.focusCatId = cat.id;
    this.setData({
      selectedCat: cat,
      markers: this.buildMarkers(this.data.nearbyCats, cat.id)
    });
    // 先挂载再切 show，遮罩淡入和卡片入场动画才有起始帧
    setTimeout(() => {
      this.setData({ cardVisible: true });
    }, 20);
  },

  closeCard() {
    this.setData({
      cardVisible: false,
      markers: this.buildMarkers(this.data.nearbyCats, null)
    });
    // 退场动画跑完再卸载内容，否则卡片会瞬间空掉
    setTimeout(() => {
      if (!this.data.cardVisible) {
        this.setData({ selectedCat: null });
      }
    }, 320);
  },

  noop() {},

  // 附近猫咪按钮
  onNearbyTap() {
    this.closeCard();
    this.loadNearbyCats();
  },

  // 跳转猫咪列表
  goToCatList() {
    wx.switchTab({ url: '/pages/cat/list' });
  },

  onCardDetail() {
    console.log('[map] onCardDetail tap, selectedCat=', this.data.selectedCat);
    wx.navigateTo({ url: `/pages/cat/detail?id=${this.data.selectedCat.id}` });
  },

  onCardAdopt() {
    wx.navigateTo({ url: `/pages/adoption/apply?catId=${this.data.selectedCat.id}` });
  },

  onCardTip() {
    wx.navigateTo({ url: `/pages/tip/create?catId=${this.data.selectedCat.id}` });
  },

  // 上报流浪猫
  onReportTap() {
    wx.navigateTo({ url: '/pages/cat/report' });
  }
});
