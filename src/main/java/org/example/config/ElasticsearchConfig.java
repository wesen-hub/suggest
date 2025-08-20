package org.example.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import org.apache.http.HttpHost;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.elasticsearch.client.RestClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ElasticsearchConfig {

    @Value("${elasticsearch.host}")
    private String host;

    @Value("${elasticsearch.port}")
    private int port;


    @Bean
    public RestClient restClient() {
        System.out.println("es host "+host);
        System.out.println("es port "+port);
        // 基础连接配置
        return RestClient.builder(new HttpHost(host, port))
                .setHttpClientConfigCallback(httpClientBuilder -> {
                    // 认证配置
                    CredentialsProvider credentialsProvider = new BasicCredentialsProvider();

                    return httpClientBuilder
                            .setDefaultCredentialsProvider(credentialsProvider);
                })
                .build();
    }

    @Bean
    public ElasticsearchTransport elasticsearchTransport() {
        // 序列化配置
        return new RestClientTransport(restClient(), new JacksonJsonpMapper());
    }

    @Bean
    public ElasticsearchClient elasticsearchClient() {
        // 单例客户端
        return new ElasticsearchClient(elasticsearchTransport());
    }
}
