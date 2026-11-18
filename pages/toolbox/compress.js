const QUALITIES = [
  { value: 40, label: '省空间 40%' },
  { value: 60, label: '均衡 60%' },
  { value: 80, label: '高清 80%' }
];

function formatSize(bytes) {
  if (!bytes && bytes !== 0) return '—';
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / 1024 / 1024).toFixed(2)} MB`;
}

function getFileSize(path) {
  return new Promise((resolve) => {
    wx.getFileInfo({ filePath: path, success: (res) => resolve(res.size || 0), fail: () => resolve(0) });
  });
}

function getImageSize(path) {
  return new Promise((resolve) => {
    wx.getImageInfo({
      src: path,
      success: (res) => resolve({ width: res.width || 0, height: res.height || 0 }),
      fail: () => resolve({ width: 0, height: 0 })
    });
  });
}

Page({
  data: {
    qualities: QUALITIES,
    quality: 60,
    originalPath: '',
    compressedPath: '',
    originalSize: 0,
    compressedSize: 0,
    originalSizeText: '—',
    compressedSizeText: '—',
    dimensionText: '',
    ratioText: '',
    hasImage: false,
    compressing: false
  },

  onLoad() {
    this.eventChannel = this.getOpenerEventChannel && this.getOpenerEventChannel();
  },

  onQuality(e) {
    const q = Number(e.currentTarget.dataset.value);
    if (q === this.data.quality) return;
    this.setData({ quality: q });
    if (this.data.originalPath) this.compress();
  },

  chooseImage() {
    wx.chooseMedia({
      count: 1,
      mediaType: ['image'],
      sizeType: ['original'],
      sourceType: ['album', 'camera'],
      success: async (res) => {
        const file = (res.tempFiles || [])[0];
        if (!file) return;
        const path = file.tempFilePath;
        const [size, dim] = await Promise.all([getFileSize(path), getImageSize(path)]);
        this.setData({
          originalPath: path,
          originalSize: size,
          originalSizeText: formatSize(size),
          hasImage: true,
          dimensionText: dim.width && dim.height ? `${dim.width}×${dim.height}` : ''
        });
        this.compress();
      }
    });
  },

  compress() {
    if (!this.data.originalPath) return;
    this.setData({ compressing: true, compressedPath: '' });
    wx.compressImage({
      src: this.data.originalPath,
      quality: this.data.quality,
      success: async (res) => {
        const path = res.tempFilePath || this.data.originalPath;
        const [size, dim] = await Promise.all([getFileSize(path), getImageSize(path)]);
        const o = this.data.originalSize;
        const ratio = o > 0 && size > 0 ? Math.round((1 - size / o) * 100) : 0;
        const origDim = this.data.dimensionText;
        this.setData({
          compressedPath: path,
          compressedSize: size,
          compressedSizeText: formatSize(size),
          ratioText: `体积减少约 ${Math.max(0, ratio)}%${dim.width && dim.height ? ` · ${origDim || '原图'} → ${dim.width}×${dim.height}` : ''}`,
          compressing: false
        });
      },
      fail: () => {
        this.setData({ compressing: false });
        wx.showToast({ title: '压缩失败，请重试', icon: 'none' });
      }
    });
  },

  preview() {
    const path = this.data.compressedPath || this.data.originalPath;
    if (!path) return;
    wx.previewImage({ current: path, urls: [path] });
  },

  saveToAlbum() {
    const path = this.data.compressedPath || this.data.originalPath;
    if (!path) {
      wx.showToast({ title: '请先选择图片', icon: 'none' });
      return;
    }
    wx.saveImageToPhotosAlbum({
      filePath: path,
      success: () => wx.showToast({ title: '已保存到相册', icon: 'success' }),
      fail: (err) => {
        const msg = (err && err.errMsg) || '';
        if (/auth deny|authorize/i.test(msg)) {
          wx.showModal({
            title: '需要相册权限',
            content: '请在设置中允许保存到相册',
            confirmText: '去设置',
            success: (r) => { if (r.confirm) wx.openSetting(); }
          });
        } else {
          wx.showToast({ title: '保存失败，请重试', icon: 'none' });
        }
      }
    });
  },

  useForPublish() {
    const path = this.data.compressedPath || this.data.originalPath;
    if (!path) {
      wx.showToast({ title: '请先选择图片', icon: 'none' });
      return;
    }
    const app = getApp();
    app.globalData.compressedImagePath = path;
    if (this.eventChannel && this.eventChannel.emit) {
      this.eventChannel.emit('compressedImage', { path });
    }
    wx.showToast({ title: '已带回上一页', icon: 'success' });
    setTimeout(() => wx.navigateBack({ delta: 1 }), 600);
  }
});
