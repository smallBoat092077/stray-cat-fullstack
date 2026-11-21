package com.straycat.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.straycat.entity.LostCatPost;
import com.straycat.exception.BusinessException;
import com.straycat.mapper.LostCatPostMapper;
import com.straycat.request.LostCatPostRequest;
import com.straycat.service.ContentSecurityService;
import com.straycat.service.GeoService;
import com.straycat.service.LostCatPostService;
import com.straycat.service.RegionService;
import com.straycat.vo.GeoResultVO;
import com.straycat.vo.LostCatPostVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class LostCatPostServiceImpl implements LostCatPostService {

    private static final int AUDIT_PASSED = 1;
    private static final int STATUS_FOUND = 1;
    private static final int MAX_IMAGES = 6;

    private final LostCatPostMapper lostCatPostMapper;
    private final ContentSecurityService contentSecurityService;
    private final RegionService regionService;
    private final GeoService geoService;

    @Override
    public Page<LostCatPostVO> list(Integer status, Integer pageNum, Integer pageSize,
                                    String provinceCode, String cityCode, String districtCode) {
        LambdaQueryWrapper<LostCatPost> wrapper = new LambdaQueryWrapper<>();
        // 广场只出审核通过的启事
        wrapper.eq(LostCatPost::getAuditStatus, AUDIT_PASSED);
        if (status != null) {
            wrapper.eq(LostCatPost::getStatus, status);
        }
        // 区域过滤（区域划分需求 R4）：不带参数 = 全部口径（存量未归属数据可见）
        if (districtCode != null && !districtCode.isBlank()) {
            wrapper.eq(LostCatPost::getDistrictCode, districtCode);
        } else if (cityCode != null && !cityCode.isBlank()) {
            wrapper.eq(LostCatPost::getCityCode, cityCode);
        } else if (provinceCode != null && !provinceCode.isBlank()) {
            wrapper.eq(LostCatPost::getProvinceCode, provinceCode);
        }
        wrapper.orderByDesc(LostCatPost::getCreatedAt);
        Page<LostCatPost> entityPage = lostCatPostMapper.selectPage(
                new Page<>(pageNum == null ? 1 : pageNum, pageSize == null ? 20 : pageSize), wrapper);
        return toVOPage(entityPage);
    }

    @Override
    public LostCatPostVO detail(Long id) {
        LostCatPost post = lostCatPostMapper.selectById(id);
        if (post == null || post.getAuditStatus() == null || post.getAuditStatus() != AUDIT_PASSED) {
            return null;
        }
        try {
            lostCatPostMapper.incrementViewCount(id);
            post.setViewCount((post.getViewCount() == null ? 0 : post.getViewCount()) + 1);
        } catch (Exception e) {
            log.warn("寻猫详情浏览数自增失败: id={}, err={}", id, e.getMessage());
        }
        LostCatPostVO vo = toVO(post);
        // 老数据未归属（码为空）但有坐标：详情页要显示省市区，用逆地理编码兜底解析
        //（结果在 GeoService 内缓存 7 天，不重复耗配额）；失败静默，详情照常下发
        if (isBlank(post.getProvinceCode()) && isBlank(post.getCityCode()) && isBlank(post.getDistrictCode())
                && post.getLatitude() != null && post.getLongitude() != null) {
            try {
                GeoResultVO geo = geoService.reverse(
                        post.getLatitude().doubleValue(), post.getLongitude().doubleValue());
                if (geo.isSuccess()) {
                    vo.setProvinceName(geo.getProvinceName());
                    vo.setCityName(geo.getCityName());
                    vo.setDistrictName(geo.getDistrictName());
                }
            } catch (Exception e) {
                log.warn("寻猫详情逆地理兜底失败: id={}, err={}", id, e.getMessage());
            }
        }
        return vo;
    }

    @Override
    public Long create(Long userId, LostCatPostRequest request) {
        if (request == null || request.getCatName() == null || request.getCatName().isBlank()) {
            throw new BusinessException("猫咪名字不能为空");
        }
        List<String> images = splitImages(request.getImages());
        if (images.size() > MAX_IMAGES) {
            throw new BusinessException("照片最多 " + MAX_IMAGES + " 张");
        }

        // 文本字段统一过一次内容安全检测（论坛/社交场景）
        contentSecurityService.check(userId, ContentSecurityService.SCENE_SOCIAL,
                request.getCatName(), request.getCoatDesc(),
                request.getAddressDesc(), request.getContactValue());

        String contactWay = "phone".equalsIgnoreCase(request.getContactWay()) ? "phone" : "inapp";
        LocalDateTime now = LocalDateTime.now();

        LostCatPost post = new LostCatPost();
        post.setUserId(userId);
        post.setCatName(request.getCatName().trim());
        post.setCoatDesc(request.getCoatDesc());
        post.setImages(images.isEmpty() ? null : String.join(",", images));
        post.setLostAt(request.getLostAt());
        post.setLatitude(request.getLatitude());
        post.setLongitude(request.getLongitude());
        post.setAddressDesc(request.getAddressDesc());
        post.setProvinceCode(request.getProvinceCode());
        post.setCityCode(request.getCityCode());
        post.setDistrictCode(request.getDistrictCode());
        post.setContactWay(contactWay);
        post.setContactValue(request.getContactValue());
        post.setStatus(0);
        post.setAuditStatus(0);
        post.setViewCount(0);
        post.setCreatedAt(now);
        post.setUpdatedAt(now);
        lostCatPostMapper.insert(post);
        return post.getId();
    }

    @Override
    public List<LostCatPostVO> mine(Long userId) {
        LambdaQueryWrapper<LostCatPost> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(LostCatPost::getUserId, userId).orderByDesc(LostCatPost::getCreatedAt);
        return lostCatPostMapper.selectList(wrapper).stream().map(this::toVO).collect(Collectors.toList());
    }

    @Override
    public void markFound(Long userId, Long id) {
        LostCatPost post = lostCatPostMapper.selectById(id);
        if (post == null) {
            throw new BusinessException("启事不存在");
        }
        if (!post.getUserId().equals(userId)) {
            throw new BusinessException("只能修改自己的启事");
        }
        LostCatPost update = new LostCatPost();
        update.setId(id);
        update.setStatus(STATUS_FOUND);
        update.setUpdatedAt(LocalDateTime.now());
        lostCatPostMapper.updateById(update);
    }

    private Page<LostCatPostVO> toVOPage(Page<LostCatPost> entityPage) {
        Page<LostCatPostVO> voPage = new Page<>(entityPage.getCurrent(), entityPage.getSize(), entityPage.getTotal());
        voPage.setRecords(entityPage.getRecords().stream().map(this::toVO).collect(Collectors.toList()));
        return voPage;
    }

    private LostCatPostVO toVO(LostCatPost p) {
        LostCatPostVO vo = new LostCatPostVO();
        vo.setId(p.getId());
        vo.setUserId(p.getUserId());
        vo.setCatName(p.getCatName());
        vo.setCoatDesc(p.getCoatDesc());
        vo.setImages(splitImages(p.getImages()));
        vo.setLostAt(p.getLostAt());
        vo.setLatitude(p.getLatitude());
        vo.setLongitude(p.getLongitude());
        vo.setAddressDesc(p.getAddressDesc());
        vo.setProvinceCode(p.getProvinceCode());
        vo.setCityCode(p.getCityCode());
        vo.setDistrictCode(p.getDistrictCode());
        // 码 -> 名称（region 字典内存翻译，列表/详情通用）
        Set<String> codes = new HashSet<>();
        if (p.getProvinceCode() != null && !p.getProvinceCode().isBlank()) {
            codes.add(p.getProvinceCode());
        }
        if (p.getCityCode() != null && !p.getCityCode().isBlank()) {
            codes.add(p.getCityCode());
        }
        if (p.getDistrictCode() != null && !p.getDistrictCode().isBlank()) {
            codes.add(p.getDistrictCode());
        }
        Map<String, String> names = regionService.namesOf(codes);
        // 老数据码可能为 null，不可变 Map 对 null key 直接抛 NPE，必须先判空
        vo.setProvinceName(p.getProvinceCode() == null ? null : names.get(p.getProvinceCode()));
        vo.setCityName(p.getCityCode() == null ? null : names.get(p.getCityCode()));
        vo.setDistrictName(p.getDistrictCode() == null ? null : names.get(p.getDistrictCode()));
        vo.setContactWay(p.getContactWay());
        vo.setContactValue(maskContact(p));
        vo.setStatus(p.getStatus());
        vo.setAuditStatus(p.getAuditStatus());
        vo.setAuditRemark(p.getAuditRemark());
        vo.setViewCount(p.getViewCount());
        vo.setCreatedAt(p.getCreatedAt());
        return vo;
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    /**
     * 联系方式公开展示前脱敏：未经审核不下发、站内消息方式不下发，
     * 审核通过的手机号只保留前 3 后 4。
     */
    private String maskContact(LostCatPost p) {
        String value = p.getContactValue();
        if (value == null || value.isBlank()) {
            return null;
        }
        if (p.getAuditStatus() == null || p.getAuditStatus() != AUDIT_PASSED) {
            return null;
        }
        if (!"phone".equalsIgnoreCase(p.getContactWay())) {
            return null;
        }
        String phone = value.trim();
        if (phone.length() >= 7) {
            return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
        }
        return "****";
    }

    private List<String> splitImages(String images) {
        if (images == null || images.isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.stream(images.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }
}
