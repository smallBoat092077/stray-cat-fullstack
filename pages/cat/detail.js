const { catApi, tipApi, feedingApi, resolveImageUrl } = require('../../utils/api');
const { LIFE_STATUS_MAP, GENDER_MAP } = require('../../utils/constants');
const attribution = require('../../utils/attribution');

const HEALTH_TAGS_MAP = {
  0: [],
  1: ['已检查'],
  2: ['已检查', '已绝育'],
  3: ['已检查', '已疫苗'],
  4: ['已检查', '已驱虫']
};

// 后端返回 ISO 串（2026-10-25T03:07:36），截到分钟就够看了
function prettyTime(iso) {
  return iso ? iso.slice(0, 16).replace('T', ' ') : '';
}

// 多图字段是逗号分隔的相对路径，空串要当"没有图"处理
function splitImages(raw) {
  if (!raw) {
    return [];
  }
  return String(raw).split(',').map(s => s.trim()).filter(Boolean);
}

const COMMENT_PAGE_SIZE = 20;

Page({
  data: {
    cat: null,
    catImages: [],
    envImages: [],
    envDesc: '',
    supporters: [],
    feedRecords: [],
    // 评论面板
    commentVisible: false,
    comments: [],
    commentTotal: 0,
    commentPage: 0,
    commentHasMore: true,
    commentLoading: false,
    commentDraft: '',
    // 当前回复目标：{ id, nickname }，null 表示发顶级评论
    replyTarget: null,
    keyboardHeight: 0,
    commentFocus: false,
    submitting: false,
    // onShareAppMessage 必须同步返回，短码在 onLoad 预生成好放这里
    shareSid: ''
  },

  async onLoad(options) {
    console.log('[detail] onLoad, options=', options);
    // 分享卡片带 ?id=，小程序码只带 scene=s=<sid>（page 不能带 query），
    // 后者靠 resolve 返回的 bizId 补齐猫咪 id
    let ctx = null;
    try {
      ctx = await attribution.capture(options || {});
    } catch (e) {
      ctx = null;
    }
    const rawId = (options && options.id) || (ctx && ctx.bizId);
    if (rawId) {
      this.catId = Number(rawId);
      this.loadCatDetail(this.catId);
    } else {
      // 短码无效/过期/网络失败：不无限转圈，提示后回首页
      wx.showToast({ title: '链接无效或已过期', icon: 'none' });
      setTimeout(() => {
        wx.switchTab({ url: '/pages/index/index' });
      }, 1200);
    }
    this.prepareShareSid(this.catId, ctx);
  },

  onShow() {
    if (this.catId) {
      this.loadCatDetail(this.catId);
    } else {
      // 从后台切回且首帧没拿到参数时再兜一次，不阻断渲染
      attribution.capture({});
    }
  },

  // 预生成分享短码：二次分享继承同一活动代号，服务端可按 campaign 串起整条传播链
  async prepareShareSid(catId, ctx) {
    if (!catId) {
      return;
    }
    try {
      const link = await attribution.createLink({
        channel: 'share_card',
        campaign: (ctx && ctx.campaign) || '',
        bizType: 'cat',
        bizId: catId,
        landingPage: 'pages/cat/detail'
      });
      if (link && link.sid) {
        this.setData({ shareSid: link.sid });
      }
    } catch (e) {
      // 生成失败就退回不带 sid 的旧分享 path，不影响分享功能
    }
  },

  async loadCatDetail(id) {
    try {
      const cat = await catApi.getDetail(id);
      const statusInfo = LIFE_STATUS_MAP[cat.lifeStatus] || { text: '未知', class: 'info' };
      const cover = resolveImageUrl(cat.coverImage);
      const catImages = splitImages(cat.images).map(resolveImageUrl);
      this.setData({
        cat: {
          ...cat,
          coverImage: cover,
          lifeStatusText: statusInfo.text,
          lifeStatusClass: statusInfo.class,
          genderText: GENDER_MAP[cat.gender] || '未知',
          healthTags: HEALTH_TAGS_MAP[cat.healthStatus] || []
        },
        // 老数据只有封面图，兜一张进去，轮播不至于空着
        catImages: catImages.length > 0 ? catImages : (cover ? [cover] : []),
        envImages: splitImages(cat.environmentImages).map(resolveImageUrl),
        envDesc: cat.environmentDesc || ''
      });
      this.loadSupporters(id);
      this.loadFeedRecords(id);
    } catch (err) {
      wx.showToast({ title: '加载失败', icon: 'none' });
    }
  },

  async loadSupporters(id) {
    try {
      const res = await tipApi.listByCat(id, { pageNum: 1, pageSize: 5 });
      this.setData({
        supporters: (res.records || []).map(item => ({ ...item, timeText: prettyTime(item.createdAt) }))
      });
    } catch (err) {
      // 打赏列表失败不影响主内容
    }
  },

  async loadFeedRecords(id) {
    try {
      const records = await feedingApi.listRecordsByCat(id);
      this.setData({
        feedRecords: (records || []).map(item => ({
          ...item,
          timeText: prettyTime(item.checkInTime),
          images: (item.images || []).map(path => resolveImageUrl(path))
        }))
      });
    } catch (err) {
      // 投喂记录只是附加信息，拿不到就不显示这一块
    }
  },

  previewFeedImage(e) {
    const current = e.currentTarget.dataset.src;
    const urls = this.data.feedRecords.reduce((acc, item) => acc.concat(item.images), []);
    wx.previewImage({ current, urls });
  },

  previewEnvImage(e) {
    const current = e.currentTarget.dataset.src;
    wx.previewImage({ current, urls: this.data.envImages });
  },

  previewCatImage(e) {
    const current = e.currentTarget.dataset.src;
    wx.previewImage({ current, urls: this.data.catImages });
  },

  onTip() {
    if (!getApp().ensureLogin()) {
      return;
    }
    wx.navigateTo({ url: `/pages/tip/create?catId=${this.catId}` });
  },

  async onLike() {
    if (!getApp().ensureLogin()) {
      return;
    }
    if (this._liking) {
      return;
    }
    this._liking = true;
    try {
      const res = await catApi.toggleLike(this.catId);
      this.setData({
        'cat.liked': res.liked,
        'cat.likeCount': res.likeCount
      });
    } catch (err) {
      // 错误提示由 request 统一处理
    } finally {
      this._liking = false;
    }
  },

  // ---------- 评论 ----------
  noop() {},

  openComments() {
    this.setData({ commentVisible: true });
    if (this.data.comments.length === 0) {
      this.loadComments(true);
    }
  },

  closeComments() {
    this.setData({ commentVisible: false, replyTarget: null, keyboardHeight: 0, commentFocus: false });
  },

  async loadComments(reset) {
    if (this.data.commentLoading) {
      return;
    }
    const page = reset ? 1 : this.data.commentPage + 1;
    this.setData({ commentLoading: true });
    try {
      const res = await catApi.getComments(this.catId, { pageNum: page, pageSize: COMMENT_PAGE_SIZE });
      const list = (res.records || []).map((item) => this.decorateComment(item));
      const merged = reset ? list : this.data.comments.concat(list);
      const total = Number(res.total) || 0;
      this.setData({
        comments: merged,
        commentTotal: total,
        commentPage: page,
        commentHasMore: merged.length < total
      });
    } catch (err) {
      // 拉取失败 request 已经提示过，面板留着让用户自己关
    } finally {
      this.setData({ commentLoading: false });
    }
  },

  loadMoreComments() {
    if (this.data.commentHasMore && !this.data.commentLoading) {
      this.loadComments(false);
    }
  },

  decorateComment(item) {
    return {
      ...item,
      avatar: resolveImageUrl(item.avatar),
      timeText: prettyTime(item.createdAt),
      expanded: false,
      replies: (item.replies || []).map((reply) => ({
        ...reply,
        avatar: resolveImageUrl(reply.avatar),
        timeText: prettyTime(reply.createdAt)
      }))
    };
  },

  async onExpandReplies(e) {
    const id = Number(e.currentTarget.dataset.id);
    const index = this.data.comments.findIndex((item) => item.id === id);
    if (index < 0) {
      return;
    }
    try {
      const res = await catApi.getCommentReplies(id, { pageNum: 1, pageSize: 50 });
      this.setData({
        [`comments[${index}].replies`]: (res.records || []).map((reply) => ({
          ...reply,
          avatar: resolveImageUrl(reply.avatar),
          timeText: prettyTime(reply.createdAt)
        })),
        [`comments[${index}].expanded`]: true
      });
    } catch (err) {
      // 展开失败保持原样，可以再点一次
    }
  },

  onDraftInput(e) {
    this.setData({ commentDraft: e.detail.value });
  },

  // 点评论正文就是回复它；parentId 传被点那条的 id，服务端自己归楼
  onReplyTap(e) {
    const dataset = e.currentTarget.dataset;
    this.setData({
      replyTarget: { id: Number(dataset.id), nickname: dataset.name || '' },
      commentFocus: true
    });
  },

  onInputFocus(e) {
    this.setData({ keyboardHeight: (e.detail && e.detail.height) || 0 });
  },

  onInputBlur() {
    this.setData({ keyboardHeight: 0, commentFocus: false });
  },

  async onSubmitComment() {
    if (!getApp().ensureLogin()) {
      return;
    }
    const content = (this.data.commentDraft || '').trim();
    if (!content || this.data.submitting) {
      return;
    }
    this.setData({ submitting: true });
    const parentId = this.data.replyTarget ? this.data.replyTarget.id : 0;
    try {
      const created = await catApi.createComment(this.catId, { parentId, content });
      const item = this.decorateComment(created);
      if (parentId === 0) {
        this.setData({
          comments: [item].concat(this.data.comments),
          commentTotal: this.data.commentTotal + 1
        });
      } else {
        this.attachReply(item);
      }
      this.setData({
        commentDraft: '',
        replyTarget: null,
        commentFocus: false,
        'cat.commentCount': (this.data.cat.commentCount || 0) + 1
      });
      wx.showToast({ title: '已发布', icon: 'none' });
    } catch (err) {
      // 内容违规等提示由 request 统一处理，草稿留着方便改
    } finally {
      this.setData({ submitting: false });
    }
  },

  // 新回复挂到所属那一楼末尾；那一楼不在已加载页里就重拉第一页
  attachReply(item) {
    const threadId = Number(item.parentId);
    const index = this.data.comments.findIndex((c) => c.id === threadId);
    if (index < 0) {
      this.loadComments(true);
      return;
    }
    const thread = this.data.comments[index];
    this.setData({
      [`comments[${index}].replyCount`]: (thread.replyCount || 0) + 1,
      [`comments[${index}].replies`]: (thread.replies || []).concat([item])
    });
  },

  onDeleteComment(e) {
    const id = Number(e.currentTarget.dataset.id);
    wx.showModal({
      title: '删除评论',
      content: '删除后这条评论（含它的回复）会一起下架，确定吗？',
      confirmColor: '#A8453B',
      success: (res) => {
        if (res.confirm) {
          this.removeComment(id);
        }
      }
    });
  },

  async removeComment(id) {
    const total = this.data.commentTotal;
    const catCount = this.data.cat.commentCount || 0;
    try {
      const top = this.data.comments.find((c) => c.id === id);
      let gone = 1;
      if (top) {
        gone = 1 + (top.replyCount || 0);
        this.setData({ comments: this.data.comments.filter((c) => c.id !== id) });
      } else {
        const index = this.data.comments.findIndex((c) => (c.replies || []).some((r) => r.id === id));
        if (index < 0) {
          return;
        }
        const thread = this.data.comments[index];
        this.setData({
          [`comments[${index}].replies`]: thread.replies.filter((r) => r.id !== id),
          [`comments[${index}].replyCount`]: Math.max((thread.replyCount || 1) - 1, 0)
        });
      }
      this.setData({
        commentTotal: Math.max(total - gone, 0),
        'cat.commentCount': Math.max(catCount - gone, 0)
      });
    } catch (err) {
      // request 已提示
    }
  },

  onLocate() {
    const cat = this.data.cat;
    if (!cat.latitude || !cat.longitude) {
      wx.showToast({ title: '这只猫还没有上报坐标', icon: 'none' });
      return;
    }
    wx.navigateTo({ url: `/pages/cat/navigate?id=${this.catId}` });
  },

  onAdopt() {
    wx.navigateTo({ url: `/pages/adoption/apply?catId=${this.data.cat.id}` });
  },

  onShareAppMessage() {
    const cat = this.data.cat || {};
    const title = `来看看${cat.name || '这只猫'}${cat.description ? '，' + cat.description : ''}`.slice(0, 30);
    const id = this.catId || cat.id || '';
    const sid = this.data.shareSid;
    return {
      title,
      path: `/pages/cat/detail?id=${id}${sid ? `&sid=${sid}` : ''}`,
      imageUrl: '/images/share-cover.png'
    };
  }
});
