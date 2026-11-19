// 激励视频广告（微信流量主）
// 开通流量主后，在微信公众平台「流量主 → 广告管理」创建激励视频广告位，
// 把生成的 adunit-xxx 填到下面即可生效。
// 留空时所有广告入口自动隐藏，不影响任何主流程（静默降级）。
const REWARDED_VIDEO_AD_UNIT_ID = '';

let _ad = null;
let _broken = false;

// 广告入口是否应该展示（未配置 ID 或运行期报错则不展示）
function rewardedAvailable() {
  return !!REWARDED_VIDEO_AD_UNIT_ID && !_broken;
}

function getAd() {
  if (!REWARDED_VIDEO_AD_UNIT_ID) return null;
  if (_ad) return _ad;
  _ad = wx.createRewardedVideoAd({ adUnitId: REWARDED_VIDEO_AD_UNIT_ID });
  _ad.onError(() => { _broken = true; });
  return _ad;
}

// 拉起激励视频。resolve(true)=完整看完；resolve(false)=未配置/中途退出/加载失败。
// 加载失败自动重试一次，重试仍失败按未看完处理，绝不阻塞主流程。
function showRewardedVideo() {
  return new Promise((resolve) => {
    const ad = getAd();
    if (!ad) { resolve(false); return; }
    const onClose = (res) => {
      ad.offClose(onClose);
      resolve(!!(res && res.isEnded));
    };
    ad.offClose(onClose);
    ad.onClose(onClose);
    ad.show().catch(() => {
      ad.load()
        .then(() => ad.show())
        .catch(() => { _broken = true; resolve(false); });
    });
  });
}

module.exports = { REWARDED_VIDEO_AD_UNIT_ID, rewardedAvailable, showRewardedVideo };
