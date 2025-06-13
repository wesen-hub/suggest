package org.example.service;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.Result;
import co.elastic.clients.elasticsearch.core.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class SearchTermService {

    private static final String INDEX_NAME = "chinese_search_demo5";
    private static final String TERM_FIELD = "title.keyword"; // 使用精确匹配字段

    @Autowired
    ElasticsearchClient esClient;

    /**
     * 更新或插入词条权重
     * @param term 要处理的词条
     * @return 操作是否成功
     */
    public boolean upsertTermWeight(String term) {
        try {
            // 1. 检查词条是否存在

            SearchRequest searchRequest = SearchRequest.of(s -> s
                    .index(INDEX_NAME)
                    .query(q -> q
                            .term(t -> t
                                    .field(TERM_FIELD)
                                    .value(v -> v.stringValue(term))
                            )
                    )
                    .size(1)
            );

            SearchResponse<?> response = esClient.search(searchRequest, Map.class);

            if(response.hits().total().value() > 0&&response.hits().hits().size()==1) {
                response.hits().hits().stream().forEach(hit -> {
                    System.out.println(((LinkedHashMap<String,Object>)hit.source()).get("title"));
                });
                return handleExistingTerm(term);
            } else {
                return handleNewTerm(term);
            }

        } catch (IOException e) {
            handleException("Error processing term: " + term, e);
            return false;
        }
    }

    private boolean handleExistingTerm(String term) throws IOException {
        UpdateResponse<Map> response = esClient.update(u -> u
                        .index(INDEX_NAME)
                        .id(getDocumentId(term)) // 根据实际ID策略调整
                        .script(sc -> sc
                                .inline(i -> i
                                        .lang("painless")
                                        .source("ctx._source.weight += 1")
                                ))
                , Map.class);
        return response.result() == Result.Updated;
    }

    private boolean handleNewTerm(String term) throws IOException {
        IndexResponse response = esClient.index(i -> i
                .index(INDEX_NAME)
                .id(getDocumentId(term))
                .document(Map.of(
                        "title", term,
                        "weight", 1
                ))
        );
        return response.result() == Result.Created;
    }

    // 获取文档ID的策略（根据需求调整）
    private String getDocumentId(String term) {
        System.out.println("term:"+term+" hash:"+term.hashCode());
        // 示例：使用term的hash作为ID，实际应根据业务需求设计
        return String.valueOf(term.hashCode());
    }

    private void handleException(String message, Exception e) {
        // 生产环境应使用日志框架
        System.err.println(message);
        e.printStackTrace();
    }
}