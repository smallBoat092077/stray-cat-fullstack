package com.straycat.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import cn.hutool.crypto.digest.BCrypt;
import com.straycat.common.ResultCode;
import com.straycat.entity.SysUser;
import com.straycat.exception.BusinessException;
import com.straycat.interceptor.RoleInterceptor;
import com.straycat.mapper.SysUserMapper;
import com.straycat.service.CoinService;
import com.straycat.service.SysUserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Map;

@Slf4j
@Service
public class SysUserServiceImpl extends ServiceImpl<SysUserMapper, SysUser> implements SysUserService {

    private static final Integer ROLE_USER = 0;
    private static final Integer ROLE_VOLUNTEER = 1;
    private static final Integer ROLE_ADMIN = 3;

    @Resource
    private CoinService coinService;

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    @Value("${wechat.appid}")
    private String appid;

    @Value("${wechat.secret}")
    private String secret;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public SysUser wxLogin(String code) {
        log.info("微信登录, code: {}", code);

        Map response = jscode2session(code);
        if (response == null) {
            return null;
        }

        String openid = (String) response.get("openid");
        String sessionKey = (String) response.get("session_key");

        if (openid == null || openid.isEmpty()) {
            log.error("微信登录失败, openid为空, response={}", response);
            return null;
        }

        // 查找或创建用户
        SysUser user = getByOpenid(openid);
        if (user == null) {
            user = new SysUser();
            user.setOpenid(openid);
            user.setSessionKey(sessionKey);
            user.setRole(0);
            user.setCreditScore(100);
            user.setStatus(1);
            user.setCoinBalance(0);
            user.setCreatedAt(LocalDateTime.now());
            user.setUpdatedAt(LocalDateTime.now());
            save(user);
            coinService.grant(user.getId(), CoinService.REGISTER_GRANT,
                    CoinService.BIZ_REGISTER, user.getId(), "注册赠送");
        } else {
            user.setSessionKey(sessionKey);
            user.setUpdatedAt(LocalDateTime.now());
            updateById(user);
        }

        return user;
    }

    /**
     * 调 jscode2session 换 openid + session_key。
     * 返回原始响应 Map，errcode 非 0 / openid 缺失 / 网络异常都返回 null，由调用方决定怎么报错
     */
    private Map jscode2session(String code) {
        String url = String.format(
                "https://api.weixin.qq.com/sns/jscode2session?appid=%s&secret=%s&js_code=%s&grant_type=authorization_code",
                appid, secret, code);

        try {
            String responseBody = restTemplate.getForObject(url, String.class);
            log.info("微信响应: {}", responseBody);

            if (responseBody == null || responseBody.isEmpty()) {
                log.error("微信接口无响应");
                return null;
            }

            Map response = objectMapper.readValue(responseBody, Map.class);

            if (response == null) {
                log.error("微信接口无响应");
                return null;
            }

            if (response.containsKey("errcode")) {
                Object errcode = response.get("errcode");
                if (errcode instanceof Number && ((Number) errcode).intValue() != 0) {
                    log.error("微信登录失败, errcode={}, errmsg={}", errcode, response.get("errmsg"));
                    return null;
                }
            }

            return response;
        } catch (Exception e) {
            log.error("微信登录异常", e);
            return null;
        }
    }

    @Override
    public SysUser bindWechat(Long userId, String code) {
        if (code == null || code.trim().isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_MISSING.getCode(), "缺少微信登录凭证");
        }
        SysUser user = getById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new BusinessException(ResultCode.USER_DISABLED);
        }
        if (user.getOpenid() != null && !user.getOpenid().isEmpty()) {
            throw new BusinessException(ResultCode.WECHAT_ALREADY_BOUND);
        }

        Map response = jscode2session(code.trim());
        String openid = response == null ? null : (String) response.get("openid");
        String sessionKey = response == null ? null : (String) response.get("session_key");
        if (openid == null || openid.isEmpty()) {
            throw new BusinessException(ResultCode.WECHAT_CODE_INVALID);
        }

        // openid 上有唯一索引，这里先查后写挡住最常见的冲突；
        // 真并发撞上时靠唯一索引兜底，不让两个账号共享同一个微信
        SysUser exists = getByOpenid(openid);
        if (exists != null && !exists.getId().equals(userId)) {
            throw new BusinessException(ResultCode.WECHAT_BIND_CONFLICT);
        }

        SysUser update = new SysUser();
        update.setId(userId);
        update.setOpenid(openid);
        // 虚拟支付的用户态签名密钥就是 session_key，绑定成功后即可充值
        update.setSessionKey(sessionKey);
        Object unionId = response.get("unionid");
        if (unionId instanceof String && !((String) unionId).isEmpty()) {
            update.setUnionId((String) unionId);
        }
        update.setUpdatedAt(LocalDateTime.now());
        updateById(update);
        log.info("账号密码用户 {} 绑定微信 openid 成功", userId);

        return getById(userId);
    }

    @Override
    public SysUser getByOpenid(String openid) {
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysUser::getOpenid, openid);
        return getOne(wrapper);
    }

    @Override
    public SysUser register(String username, String rawPassword) {
        if (username == null || username.trim().isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_MISSING.getCode(), "用户名不能为空");
        }
        if (rawPassword == null || rawPassword.length() < 6) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "密码长度至少6位");
        }
        username = username.trim();
        if (getByUsername(username) != null) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "用户名已存在");
        }

        SysUser user = new SysUser();
        user.setUsername(username);
        user.setNickname(username);
        user.setPassword(BCrypt.hashpw(rawPassword));
        user.setRole(0);
        user.setCreditScore(100);
        user.setStatus(1);
        user.setCoinBalance(0);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        save(user);
        coinService.grant(user.getId(), CoinService.REGISTER_GRANT,
                CoinService.BIZ_REGISTER, user.getId(), "注册赠送");
        return user;
    }

    @Override
    public SysUser loginByPassword(String username, String rawPassword) {
        SysUser user = getByUsername(username == null ? null : username.trim());
        if (user == null || user.getPassword() == null
                || rawPassword == null || !BCrypt.checkpw(rawPassword, user.getPassword())) {
            throw new BusinessException(ResultCode.LOGIN_FAILED.getCode(), "用户名或密码错误");
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new BusinessException(ResultCode.USER_DISABLED);
        }
        return user;
    }

    private SysUser getByUsername(String username) {
        if (username == null || username.isEmpty()) {
            return null;
        }
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysUser::getUsername, username);
        return getOne(wrapper);
    }

    @Override
    public void grantRole(Long userId, Long operatorId, Integer role) {
        if (userId == null || role == null
                || (!role.equals(ROLE_USER) && !role.equals(ROLE_VOLUNTEER) && !role.equals(ROLE_ADMIN))) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "角色取值不合法");
        }
        if (userId.equals(operatorId)) {
            throw new BusinessException("不能修改自己的角色");
        }
        if (getById(userId) == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        SysUser update = new SysUser();
        update.setId(userId);
        update.setRole(role);
        update.setUpdatedAt(LocalDateTime.now());
        updateById(update);
        redisTemplate.delete(RoleInterceptor.cacheKey(userId));
        log.info("管理员 {} 将用户 {} 的角色设为 {}", operatorId, userId, role);
    }
}
