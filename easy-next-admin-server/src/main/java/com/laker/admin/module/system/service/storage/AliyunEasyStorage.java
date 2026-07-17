package com.laker.admin.module.system.service.storage;

import com.aliyun.oss.OSSClient;
import com.aliyun.oss.model.OSSObject;
import com.aliyun.oss.model.ObjectMetadata;
import com.aliyun.oss.model.PutObjectRequest;
import com.aliyun.oss.model.PutObjectResult;
import com.laker.admin.config.properties.EasyNextAdminConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.io.InputStream;

@Slf4j
@Service
@Primary
@ConditionalOnProperty(prefix = "easy.features", name = "oss", havingValue = "true")
@ConditionalOnProperty(prefix = "easy.storage.aliyun", name = "enable", havingValue = "true")
public class AliyunEasyStorage implements EasyStorage {

    private final String endpoint;
    private final String accessKeyId;
    private final String accessKeySecret;
    private final String bucketName;

    public AliyunEasyStorage(EasyNextAdminConfig easyConfig) {
        EasyNextAdminConfig.Aliyun aliyun = easyConfig.getStorage().getAliyun();
        endpoint = aliyun.getEndpoint();
        accessKeyId = aliyun.getAccessKeyId();
        accessKeySecret = aliyun.getAccessKeySecret();
        bucketName = aliyun.getBucketName();
    }

    private OSSClient getOSSClient() {
        return new OSSClient(endpoint, accessKeyId, accessKeySecret);
    }


    /**
     * 阿里云OSS对象存储简单上传实现
     */
    @Override
    public String store(InputStream inputStream, long contentLength, String contentType, String fileName) {
        // 简单文件上传, 最大支持 5 GB, 适用于小文件上传, 建议 20M以下的文件使用该接口
        ObjectMetadata objectMetadata = new ObjectMetadata();
        objectMetadata.setContentLength(contentLength);
        objectMetadata.setContentType(contentType);
        // 对象键（Key）是对象在存储桶中的唯一标识。
        PutObjectRequest putObjectRequest = new PutObjectRequest(bucketName, fileName, inputStream, objectMetadata);
        PutObjectResult putObjectResult = getOSSClient().putObject(putObjectRequest);
        return fileName;
    }

    @Override
    public boolean exists(String filePath) {
        return getOSSClient().doesObjectExist(bucketName, filePath);
    }

    @Override
    public InputStream read(String filePath) {
        OSSObject ossObject = getOSSClient().getObject(bucketName, filePath);
        return ossObject.getObjectContent();
    }

    @Override
    public void delete(String filePath) {
        try {
            getOSSClient().deleteObject(bucketName, filePath);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }

    }

    @Override
    public String getUrl(String filePath) {
        return "https://" + bucketName + "." + endpoint + "/" + filePath;
    }
}
