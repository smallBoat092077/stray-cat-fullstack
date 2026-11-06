package com.straycat.service;

import java.util.List;
import java.util.Map;

/**
 * 猫粮礼包充值（微信虚拟支付 - 道具直购）
 */
public interface RechargeService {

    String PAY_MODE = "short_series_goods";

    Map<String, Object> capability();

    List<Map<String, Object>> listPacks();

    /**
     * 下单并返回前端调起 wx.requestVirtualPayment 所需的签名参数
     */
    Map<String, Object> createOrder(Long userId, String packCode);

    /**
     * 前端支付成功后主动查单确认真的付了，再落账
     */
    Map<String, Object> confirmPaid(Long userId, String outTradeNo);

    Map<String, Object> queryStatus(Long userId, String outTradeNo);

    /**
     * 我的充值记录，倒序分页
     */
    List<Map<String, Object>> listOrders(Long userId, int pageNum, int pageSize);

    /**
     * 微信发货通知：按订单号幂等落账
     */
    boolean deliverByOutTradeNo(String outTradeNo, String wxOrderId);

    boolean refundByOutTradeNo(String outTradeNo, String wxOrderId);

    /**
     * 定时对账，兜住回调丢失的情况
     */
    void reconcile();
}
