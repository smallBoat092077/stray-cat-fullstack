package com.straycat.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.straycat.entity.CoinTransaction;

public interface CoinService {

    String BIZ_REGISTER = "REGISTER";
    String BIZ_SIGNIN = "SIGNIN";
    String BIZ_REPORT = "REPORT";
    String BIZ_CHECKIN = "CHECKIN";
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

    int getBalance(Long userId);

    int sumTipped(Long userId);

    Page<CoinTransaction> listTransactions(Long userId, Integer pageNum, Integer pageSize);
}
