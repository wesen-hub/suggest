package org.example.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig implements WebMvcConfigurer {
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")  // 所有接口
                .allowedOriginPatterns("*")  // 允许所有来源（生产环境应指定具体域名）
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH")  // 允许的方法
                .allowedHeaders("*")  // 允许所有请求头
                .exposedHeaders("Authorization", "Content-Disposition")  // 暴露的响应头
                .allowCredentials(true)  // 允许发送凭据（如cookies）
                .maxAge(3600);  // 预检请求缓存时间（秒）
    }
}
