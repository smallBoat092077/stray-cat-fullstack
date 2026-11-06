const { adoptionApi, feedingApi, userApi, catApi, resolveImageUrl } = require('../../utils/api');
const { ADOPTION_STATUS_MAP, RECORD_STATUS_MAP } = require('../../utils/constants');

const HOUSING_MAP = { 0: '整租', 1: '合租', 2: '自有住房', 3: '宿舍' };

const ADOPTION_APPROVED = 2;
const ADOPTION_REJECTED = 3;
const RECORD_APPROVED = 1;
const RECORD_REJECTED = 2;

function prettyTime(iso) {
  return iso ? iso.slice(0, 16).replace('T', ' ') : '';
}

Page({
  data: {
    ready: false,
    tab: 'adoption',
    pendingOnly: true,
    adoptionList: [],
    recordList: [],
    loading: false
  },

  async onLoad() {
    try {
      const user = await userApi.getCurrent();
      if (!user || user.role === null || user.role === undefined || user.role < 1) {
        wx.showToast({ title: '没有该操作权限', icon: 'none' });
        setTimeout(() => wx.navigateBack(), 800);
        return;
      }
      this.setData({ ready: true });
      this.loadList();
    } catch (err) {
      // 提示由 request 统一处理
    }
  },

  onPullDownRefresh() {
    this.loadList().then(() => wx.stopPullDownRefresh());
  },

  loadList() {
    return this.data.tab === 'adoption' ? this.loadAdoptions() : this.loadRecords();
  },

  onTab(e) {
    const tab = e.currentTarget.dataset.tab;
    if (tab === this.data.tab) {
      return;
    }
    this.setData({ tab });
    this.loadList();
  },

  onScope(e) {
    const pendingOnly = e.currentTarget.dataset.pending === '1';
    if (pendingOnly === this.data.pendingOnly) {
      return;
    }
    this.setData({ pendingOnly });
    this.loadList();
  },

  async loadAdoptions() {
    this.setData({ loading: true });
    try {
      const params = { pageNum: 1, pageSize: 20 };
      if (this.data.pendingOnly) {
        params.status = 0;
      }
      const result = await adoptionApi.getApplicationList(params);
      const records = result.records || [];
      const [names, catNames] = await Promise.all([
        this.loadUserNames(records.map(item => item.applicantId)),
        this.loadCatNames(records.map(item => item.catId))
      ]);
      this.setData({
        adoptionList: records.map(item => ({
          ...item,
          applicantName: names[item.applicantId] || `用户${item.applicantId}`,
          catName: catNames[item.catId] || `猫咪${item.catId}`,
          housingText: HOUSING_MAP[item.housingType] || '未填写',
          statusText: ADOPTION_STATUS_MAP[item.status] ? ADOPTION_STATUS_MAP[item.status].text : '未知',
          statusClass: ADOPTION_STATUS_MAP[item.status] ? ADOPTION_STATUS_MAP[item.status].class : 'info'
        })),
        loading: false
      });
    } catch (err) {
      this.setData({ loading: false });
    }
  },

  async loadRecords() {
    this.setData({ loading: true });
    try {
      const records = await feedingApi.listRecords(this.data.pendingOnly ? 0 : null, 20);
      this.setData({
        recordList: (records || []).map(item => ({
          ...item,
          timeText: prettyTime(item.checkInTime),
          images: (item.images || []).map(path => resolveImageUrl(path)),
          statusText: RECORD_STATUS_MAP[item.status] ? RECORD_STATUS_MAP[item.status].text : '未知',
          statusClass: RECORD_STATUS_MAP[item.status] ? RECORD_STATUS_MAP[item.status].class : 'info'
        })),
        loading: false
      });
    } catch (err) {
      this.setData({ loading: false });
    }
  },

  async loadUserNames(ids) {
    const map = {};
    await Promise.all([...new Set(ids.filter(Boolean))].map(async (id) => {
      try {
        const info = await userApi.getInfo(id);
        if (info && info.nickname) {
          map[id] = info.nickname;
        }
      } catch (err) {
        // 昵称只是展示用的，拿不到就退回用户 id
      }
    }));
    return map;
  },

  async loadCatNames(ids) {
    const map = {};
    await Promise.all([...new Set(ids.filter(Boolean))].map(async (id) => {
      try {
        const cat = await catApi.getDetail(id);
        if (cat && cat.name) {
          map[id] = cat.name;
        }
      } catch (err) {
        // 同上
      }
    }));
    return map;
  },

  onApprove(e) {
    const id = Number(e.currentTarget.dataset.id);
    const isAdoption = this.data.tab === 'adoption';
    wx.showModal({
      title: '确认通过',
      content: isAdoption
        ? '通过后这只猫咪会标记为已领养，并绑定申请人'
        : '通过后这条打卡记录会在猫咪详情页公开',
      success: (res) => {
        if (res.confirm) {
          this.submitReview(id, isAdoption ? ADOPTION_APPROVED : RECORD_APPROVED, '');
        }
      }
    });
  },

  onReject(e) {
    const id = Number(e.currentTarget.dataset.id);
    const isAdoption = this.data.tab === 'adoption';
    wx.showModal({
      title: '拒绝理由',
      editable: true,
      placeholderText: '写一句理由，申请人能看到',
      success: (res) => {
        if (res.confirm) {
          this.submitReview(id, isAdoption ? ADOPTION_REJECTED : RECORD_REJECTED, res.content || '');
        }
      }
    });
  },

  async submitReview(id, status, comment) {
    wx.showLoading({ title: '提交中...' });
    try {
      if (this.data.tab === 'adoption') {
        await adoptionApi.review(id, status, comment);
      } else {
        await feedingApi.reviewRecord(id, status, comment);
      }
      wx.hideLoading();
      wx.showToast({ title: '已审核', icon: 'success' });
      this.loadList();
    } catch (err) {
      wx.hideLoading();
    }
  },

  onCall(e) {
    const phone = e.currentTarget.dataset.phone;
    if (!phone) {
      return;
    }
    wx.makePhoneCall({ phoneNumber: phone, fail: () => {} });
  },

  previewRecordImage(e) {
    const { index, img } = e.currentTarget.dataset;
    const record = this.data.recordList[index];
    if (!record) {
      return;
    }
    wx.previewImage({ current: img, urls: record.images });
  }
});
