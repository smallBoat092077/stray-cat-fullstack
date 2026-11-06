package com.straycat.controller;

import com.straycat.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/common")
@Tag(name = "通用接口", description = "文件上传等通用功能")
public class CommonController {

    @Value("${file.upload-path:./uploads}")
    private String uploadPath;

    @PostMapping("/upload")
    @Operation(summary = "上传文件")
    public Result<String> upload(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return Result.error("文件不能为空");
        }

        try {
            String originalName = file.getOriginalFilename();
            String ext = originalName != null ? originalName.substring(originalName.lastIndexOf(".")) : ".jpg";
            String fileName = UUID.randomUUID().toString() + ext;

            File dir = new File(uploadPath).getAbsoluteFile();
            if (!dir.exists()) {
                dir.mkdirs();
            }

            File dest = new File(dir, fileName).getAbsoluteFile();
            file.transferTo(dest);

            String url = "/uploads/" + fileName;
            return Result.success(url);
        } catch (IOException e) {
            log.error("文件上传失败", e);
            return Result.error("上传失败");
        }
    }
}
