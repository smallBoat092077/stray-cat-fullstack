package com.straycat.common;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

/**
 * 识别照片真实性校验（花纹识别一期 §6；2026-11-15 性能优化 P0-1 改写）。
 *
 * <p>性能优化背景：原实现对每张 JPEG/PNG 调用 {@code ImageIO.read()} 全量解码，
 * 目的仅为读取宽高做 4096 上限判断。1080×1920 的照片会分配约 6–8MB BufferedImage，
 * 在 UAT 2 核 / swap 已用 1.8G 的环境下被放大到 4.6–38.6 秒。
 * 现改为<b>只读文件头解析尺寸</b>，不解码像素，稳态耗时降到毫秒级。
 *
 * <p>兼容要点（UAT 实测，不能只按扩展名 / 只看 jpg+png 判定）：
 * <ol>
 *   <li>wx.chooseMedia 交给 wx.uploadFile 的微信临时文件名常不带扩展名
 *       （iOS 上形如 wxfile://tmp_xxx），不能因后缀白名单一律拒绝。</li>
 *   <li>微信压缩后的图片常是 <b>WebP</b>，但扩展名仍为 .png（UAT 实测 3 张 .png 文件头为
 *       RIFF....WEBP）。Java 标准 ImageIO 没有 WebP reader，WebP 走 RIFF 结构校验 +
 *       VP8X/VP8L/VP8 头解析宽高，真实解码交给下游 Python/PIL。</li>
 *   <li>头解析失败一律<b>放行</b>（不阻断上传），由下游 Python 与现有降级链路兜底，
 *       避免新代码引入拒真。</li>
 * </ol>
 *
 * <p>返回 null 表示通过，否则返回可直接展示给用户的错误文案。
 */
public final class PhotoValidator {

    private static final int MAX_SIDE = 4096;
    /** 覆盖 PNG IHDR(24B) 与 WebP VP8X / VP8L / VP8(30B) 的尺寸字段 */
    private static final int HEAD_LEN = 32;

    private PhotoValidator() {}

    public static String validate(MultipartFile photo) {
        if (photo == null || photo.isEmpty()) {
            return "照片不能为空";
        }
        String name = photo.getOriginalFilename();
        String lower = name == null ? "" : name.toLowerCase(Locale.ROOT);
        int slash = Math.max(lower.lastIndexOf('/'), lower.lastIndexOf('\\'));
        int dot = lower.lastIndexOf('.');
        if (dot > slash) {
            String ext = lower.substring(dot);
            if (!(".jpg".equals(ext) || ".jpeg".equals(ext) || ".png".equals(ext)
                    || ".webp".equals(ext))) {
                return "照片格式不支持，仅支持 jpg / jpeg / png / webp";
            }
        }
        byte[] head;
        try (InputStream in = photo.getInputStream()) {
            head = in.readNBytes(HEAD_LEN);
        } catch (IOException e) {
            return "照片读取失败，请重新上传";
        }
        boolean jpeg = head.length >= 3
                && (head[0] & 0xFF) == 0xFF && (head[1] & 0xFF) == 0xD8 && (head[2] & 0xFF) == 0xFF;
        boolean png = head.length >= 4
                && (head[0] & 0xFF) == 0x89 && head[1] == 0x50 && head[2] == 0x4E && head[3] == 0x47;
        boolean webp = isWebp(head);

        if (webp) {
            // WebP：校验 RIFF 容器声明长度与实际文件大小一致，防止截断 / 拼接伪造
            if (!riffLengthMatches(head, photo.getSize())) {
                return "照片内容不完整，请重新上传";
            }
            return checkSize(webpSize(head));
        }
        if (png) {
            return checkSize(pngSize(head));
        }
        if (jpeg) {
            return checkSize(jpegSize(photo));
        }
        return "照片内容不是有效的 jpg / jpeg / png / webp 图片";
    }

    /** 尺寸解析不出来时放行；解析出来且超限时拒绝 */
    private static String checkSize(int[] wh) {
        if (wh != null && (wh[0] > MAX_SIDE || wh[1] > MAX_SIDE)) {
            return "照片尺寸过大，请上传 4096×4096 以内的图片";
        }
        return null;
    }

    /** PNG：8B 签名 + 4B 长度 + "IHDR" + 4B 宽 + 4B 高（大端） */
    private static int[] pngSize(byte[] h) {
        if (h.length < 24 || h[12] != 'I' || h[13] != 'H' || h[14] != 'D' || h[15] != 'R') {
            return null;
        }
        int w = beInt(h, 16);
        int ht = beInt(h, 20);
        return w > 0 && ht > 0 ? new int[]{w, ht} : null;
    }

    /**
     * JPEG：顺序扫描 segment 找 SOF（SOF0–SOF15，排除 DHT/JPG/DAC）。
     * 只读段头 + SOF 的 5 字节负载，其余段用 skipNBytes 跳过，不分配像素缓冲。
     */
    private static int[] jpegSize(MultipartFile photo) {
        try (InputStream in = photo.getInputStream()) {
            if (in.read() != 0xFF || in.read() != 0xD8) {
                return null;
            }
            while (true) {
                int b = in.read();
                if (b < 0) {
                    return null;
                }
                if (b != 0xFF) {
                    continue;
                }
                int marker;
                do {
                    marker = in.read();
                } while (marker == 0xFF);
                if (marker < 0) {
                    return null;
                }
                // SOI / TEM / RSTn / EOI 无长度字段
                if (marker == 0xD8 || marker == 0x01 || (marker >= 0xD0 && marker <= 0xD9)) {
                    continue;
                }
                if (marker == 0xDA) {
                    return null; // SOS 之后是熵编码数据，不再有尺寸信息
                }
                int hi = in.read();
                int lo = in.read();
                if (hi < 0 || lo < 0) {
                    return null;
                }
                int len = ((hi & 0xFF) << 8) | (lo & 0xFF);
                if (len < 2) {
                    return null;
                }
                boolean sof = marker >= 0xC0 && marker <= 0xCF
                        && marker != 0xC4 && marker != 0xC8 && marker != 0xCC;
                if (sof) {
                    byte[] p = in.readNBytes(Math.min(len - 2, 7));
                    if (p.length < 5) {
                        return null;
                    }
                    int ht = ((p[1] & 0xFF) << 8) | (p[2] & 0xFF);
                    int w = ((p[3] & 0xFF) << 8) | (p[4] & 0xFF);
                    return w > 0 && ht > 0 ? new int[]{w, ht} : null;
                }
                in.skipNBytes(len - 2L);
            }
        } catch (IOException | RuntimeException e) {
            return null; // 头不标准时放行，由下游 Python/PIL 兜底
        }
    }

    /** WebP：VP8X / VP8L / VP8 三种 chunk 的尺寸字段（RIFF 头之后 12B 起） */
    private static int[] webpSize(byte[] h) {
        if (h.length < 30) {
            return null;
        }
        String chunk = new String(new byte[]{h[12], h[13], h[14], h[15]},
                java.nio.charset.StandardCharsets.US_ASCII);
        if ("VP8X".equals(chunk)) {
            int w = le24(h, 24) + 1;
            int ht = le24(h, 27) + 1;
            return w > 0 && ht > 0 ? new int[]{w, ht} : null;
        }
        if ("VP8L".equals(chunk) && (h[20] & 0xFF) == 0x2F) {
            int bits = (h[21] & 0xFF) | ((h[22] & 0xFF) << 8)
                    | ((h[23] & 0xFF) << 16) | ((h[24] & 0xFF) << 24);
            int w = (bits & 0x3FFF) + 1;
            int ht = ((bits >>> 14) & 0x3FFF) + 1;
            return w > 0 && ht > 0 ? new int[]{w, ht} : null;
        }
        if ("VP8 ".equals(chunk)
                && (h[23] & 0xFF) == 0x9D && (h[24] & 0xFF) == 0x01 && (h[25] & 0xFF) == 0x2A) {
            int w = (((h[26] & 0xFF) | ((h[27] & 0xFF) << 8)) & 0x3FFF);
            int ht = (((h[28] & 0xFF) | ((h[29] & 0xFF) << 8)) & 0x3FFF);
            return w > 0 && ht > 0 ? new int[]{w, ht} : null;
        }
        return null;
    }

    private static int beInt(byte[] h, int off) {
        return ((h[off] & 0xFF) << 24) | ((h[off + 1] & 0xFF) << 16)
                | ((h[off + 2] & 0xFF) << 8) | (h[off + 3] & 0xFF);
    }

    private static int le24(byte[] h, int off) {
        return (h[off] & 0xFF) | ((h[off + 1] & 0xFF) << 8) | ((h[off + 2] & 0xFF) << 16);
    }

    /** RIFF(4) + size(4, little-endian) + WEBP(4) + chunk(4)，VP8 / VP8L / VP8X 均可 */
    private static boolean isWebp(byte[] h) {
        if (h.length < 16) {
            return false;
        }
        boolean riff = h[0] == 'R' && h[1] == 'I' && h[2] == 'F' && h[3] == 'F';
        boolean webp = h[8] == 'W' && h[9] == 'E' && h[10] == 'B' && h[11] == 'P';
        String chunk = new String(new byte[]{h[12], h[13], h[14], h[15]},
                java.nio.charset.StandardCharsets.US_ASCII);
        return riff && webp && ("VP8 ".equals(chunk) || "VP8L".equals(chunk) || "VP8X".equals(chunk));
    }

    /** RIFF size 表示「文件总长 - 8」，允许末尾 1 字节 padding 的奇长度对齐 */
    private static boolean riffLengthMatches(byte[] h, long fileSize) {
        long declared = ((h[4] & 0xFFL)) | ((h[5] & 0xFFL) << 8)
                | ((h[6] & 0xFFL) << 16) | ((h[7] & 0xFFL) << 24);
        long expected = declared + 8;
        return fileSize == expected || fileSize == expected + 1;
    }
}
