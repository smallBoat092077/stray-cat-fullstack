package com.straycat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("cat_profile")
public class CatProfile {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    /**
     * 性别: 0母 1公 2未知
     */
    private Integer gender;

    private String ageEstimate;

    private String breed;

    private String color;

    private String personality;

    /**
     * 健康状态: 0未检查 1已检查 2已绝育 3已疫苗 4已驱虫
     */
    private Integer healthStatus;

    /**
     * 生活状态: 0流浪中 1投喂中 2待领养 3已领养 4领养回访中
     */
    private Integer lifeStatus;

    private BigDecimal latitude;

    private BigDecimal longitude;

    private String locationDesc;

    /**
     * 上报选点的真实地址（地图 chooseLocation 返回的 POI 名称/地址）。
     * 与 locationDesc（手填描述，如「路上」）分离：详情/导航优先展示本字段，
     * 避免把自由文本当作地图地点名（地点名错位修复）。
     */
    private String locationName;

    /**
     * 上报时归属省/市/区（国标行政区码，region 字典同源；区域划分需求 R2）。
     * 三列全 null = 存量未归属，仅在「全部」口径可见（D4）
     */
    private String provinceCode;

    private String cityCode;

    private String districtCode;

    private Long discovererId;

    private Long caregiverId;

    private Long adopterId;

    private String description;

    private String coverImage;

    /** 封面感知哈希（dhash64），用于上报去重；解析失败为 NULL */
    private Long coverPhash;

    /**
     * 猫咪照片，逗号分隔的 /uploads/ 相对路径，最多 3 张（详情轮播按它渲染）
     */
    private String images;

    /**
     * 周边环境照片，逗号分隔的 /uploads/ 相对路径，最多 6 张
     */
    private String environmentImages;

    /**
     * 周边环境描述：猫咪出没处的环境情况，方便后来的志愿者找到它
     */
    private String environmentDesc;

    private Integer viewCount;

    private Integer likeCount;

    /**
     * 评论数（含回复），由评论接口维护
     */
    private Integer commentCount;

    /**
     * 累计收到的猫粮
     */
    private Integer coinTotal;

    /**
     * 当前用户是否已点赞，仅详情接口回填，不落库
     */
    @TableField(exist = false)
    private Boolean liked;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
