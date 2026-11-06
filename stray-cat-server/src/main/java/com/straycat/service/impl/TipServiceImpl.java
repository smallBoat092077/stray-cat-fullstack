package com.straycat.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.straycat.common.ResultCode;
import com.straycat.entity.CatProfile;
import com.straycat.entity.CatTip;
import com.straycat.entity.SysUser;
import com.straycat.exception.BusinessException;
import com.straycat.mapper.CatProfileMapper;
import com.straycat.mapper.CatTipMapper;
import com.straycat.mapper.SysUserMapper;
import com.straycat.service.CoinService;
import com.straycat.service.ContentSecurityService;
import com.straycat.service.FeedingTaskService;
import com.straycat.service.TipService;
import com.straycat.util.PrivacyMask;
import com.straycat.vo.TipVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
public class TipServiceImpl implements TipService {

    private static final int MAX_MESSAGE_LENGTH = 50;

    @Resource
    private CatTipMapper catTipMapper;

    @Resource
    private CatProfileMapper catProfileMapper;

    @Resource
    private SysUserMapper sysUserMapper;

    @Resource
    private CoinService coinService;

    @Resource
    private ContentSecurityService contentSecurityService;

    @Resource
    private FeedingTaskService feedingTaskService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TipVO tip(Long fromUserId, Long catId, Integer amount, String message) {
        if (amount == null || amount < MIN_AMOUNT || amount > MAX_AMOUNT) {
            throw new BusinessException(ResultCode.COIN_AMOUNT_INVALID.getCode(),
                    "单次猫粮需在 " + MIN_AMOUNT + "~" + MAX_AMOUNT + " 之间");
        }
        CatProfile cat = catProfileMapper.selectById(catId);
        if (cat == null) {
            throw new BusinessException(ResultCode.CAT_NOT_FOUND);
        }

        String messageText = cleanMessage(message);
        contentSecurityService.check(fromUserId, ContentSecurityService.SCENE_COMMENT, messageText);

        CatTip tip = new CatTip();
        tip.setCatId(catId);
        tip.setFromUserId(fromUserId);
        tip.setCoinAmount(amount);
        tip.setMessage(messageText);
        tip.setCreatedAt(LocalDateTime.now());
        catTipMapper.insert(tip);

        // 扣款失败会抛异常，上面的打赏记录随事务一起回滚
        coinService.spend(fromUserId, amount, CoinService.BIZ_TIP, tip.getId(), "给" + cat.getName() + "送猫粮");
        catProfileMapper.addCoinTotal(catId, amount);

        SysUser from = sysUserMapper.selectById(fromUserId);
        TipVO vo = toVO(tip);
        vo.setCatName(cat.getName());
        vo.setNickname(PrivacyMask.nickname(from == null ? null : from.getNickname()));
        // 打赏即委托：生成一条待接单任务，用户在投喂栏目里跟进度
        vo.setTaskId(feedingTaskService.createTaskFromTip(cat, tip.getId(), fromUserId, amount));
        return vo;
    }

    @Override
    public Page<TipVO> listByCat(Long catId, Integer pageNum, Integer pageSize) {
        LambdaQueryWrapper<CatTip> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CatTip::getCatId, catId).orderByDesc(CatTip::getCreatedAt);
        return decorate(catTipMapper.selectPage(new Page<>(pageNum, pageSize), wrapper), true);
    }

    @Override
    public Page<TipVO> listMine(Long userId, Integer pageNum, Integer pageSize) {
        LambdaQueryWrapper<CatTip> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CatTip::getFromUserId, userId).orderByDesc(CatTip::getCreatedAt);
        return decorate(catTipMapper.selectPage(new Page<>(pageNum, pageSize), wrapper), false);
    }

    private Page<TipVO> decorate(Page<CatTip> source, boolean withNickname) {
        List<CatTip> records = source.getRecords();
        Map<Long, String> catNames = new HashMap<>();
        Map<Long, String> nicknames = new HashMap<>();

        if (!records.isEmpty()) {
            Set<Long> catIds = records.stream().map(CatTip::getCatId).collect(Collectors.toSet());
            for (CatProfile cat : catProfileMapper.selectBatchIds(catIds)) {
                catNames.put(cat.getId(), cat.getName());
            }
            if (withNickname) {
                Set<Long> userIds = records.stream().map(CatTip::getFromUserId).collect(Collectors.toSet());
                for (SysUser user : sysUserMapper.selectBatchIds(userIds)) {
                    nicknames.put(user.getId(), user.getNickname());
                }
            }
        }

        Page<TipVO> target = new Page<>(source.getCurrent(), source.getSize(), source.getTotal());
        target.setRecords(records.stream().map(tip -> {
            TipVO vo = toVO(tip);
            vo.setCatName(catNames.get(tip.getCatId()));
            if (withNickname) {
                vo.setNickname(PrivacyMask.nickname(nicknames.get(tip.getFromUserId())));
            }
            return vo;
        }).collect(Collectors.toList()));
        return target;
    }

    private TipVO toVO(CatTip tip) {
        TipVO vo = new TipVO();
        vo.setId(tip.getId());
        vo.setCatId(tip.getCatId());
        vo.setCoinAmount(tip.getCoinAmount());
        vo.setMessage(tip.getMessage());
        vo.setCreatedAt(tip.getCreatedAt());
        return vo;
    }

    private String cleanMessage(String message) {
        if (message == null) {
            return null;
        }
        String cleaned = message.replaceAll("[\\r\\n\\t]", " ").trim();
        if (cleaned.isEmpty()) {
            return null;
        }
        return cleaned.length() > MAX_MESSAGE_LENGTH ? cleaned.substring(0, MAX_MESSAGE_LENGTH) : cleaned;
    }
}
