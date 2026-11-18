package com.straycat.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.straycat.common.ResultCode;
import com.straycat.entity.CoinTransaction;
import com.straycat.entity.SysUser;
import com.straycat.exception.BusinessException;
import com.straycat.mapper.CoinTransactionMapper;
import com.straycat.mapper.SysUserMapper;
import com.straycat.service.CoinService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class CoinServiceImpl implements CoinService {

    @Resource
    private SysUserMapper sysUserMapper;

    @Resource
    private CoinTransactionMapper coinTransactionMapper;

    @Override
    public boolean grant(Long userId, int amount, String bizType, Long bizId, String remark) {
        if (userId == null || amount <= 0) {
            return false;
        }
        CoinTransaction tx = new CoinTransaction();
        tx.setUserId(userId);
        tx.setAmount(amount);
        tx.setBizType(bizType);
        tx.setBizId(bizId == null ? 0L : bizId);
        tx.setRemark(remark);
        tx.setBalanceAfter(0);
        tx.setCreatedAt(LocalDateTime.now());
        try {
            // 先占唯一键再加钱：并发重复请求只有一条能插入成功，失败方不会动余额
            coinTransactionMapper.insert(tx);
        } catch (DuplicateKeyException e) {
            log.info("奖励已发放，跳过 userId={} bizType={} bizId={}", userId, bizType, bizId);
            return false;
        }
        sysUserMapper.addCoin(userId, amount);
        refreshBalance(tx.getId(), userId);
        return true;
    }

    @Override
    public int spend(Long userId, int amount, String bizType, Long bizId, String remark) {
        if (userId == null || amount <= 0) {
            throw new BusinessException(ResultCode.COIN_AMOUNT_INVALID.getCode(), "猫粮数量不合法");
        }
        if (sysUserMapper.trySpendCoin(userId, amount) == 0) {
            throw new BusinessException(ResultCode.COIN_NOT_ENOUGH);
        }
        int balance = readBalance(userId);

        CoinTransaction tx = new CoinTransaction();
        tx.setUserId(userId);
        tx.setAmount(-amount);
        tx.setBizType(bizType);
        tx.setBizId(bizId == null ? 0L : bizId);
        tx.setRemark(remark);
        tx.setBalanceAfter(balance);
        tx.setCreatedAt(LocalDateTime.now());
        coinTransactionMapper.insert(tx);
        return balance;
    }

    @Override
    public boolean signIn(Long userId) {
        long todayKey = Long.parseLong(LocalDate.now().toString().replace("-", ""));
        return grant(userId, SIGNIN_REWARD, BIZ_SIGNIN, todayKey, "每日签到");
    }

    @Override
    public boolean hasSignedIn(Long userId) {
        long todayKey = Long.parseLong(LocalDate.now().toString().replace("-", ""));
        LambdaQueryWrapper<CoinTransaction> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CoinTransaction::getUserId, userId)
               .eq(CoinTransaction::getBizType, BIZ_SIGNIN)
               .eq(CoinTransaction::getBizId, todayKey);
        return coinTransactionMapper.selectCount(wrapper) > 0;
    }

    /**
     * 连续签到天数。签到记录的 bizId 就是 yyyyMMdd 数字串，
     * 按天倒序遍历：当前连续段从今天（未签则从昨天）起逐日回数；
     * 历史最长在遍历过程中顺手取。单用户签到记录量级极小，全量查即可。
     */
    @Override
    public Map<String, Integer> signInStats(Long userId) {
        LambdaQueryWrapper<CoinTransaction> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CoinTransaction::getUserId, userId)
               .eq(CoinTransaction::getBizType, BIZ_SIGNIN)
               .orderByDesc(CoinTransaction::getBizId);
        List<Long> dayKeys = coinTransactionMapper.selectList(wrapper).stream()
                .map(CoinTransaction::getBizId)
                .filter(java.util.Objects::nonNull)
                .toList();

        long todayKey = Long.parseLong(LocalDate.now().toString().replace("-", ""));
        long yesterdayKey = Long.parseLong(LocalDate.now().minusDays(1).toString().replace("-", ""));
        // 今天没签不打断连续：昨天还在链上就算「连续 N 天，今天待续」
        java.util.Set<Long> signed = new java.util.HashSet<>(dayKeys);
        long cursor = signed.contains(todayKey) ? todayKey : yesterdayKey;
        int current = 0;
        while (signed.contains(cursor)) {
            current++;
            cursor--;
        }
        int max = 0;
        int run = 0;
        long prev = Long.MIN_VALUE;
        for (Long key : dayKeys) {
            run = (prev != Long.MIN_VALUE && key == prev - 1) ? run + 1 : 1;
            max = Math.max(max, run);
            prev = key;
        }
        Map<String, Integer> stats = new java.util.LinkedHashMap<>();
        stats.put("current", current);
        stats.put("max", Math.max(max, current));
        return stats;
    }

    @Override
    public int getBalance(Long userId) {
        return readBalance(userId);
    }

    @Override
    public int sumTipped(Long userId) {
        return coinTransactionMapper.sumTipped(userId);
    }

    @Override
    public Page<CoinTransaction> listTransactions(Long userId, Integer pageNum, Integer pageSize) {
        LambdaQueryWrapper<CoinTransaction> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CoinTransaction::getUserId, userId)
               .orderByDesc(CoinTransaction::getCreatedAt);
        return coinTransactionMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
    }

    private int readBalance(Long userId) {
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null || user.getCoinBalance() == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        return user.getCoinBalance();
    }

    private void refreshBalance(Long txId, Long userId) {
        int balance = readBalance(userId);
        coinTransactionMapper.update(null, new LambdaUpdateWrapper<CoinTransaction>()
                .set(CoinTransaction::getBalanceAfter, balance)
                .eq(CoinTransaction::getId, txId));
    }
}
