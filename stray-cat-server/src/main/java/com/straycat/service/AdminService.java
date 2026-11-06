package com.straycat.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.straycat.entity.SysUser;
import com.straycat.vo.AdminTipVO;
import com.straycat.vo.CoinLedgerVO;

import java.util.Map;

public interface AdminService {

    /**
     * 后台首页的汇总计数，全部是单表 count/sum，不JOIN
     */
    Map<String, Object> stats();

    /**
     * 用户分页，keyword 匹配昵称或账号，role 为空取全部
     */
    Page<SysUser> pageUsers(String keyword, Integer role, Integer pageNum, Integer pageSize);

    /**
     * 全局猫粮流水，带上昵称，便于核对发奖与扣减是否对得上
     */
    Page<CoinLedgerVO> pageLedger(Long userId, String bizType, Integer pageNum, Integer pageSize);

    /**
     * 全局打赏记录，后台看全昵称和打赏人 id，公开列表仍是脱敏昵称
     */
    Page<AdminTipVO> pageTips(Long catId, Integer pageNum, Integer pageSize);
}
