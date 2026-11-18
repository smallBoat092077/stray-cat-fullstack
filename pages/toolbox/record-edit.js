const care = require('../../utils/care.js');
const { uploadFile } = require('../../utils/api.js');

const TYPES = [
  { key: 'feed', label: '进食', short: '食', unit: '克' },
  { key: 'water', label: '喝水', short: '水', unit: '毫升' },
  { key: 'litter', label: '排泄', short: '泄', unit: '次' },
  { key: 'medicine', label: '吃药', short: '药', unit: '次' }
];

function pad(n) { return String(n).padStart(2, '0'); }

function nowParts() {
  const d = new Date();
  return {
    date: `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`,
    time: `${pad(d.getHours())}:${pad(d.getMinutes())}`
  };
}

Page({
  data: {
    catId: '',
    recordId: '',
    types: TYPES,
    type: 'feed',
    date: '',
    time: '',
    amount: '',
    medName: '',
    photo: '',
    remark: '',
    uploading: false,
    saving: false
  },

  onLoad(options) {
    const cat = care.getCurrentCat();
    const n = nowParts();
    this.setData({ catId: options.id || cat.id, date: n.date, time: n.time });
    if (options.type) this.setData({ type: options.type });
    if (options.recordId) {
      const rec = care.getRecord(this.data.catId, options.recordId);
      if (rec) {
        const d = new Date(rec.time || '');
        const date = isNaN(d.getTime()) ? n.date : `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
        const time = isNaN(d.getTime()) ? n.time : `${pad(d.getHours())}:${pad(d.getMinutes())}`;
        this.setData({
          recordId: rec.id,
          type: rec.type || 'feed',
          date,
          time,
          amount: rec.amount != null ? String(rec.amount) : '',
          medName: rec.medName || '',
          photo: rec.photo || '',
          remark: rec.remark || ''
        });
        wx.setNavigationBarTitle({ title: '编辑记录' });
      }
    }
  },

  onType(e) {
    this.setData({ type: e.currentTarget.dataset.type });
  },

  onDateChange(e) {
    this.setData({ date: e.detail.value });
  },

  onTimeChange(e) {
    this.setData({ time: e.detail.value });
  },

  onInput(e) {
    this.setData({ [e.currentTarget.dataset.field]: e.detail.value });
  },

  choosePhoto() {
    wx.chooseMedia({
      count: 1,
      mediaType: ['image'],
      sizeType: ['compressed'],
      sourceType: ['album', 'camera'],
      success: async (res) => {
        const file = (res.tempFiles || [])[0];
        if (!file) return;
        this.setData({ uploading: true });
        try {
          const url = await uploadFile(file.tempFilePath);
          this.setData({ photo: url });
        } catch (e) {
          wx.showToast({ title: '照片上传失败，请重试', icon: 'none' });
        } finally {
          this.setData({ uploading: false });
        }
      }
    });
  },

  previewPhoto() {
    wx.previewImage({ current: this.data.photo, urls: [this.data.photo] });
  },

  removePhoto() {
    this.setData({ photo: '' });
  },

  onSave() {
    if (this.data.saving) return;
    const d = this.data;
    const meta = TYPES.find((t) => t.key === d.type) || TYPES[0];
    if (d.type === 'medicine' && !d.medName.trim()) {
      wx.showToast({ title: '请填写用药名', icon: 'none' });
      return;
    }
    if (d.amount !== '' && isNaN(Number(d.amount))) {
      wx.showToast({ title: '请输入正确的量', icon: 'none' });
      return;
    }
    this.setData({ saving: true });
    const time = `${d.date}T${d.time}:00`;
    care.saveRecord(d.catId, {
      id: d.recordId || undefined,
      type: d.type,
      time,
      amount: d.amount !== '' ? Number(d.amount) : '',
      medName: d.type === 'medicine' ? d.medName.trim() : '',
      photo: d.photo,
      remark: d.remark.trim(),
      createdAt: new Date().toISOString()
    });
    wx.showToast({ title: d.recordId ? '已保存' : '已记录', icon: 'success' });
    setTimeout(() => wx.navigateBack({ delta: 1 }), 600);
  }
});
