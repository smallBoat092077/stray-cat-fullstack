package com.straycat.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 行政区域（国标，只读字典）。code 为国标行政区码（420100），主键。
 * 数据由管理端 /admin/region/sync 从腾讯 district API 拉取，迁移只建结构（区域划分需求 §1.1）。
 * 直辖市特例：市级行与省级行 code 相同（如北京 110000），同步时 INSERT IGNORE 去重，
 * 树构建时省级下无市级子节点则直接挂区级（见 RegionServiceImpl.childrenOf）。
 */
@Data
@TableName("region")
public class Region {

    @TableId
    private String code;

    private String name;

    /** 上级行政区码，省级为 null */
    private String parentCode;

    /** 1省 2市 3区 */
    private Integer level;

    private Integer sort;
}
