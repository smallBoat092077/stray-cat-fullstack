package com.straycat.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.straycat.entity.CoinTransaction;

import java.util.Map;

public interface CoinService {

    String BIZ_REGISTER = "REGISTER";
    String BIZ_SIGNIN = "SIGNIN";
    String BIZ_REPORT = "REPORT";
    String BIZ_CHECKIN = "CHECKIN";
    String BIZ_VOLUNTEER_LEVEL = "VOLUNTEER_LEVEL";
    String BIZ_FOLLOWUP = "FOLLOWUP";
    String BIZ_TIP = "TIP";
    String BIZ_RECHARGE = "RECHARGE";
    String BIZ_RECHARGE_REFUND = "RECHARGE_REFUND";

    int SIGNIN_REWARD = 5;
    int REPORT_REWARD = 10;
    int CHECKIN_REWARD = 50;
    int FOLLOWUP_REWARD = 20;
    int REGISTER_GRANT = 100;

    /**
     * 按行为发放猫粮，同一 (userId, bizType, bizId) 只生效一次
     */
    boolean grant(Long userId, int amount, String bizType, Long bizId, String remark);

    /**
     * 扣减猫粮，余额不足抛业务异常；返回扣减后余额
     */
    int spend(Long userId, int amount, String bizType, Long bizId, String remark);

    boolean signIn(Long userId);

    boolean hasSignedIn(Long userId);

    /**
     * 连续签到统计：current 从今天（今天没签则从昨天）向回数连续天数，
     * max 为历史最长。纯查询推导，不做缓存。
     */
    Map<String, Integer> signInStats(Long userId);

    int getBalance(Long userId);

    int sumTipped(Long userId);

    Page<CoinTransaction> listTransactions(Long userId, Integer pageNum, Integer pageSize);
}
