package com.jiwei.merchant.controller;

import com.jiwei.common.result.Result;
import com.jiwei.framework.utils.AliOssUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

/**
 * 通用接口（文件上传等）
 */
@RestController
@RequestMapping("/common")
@Slf4j
@Tag(name = "通用接口")
public class CommonController {

    @Autowired
    private AliOssUtil aliOssUtil;

    /**
     * 上传文件到阿里云 OSS
     * 支持的目录: dish（菜品图）、note（笔记图）、avatar（头像）、setmeal（套餐图）
     *
     * @param file 上传的文件
     * @param directory 存储目录，默认 "dish"
     * @return 文件访问URL
     */
    @PostMapping("/upload")
    @Operation(summary = "上传文件到OSS")
    public Result<Map<String, String>> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "dish") String directory) {
        log.info("文件上传: name={}, size={}, directory={}",
                file.getOriginalFilename(), file.getSize(), directory);

        //1、调用 AliOssUtil 上传文件
        // 如果 OSS 凭证未配置，AliOssUtil 内部会自动降级为 Mock 模式
        // 返回 /uploads/{directory}/{uuid}_{filename} 格式的本地路径
        String url = aliOssUtil.upload(file, directory);

        //2、返回结果
        Map<String, String> result = new HashMap<>();
        result.put("url", url);
        return Result.success(result);
    }
}
