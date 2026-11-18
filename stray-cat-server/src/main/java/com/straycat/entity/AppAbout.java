package com.straycat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 「关于我们」页面配置（单行表，id 恒为 1）。
 * 后台可随时更新；客户端 GET /about 读 Redis 缓存（5 分钟），更新后立即失效。
 */
@Data
@TableName("app_about")
public class AppAbout {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 应用名 */
    private String appName;

    /** 当前版本号，如 v1.1.0 */
    private String version;

    /** 品牌 slogan，如「遇一只猫 · 安一个家」 */
    private String slogan;

    /** 理念主句，如「让领养代替购买，爱不流浪」 */
    private String mottoMain;

    /** 理念副句，如「遇一只猫，安一个家」 */
    private String mottoSub;

    /** 管理员电话（点击拨打） */
    private String phone;

    /** 联系邮箱（点击复制） */
    private String email;

    /** 联系时间提示，如「9:00–21:00」 */
    private String contactTip;

    /** 底部版权文案 */
    private String copyright;

    private LocalDateTime updatedAt;
}
