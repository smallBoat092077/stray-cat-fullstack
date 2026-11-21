package com.straycat.request;

import lombok.Data;

/**
 * 志愿者申请提交表单。
 */
@Data
public class VolunteerApplyRequest {

    private String realName;

    private String contact;

    private String region;

    private String serviceTime;

    private String reason;
}
