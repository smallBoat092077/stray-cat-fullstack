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
