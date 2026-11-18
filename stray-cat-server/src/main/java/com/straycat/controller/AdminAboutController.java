package com.straycat.controller;

import com.straycat.annotation.RequireRole;
import com.straycat.common.Result;
import com.straycat.entity.AdminOperationLog;
import com.straycat.entity.AppAbout;
import com.straycat.entity.SysUser;
import com.straycat.interceptor.AuthInterceptor;
import com.straycat.mapper.AdminOperationLogMapper;
import com.straycat.mapper.AppAboutMapper;
import com.straycat.mapper.SysUserMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.regex.Pattern;

/**
 * 管理端「关于我们」配置读写，@RequireRole(3)。
 * 保存即生效（客户端无缓存层）；电话/邮箱做基础格式校验；写操作落 admin_operation_log。
 */
@Slf4j
@Tag(name = "后台关于我们配置（管理员）")
@RestController
@RequestMapping("/admin/about")
@RequiredArgsConstructor
public class AdminAboutController {

    private static final Pattern PHONE_PATTERN = Pattern.compile("^1\\d{10}$");
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    private static final int MAX_LEN = 200;

    private final AppAboutMapper appAboutMapper;
    private final AdminOperationLogMapper operationLogMapper;
    private final SysUserMapper sysUserMapper;

    @Operation(summary = "查询关于我们配置")
    @GetMapping
    @RequireRole(3)
    public Result<AppAbout> get() {
        return Result.success(ensureRow());
    }

    @Operation(summary = "保存关于我们配置（保存后客户端即时生效）")
    @PutMapping
    @RequireRole(3)
    public Result<AppAbout> save(@RequestBody AppAbout form, HttpServletRequest request) {
        String phone = trim(form.getPhone());
        if (phone != null && !PHONE_PATTERN.matcher(phone).matches()) {
            return Result.error("电话需为 11 位手机号");
        }
        String email = trim(form.getEmail());
        if (email != null && !EMAIL_PATTERN.matcher(email).matches()) {
            return Result.error("邮箱格式不正确");
        }

        AppAbout row = ensureRow();
        if (notBlank(form.getAppName())) row.setAppName(cut(form.getAppName()));
        if (notBlank(form.getVersion())) row.setVersion(cut(form.getVersion()));
        if (form.getSlogan() != null) row.setSlogan(cut(form.getSlogan()));
        if (notBlank(form.getMottoMain())) row.setMottoMain(cut(form.getMottoMain()));
        if (form.getMottoSub() != null) row.setMottoSub(cut(form.getMottoSub()));
        if (notBlank(form.getPhone())) row.setPhone(phone);
        if (notBlank(form.getEmail())) row.setEmail(email);
        if (form.getContactTip() != null) row.setContactTip(cut(form.getContactTip()));
        if (form.getCopyright() != null) row.setCopyright(cut(form.getCopyright()));
        row.setUpdatedAt(LocalDateTime.now());
        appAboutMapper.updateById(row);

        Long operatorId = (Long) request.getAttribute(AuthInterceptor.USER_ID_ATTR);
        writeLog(request, "ABOUT_CONFIG_SAVE", operatorId, "更新「关于我们」配置（版本 " + row.getVersion() + "）");
        return Result.success("已保存，客户端即时生效", row);
    }

    private AppAbout ensureRow() {
        AppAbout row = appAboutMapper.selectById(1L);
        if (row != null) {
            return row;
        }
        AppAbout seed = AboutController.defaultAbout();
        seed.setId(1L);
        appAboutMapper.insert(seed);
        return seed;
    }

    private void writeLog(HttpServletRequest request, String action, Long operatorId, String detail) {
        AdminOperationLog entity = new AdminOperationLog();
        entity.setOperatorId(operatorId);
        String operatorName = "system";
        if (operatorId != null) {
            SysUser operator = sysUserMapper.selectById(operatorId);
            operatorName = operator == null || operator.getUsername() == null
                    ? String.valueOf(operatorId) : operator.getUsername();
        }
        entity.setOperatorName(operatorName);
        entity.setAction(action);
        entity.setDetail(detail);
        entity.setIp(request.getRemoteAddr());
        entity.setCreatedAt(LocalDateTime.now());
        operationLogMapper.insert(entity);
    }

    private static String trim(String v) {
        return v == null ? null : v.trim();
    }

    private static boolean notBlank(String v) {
        return v != null && !v.isBlank();
    }

    private static String cut(String v) {
        String t = v.trim();
        return t.length() > MAX_LEN ? t.substring(0, MAX_LEN) : t;
    }
}
