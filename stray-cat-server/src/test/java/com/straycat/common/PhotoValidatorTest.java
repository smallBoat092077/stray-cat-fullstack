package com.straycat.common;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PhotoValidator 性能优化 P0-1 的回归测试：
 * 只读头解析尺寸后，四类真机图片仍放行、超大图仍拒绝、伪造头不误拒。
 */
class PhotoValidatorTest {

    private static MultipartFile file(String name, byte[] bytes) {
        return new MockMultipartFile("photo", name, "application/octet-stream", bytes);
    }

    @Test
    void jpegSof0Passes() {
        assertNull(PhotoValidator.validate(file("cat.jpg", jpeg(1080, 1920))));
    }

    @Test
    void pngIhdrPasses() {
        assertNull(PhotoValidator.validate(file("cat.png", png(726, 1570))));
    }

    @Test
    void webpDisguisedAsPngPasses() {
        // 微信压缩后的 WebP 常保留 .png 扩展名（UAT 实测）
        assertNull(PhotoValidator.validate(file("wx_image.png", webp(750, 422, 64))));
    }

    @Test
    void wechatTempFileWithoutExtensionPasses() {
        assertNull(PhotoValidator.validate(file("wxfile_tmp_abc", jpeg(1080, 1920))));
    }

    @Test
    void oversizedImageRejected() {
        String msg = PhotoValidator.validate(file("huge.png", png(5000, 5000)));
        assertNotNull(msg);
        assertTrue(msg.contains("尺寸过大"));
    }

    @Test
    void nonImageRejected() {
        String msg = PhotoValidator.validate(file("x.jpg", "not an image at all".getBytes(StandardCharsets.UTF_8)));
        assertNotNull(msg);
        assertTrue(msg.contains("不是有效"));
    }

    @Test
    void truncatedWebpRejected() {
        byte[] b = webp(750, 422, 64);
        b[4] = 0x01; // 破坏 RIFF 声明长度，应命中「内容不完整」
        String msg = PhotoValidator.validate(file("x.webp", b));
        assertNotNull(msg);
        assertTrue(msg.contains("不完整"));
    }

    @Test
    void corruptJpegBodyIsPassedThroughToPython() {
        // 魔数正确但后续段损坏：头解析失败应放行，不阻断上传，由下游 PIL 兜底
        byte[] b = new byte[]{ (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 1, 2, 3, 4, 5 };
        assertNull(PhotoValidator.validate(file("broken.jpg", b)));
    }

    @Test
    void realFixturesStillPass() throws Exception {
        Path dir = Path.of("..", "stray-cat-recognizer", "eval", "fixtures");
        if (!Files.isDirectory(dir)) {
            return; // 构建环境没有评测素材时跳过，核心逻辑由上面的合成头用例覆盖
        }
        try (var stream = Files.list(dir)) {
            for (Path p : stream.filter(Files::isRegularFile).toList()) {
                String n = p.getFileName().toString();
                assertNull(PhotoValidator.validate(file(n, Files.readAllBytes(p))),
                        "真实 fixture 应放行: " + n);
            }
        }
    }

    // ---------------- 合成图片头 ----------------

    private static byte[] png(int w, int h) {
        byte[] b = new byte[33];
        byte[] sig = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
        System.arraycopy(sig, 0, b, 0, 8);
        byte[] ihdrTag = {0, 0, 0, 13, 'I', 'H', 'D', 'R'};
        System.arraycopy(ihdrTag, 0, b, 8, 8);
        putBE(b, 16, w);
        putBE(b, 20, h);
        return b;
    }

    private static byte[] jpeg(int w, int h) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(0xFF); out.write(0xD8);                 // SOI
        out.write(0xFF); out.write(0xE0);                 // APP0
        out.write(0x00); out.write(0x10);                 // length = 16
        out.writeBytes("JFIF\0".getBytes(StandardCharsets.US_ASCII));
        for (int i = 0; i < 9; i++) out.write(0);        // APP0 payload 补齐 14B
        out.write(0xFF); out.write(0xC0);                 // SOF0
        out.write(0x00); out.write(0x11);                 // length = 17
        out.write(8);                                     // precision
        out.write((h >> 8) & 0xFF); out.write(h & 0xFF);
        out.write((w >> 8) & 0xFF); out.write(w & 0xFF);
        for (int i = 0; i < 10; i++) out.write(0);        // SOF0 payload 补齐 15B
        out.write(0xFF); out.write(0xD9);                 // EOI
        return out.toByteArray();
    }

    private static byte[] webp(int w, int h, int totalLen) {
        byte[] b = new byte[Math.max(totalLen, 30)];
        b[0] = 'R'; b[1] = 'I'; b[2] = 'F'; b[3] = 'F';
        putLE32(b, 4, b.length - 8);
        b[8] = 'W'; b[9] = 'E'; b[10] = 'B'; b[11] = 'P';
        b[12] = 'V'; b[13] = 'P'; b[14] = '8'; b[15] = ' ';
        putLE32(b, 16, b.length - 20);
        b[20] = (byte) 0xD0; b[21] = (byte) 0x87; b[22] = 0x02;
        b[23] = (byte) 0x9D; b[24] = 0x01; b[25] = 0x2A;
        b[26] = (byte) (w & 0xFF); b[27] = (byte) ((w >> 8) & 0xFF);
        b[28] = (byte) (h & 0xFF); b[29] = (byte) ((h >> 8) & 0xFF);
        return b;
    }

    private static void putBE(byte[] b, int off, int v) {
        b[off] = (byte) (v >>> 24); b[off + 1] = (byte) (v >>> 16);
        b[off + 2] = (byte) (v >>> 8); b[off + 3] = (byte) v;
    }

    private static void putLE32(byte[] b, int off, int v) {
        b[off] = (byte) v; b[off + 1] = (byte) (v >>> 8);
        b[off + 2] = (byte) (v >>> 16); b[off + 3] = (byte) (v >>> 24);
    }
}
