package com.straycat.dto;

import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

/**
 * 打卡识别请求
 */
@Data
public class CheckinRecognizeForm {

    /**
     * 任务 ID
     */
    private Long taskId;

    /**
     * 打卡照片
     */
    private MultipartFile photo;

    /**
     * 猫咪 ID（可选，用于手动选择）
     */
    private Long catId;

    /**
     * 经度
     */
    private Double longitude;

    /**
     * 纬度
     */
    private Double latitude;

    /**
     * 地址
     */
    private String address;

    /**
     * 投喂份数
     */
    private Integer portions;

    /**
     * 备注
     */
    private String remark;
}
