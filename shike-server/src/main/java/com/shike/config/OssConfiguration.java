package com.shike.config;

import com.shike.properties.AliOssProperties;
import com.shike.utils.AliOssUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
public class OssConfiguration {

    @ConditionalOnMissingBean
    @Bean
    public AliOssUtil aliOssUtil(AliOssProperties aliOssProperties) {
        // ⚠️ 千万不要把 aliOssProperties 整个打出来 —— 它包含 accessKeyId / accessKeySecret，
        //    会以明文进日志文件。日志一旦泄露 = 密钥泄露。
        log.info("阿里云 OSS 已初始化：bucket={}, endpoint={}",
                aliOssProperties.getBucketName(), aliOssProperties.getEndpoint());
        return new AliOssUtil(aliOssProperties.getEndpoint(),
                aliOssProperties.getAccessKeyId(),
                aliOssProperties.getAccessKeySecret(),
                aliOssProperties.getBucketName());
    }
}
