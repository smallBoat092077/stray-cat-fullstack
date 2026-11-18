package com.straycat.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 识别结果。confidence: high(>=matchThreshold 且与第二名差距达标) / medium / low。
 * degraded=true 表示识别服务不可用，前端走「手动选择猫咪」兜底，不阻断打卡。
 * rejected=true 表示二期拒识闸门判定「照片中未检测到猫咪」，服务健康但不产生任何候选。
 */
@Data
public class RecognizeResultVO {

    private boolean success;

    private boolean degraded;

    /** 二期：拒识闸门命中（非猫图 / 截图 / 噪声 / 纯色 / 过度模糊） */
    private boolean rejected;

    /** 二期：拒识原因编码，如 noise / ui_screenshot / low_information */
    private String rejectReason;

    private String confidence;

    private List<RecognizeMatchVO> matches;

    private String message;

    /** 统一降级结果：任何异常 / 熔断 / 超时 / 灰度关闭都返回它，HTTP 200，绝不抛 500 */
    public static RecognizeResultVO degraded() {
        RecognizeResultVO vo = new RecognizeResultVO();
        vo.setSuccess(false);
        vo.setDegraded(true);
        vo.setRejected(false);
        vo.setConfidence(null);
        vo.setMatches(new ArrayList<>());
        vo.setMessage("识别服务暂不可用，请手动选择猫咪");
        return vo;
    }

    /** 二期拒识：服务是健康的，只是这张照片里没有可识别的猫咪 */
    public static RecognizeResultVO rejected(String reason, String message) {
        RecognizeResultVO vo = new RecognizeResultVO();
        vo.setSuccess(false);
        vo.setDegraded(false);
        vo.setRejected(true);
        vo.setRejectReason(reason);
        vo.setConfidence("low");
        vo.setMatches(new ArrayList<>());
        vo.setMessage(message == null || message.isBlank()
                ? "照片中未检测到猫咪，请重新拍摄或手动选择猫咪" : message);
        return vo;
    }
}
