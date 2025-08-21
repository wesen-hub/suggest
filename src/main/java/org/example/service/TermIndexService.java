package org.example.service;

import co.elastic.clients.elasticsearch._types.query_dsl.QueryBuilders;
import co.elastic.clients.elasticsearch.core.DeleteByQueryRequest;
import co.elastic.clients.elasticsearch.core.DeleteByQueryResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

@Service
public class TermIndexService {

    private static final int BATCH_SIZE = 1000;  // 批次大小
    private static final String INDEX_NAME = "term_index";

    @Autowired
    private ElasticsearchClient esClient;
    //从配置文件获取文件路径


    /**
     * 删除指定索引下的所有文档
     * @param indexName 目标索引名称
     * @return 是否删除成功
     */
    public boolean deleteAllDocuments(String indexName) {
        int maxRetries = 10; // 最大重试次数
        long retryIntervalMs = 4000; // 重试间隔(毫秒)
        int retryCount = 0;

        while (retryCount < maxRetries) {
            try {
                // 构建删除请求
                DeleteByQueryRequest request = DeleteByQueryRequest.of(d -> d
                        .index(indexName)
                        .query(QueryBuilders.matchAll().build()._toQuery())
                );
                // 执行删除操作
                DeleteByQueryResponse response = esClient.deleteByQuery(request);
                return response.deleted() > 0;
            } catch (IOException e) {
                // 检查是否是连接拒绝错误
                if (e.getMessage().contains("Connection refused") ||
                        e.getMessage().contains("Failed to connect")) {

                    retryCount++;
                    System.out.println("ES连接拒绝，等待重试 (" + retryCount + "/" + maxRetries +
                            ")，将在 " + retryIntervalMs/1000 + " 秒后重试...");

                    // 等待一段时间后重试
                    try {
                        Thread.sleep(retryIntervalMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("重试过程被中断", ie);
                    }

                    // 随着重试次数增加，逐渐增加等待时间(可选)
                    retryIntervalMs = Math.min(retryIntervalMs +1000, 30000); // 最大30秒
                } else {
                    // 如果是其他类型的IOException，直接抛出
                    throw new RuntimeException("删除索引数据失败: " + e.getMessage(), e);
                }
            }
        }

        // 达到最大重试次数后仍然失败
        throw new RuntimeException("删除索引数据失败: 已达到最大重试次数(" + maxRetries +
                ")，ES可能未启动或不可用");
    }

    /**
     * 从文件中批量导入词条索引
     * @param filePath 文件路径
     * @throws IOException 读取文件或执行批量请求时可能抛出异常
     */
    public void termIndexWordsFromFile(String filePath) throws IOException {
        System.out.println("开始从文件导入词条索引: " + filePath);
        BulkRequest.Builder bulkBuilder = new BulkRequest.Builder();
        int currentCount = 0;
        try (var lines = Files.lines(Paths.get(filePath))) {
            var iterator = lines.iterator();

            while (iterator.hasNext()) {
                String line = iterator.next().trim();
                if (line.isEmpty()) continue;

                // 解析行数据
                String[] parts = line.split("\\s+", 2);
                String word = parts[0];
                int weight = parseWeight(parts);

                // 构建文档
                Map<String, Object> doc = new HashMap<>();
                //添加id
                doc.put("termWords", word);
                doc.put("weight", weight);

                // 添加批量请求
                bulkBuilder.operations(op -> op
                        .index(idx -> idx
                                .index(INDEX_NAME)
                                .document(doc)
                        )
                );
                currentCount++;

                // 批量提交
                if (currentCount % BATCH_SIZE == 0) {
                    executeBulk(bulkBuilder);
                    bulkBuilder = new BulkRequest.Builder();
                }
            }

            // 提交剩余文档
            if (currentCount % BATCH_SIZE != 0) {
                executeBulk(bulkBuilder);
            }
        }
    }

    /**
     * 解析权重信息
     * @param parts 分割后的行数据
     * @return 权重值，默认为1
     */
    private int parseWeight(String[] parts) {
        try {
            return (parts.length > 1 && !parts[1].isEmpty()) ?
                    Integer.parseInt(parts[1]) : 1;
        } catch (NumberFormatException e) {
            return 1;  // 格式错误时使用默认值
        }
    }

    private void executeBulk(BulkRequest.Builder bulkBuilder) throws IOException {
        BulkResponse response = esClient.bulk(bulkBuilder.build());

        // 检查错误（实际生产环境需要更完善的错误处理）
        if (response.errors()) {
            for (BulkResponseItem item : response.items()) {
                if (item.error() != null) {
                    System.err.println("Error indexing document: " + item.error().reason());
                }
            }
        }
    }
}
