const { campaignApi, resolveImageUrl, messageApi } = require('../../utils/api');
const { LIFE_STATUS_MAP } = require('../../utils/constants');
const attribution = require('../../utils/attribution');
const { prefetchTemplates, setTemplateMap, requestSubscribe } = require('../../utils/subscribe');

/**
 * 活动聚合落地页。三个入口殊途同归：
 * 1. 公众号图文/线下物料扫带参小程序码 → scene=s=<sid> → resolve 补 bizId(活动id)
 * 2. 分享卡片 → ?sid=<sid> 或 ?code=<活动代号>
 * 3. 站内消息「活动提醒」→ ?id=<活动id>
 */
Page({
  data: {
    campaign: null,
    cats: [],
    loading: true,
    notFound: false,
    // 后台配了 campaignReminder 模板时才给「订阅活动提醒」入口
    canSubscribe: false,
    subscribing: false,
    // onShareAppMessage 必须同步返回，短码 onLoad 预生成
    shareSid: ''
  },

  async onLoad(options) {
    this._code = (options && options.code) || '';
    this._id = (options && options.id) || '';
    let ctx = null;
    try {
      ctx = await attribution.capture(options || {});
    } catch (e) {
      ctx = null;
    }
    // 扫码进来只有 scene 短码：resolve 出 bizType=campaign + bizId（活动 id）
    if (!this._code && !this._id && ctx && ctx.bizType === 'campaign' && ctx.bizId) {
      this._id = String(ctx.bizId);
    }
    if (ctx && ctx.campaign) {
      this._code = this._code || ctx.campaign;
    }
    this.loadDetail();
    this.checkSubscribeTemplate();
  },

  onShow() {
    // 从后台切回且首帧没拿到参数时再兜一次，不阻断渲染
    if (!this.data.campaign) {
      attribution.capture({});
    }
  },

  onPullDownRefresh() {
    this.loadDetail().then(() => wx.stopPullDownRefresh());
  },

  async loadDetail() {
    if (!this._code && !this._id) {
      this.setData({ loading: false, notFound: true });
      return;
    }
    this.setData({ loading: true });
    try {
      const params = {};
      if (this._code) {
        params.code = this._code;
      } else {
        params.id = this._id;
      }
      const detail = await campaignApi.detail(params);
      if (!detail) {
        this.setData({ loading: false, notFound: true });
        return;
      }
      const cats = (detail.cats || []).map((cat) => {
        const info = LIFE_STATUS_MAP[cat.lifeStatus] || { text: '未知', class: 'info' };
        return {
          ...cat,
          coverImage: resolveImageUrl(cat.coverImage),
          lifeStatusText: info.text,
          lifeStatusClass: info.class
        };
      });
      this.setData({ campaign: detail, cats, loading: false, notFound: false });
      // 分享短码绑活动代号，二次分享与投放归因同一 campaign
      this.prepareShareSid(detail);
    } catch (err) {
      console.error('[campaign] 加载活动失败:', err);
      this.setData({ loading: false, notFound: true });
    }
  },

  // 模板没配置（个人主体还在申请中）时按钮整体隐藏，不阻塞发版
  async checkSubscribeTemplate() {
    try {
      const templates = await messageApi.templates();
      setTemplateMap(templates);
      this.setData({ canSubscribe: !!(templates && templates.campaignReminder) });
    } catch (e) {
      this.setData({ canSubscribe: false });
    }
  },

  async prepareShareSid(detail) {
    try {
      const link = await attribution.createLink({
        channel: 'share_card',
        campaign: detail.code || '',
        bizType: 'campaign',
        bizId: detail.id,
        landingPage: 'pages/campaign/index'
      });
      if (link && link.sid) {
        this.setData({ shareSid: link.sid });
      }
    } catch (e) {
      // 失败退回不带 sid 的分享
    }
  },

  // 活动页的订阅引导复用消息中心的机制：模板 ID 由服务端下发，手势内弹授权
  async onSubscribe() {
    if (this.data.subscribing) {
      return;
    }
    prefetchTemplates();
    this.setData({ subscribing: true });
    try {
      const r = await requestSubscribe(['campaignReminder']);
      let title;
      if (r.skipped) {
        title = '订阅通道未就绪，稍后再试';
      } else if (r.accept > 0) {
        title = '已订阅活动提醒';
      } else if (r.ban > 0) {
        title = '此前选过「总是拒绝」，微信不再询问';
      } else {
        title = '未完成订阅，可再点一次';
      }
      wx.showToast({ title, icon: 'none', duration: 2500 });
    } finally {
      this.setData({ subscribing: false });
    }
  },

  goCat(e) {
    const id = e.currentTarget.dataset.id;
    wx.navigateTo({ url: `/pages/cat/detail?id=${id}` });
  },

  goCatList() {
    wx.switchTab({ url: '/pages/cat/list' });
  },

  onShareAppMessage() {
    const name = this.data.campaign ? this.data.campaign.name : '猫遇猫舍';
    const path = this.data.shareSid
      ? `/pages/campaign/index?sid=${this.data.shareSid}`
      : (this.data.campaign
        ? `/pages/campaign/index?code=${this.data.campaign.code}`
        : '/pages/campaign/index');
    return {
      title: `${name} · 一起来看看`,
      path,
      imageUrl: '/images/share-cover.png'
    };
  }
});
