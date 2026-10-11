package com.jiwei.framework.utils;

import cn.hutool.core.util.StrUtil;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.PutObjectRequest;
import com.jiwei.framework.properties.AliOssProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

/**
 * 阿里云 OSS 文件上传工具类
 *
 * 需手动填写的配置（application-dev.yml）：
 *   jiwei.alioss.endpoint        = oss-cn-hangzhou.aliyuncs.com
 *   jiwei.alioss.access-key-id    = 你的AccessKey ID
 *   jiwei.alioss.access-key-secret = 你的AccessKey Secret
 *   jiwei.alioss.bucket-name      = jiwei-life
 *
 * 获取 AccessKey: https://ram.console.aliyun.com/manage/ak
 * OSS 控制台: https://oss.console.aliyun.com/
 */
@Component
@Slf4j
public class AliOssUtil {

    @Autowired
    private AliOssProperties aliOssProperties;

    /**
     * 上传文件到阿里云 OSS
     *
     * @param file 上传的文件
     * @param directory 存储目录（如 "dish"、"note"、"avatar"）
     * @return 文件公网访问URL，Mock模式下返回本地路径标识
     */
    public String upload(MultipartFile file, String directory) {
        if (file == null || file.isEmpty()) {
            log.warn("上传文件为空");
            return null;
        }

        String endpoint = aliOssProperties.getEndpoint();
        String accessKeyId = aliOssProperties.getAccessKeyId();
        String accessKeySecret = aliOssProperties.getAccessKeySecret();
        String bucketName = aliOssProperties.getBucketName();

        // ========== Mock 模式：未配置 OSS 凭证 ==========
        // 你需要手动填写以上四项到 application-dev.yml 或环境变量中
        // 未配置时使用 Mock 模式，返回一个带有 UUID 的本地路径标识
        if (StrUtil.isBlank(endpoint)
                || StrUtil.isBlank(accessKeyId)
                || StrUtil.isBlank(accessKeySecret)
                || StrUtil.isBlank(bucketName)
                || "your-access-key-id".equals(accessKeyId)) {

            String mockFileName = UUID.randomUUID().toString()
                    + "_" + file.getOriginalFilename();
            String mockUrl = "/uploads/" + directory + "/" + mockFileName;
            log.warn("OSS凭证未配置，使用Mock模式: {}", mockUrl);
            return mockUrl;
        }

        // ========== 生产模式：上传到阿里云 OSS ==========
        //1、生成唯一文件名（UUID + 原始扩展名）
        String originalFilename = file.getOriginalFilename();
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }
        String objectName = directory + "/" + UUID.randomUUID().toString() + extension;

        //2、创建 OSSClient
        OSS ossClient = new OSSClientBuilder().build(endpoint, accessKeyId, accessKeySecret);

        try {
            //3、上传文件
            PutObjectRequest putObjectRequest = new PutObjectRequest(
                    bucketName, objectName, file.getInputStream());
            ossClient.putObject(putObjectRequest);

            //4、构建访问URL
            // 格式: https://{bucketName}.{endpoint}/{objectName}
            String url = "https://" + bucketName + "." + endpoint + "/" + objectName;
            log.info("OSS上传成功: {} → {}", originalFilename, url);
            return url;
        } catch (IOException e) {
            log.error("OSS上传失败: {}", e.getMessage(), e);
            return null;
        } finally {
            //5、关闭OSSClient
            ossClient.shutdown();
        }
    }
}
