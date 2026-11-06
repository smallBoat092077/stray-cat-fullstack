const { catApi, geoApi } = require('../../utils/api');
const { LIFE_STATUS_MAP } = require('../../utils/constants');

function formatDistance(meters) {
  return meters < 1000 ? `${Math.round(meters)} 米` : `${(meters / 1000).toFixed(1)} 公里`;
}

function formatDuration(seconds) {
  const minutes = Math.max(1, Math.round(seconds / 60));
  return minutes < 60 ? `步行约 ${minutes} 分钟` : `步行约 ${(minutes / 60).toFixed(1)} 小时`;
}

Page({
  data: {
    cat: null,
    myLat: null,
    myLng: null,
    markers: [],
    polyline: [],
    includePoints: [],
    distanceText: '',
    durationText: '',
    routeSource: '',
    locating: true,
    errorMsg: ''
  },

  onLoad(options) {
    this.catId = Number(options.id);
    this.loadCat();
  },

  async loadCat() {
    try {
      const cat = await catApi.getDetail(this.catId);
      const statusInfo = LIFE_STATUS_MAP[cat.lifeStatus] || { text: '未知', class: 'info' };
      this.setData({ cat: { ...cat, lifeStatusText: statusInfo.text, lifeStatusClass: statusInfo.class } });
      if (!cat.latitude || !cat.longitude) {
        this.setData({ locating: false, errorMsg: '这只猫还没有上报坐标，暂时无法导航' });
        return;
      }
      this.locate();
    } catch (err) {
      this.setData({ locating: false, errorMsg: '加载猫咪信息失败，点「重新定位」重试' });
    }
  },

  locate() {
    if (!this.data.cat) {
      this.loadCat();
      return;
    }
    this.setData({ locating: true, errorMsg: '' });
    wx.getLocation({
      // 上报走的是 chooseLocation，返回的就是 gcj02；这里换成 wgs84 会整体偏上百米
      type: 'gcj02',
      success: (res) => this.requestRoute(res.latitude, res.longitude),
      fail: () => {
        this.setData({
          locating: false,
          errorMsg: '没拿到你的位置。点右上角「···」→「设置」里允许获取位置，再点重新定位'
        });
      }
    });
  },

  requestRoute(myLat, myLng) {
    const cat = this.data.cat;
    const mePoint = { latitude: myLat, longitude: myLng };
    const catPoint = { latitude: cat.latitude, longitude: cat.longitude };
    this.setData({ myLat, myLng });
    geoApi
      .getRoute(this.catId, myLat, myLng)
      .then((route) => this.renderRoute(mePoint, catPoint, route))
      .catch((err) => {
        console.error('路线获取失败:', err);
        this.setData({ locating: false, errorMsg: '路线获取失败，点「重新定位」重试' });
      });
  },

  renderRoute(mePoint, catPoint, route) {
    const cat = this.data.cat;
    const points = route.polyline && route.polyline.length >= 2 ? route.polyline : [mePoint, catPoint];
    this.setData({
      locating: false,
      routeSource: route.source,
      distanceText: formatDistance(route.distanceMeters || 0),
      durationText: formatDuration(route.durationSeconds || 0),
      markers: [
        {
          id: 1,
          ...mePoint,
          width: 28,
          height: 28,
          callout: {
            content: '我的位置',
            color: '#6D6A60',
            fontSize: 12,
            borderRadius: 4,
            bgColor: '#FFFDF7',
            padding: 6,
            borderWidth: 1,
            borderColor: '#C3A15F',
            display: 'ALWAYS'
          }
        },
        {
          id: 2,
          ...catPoint,
          width: 36,
          height: 36,
          callout: {
            content: cat.name,
            color: '#14493C',
            fontSize: 12,
            borderRadius: 4,
            bgColor: '#FFFDF7',
            padding: 6,
            borderWidth: 1,
            borderColor: '#C3A15F',
            display: 'ALWAYS'
          }
        }
      ],
      polyline: [
        {
          points,
          color: '#14493CCC',
          width: 5,
          arrowLine: true
        }
      ],
      includePoints: [mePoint, catPoint]
    });
  },

  onOpenMap() {
    const cat = this.data.cat;
    if (!cat || !cat.latitude) {
      return;
    }
    wx.openLocation({
      latitude: cat.latitude,
      longitude: cat.longitude,
      name: `${cat.name} · 流浪猫位置`,
      address: cat.locationDesc || '',
      scale: 18,
      fail: () => wx.showToast({ title: '打开地图失败', icon: 'none' })
    });
  }
});
