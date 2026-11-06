package com.straycat.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.straycat.entity.AdoptionApplication;
import com.straycat.entity.CatProfile;
import com.straycat.entity.CatTip;
import com.straycat.entity.CoinTransaction;
import com.straycat.entity.FeedingRecord;
import com.straycat.entity.FeedingTask;
import com.straycat.entity.SysUser;
import com.straycat.mapper.AdoptionApplicationMapper;
import com.straycat.mapper.CatProfileMapper;
import com.straycat.mapper.CatTipMapper;
import com.straycat.mapper.CoinTransactionMapper;
import com.straycat.mapper.FeedingRecordMapper;
import com.straycat.mapper.FeedingTaskMapper;
import com.straycat.mapper.SysUserMapper;
import com.straycat.service.AdminService;
import com.straycat.vo.AdminTipVO;
import com.straycat.vo.CoinLedgerVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminServiceImpl implements AdminService {

    private static final int MAX_PAGE_SIZE = 100;

    private final SysUserMapper sysUserMapper;
    private final CatProfileMapper catProfileMapper;
    private final AdoptionApplicationMapper adoptionApplicationMapper;
    private final FeedingTaskMapper feedingTaskMapper;
    private final FeedingRecordMapper feedingRecordMapper;
    private final CoinTransactionMapper coinTransactionMapper;
    private final CatTipMapper catTipMapper;

    @Override
    public Map<String, Object> stats() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("userTotal", sysUserMapper.selectCount(null));
        data.put("volunteerTotal", sysUserMapper.selectCount(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getRole, 1)));
        data.put("adminTotal", sysUserMapper.selectCount(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getRole, 3)));

        data.put("catTotal", catProfileMapper.selectCount(null));
        data.put("catFeeding", catProfileMapper.selectCount(
                new LambdaQueryWrapper<CatProfile>().eq(CatProfile::getLifeStatus, 1)));
        data.put("catWaitingAdoption", catProfileMapper.selectCount(
                new LambdaQueryWrapper<CatProfile>().eq(CatProfile::getLifeStatus, 2)));
        data.put("catAdopted", catProfileMapper.selectCount(
                new LambdaQueryWrapper<CatProfile>().eq(CatProfile::getLifeStatus, 3)));

        data.put("adoptionPending", adoptionApplicationMapper.selectCount(
                new LambdaQueryWrapper<AdoptionApplication>().in(AdoptionApplication::getStatus, List.of(0, 1))));
        data.put("adoptionApproved", adoptionApplicationMapper.selectCount(
                new LambdaQueryWrapper<AdoptionApplication>().eq(AdoptionApplication::getStatus, 2)));
        data.put("adoptionRejected", adoptionApplicationMapper.selectCount(
                new LambdaQueryWrapper<AdoptionApplication>().eq(AdoptionApplication::getStatus, 3)));

        data.put("taskPending", feedingTaskMapper.selectCount(
                new LambdaQueryWrapper<FeedingTask>().eq(FeedingTask::getStatus, 0)));
        data.put("taskAccepted", feedingTaskMapper.selectCount(
                new LambdaQueryWrapper<FeedingTask>().eq(FeedingTask::getStatus, 1)));
        data.put("taskCheckedIn", feedingTaskMapper.selectCount(
                new LambdaQueryWrapper<FeedingTask>().eq(FeedingTask::getStatus, 2)));
        data.put("taskDone", feedingTaskMapper.selectCount(
                new LambdaQueryWrapper<FeedingTask>().eq(FeedingTask::getStatus, 3)));

        data.put("recordPending", feedingRecordMapper.selectCount(
                new LambdaQueryWrapper<FeedingRecord>().eq(FeedingRecord::getStatus, 0)));

        data.put("tipTotal", catTipMapper.selectCount(null));
        data.put("tipCoinTotal", firstNumber(catTipMapper.selectObjs(
                new QueryWrapper<CatTip>().select("COALESCE(SUM(coin_amount), 0)"))));
        data.put("coinGrantedToday", firstNumber(coinTransactionMapper.selectObjs(
                new QueryWrapper<CoinTransaction>()
                        .select("COALESCE(SUM(amount), 0)")
                        .gt("amount", 0)
                        .apply("DATE(created_at) = CURDATE()"))));
        return data;
    }

    @Override
    public Page<SysUser> pageUsers(String keyword, Integer role, Integer pageNum, Integer pageSize) {
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            String trimmed = keyword.trim();
            wrapper.and(w -> w.like(SysUser::getNickname, trimmed).or().like(SysUser::getUsername, trimmed));
        }
        if (role != null) {
            wrapper.eq(SysUser::getRole, role);
        }
        wrapper.orderByDesc(SysUser::getId);
        return sysUserMapper.selectPage(page(pageNum, pageSize), wrapper);
    }

    @Override
    public Page<CoinLedgerVO> pageLedger(Long userId, String bizType, Integer pageNum, Integer pageSize) {
        LambdaQueryWrapper<CoinTransaction> wrapper = new LambdaQueryWrapper<>();
        if (userId != null) {
            wrapper.eq(CoinTransaction::getUserId, userId);
        }
        if (bizType != null && !bizType.isBlank()) {
            wrapper.eq(CoinTransaction::getBizType, bizType);
        }
        wrapper.orderByDesc(CoinTransaction::getId);
        Page<CoinTransaction> source = coinTransactionMapper.selectPage(page(pageNum, pageSize), wrapper);

        Map<Long, String> nicknames = new HashMap<>();
        List<Long> userIds = source.getRecords().stream()
                .map(CoinTransaction::getUserId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (!userIds.isEmpty()) {
            for (SysUser user : sysUserMapper.selectBatchIds(userIds)) {
                nicknames.put(user.getId(), user.getNickname());
            }
        }

        Page<CoinLedgerVO> target = new Page<>(source.getCurrent(), source.getSize(), source.getTotal());
        target.setRecords(source.getRecords().stream().map(tx -> {
            CoinLedgerVO vo = new CoinLedgerVO();
            vo.setId(tx.getId());
            vo.setUserId(tx.getUserId());
            vo.setNickname(nicknames.get(tx.getUserId()));
            vo.setAmount(tx.getAmount());
            vo.setBalanceAfter(tx.getBalanceAfter());
            vo.setBizType(tx.getBizType());
            vo.setBizId(tx.getBizId());
            vo.setRemark(tx.getRemark());
            vo.setCreatedAt(tx.getCreatedAt());
            return vo;
        }).collect(Collectors.toList()));
        return target;
    }

    @Override
    public Page<AdminTipVO> pageTips(Long catId, Integer pageNum, Integer pageSize) {
        LambdaQueryWrapper<CatTip> wrapper = new LambdaQueryWrapper<>();
        if (catId != null) {
            wrapper.eq(CatTip::getCatId, catId);
        }
        wrapper.orderByDesc(CatTip::getId);
        Page<CatTip> source = catTipMapper.selectPage(page(pageNum, pageSize), wrapper);

        Map<Long, String> catNames = new HashMap<>();
        Map<Long, String> nicknames = new HashMap<>();
        List<CatTip> records = source.getRecords();
        if (!records.isEmpty()) {
            List<Long> catIds = records.stream().map(CatTip::getCatId).filter(Objects::nonNull).distinct().toList();
            if (!catIds.isEmpty()) {
                catProfileMapper.selectBatchIds(catIds).forEach(cat -> catNames.put(cat.getId(), cat.getName()));
            }
            List<Long> userIds = records.stream().map(CatTip::getFromUserId).filter(Objects::nonNull).distinct().toList();
            if (!userIds.isEmpty()) {
                for (SysUser user : sysUserMapper.selectBatchIds(userIds)) {
                    nicknames.put(user.getId(), user.getNickname());
                }
            }
        }

        Page<AdminTipVO> target = new Page<>(source.getCurrent(), source.getSize(), source.getTotal());
        target.setRecords(records.stream().map(tip -> {
            AdminTipVO vo = new AdminTipVO();
            vo.setId(tip.getId());
            vo.setCatId(tip.getCatId());
            vo.setCatName(catNames.get(tip.getCatId()));
            vo.setFromUserId(tip.getFromUserId());
            vo.setNickname(nicknames.get(tip.getFromUserId()));
            vo.setCoinAmount(tip.getCoinAmount());
            vo.setMessage(tip.getMessage());
            vo.setCreatedAt(tip.getCreatedAt());
            return vo;
        }).collect(Collectors.toList()));
        return target;
    }

    private <T> Page<T> page(Integer pageNum, Integer pageSize) {
        int size = (pageSize == null || pageSize <= 0) ? 20 : Math.min(pageSize, MAX_PAGE_SIZE);
        return new Page<>(pageNum == null || pageNum < 1 ? 1 : pageNum, size);
    }

    /**
     * selectObjs 只回一列一行，取不到就当 0，别让整个首页报错
     */
    private long firstNumber(List<Object> rows) {
        if (rows == null || rows.isEmpty() || rows.get(0) == null) {
            return 0L;
        }
        return Long.parseLong(rows.get(0).toString());
    }
}
