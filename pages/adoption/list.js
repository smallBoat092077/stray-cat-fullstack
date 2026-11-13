const { adoptionApi } = require('../../utils/api');
const { prefetchTemplates, requestSubscribe, SUBSCRIBE_KEYS } = require('../../utils/subscribe');
const { ADOPTION_STATUS_MAP, CO_ADOPTION_STATUS_MAP } = require('../../utils/constants');

// 这几种状态下申请还活着，可以继续邀请共同领养人
const INVITABLE_STATUS = [0, 1, 2, 4];

Page({
  data: {
    adoptionList: [],
    pendingInvites: [],
    acceptedCoAdoptions: [],
    currentStatus: '',
    pageNum: 1,
    pageSize: 10,
    hasMore: true,
    loading: false
  },

  onLoad() {
    prefetchTemplates();
    this.loadAdoptions();
  },

  onShow() {
    // 从消息中心点进来时，邀请状态可能已经变了，每次回到页面都刷一遍
    this.loadCoAdoptions();
  },

  onPullDownRefresh() {
    this.setData({ pageNum: 1, hasMore: true, adoptionList: [] });
    Promise.all([this.loadAdoptions(), this.loadCoAdoptions()])
      .then(() => wx.stopPullDownRefresh());
  },

  onReachBottom() {
    if (this.data.hasMore && !this.data.loading) {
      this.loadAdoptions();
    }
  },

  async loadAdoptions() {
    this.setData({ loading: true });
    try {
      const app = getApp();
      const applicantId = app.globalData.userInfo && app.globalData.userInfo.id;

      const params = {
        pageNum: this.data.pageNum,
        pageSize: this.data.pageSize
      };

      if (applicantId) {
        params.applicantId = applicantId;
      }
      if (this.data.currentStatus !== '') {
        params.status = this.data.currentStatus;
      }

      const result = await adoptionApi.getApplicationList(params);
      const myId = app.globalData.userInfo && app.globalData.userInfo.id;
      const list = (result.records || []).map(item => ({
        ...item,
        statusText: (ADOPTION_STATUS_MAP[item.status] || {}).text || '未知',
        statusClass: (ADOPTION_STATUS_MAP[item.status] || {}).class || 'info',
        // 只有自己的申请单能邀请共同领养人，志愿者看到的别人的单子不给入口
        canInvite: INVITABLE_STATUS.indexOf(item.status) >= 0 && item.applicantId === myId
      }));

      this.setData({
        adoptionList: this.data.pageNum === 1 ? list : [...this.data.adoptionList, ...list],
        hasMore: result.current < result.pages,
        pageNum: this.data.pageNum + 1
      });
    } catch (err) {
      console.error('加载领养列表失败:', err);
    } finally {
      this.setData({ loading: false });
    }
  },

  // 我收到的共同领养邀请：待确认的单独拎出来，同意的留在下方
  async loadCoAdoptions() {
    if (!getApp().globalData.token) {
      return;
    }
    try {
      const list = await adoptionApi.myCoAdoptions();
      const mapped = (list || []).map(item => ({
        ...item,
        statusText: (CO_ADOPTION_STATUS_MAP[item.status] || {}).text || '未知',
        statusClass: (CO_ADOPTION_STATUS_MAP[item.status] || {}).class || 'info',
        appStatusText: (ADOPTION_STATUS_MAP[item.applicationStatus] || {}).text || ''
      }));
      this.setData({
        pendingInvites: mapped.filter(item => item.status === 0),
        acceptedCoAdoptions: mapped.filter(item => item.status === 1)
      });
    } catch (err) {
      console.error('加载共同领养失败:', err);
    }
  },

  async onRespond(e) {
    const { id, accept } = e.currentTarget.dataset;
    // dataset 拿到的是字符串，统一成布尔再传给后端
    const agreed = String(accept) === '1' || accept === true;
    // 成为共同领养人之后，这张申请单的审核结论也会推给自己
    requestSubscribe(SUBSCRIBE_KEYS.adoption);
    try {
      await adoptionApi.respondCoAdoption(id, agreed);
      wx.showToast({ title: agreed ? '已同意共同领养' : '已拒绝', icon: 'none' });
      this.loadCoAdoptions();
    } catch (err) {
      // 错误提示由 request 统一处理
    }
  },

  // 邀请共同领养人：按账号名或昵称找人，重名时后端会要求改用账号名
  onInvite(e) {
    const applicationId = e.currentTarget.dataset.id;
    if (!applicationId) {
      return;
    }
    // 邀请人自己会收到「对方同意/拒绝」的推送，先在这次点击里把额度要下来
    requestSubscribe(SUBSCRIBE_KEYS.coAdoptionResult);
    wx.showModal({
      title: '邀请共同领养',
      editable: true,
      placeholderText: '对方账号名或昵称',
      confirmText: '发送邀请',
      success: (res) => {
        if (!res.confirm) {
          return;
        }
        const keyword = (res.content || '').trim();
        if (!keyword) {
          wx.showToast({ title: '请填写账号名或昵称', icon: 'none' });
          return;
        }
        adoptionApi.inviteCoAdopter(applicationId, keyword)
          .then(() => {
            wx.showToast({ title: '邀请已发送', icon: 'success' });
            // 名额和人数变了，从第一页重新拉
            this.setData({ pageNum: 1, hasMore: true, adoptionList: [] });
            this.loadAdoptions();
          })
          .catch(() => {});
      }
    });
  },

  // 点共同领养人数看名单
  async onShowCoAdopters(e) {
    const applicationId = e.currentTarget.dataset.id;
    try {
      const list = await adoptionApi.getCoAdopters(applicationId);
      const lines = (list || []).map(item => {
        const status = (CO_ADOPTION_STATUS_MAP[item.status] || {}).text || '';
        return `${item.userName}（${status}）`;
      });
      wx.showModal({
        title: '共同领养人',
        content: lines.length ? lines.join('\n') : '还没有邀请共同领养人',
        showCancel: false,
        confirmText: '知道了'
      });
    } catch (err) {
      // 无权限或网络异常时由 request 提示
    }
  },

  onFilter(e) {
    const status = e.currentTarget.dataset.status;
    this.setData({ currentStatus: status, pageNum: 1, hasMore: true, adoptionList: [] });
    this.loadAdoptions();
  },

  onReapply(e) {
    // apply 页认的是 catId；这里以前传的是申请单 id，进去必然提示「猫咪信息缺失」
    const catId = e.currentTarget.dataset.catId;
    if (!catId) {
      wx.showToast({ title: '猫咪信息缺失，请从猫咪详情进入', icon: 'none' });
      return;
    }
    wx.navigateTo({ url: `/pages/adoption/apply?catId=${catId}` });
  },

  goToCatList() {
    wx.switchTab({ url: '/pages/cat/list' });
  }
});
