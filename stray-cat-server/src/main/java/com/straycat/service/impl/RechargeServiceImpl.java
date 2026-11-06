package com.straycat.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.straycat.common.ResultCode;
import com.straycat.config.XpayProperties;
import com.straycat.entity.CoinRechargeOrder;
import com.straycat.entity.SysUser;
import com.straycat.exception.BusinessException;
import com.straycat.mapper.CoinRechargeOrderMapper;
import com.straycat.mapper.SysUserMapper;
import com.straycat.service.CoinService;
import com.straycat.service.RechargeService;
import com.straycat.util.XpaySigner;
import com.straycat.weixin.XpayClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Service
public class RechargeServiceImpl implements RechargeService {

    private static final String PAY_URI = "requestVirtualPayment";
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    /** 微信 order.status：已支付待发货 */
    private static final int WX_STATUS_PAID = 2;
    /** 微信 order.status：发货中 */
    private static final int WX_STATUS_DELIVERING = 3;
    /** 微信 order.status：已发货 */
    private static final int WX_STATUS_DELIVERED = 4;
    /** 微信 order.status：退款，商户侧扣款完成 */
    private static final int WX_STATUS_REFUND_MERCHANT = 5;
    /** 微信 order.status：订单已关闭 */
    private static final int WX_STATUS_CLOSED = 6;
    /** 微信 order.status：退款，用户侧收款完成 */
    private static final int WX_STATUS_REFUND_USER = 8;

    @Resource
    private XpayProperties xpayProperties;

    @Resource
    private XpayClient xpayClient;

    @Resource
    private CoinService coinService;

    @Resource
    private SysUserMapper sysUserMapper;

    @Resource
    private CoinRechargeOrderMapper orderMapper;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Map<String, Object> capability() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("enabled", xpayProperties.ready());
        data.put("env", xpayProperties.getEnv());
        return data;
    }

    @Override
    public List<Map<String, Object>> listPacks() {
        List<Map<String, Object>> packs = new ArrayList<>();
        for (XpayProperties.Pack pack : xpayProperties.getPacks()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("code", pack.getCode());
            item.put("title", pack.getTitle());
            item.put("coinAmount", pack.getCoinAmount());
            item.put("priceFen", pack.getPriceFen());
            packs.add(item);
        }
        return packs;
    }

    @Override
    public Map<String, Object> createOrder(Long userId, String packCode) {
        if (!xpayProperties.ready()) {
            throw new BusinessException(ResultCode.XPAY_NOT_CONFIGURED);
        }
        XpayProperties.Pack pack = xpayProperties.findPack(packCode);
        if (pack == null || pack.getCoinAmount() == null || pack.getCoinAmount() <= 0) {
            throw new BusinessException(ResultCode.RECHARGE_PACK_INVALID);
        }

        SysUser user = sysUserMapper.selectById(userId);
        // 用户态签名的密钥就是 session_key，账号密码注册的用户没有它，无法走虚拟支付
        if (user == null || isBlank(user.getOpenid()) || isBlank(user.getSessionKey())) {
            throw new BusinessException(ResultCode.XPAY_WX_LOGIN_REQUIRED);
        }

        String outTradeNo = "RC" + LocalDateTime.now().format(TIME_FORMAT)
                + ThreadLocalRandom.current().nextInt(1000, 10000);

        Map<String, Object> signDataMap = new LinkedHashMap<>();
        signDataMap.put("offerId", xpayProperties.getOfferId());
        signDataMap.put("buyQuantity", 1);
        signDataMap.put("env", xpayProperties.getEnv());
        signDataMap.put("currencyType", "CNY");
        signDataMap.put("productId", pack.getProductId());
        signDataMap.put("goodsPrice", pack.getPriceFen());
        signDataMap.put("outTradeNo", outTradeNo);
        signDataMap.put("attach", "u" + userId);

        String signData = writeJson(signDataMap);
        String paySig = XpaySigner.paySig(xpayProperties.getAppKey(), PAY_URI, signData);
        String signature = XpaySigner.userSig(user.getSessionKey(), signData);

        CoinRechargeOrder order = new CoinRechargeOrder();
        order.setUserId(userId);
        order.setOpenid(user.getOpenid());
        order.setOutTradeNo(outTradeNo);
        order.setProductId(pack.getProductId());
        order.setCoinAmount(pack.getCoinAmount());
        order.setPriceFen(pack.getPriceFen());
        order.setStatus(CoinRechargeOrder.STATUS_CREATED);
        order.setSignData(signData);
        order.setCreatedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());
        orderMapper.insert(order);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("signData", signData);
        data.put("mode", PAY_MODE);
        data.put("paySig", paySig);
        data.put("signature", signature);
        data.put("outTradeNo", outTradeNo);
        data.put("coinAmount", pack.getCoinAmount());
        data.put("priceFen", pack.getPriceFen());
        return data;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> confirmPaid(Long userId, String outTradeNo) {
        CoinRechargeOrder order = loadOwned(userId, outTradeNo);
        if (order.getStatus() >= CoinRechargeOrder.STATUS_DELIVERED) {
            return statusView(order);
        }
        applyWechatStatus(order, queryWechatStatus(order));
        return statusView(orderMapper.selectById(order.getId()));
    }

    @Override
    public Map<String, Object> queryStatus(Long userId, String outTradeNo) {
        return statusView(loadOwned(userId, outTradeNo));
    }

    @Override
    public List<Map<String, Object>> listOrders(Long userId, int pageNum, int pageSize) {
        Page<CoinRechargeOrder> page = orderMapper.selectPage(
                new Page<>(Math.max(pageNum, 1), Math.min(Math.max(pageSize, 1), 50)),
                new LambdaQueryWrapper<CoinRechargeOrder>()
                        .eq(CoinRechargeOrder::getUserId, userId)
                        .orderByDesc(CoinRechargeOrder::getCreatedAt));
        List<Map<String, Object>> list = new ArrayList<>();
        for (CoinRechargeOrder order : page.getRecords()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("outTradeNo", order.getOutTradeNo());
            item.put("coinAmount", order.getCoinAmount());
            item.put("priceFen", order.getPriceFen());
            item.put("status", order.getStatus());
            item.put("createdAt", formatTime(order.getCreatedAt()));
            item.put("paidAt", formatTime(order.getPaidAt()));
            list.add(item);
        }
        return list;
    }

    private String formatTime(LocalDateTime time) {
        return time == null ? null : time.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deliverByOutTradeNo(String outTradeNo, String wxOrderId) {
        CoinRechargeOrder order = findByOutTradeNo(outTradeNo);
        if (order == null) {
            log.warn("发货通知找不到订单 outTradeNo={}", outTradeNo);
            return false;
        }
        return deliver(order, wxOrderId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean refundByOutTradeNo(String outTradeNo, String wxOrderId) {
        CoinRechargeOrder order = findByOutTradeNo(outTradeNo);
        if (order == null) {
            return false;
        }
        // 先抢状态再回收：并发的重复通知只会有一条通过
        if (orderMapper.markRefunded(outTradeNo, wxOrderId) == 0) {
            return false;
        }
        int recover = Math.min(coinService.getBalance(order.getUserId()), order.getCoinAmount());
        if (recover > 0) {
            coinService.spend(order.getUserId(), recover, CoinService.BIZ_RECHARGE_REFUND,
                    order.getId(), "充值退款回收");
        }
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @Scheduled(cron = "0 */5 * * * *")
    public void reconcile() {
        if (!xpayProperties.ready() || !Boolean.TRUE.equals(xpayProperties.getReconcileEnabled())) {
            return;
        }
        LambdaQueryWrapper<CoinRechargeOrder> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(CoinRechargeOrder::getStatus, CoinRechargeOrder.STATUS_CREATED, CoinRechargeOrder.STATUS_PAID)
               .lt(CoinRechargeOrder::getCreatedAt, LocalDateTime.now().minusMinutes(2))
               .orderByAsc(CoinRechargeOrder::getId)
               .last("LIMIT 20");
        List<CoinRechargeOrder> pending = orderMapper.selectList(wrapper);
        for (CoinRechargeOrder order : pending) {
            try {
                applyWechatStatus(order, queryWechatStatus(order));
            } catch (Exception e) {
                log.warn("对账订单 {} 失败: {}", order.getOutTradeNo(), e.getMessage());
            }
        }
    }

    /**
     * 落账唯一入口：先占 uk_biz 再加钱，重复回调/重复查单都不会重复到账
     */
    private boolean deliver(CoinRechargeOrder order, String wxOrderId) {
        if (order.getStatus() >= CoinRechargeOrder.STATUS_DELIVERED) {
            return false;
        }
        boolean granted = coinService.grant(order.getUserId(), order.getCoinAmount(),
                CoinService.BIZ_RECHARGE, order.getId(), "购买猫粮礼包");
        orderMapper.markDelivered(order.getOutTradeNo(), wxOrderId);
        log.info("充值到账 outTradeNo={} userId={} coin={} 首次发放={}",
                order.getOutTradeNo(), order.getUserId(), order.getCoinAmount(), granted);
        return granted;
    }

    private void applyWechatStatus(CoinRechargeOrder order, int wxStatus) {
        String wxOrderId = order.getWxOrderId();
        switch (wxStatus) {
            case WX_STATUS_PAID, WX_STATUS_DELIVERING -> {
                orderMapper.markPaid(order.getOutTradeNo(), wxOrderId);
                deliver(order, wxOrderId);
            }
            case WX_STATUS_DELIVERED -> deliver(order, wxOrderId);
            case WX_STATUS_CLOSED -> orderMapper.markClosed(order.getOutTradeNo());
            case WX_STATUS_REFUND_MERCHANT, WX_STATUS_REFUND_USER ->
                    refundByOutTradeNo(order.getOutTradeNo(), wxOrderId);
            default -> log.debug("订单 {} 状态 {} 暂不处理", order.getOutTradeNo(), wxStatus);
        }
    }

    @SuppressWarnings("unchecked")
    private int queryWechatStatus(CoinRechargeOrder order) {
        Map<String, Object> response = xpayClient.queryOrder(order.getOpenid(), order.getOutTradeNo());
        if (response == null) {
            return -1;
        }
        Object errcode = response.get("errcode");
        if (errcode instanceof Number && ((Number) errcode).intValue() != 0) {
            log.warn("query_order 返回错误 {} {}", errcode, response.get("errmsg"));
            return -1;
        }
        Object orderNode = response.get("order");
        if (!(orderNode instanceof Map)) {
            return -1;
        }
        Object status = ((Map<String, Object>) orderNode).get("status");
        if (status instanceof Number) {
            Object wxOrderId = ((Map<String, Object>) orderNode).get("wx_order_id");
            if (wxOrderId != null) {
                order.setWxOrderId(String.valueOf(wxOrderId));
            }
            return ((Number) status).intValue();
        }
        return -1;
    }

    private CoinRechargeOrder loadOwned(Long userId, String outTradeNo) {
        CoinRechargeOrder order = findByOutTradeNo(outTradeNo);
        if (order == null || !order.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.RECHARGE_ORDER_NOT_FOUND);
        }
        return order;
    }

    private CoinRechargeOrder findByOutTradeNo(String outTradeNo) {
        if (isBlank(outTradeNo)) {
            return null;
        }
        LambdaQueryWrapper<CoinRechargeOrder> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CoinRechargeOrder::getOutTradeNo, outTradeNo);
        return orderMapper.selectOne(wrapper);
    }

    private Map<String, Object> statusView(CoinRechargeOrder order) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("outTradeNo", order.getOutTradeNo());
        data.put("status", order.getStatus());
        data.put("coinAmount", order.getCoinAmount());
        data.put("delivered", order.getStatus() != null
                && order.getStatus() == CoinRechargeOrder.STATUS_DELIVERED);
        data.put("balance", coinService.getBalance(order.getUserId()));
        return data;
    }

    private String writeJson(Map<String, Object> map) {
        try {
            return objectMapper.writeValueAsString(map);
        } catch (Exception e) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR.getCode(), "订单参数序列化失败");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
