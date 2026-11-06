package com.straycat.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 腾讯位置服务 WebAPI。key 只留在服务端：小程序包可被解出，而且由服务端代理之后，
 * 前端只需要我们自己的域名，不必把 apis.map.qq.com 加进 request 合法域名。
 */
@Data
@Component
@ConfigurationProperties(prefix = "tencent.map")
public class TencentMapProperties {

    private String key = "";

    /**
     * walking / driving / cycling，直接拼进 direction 接口的路径
     */
    private String mode = "walking";

    public boolean ready() {
        return key != null && !key.isBlank();
    }
}
