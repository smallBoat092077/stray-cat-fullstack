package com.straycat.controller;

import com.straycat.common.Result;
import com.straycat.entity.AppAbout;
import com.straycat.mapper.AppAboutMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 客户端「关于我们」配置：公开接口，直接读单行配置表。
 * 表频次低、查询廉价，不引入缓存层；管理端保存后客户端下次进入页面即拉到新值。
 */
@RestController
@RequiredArgsConstructor
public class AboutController {

    private final AppAboutMapper appAboutMapper;

    @GetMapping("/about")
    public Result<AppAbout> about() {
        return Result.success(ensureRow());
    }

    /** 表空时用默认值初始化，保证客户端永远有内容可显示 */
    private AppAbout ensureRow() {
        AppAbout row = appAboutMapper.selectById(1L);
        if (row != null) {
            return row;
        }
        AppAbout seed = defaultAbout();
        seed.setId(1L);
        appAboutMapper.insert(seed);
        return seed;
    }

    static AppAbout defaultAbout() {
        AppAbout a = new AppAbout();
        a.setAppName("猫遇猫舍");
        a.setVersion("v1.1.0");
        a.setSlogan("遇一只猫 · 安一个家");
        a.setMottoMain("让领养代替购买\n爱不流浪");
        a.setMottoSub("遇一只猫，安一个家");
        a.setPhone("17671710525");
        a.setEmail("1343076057@qq.com");
        a.setContactTip("如有领养咨询、流浪猫求助或合作意向，欢迎在工作时间（9:00–21:00）与我们联系。");
        a.setCopyright("猫遇猫舍 · 用心善待每一只小猫\n© 2026 CatMeet Cattery");
        a.setUpdatedAt(java.time.LocalDateTime.now());
        return a;
    }
}
