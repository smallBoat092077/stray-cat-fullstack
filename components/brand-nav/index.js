Component({
  properties: {
    // 非 tab 页传 true：左侧出现返回箭头（自定义导航后系统返回键没有了）
    back: { type: Boolean, value: false },
    // 首页地图是原生组件，fixed 层可能被盖住，传 false 走文档流
    fixed: { type: Boolean, value: true }
  },

  data: {
    statusBarHeight: 20,
    navBarHeight: 44,
    totalHeight: 64
  },

  lifetimes: {
    attached() {
      // 按机型实测状态栏与右上角胶囊，避免写死高度在刘海屏上错位
      let statusBarHeight = 20;
      let navBarHeight = 44;
      try {
        const win = wx.getWindowInfo ? wx.getWindowInfo() : wx.getSystemInfoSync();
        statusBarHeight = win.statusBarHeight || 20;
        const menu = wx.getMenuButtonBoundingClientRect ? wx.getMenuButtonBoundingClientRect() : null;
        if (menu && menu.height) {
          navBarHeight = (menu.top - statusBarHeight) * 2 + menu.height;
        }
      } catch (e) {
        // 兜底走默认值
      }
      this.setData({
        statusBarHeight,
        navBarHeight,
        totalHeight: statusBarHeight + navBarHeight
      });
    }
  },

  methods: {
    onBack() {
      // 从分享卡片直接进来时没有上一页，回首页而不是卡死
      if (getCurrentPages().length > 1) {
        wx.navigateBack();
      } else {
        wx.reLaunch({ url: '/pages/index/index' });
      }
    }
  }
});
