package com.straycat.controller;

import com.straycat.annotation.RequireRole;
import com.straycat.common.Result;
import com.straycat.entity.CatPoster;
import com.straycat.service.CatPosterService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

@Tag(name = "海报管理")
@RestController
@RequestMapping("/poster")
@RequiredArgsConstructor
public class PosterController {

    private final CatPosterService posterService;

    @Value("${file.upload-path:./uploads}")
    private String uploadPath;

    /**
     * 只允许站内页面路径：pages/ 开头、可选 query。个人主体无法配置业务域名，
     * 海报挂外链等于给审核埋雷，服务端直接拒绝。
     */
    private static final Pattern LINK_PATTERN =
            Pattern.compile("^pages/[A-Za-z0-9_/-]+(\\?[A-Za-z0-9_=&.%-]*)?$");

    @Operation(summary = "获取海报列表")
    @GetMapping("/list")
    public Result<List<CatPoster>> list() {
        return Result.success(posterService.listOrdered());
    }

    @Operation(summary = "上传海报")
    @PostMapping("/upload")
    @RequireRole(3)
    public Result<CatPoster> upload(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return Result.error("文件不能为空");
        }
        try {
            String originalName = file.getOriginalFilename();
            String ext = originalName != null && originalName.contains(".")
                    ? originalName.substring(originalName.lastIndexOf(".")) : ".jpg";
            String fileName = UUID.randomUUID().toString() + ext;

            File dir = new File(uploadPath).getAbsoluteFile();
            if (!dir.exists()) {
                dir.mkdirs();
            }

            File dest = new File(dir, fileName).getAbsoluteFile();
            file.transferTo(dest);

            int maxOrder = posterService.list().stream()
                    .mapToInt(CatPoster::getSortOrder).max().orElse(-1);

            CatPoster poster = new CatPoster();
            poster.setImageUrl("/uploads/" + fileName);
            poster.setSortOrder(maxOrder + 1);
            posterService.save(poster);
            return Result.success(poster);
        } catch (IOException e) {
            return Result.error("上传失败");
        }
    }

    @Operation(summary = "删除海报")
    @DeleteMapping("/{id}")
    @RequireRole(3)
    public Result<Void> delete(@PathVariable Long id) {
        posterService.deleteById(id);
        return Result.success(null);
    }

    @Operation(summary = "设置海报跳转链接")
    @PostMapping("/{id}/link")
    @RequireRole(3)
    public Result<CatPoster> setLink(@PathVariable Long id, @RequestParam(required = false) String linkUrl) {
        CatPoster poster = posterService.getById(id);
        if (poster == null) {
            return Result.error("海报不存在");
        }
        String link = linkUrl == null ? "" : linkUrl.trim();
        if (!link.isEmpty() && !LINK_PATTERN.matcher(link).matches()) {
            return Result.error("链接必须是小程序内页面路径，如 pages/cat/detail?id=1");
        }
        poster.setLinkUrl(link.isEmpty() ? null : link);
        posterService.updateById(poster);
        return Result.success(poster);
    }
}
