package com.straycat.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class FeedingRecordVO {
    private Long id;
    private Long taskId;
    private Long catId;
    private String catName;
    /**
     * 投喂地点，取自关联猫咪的 cat_profile.location_desc
     */
    private String locationDesc;
    private String volunteerName;
    private LocalDateTime checkInTime;
    /**
     * 打卡点与投喂点的距离（米）
     */
    private Integer distance;
    private String foodUsed;
    /**
     * 这一单投喂的猫粮份数：优先任务的认捐数量，其次自备猫粮数量
     */
    private Integer foodAmount;
    /**
     * /uploads/ 相对路径，由前端拼上 baseUrl 再渲染
     */
    private List<String> images;
    /**
     * 审核状态: 0待审核 1已通过 2已拒绝，审核工作台要按它分组
     */
    private Integer status;
    private String reviewComment;
}
