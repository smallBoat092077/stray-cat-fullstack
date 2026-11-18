package com.straycat.common;

import com.straycat.entity.CatProfile;
import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;

/**
 * 猫咪头像统一取值口径（猫咪头像功能设计方案 §2，三处共用保证一致）：
 * <ol>
 *   <li>cover_image 非空 —— 管理员设置过的头像，最高优先；</li>
 *   <li>否则 images 第一张非空路径 —— 默认用上报图片第一张；</li>
 *   <li>都没有 —— 返回 null，前端显示默认占位图。</li>
 * </ol>
 * 只读方法，不修改 images；images/environment_images 均为逗号分隔的 /uploads 相对路径。
 */
public final class CatAvatarUtil {

    private CatAvatarUtil() {
    }

    public static String resolveCatAvatar(CatProfile cat) {
        if (cat == null) {
            return null;
        }
        if (StringUtils.isNotBlank(cat.getCoverImage())) {
            return cat.getCoverImage().trim();
        }
        return firstImage(cat.getImages());
    }

    /**
     * 取逗号分隔图片串里的第一张非空相对路径；空串/null 返回 null。
     */
    public static String firstImage(String images) {
        if (StringUtils.isBlank(images)) {
            return null;
        }
        return Arrays.stream(images.split(","))
                .map(String::trim)
                .filter(StringUtils::isNotBlank)
                .findFirst()
                .orElse(null);
    }

    /**
     * imageUrl 是否属于该猫可作头像的图片集合（cover_image + images，不含环境图，
     * 方案 D2：环境图多为喂猫点/周边，不适合当头像）。用于管理员挑选接口防越权引用。
     */
    public static boolean isOwnedCatImage(CatProfile cat, String imageUrl) {
        if (cat == null || StringUtils.isBlank(imageUrl)) {
            return false;
        }
        String target = imageUrl.trim();
        return target.equals(StringUtils.trimToEmpty(cat.getCoverImage()))
                || Arrays.stream(StringUtils.defaultString(cat.getImages()).split(","))
                .map(String::trim)
                .anyMatch(target::equals);
    }
}
