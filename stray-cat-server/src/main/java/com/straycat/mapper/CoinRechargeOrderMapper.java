package com.straycat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.straycat.entity.CoinRechargeOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface CoinRechargeOrderMapper extends BaseMapper<CoinRechargeOrder> {

    @Update("UPDATE coin_recharge_order SET status = 1, paid_at = NOW(), updated_at = NOW(), " +
            "wx_order_id = COALESCE(#{wxOrderId}, wx_order_id) " +
            "WHERE out_trade_no = #{outTradeNo} AND status = 0")
    int markPaid(@Param("outTradeNo") String outTradeNo, @Param("wxOrderId") String wxOrderId);

    @Update("UPDATE coin_recharge_order SET status = 2, delivered_at = NOW(), updated_at = NOW(), " +
            "paid_at = COALESCE(paid_at, NOW()), wx_order_id = COALESCE(#{wxOrderId}, wx_order_id) " +
            "WHERE out_trade_no = #{outTradeNo} AND status IN (0, 1)")
    int markDelivered(@Param("outTradeNo") String outTradeNo, @Param("wxOrderId") String wxOrderId);

    @Update("UPDATE coin_recharge_order SET status = 3, updated_at = NOW(), " +
            "wx_order_id = COALESCE(#{wxOrderId}, wx_order_id) " +
            "WHERE out_trade_no = #{outTradeNo} AND status IN (1, 2)")
    int markRefunded(@Param("outTradeNo") String outTradeNo, @Param("wxOrderId") String wxOrderId);

    @Update("UPDATE coin_recharge_order SET status = 4, updated_at = NOW() " +
            "WHERE out_trade_no = #{outTradeNo} AND status = 0")
    int markClosed(@Param("outTradeNo") String outTradeNo);
}
