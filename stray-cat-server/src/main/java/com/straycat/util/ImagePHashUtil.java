package com.straycat.util;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;

/**
 * 图片感知哈希（dhash 64bit）：把图片缩到 9×8 灰度，逐行比较相邻像素亮度生成指纹。
 * 同一张照片重新上传（文件名/压缩率不同）哈希几乎不变（汉明距离通常 ≤2），
 * 不同照片距离通常 >20。用于流浪猫上报去重。
 */
public final class ImagePHashUtil {

    private ImagePHashUtil() {
    }

    /** 计算图片的 dhash；解析失败返回 null（调用方降级为不做哈希比对） */
    public static Long dhash(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        try {
            BufferedImage source = ImageIO.read(new ByteArrayInputStream(bytes));
            if (source == null) {
                return null;
            }
            BufferedImage small = new BufferedImage(9, 8, BufferedImage.TYPE_BYTE_GRAY);
            Graphics2D g = small.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(source, 0, 0, 9, 8, null);
            g.dispose();
            long hash = 0L;
            for (int y = 0; y < 8; y++) {
                for (int x = 0; x < 8; x++) {
                    if (gray(small.getRGB(x, y)) > gray(small.getRGB(x + 1, y))) {
                        hash |= 1L << (y * 8 + x);
                    }
                }
            }
            return hash;
        } catch (Exception e) {
            return null;
        }
    }

    /** 两指纹汉明距离：≤8 视为同一张照片 */
    public static int hamming(long a, long b) {
        return Long.bitCount(a ^ b);
    }

    private static int gray(int rgb) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        return (r * 299 + g * 587 + b * 114) / 1000;
    }
}
