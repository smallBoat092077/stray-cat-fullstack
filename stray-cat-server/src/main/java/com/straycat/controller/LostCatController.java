package com.straycat.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.straycat.common.Result;
import com.straycat.common.ResultCode;
import com.straycat.exception.BusinessException;
import com.straycat.interceptor.AuthInterceptor;
import com.straycat.request.LostCatPostRequest;
import com.straycat.service.GeoService;
import com.straycat.service.LostCatPostService;
import com.straycat.vo.GeoResultVO;
import com.straycat.vo.LostCatPostVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 寻猫启事（方案 5.1）。广场免登，发布/我的/标记已找到需登录。
 */
@Tag(name = "寻猫启事")
@RestController
@RequestMapping("/lost-cat")
@RequiredArgsConstructor
public class LostCatController {

    private final LostCatPostService lostCatPostService;

    private final GeoService geoService;

    @Operation(summary = "广场列表（只出审核通过）")
    @GetMapping("/list")
    public Result<Page<LostCatPostVO>> list(@RequestParam(required = false) Integer status,
                                            @RequestParam(defaultValue = "1") Integer pageNum,
                                            @RequestParam(defaultValue = "20") Integer pageSize,
                                            @RequestParam(required = false) String provinceCode,
                                            @RequestParam(required = false) String cityCode,
                                            @RequestParam(required = false) String districtCode) {
        // 区域过滤（区域划分需求 R4）：不带参数 = 全部口径（存量未归属数据可见）
        return Result.success(lostCatPostService.list(status, pageNum, pageSize, provinceCode, cityCode, districtCode));
    }

    @Operation(summary = "详情（view_count 自增）")
    @GetMapping("/detail/{id}")
    public Result<LostCatPostVO> detail(@PathVariable Long id) {
        return Result.success(lostCatPostService.detail(id));
    }

    @Operation(summary = "发布（需登录，先审后发）")
    @PostMapping("/create")
    public Result<Long> create(@RequestBody LostCatPostRequest request, HttpServletRequest httpRequest) {
        // 区域归属（区域划分需求 R2）：端侧已传码优先；缺失且有坐标时后端兜底补码，失败不阻断发布
        if (isBlank(request.getProvinceCode()) && isBlank(request.getCityCode()) && isBlank(request.getDistrictCode())
                && request.getLatitude() != null && request.getLongitude() != null) {
            try {
                GeoResultVO geo = geoService.reverse(request.getLatitude().doubleValue(), request.getLongitude().doubleValue());
                if (geo.isSuccess()) {
                    request.setProvinceCode(geo.getProvinceCode());
                    request.setCityCode(geo.getCityCode());
                    request.setDistrictCode(geo.getDistrictCode());
                }
            } catch (Exception e) {
                // 兜底补码失败不阻断发布，落库为未归属
            }
        }
        return Result.success(lostCatPostService.create(requireUserId(httpRequest), request));
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    @Operation(summary = "我的发布（需登录，含待审/驳回）")
    @GetMapping("/mine")
    public Result<List<LostCatPostVO>> mine(HttpServletRequest httpRequest) {
        return Result.success(lostCatPostService.mine(requireUserId(httpRequest)));
    }

    @Operation(summary = "标记已找到（需登录，仅本人）")
    @PostMapping("/{id}/found")
    public Result<Void> found(@PathVariable Long id, HttpServletRequest httpRequest) {
        lostCatPostService.markFound(requireUserId(httpRequest), id);
        return Result.success(null);
    }

    private Long requireUserId(HttpServletRequest request) {
        Object uid = request.getAttribute(AuthInterceptor.USER_ID_ATTR);
        if (uid == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        return Long.valueOf(uid.toString());
    }
}
