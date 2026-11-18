const { catApi, resolveImageUrl } = require('../../utils/api');
const { LIFE_STATUS_MAP, GENDER_MAP, HEALTH_STATUS_MAP } = require('../../utils/constants');
const { getCurrentLocation, requestPrivacyConsent, notifyError, describeError } = require('../../utils/location');

// 两张素材的宽高比略有差别，分别给尺寸，免得图钉被拉变形
const MARKER_VARIANTS = {
  normal: { iconPath: '/images/marker-cat.png', width: 40, height: 53 },
  adopt: { iconPath: '/images/marker-cat-adopt.png', width: 40, height: 52 }
};

// 附近没有猫时展示的人气猫咪数量
const POPULAR_LIMIT = 3;

// 上次定位结果缓存：有了它，首页可以先用旧坐标把地图和列表拉起来，不必干等 GPS
const LOCATION_CACHE_KEY = 'lastLocation';
const LOCATION_CACHE_MAX_AGE = 7 * 24 * 60 * 60 * 1000;
// 位移超过这个距离才重新拉附近列表，避免缓存请求与真实定位请求重复
const RELOAD_DISTANCE_M = 300;

function distanceMeters(lat1, lng1, lat2, lng2) {
  const R = 6371000;
  const rad = (d) => (d * Math.PI) / 180;
  const dLat = rad(lat2 - lat1);
  const dLng = rad(lng2 - lng1);
  const a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
    Math.cos(rad(lat1)) * Math.cos(rad(lat2)) * Math.sin(dLng / 2) * Math.sin(dLng / 2);
  return 2 * R * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
}

Page({
  data: {
    // 上海人民广场：仅作定位成功前的占位，失败时必须让用户看见提示，不能假装已定位
    latitude: 31.230416,
    longitude: 121.473701,
    markers: [],
    nearbyCats: [],
    popularCats: [],
    // nearby = 附近有猫，popular = 附近没猫、抽屉换成人气榜
    mode: 'nearby',
    selectedCat: null,
    cardVisible: false,
    // 初始为 true：进页面立刻显示骨架屏，而不是先白一下再加载
    loading: true,
    locating: false,
    locationFailed: false,
    // 隐私指引未同意且自动弹窗被抑制（冷启动无手势）→ 显示页面内同意卡片
    privacyConsentNeeded: false,
    // 精确定位已开通，恒为 false；保留字段以兼容 location.js 的返回结构
    locationFuzzy: false
  },

  onLoad() {
    // 先用上次的坐标直出地图与列表（请求立即发出），定位在后台并行跑
    const cached = this.readLocationCache();
    if (cached) {
      this.hasCoords = true;
      this.setData({ latitude: cached.latitude, longitude: cached.longitude });
      this.loadNearbyCats(cached.latitude, cached.longitude);
    }
    // autoPrompt:false —— 冷启动不在手势上下文，自动弹隐私窗必被抑制，直接出同意卡片
    this.getLocation({ autoPrompt: false });
  },

  onShow() {
    // 从系统设置 / 授权页返回时，之前失败过就自动再取一次，省得用户以为按钮没反应
    if (this.data.privacyConsentNeeded) {
      // 隐私弹窗需要用户手势才会显示，自动重试只会再失败一次，等用户点同意卡片
      return;
    }
    if (this.data.locationFailed && !this.data.locating) {
      this.getLocation({ autoPrompt: false });
    }
  },

  // 获取位置，失败时按原因给出可操作提示（精确定位已开通）
  // opts.skipPrivacyCheck：刚在官方弹窗点完同意，跳过隐私检查直接定位
  // opts.autoPrompt：首页一律传 false——冷启动/地图按钮都不是隐私弹窗认可的手势，
  //   自动弹窗只会白等 5 秒并刷出误导性日志，未同意时直接显示页面内同意卡片
  getLocation(opts) {
    const o = opts || {};
    if (this.data.locating) {
      return;
    }
    this.setData({ locating: true });
    getCurrentLocation(o)
      .then((res) => {
        const moved = this.hasCoords
          ? distanceMeters(this.data.latitude, this.data.longitude, res.latitude, res.longitude)
          : Infinity;
        this.hasCoords = true;
        this.locateRetried = false;
        this.timeoutPickPrompted = false;
        this.consentLocating = false;
        this.consentRetried = false;
        this.writeLocationCache(res);
        this.setData({
          latitude: res.latitude,
          longitude: res.longitude,
          locating: false,
          locationFailed: false,
          privacyConsentNeeded: false,
          locationFuzzy: res.fuzzy
        });
        // 坐标几乎没变就不再重复请求，缓存那次的结果已经够用
        if (moved > RELOAD_DISTANCE_M) {
          this.loadNearbyCats();
        }
      })
      .catch((err) => {
        const msg = (err && err.errMsg) || '';
        // 把失败原因透出到页面横幅：发布环境没有 vConsole，用户截图横幅即可上报真实 errMsg
        const tip = describeError(err);
        // 隐私指引未同意且自动弹窗被抑制（冷启动 onLoad 无手势上下文）：
        // 重试和选点都解决不了，展示页面内同意卡片，用户点击后再授权+定位
        if (err && err.needConsent) {
          this.setData({
            locating: false,
            locationFailed: true,
            privacyConsentNeeded: true,
            locateTitle: tip.title,
            locateError: msg || 'unknown'
          });
          if (!this.hasCoords) {
            this.loadNearbyCats();
          }
          return;
        }
        this.setData({
          locating: false,
          locationFailed: true,
          locateTitle: tip.title,
          locateError: msg || 'unknown'
        });
        // 超时多半是 GPS 在室内拿不到定位，静默自动重试一次；仍失败才走失败提示
        if (/timeout/i.test(msg) && !this.locateRetried) {
          this.locateRetried = true;
          this.setData({ locating: false });
          setTimeout(() => this.getLocation(o), 1500);
          return;
        }
        // 刚同意隐私指引后平台状态同步可能有几百毫秒到数秒延迟：仍报 privacy/auth
        // 类错误时，稍候用同样的跳过检查方式再试一次
        if (this.consentLocating && /privacy|auth/i.test(msg) && !this.consentRetried) {
          this.consentRetried = true;
          this.setData({ locating: false });
          setTimeout(() => this.getLocation({ skipPrivacyCheck: true, autoPrompt: false }), 1500);
          return;
        }
        // 第二次仍超时：不再反复请求，直接打开地图选点（原生选点页不依赖
        // wx.getLocation，iOS 正式环境 getLocation 挂起时这是必然可用的路径）。
        // 选点页以「当前位置」为默认中心，用户直接点确定即可拿到坐标。
        if (/timeout/i.test(msg) && !this.timeoutPickPrompted) {
          this.timeoutPickPrompted = true;
          wx.showToast({ title: '自动定位不可用，已打开地图选点', icon: 'none', duration: 2500 });
          setTimeout(() => this.onChooseLocation(), 600);
          return;
        }
        notifyError(err);
        // 一点坐标都没有时才退回人气榜（用缓存坐标时列表已在加载，不必再打一次）
        if (!this.hasCoords) {
          this.loadNearbyCats();
        } else if (this.data.loading) {
          this.setData({ loading: false });
        }
      });
  },

  // 手动选点兜底：GPS 拿不到定位时，让用户直接在地图上指出自己大概在哪。
  // chooseLocation 是地图选点，不依赖本机 GPS，已声明且审核通过
  onChooseLocation() {
    wx.chooseLocation({
      success: (res) => {
        if (!res.latitude || !res.longitude) {
          return;
        }
        this.locateRetried = false;
        this.hasCoords = true;
        this.writeLocationCache({ latitude: res.latitude, longitude: res.longitude });
        this.setData({
          latitude: res.latitude,
          longitude: res.longitude,
          locating: false,
          locationFailed: false
        });
        this.loadNearbyCats(res.latitude, res.longitude);
      }
    });
  },

  readLocationCache() {
    try {
      const c = wx.getStorageSync(LOCATION_CACHE_KEY);
      if (c && c.latitude && c.longitude && Date.now() - c.ts < LOCATION_CACHE_MAX_AGE) {
        return c;
      }
    } catch (e) { /* 读缓存失败按无缓存处理 */ }
    return null;
  },

  writeLocationCache(res) {
    try {
      wx.setStorageSync(LOCATION_CACHE_KEY, {
        latitude: res.latitude,
        longitude: res.longitude,
        ts: Date.now()
      });
    } catch (e) { /* 写缓存失败不影响主流程 */ }
  },

  // 地图上的「我的位置 / 重试定位」按钮
  onRelocate() {
    if (this.data.locating) {
      return;
    }
    // cover-view（原生组件）的点击不被微信视作隐私弹窗认可的手势，
    // 未同意时自动弹窗必然被抑制，所以这里也不自动弹，直接走同意卡片
    this.getLocation({ autoPrompt: false });
  },

  // 官方隐私同意按钮：<button open-type="agreePrivacyAuthorization">
  // 这是最可靠的一条路——点击本身就是「用户同意」动作，微信直接把授权记下来，
  // 不需要再弹 requirePrivacyAuthorize 那个在真机上经常不显示的窗口
  onPrivacyAgreeByButton() {
    this.agreeHandledAt = Date.now();
    this.startLocatingAfterConsent();
  },

  // button 上同时挂了 bindtap：老基础库不支持 open-type 时 agree 事件不会来，
  // 700ms 内没收到就退回「主动弹官方隐私窗」的老路径
  onPrivacyAgreeTap() {
    setTimeout(() => {
      if (this.agreeHandledAt && Date.now() - this.agreeHandledAt < 2000) {
        return; // open-type 已经处理过，避免重复定位
      }
      this.onPrivacyAgree();
    }, 700);
  },

  // 同意已生效：跳过隐私检查直接定位（平台状态同步有延迟，不能再问 getPrivacySetting）
  startLocatingAfterConsent() {
    if (this.data.locating) {
      return;
    }
    this.setData({ privacyConsentNeeded: false });
    this.consentLocating = true;
    this.consentRetried = false;
    // 稍等一拍再定位：授权状态刚落库时立刻调隐私接口在 iOS 上有被拦的先例
    setTimeout(() => {
      this.getLocation({ skipPrivacyCheck: true, autoPrompt: false });
    }, 300);
  },

  // 旧路径：主动弹官方隐私窗，仅作为 bindtap 兜底保留
  onPrivacyAgree() {
    if (this.data.locating) {
      return;
    }
    requestPrivacyConsent()
      .then(() => {
        this.startLocatingAfterConsent();
      })
      .catch((e) => {
        const em = (e && e.errMsg) || '';
        if (/timeout/i.test(em)) {
          // 弹窗连点击后都没显示：给出可执行选择，不让用户对着卡片干等
          wx.showModal({
            title: '隐私弹窗未显示',
            content: '微信没能弹出隐私授权窗。可以先手动选点看附近猫咪，或稍后退出重进再试。',
            confirmText: '手动选点',
            cancelText: '稍后再试',
            success: (r) => {
              if (r.confirm) {
                this.onChooseLocation();
              }
            }
          });
          return;
        }
        // 用户在官方弹窗里点了拒绝：明确告知后果并保留卡片，引导手动选点
        wx.showToast({ title: '未同意隐私指引，可先手动选点', icon: 'none', duration: 2500 });
      });
  },

  // 加载附近猫咪，空了就退到人气榜。lat/lng 不传就用当前坐标
  async loadNearbyCats(lat, lng) {
    // 请求序号：缓存请求和真实定位请求可能并发，只认最后一次的结果
    this.reqSeq = (this.reqSeq || 0) + 1;
    const seq = this.reqSeq;
    const latitude = lat != null ? lat : this.data.latitude;
    const longitude = lng != null ? lng : this.data.longitude;

    try {
      const cats = await catApi.getNearby(latitude, longitude, 5);
      if (seq !== this.reqSeq) {
        return;
      }
      const nearbyCats = cats.map((cat) => this.decorateCat(cat));
      if (nearbyCats.length > 0) {
        this.setData({
          mode: 'nearby',
          nearbyCats,
          popularCats: [],
          markers: this.buildMarkers(nearbyCats, null),
          loading: false
        });
        return;
      }
      await this.loadPopularCats(seq);
    } catch (err) {
      console.error('加载附近猫咪失败:', err);
      if (seq === this.reqSeq) {
        this.setData({ loading: false });
      }
    }
  },

  // 人气猫咪：只做抽屉卡片，不上地图——上地图就得缩放到全市，用户会误判距离
  async loadPopularCats(seq) {
    try {
      const cats = await catApi.getPopular(POPULAR_LIMIT);
      if (seq != null && seq !== this.reqSeq) {
        return;
      }
      this.setData({
        mode: 'popular',
        nearbyCats: [],
        popularCats: cats.map((cat) => this.decorateCat(cat)),
        markers: [],
        loading: false
      });
    } catch (err) {
      console.error('加载人气猫咪失败:', err);
      if (seq == null || seq === this.reqSeq) {
        this.setData({ loading: false });
      }
    }
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
        cat.color || '花色未知',
        cat.breed || ''
      ].filter(Boolean).join(' · ')
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

  // 上报流浪猫（已降级为普通页面，用 navigateTo）
  onReportTap() {
    wx.navigateTo({ url: '/pages/cat/report' });
  }
});
