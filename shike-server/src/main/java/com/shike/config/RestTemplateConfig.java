package com.shike.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * 给 AI 转发用的 RestTemplate。
 *
 * 为什么要单独配一个 Bean：
 *   默认的 RestTemplate 没有超时限制。调大模型一次要十几秒，
 *   不设超时的话，Python 那边卡住会把 Tomcat 线程一起拖死。
 *   所以这里显式设两个值：
 *     · connectTimeout 5s   —— 连不上就快速失败
 *     · readTimeout   120s  —— 等模型回答，要宽容
 */
@Configuration
public class RestTemplateConfig {

    @Bean("aiRestTemplate")
    public RestTemplate aiRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);
        factory.setReadTimeout(120_000);
        return new RestTemplate(factory);
    }
}
