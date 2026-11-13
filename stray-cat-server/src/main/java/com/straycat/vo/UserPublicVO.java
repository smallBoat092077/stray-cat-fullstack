package com.straycat.vo;

import lombok.Data;

/**
 * 用户公开信息：只含展示字段，避免实体全字段序列化把 username/openid 等键名暴露出去
 */
@Data
public class UserPublicVO {

    private Long id;

    private String nickname;

    private String avatarUrl;

    private Integer role;
}
