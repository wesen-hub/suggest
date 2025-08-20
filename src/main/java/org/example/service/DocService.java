package org.example.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
import co.elastic.clients.elasticsearch.core.bulk.IndexOperation;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.SneakyThrows;
import org.example.utils.PinyinUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;

@Service
public class DocService {
    @Autowired
    ElasticsearchClient client;
    final int BATCH_SIZE = 1000;
    final String INDEX_NAME = "completion_index";


    @Autowired
    TermIndexService termIndexService;

    // 文档实体类（与索引映射对齐）
    @Data
    @AllArgsConstructor
    public static class SuggestDoc {
        private TitleSuggest title_suggest;
        private Integer weight;
    }

    @Data
    @AllArgsConstructor
    public static class TitleSuggest {
        private List<String> input;
        private Integer weight;
    }

    @SneakyThrows
    public void bulkInsertFromFile(String filePath) {
        System.out.println("开始从文件导入词条索引: " + filePath);
        List<BulkOperation> operations = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = br.readLine()) != null) {
                // 解析词条和权重
                String[] parts = line.split("\\s+");
                String keyword = parts[0];
                System.out.println("处理词条: " + keyword);
                int weight = (parts.length > 1) ? Integer.parseInt(parts[1]) : 1;

                // 生成拼音全拼和首字母
                String fullPinyin = PinyinUtils.getFullPinyin(keyword);      // 如 "nuojiya"
                String initials = PinyinUtils.getInitials(keyword);     // 如 "njy"

                // 构建输入数组
                List<String> inputs = List.of(keyword, fullPinyin, initials);

                // 创建文档对象
                SuggestDoc doc = new SuggestDoc(
                        new TitleSuggest(inputs, weight),
                        weight
                );

                // 添加批量操作
                operations.add(IndexOperation.of(io ->
                        io.index(INDEX_NAME).document(doc)
                )._toBulkOperation());

                // 批量提交
                if (operations.size() >= BATCH_SIZE) {
                    executeBulk(operations);
                    operations.clear();
                }
            }
            // 提交剩余数据
            if (!operations.isEmpty()) {
                executeBulk(operations);
            }
        }
    }

    @SneakyThrows
    private void executeBulk(List<BulkOperation> operations) {
        client.bulk(BulkRequest.of(br -> br.operations(operations)));
    }


}
