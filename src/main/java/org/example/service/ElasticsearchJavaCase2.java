package org.example.service;


import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.SuggestMode;
import co.elastic.clients.elasticsearch._types.query_dsl.Operator;
import co.elastic.clients.elasticsearch.core.*;
import co.elastic.clients.json.JsonData;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
public class ElasticsearchJavaCase2 {
    private static final String INDEX_NAME = "poet-index";

    private ElasticsearchTransport transport;
    private ElasticsearchClient client;

    @Before
    public void before() {
        RestClient restClient = RestClient.builder(
                new HttpHost("10.49.196.10", 9200),
                new HttpHost("10.49.196.11", 9200),
                new HttpHost("10.49.196.12", 9200)).build();
        ObjectMapper objectMapper = new ObjectMapper();
        transport = new RestClientTransport(restClient, new JacksonJsonpMapper(objectMapper));
        client = new ElasticsearchClient(transport);
    }

    @After
    public void after() throws IOException {
        transport.close();
    }

    /**
     * 查询文档数量
     */
    @Test
    public void count() throws IOException {
        //查询该索引的所有文档数量
        CountResponse response = client.count(builder -> builder.index(INDEX_NAME));
        log.info("response={}", response);

        //通过 Lucene 查询语法指定条件；8.13.4会报错”contains unrecognized parameter: [q]“，因为 API 提交了请求 "{}"，应该时不需要请求体
        //response = client.count(builder -> builder.index(INDEX_NAME).q("name:杜甫"));
        log.info("response={}", response);

        //通过 "Query DSL" 指定条件
        response = client.count(builder -> builder
                .index(INDEX_NAME)
                .query(queryBuilder -> queryBuilder
                        .term(termQueryBuilder -> termQueryBuilder
                                .field("name").value("杜甫")
                        )
                )
        );
        log.info("response={}", response);
    }

    /**
     * term/terms查询,对输入内容不做分词处理
     */
    @Test
    public void searchTerm() throws IOException {
        SearchResponse<Map> response = client.search(searchRequestBuilder -> searchRequestBuilder
                        .index(INDEX_NAME)
                        .query(queryBuilder -> queryBuilder
                                .term(termQueryBuilder -> termQueryBuilder
                                        .field("name").value("李白")))
                        .sort(sortOptionsBuilder -> sortOptionsBuilder
                                .field(fieldSortBuilder -> fieldSortBuilder
                                        .field("age").order(SortOrder.Asc)))
                        .source(sourceConfigBuilder -> sourceConfigBuilder
                                .filter(sourceFilterBuilder -> sourceFilterBuilder
                                        .includes("age", "name")))
                        .from(0)
                        .size(10)
                , Map.class);
        log.info("response={}", response);

        List<FieldValue> words = new ArrayList<>();
        words.add(new FieldValue.Builder().stringValue("李白").build());
        words.add(new FieldValue.Builder().stringValue("杜甫").build());
        SearchResponse<Poet> response2 = client.search(searchRequestBuilder -> searchRequestBuilder
                        .index(INDEX_NAME)
                        .query(queryBuilder -> queryBuilder
                                .terms(termsQueryBuilder -> termsQueryBuilder
                                        .field("name").terms(termsQueryFieldBuilder -> termsQueryFieldBuilder.value(words))))
                        .source(sourceConfigBuilder -> sourceConfigBuilder
                                .filter(sourceFilterBuilder -> sourceFilterBuilder
                                        .excludes("about")))
                        .from(0)
                        .size(10)
                , Poet.class);
        log.info("response2={}", response2);
    }

    /**
     * range(范围)查询
     */
    @Test
    public void searchRange() throws IOException {
        SearchResponse<Poet> response = client.search(searchRequestBuilder -> searchRequestBuilder
                        .index(INDEX_NAME)
                        .query(queryBuilder -> queryBuilder
                                .range(rangeQueryBuilder -> rangeQueryBuilder
                                        .field("age").gte(JsonData.of("20")).lt(JsonData.of("40"))))
                , Poet.class);
        log.info("response={}", response);
    }

    /**
     * exists 查询，查询对应字段不为空的数据
     */
    @Test
    public void exists() throws IOException {
        SearchResponse<Poet> response = client.search(searchRequestBuilder -> searchRequestBuilder
                        .index(INDEX_NAME).query(builder -> builder.exists(builder1 -> builder1.field("poems")))
                , Poet.class);
        log.info("response={}", response);
    }

    /**
     * match查询，对输入内容先分词再查询
     */
    @Test
    public void searchMatch() throws IOException {
        SearchResponse<Map> response = client.search(searchRequestBuilder -> searchRequestBuilder
                        .index(INDEX_NAME)
                        .query(queryBuilder -> queryBuilder
                                .match(matchQueryBuilder -> matchQueryBuilder
                                        .field("success").query("思想")))
                , Map.class);
        log.info("response={}", response);
    }

    /**
     * multi_match 查询，多个字段进行匹配。
     */
    @Test
    public void searchMultiMatch() throws IOException {
        SearchResponse<Poet> response = client.search(searchRequestBuilder -> searchRequestBuilder
                        .index(INDEX_NAME)
                        .query(queryBuilder -> queryBuilder
                                .multiMatch(multiMatchQueryBuilder -> multiMatchQueryBuilder
                                        .fields("about", "success").query("思想")))
                , Poet.class);
        log.info("response={}", response);
    }

    /**
     * match_phrase 查询；类似 match，需要满足以下条件：
     * 1.文档的分词列表要包含所有的搜索分词列表
     * 2.搜索分词次序要和文档分词次序一致
     * 3.slop 参数控制着匹配到的文档分词最大间距，默认为0(匹配到分词要紧挨着)；slop >= 2 时不要求分词次序一致
     */
    @Test
    public void searchMatchPhrase() throws IOException {
        SearchResponse<Poet> response = client.search(searchRequestBuilder -> searchRequestBuilder
                        .index(INDEX_NAME)
                        .query(queryBuilder -> queryBuilder
                                .matchPhrase(matchPhraseQueryBuilder -> matchPhraseQueryBuilder
                                        .field("success").query("文学作家")))
                , Poet.class);
        log.info("response={}", response);

        //slop >= 2，次序不一致也可以匹配
        response = client.search(searchRequestBuilder -> searchRequestBuilder
                        .index(INDEX_NAME)
                        .query(queryBuilder -> queryBuilder
                                .matchPhrase(matchPhraseQueryBuilder -> matchPhraseQueryBuilder
                                        .field("success").query("作家文学").slop(2)))
                , Poet.class);
        log.info("response={}", response);
    }

    /**
     * match_phrase_prefix 查询；与 match_phrase 类似，最后一个分词会作为前缀来匹配，匹配的最大长度通过 max_expansions 来参数控制。
     */
    @Test
    public void searchMatchPhrasePrefix() throws IOException {
        SearchResponse<Poet> response = client.search(searchRequestBuilder -> searchRequestBuilder
                        .index(INDEX_NAME)
                        .query(queryBuilder -> queryBuilder
                                .matchPhrasePrefix(matchPhrasePrefixQueryBuilder -> matchPhrasePrefixQueryBuilder
                                        .field("success").query("文学作")))
                , Poet.class);
        log.info("response={}", response);

        //slop >= 2，次序不一致也可以匹配
        response = client.search(searchRequestBuilder -> searchRequestBuilder
                        .index(INDEX_NAME)
                        .query(queryBuilder -> queryBuilder
                                .matchPhrasePrefix(matchPhrasePrefixQueryBuilder -> matchPhrasePrefixQueryBuilder
                                        .field("success").query("作家文").slop(2).maxExpansions(50)))
                , Poet.class);
        log.info("response={}", response);
    }

    /**
     * match_all 查询，查询所有文档
     */
    @Test
    public void searchMatchAll() throws IOException {
        SearchResponse<Poet> response = client.search(searchRequestBuilder -> searchRequestBuilder
                        .index(INDEX_NAME)
                        .query(queryBuilder -> queryBuilder
                                .matchAll(matchAllQueryBuilder -> matchAllQueryBuilder))
                , Poet.class);
        log.info("response={}", response);

        //不加请求体，也是一样的效果，查询所有文档。
        response = client.search(searchRequestBuilder -> searchRequestBuilder.index(INDEX_NAME), Poet.class);
        log.info("response={}", response);
    }

    /**
     * match_none 查询，与 match_all 相反，返回 0 个文档。
     */
    @Test
    public void searchMatchNone() throws IOException {
        SearchResponse<Poet> response = client.search(searchRequestBuilder -> searchRequestBuilder
                        .index(INDEX_NAME)
                        .query(queryBuilder -> queryBuilder
                                .matchNone(matchAllQueryBuilder -> matchAllQueryBuilder))
                , Poet.class);
        log.info("response={}", response);
    }

    /**
     * query_string 查询，可以同时实现多种查询
     */
    @Test
    public void searchQueryString() throws IOException {
        //类似 match
        SearchResponse<Poet> response = client.search(searchRequestBuilder -> searchRequestBuilder
                        .index(INDEX_NAME)
                        .query(queryBuilder -> queryBuilder
                                .queryString(queryStringQueryBuilder -> queryStringQueryBuilder
                                        .defaultField("success").query("古典文学")))
                , Poet.class);
        log.info("response={}", response);

        //类似 mulit_match
        response = client.search(searchRequestBuilder -> searchRequestBuilder
                        .index(INDEX_NAME)
                        .query(queryBuilder -> queryBuilder
                                .queryString(queryStringQueryBuilder -> queryStringQueryBuilder
                                        .fields("about", "success").query("古典文学")))
                , Poet.class);
        log.info("response={}", response);

        //类似 match_phrase
        response = client.search(searchRequestBuilder -> searchRequestBuilder
                        .index(INDEX_NAME)
                        .query(queryBuilder -> queryBuilder
                                .queryString(queryStringQueryBuilder -> queryStringQueryBuilder
                                        .defaultField("success").query("\"文学作家\"")))
                , Poet.class);
        log.info(response.toString());

        //带运算符查询，运算符两边的词不再分词
        //查询同时包含 ”文学“ 和 ”伟大“ 的文档
        response = client.search(searchRequestBuilder -> searchRequestBuilder
                        .index(INDEX_NAME)
                        .query(queryBuilder -> queryBuilder
                                .queryString(queryStringQueryBuilder -> queryStringQueryBuilder
                                        .fields("success").query("文学 AND 伟大")))
                , Poet.class);
        log.info("response={}", response);

        //等同上一个查询
        response = client.search(searchRequestBuilder -> searchRequestBuilder
                        .index(INDEX_NAME)
                        .query(queryBuilder -> queryBuilder
                                .queryString(queryStringQueryBuilder -> queryStringQueryBuilder
                                        .fields("success").query("文学 伟大").defaultOperator(Operator.And)))
                , Poet.class);
        log.info("response={}", response);

        //查询 name 或 success 字段包含"文学"和"伟大"这两个单词，或者包含"李白"这个单词的文档。
        response = client.search(searchRequestBuilder -> searchRequestBuilder
                        .index(INDEX_NAME)
                        .query(queryBuilder -> queryBuilder
                                .queryString(queryStringQueryBuilder -> queryStringQueryBuilder
                                        .fields("name","success").query("(文学 AND 伟大) OR 高度")))
                , Poet.class);
        log.info("response={}", response);
    }

    /**
     * simple_query_string 查询,类似 query_string，主要区别如下：
     * 1、不支持 AND OR NOT，会当做字符处理；使用 + 代替 AND，| 代替 OR，- 代替 NOT
     * 2、会忽略错误的语法
     */
    @Test
    public void searchSimpleQueryString() throws IOException {
        //查询同时包含 ”文学“ 和 ”伟大“ 的文档
        SearchResponse<Poet> response = client.search(searchRequestBuilder -> searchRequestBuilder
                        .index(INDEX_NAME)
                        .query(queryBuilder -> queryBuilder
                                .simpleQueryString(simpleQueryStringQueryBuilder -> simpleQueryStringQueryBuilder
                                        .fields("success").query("文学 + 伟大")))
                , Poet.class);
        log.info("response={}", response);
    }

    /**
     * 模糊查询
     */
    @Test
    public void searchFuzzy() throws IOException {
        //全文查询时使用模糊参数，先分词再计算模糊选项。
        SearchResponse<Poet> response = client.search(searchRequestBuilder -> searchRequestBuilder
                        .index(INDEX_NAME)
                        .query(queryBuilder -> queryBuilder
                                .match(matchQueryBuilder -> matchQueryBuilder
                                        .field("success").query("思考").fuzziness("1")))
                , Poet.class);
        log.info(response.toString());

        //使用 fuzzy query，对输入不分词，直接计算模糊选项。
        SearchResponse<Poet> response2 = client.search(searchRequestBuilder -> searchRequestBuilder
                        .index(INDEX_NAME)
                        .query(queryBuilder -> queryBuilder
                                .fuzzy(fuzzyQueryBuilder ->  fuzzyQueryBuilder
                                        .field("success").fuzziness("1").value("理想")))
                , Poet.class);
        log.info(response2.toString());
    }

    /**
     * wildcard 查询，类似 SQL 语句中的 like；? 匹配一个字符，* 匹配多个字符
     */
    @Test
    public void searchWildcard() throws IOException {
        SearchResponse<Poet> response = client.search(searchRequestBuilder -> searchRequestBuilder
                        .index(INDEX_NAME)
                        .query(queryBuilder -> queryBuilder.wildcard(wildcardQueryBuilder -> wildcardQueryBuilder
                                .field("name")
                                .wildcard("李*")))
                , Poet.class);
        log.info(response.toString());
    }

    /**
     * bool 查询,组合查询
     */
    @Test
    public void searchBool() throws IOException {
        //查询 success 包含 “思想” 且 age 在 [20-40] 之间的文档
        SearchResponse<Poet> response = client.search(searchRequestBuilder -> searchRequestBuilder
                        .index(INDEX_NAME)
                        .query(queryBuilder -> queryBuilder
                                .bool(boolQueryBuilder -> boolQueryBuilder
                                        .must(queryBuilder2 -> queryBuilder2
                                                .match(matchQueryBuilder -> matchQueryBuilder
                                                        .field("success").query("思想"))
                                        )
                                        .must(queryBuilder2 -> queryBuilder2
                                                .range(rangeQueryBuilder -> rangeQueryBuilder
                                                        .field("age").gte(JsonData.of("20")).lt(JsonData.of("40")))
                                        )
                                )
                        )
                , Poet.class);
        log.info(response.toString());

        //过滤出 success 包含 “思想” 且 age 在 [20-40] 之间的文档，不计算得分
        SearchResponse<Poet> response2 = client.search(searchRequestBuilder -> searchRequestBuilder
                        .index(INDEX_NAME)
                        .query(queryBuilder -> queryBuilder
                                .bool(boolQueryBuilder -> boolQueryBuilder
                                        .filter(queryBuilder2 -> queryBuilder2
                                                .match(matchQueryBuilder -> matchQueryBuilder
                                                        .field("success").query("思想"))
                                        )
                                        .filter(queryBuilder2 -> queryBuilder2
                                                .range(rangeQueryBuilder -> rangeQueryBuilder
                                                        .field("age").gte(JsonData.of("20")).lt(JsonData.of("40")))
                                        )
                                )
                        )
                , Poet.class);
        log.info(response2.toString());
    }

    /**
     * aggs 查询，聚合查询
     */
    @Test
    public void searchAggs() throws IOException {
        //求和，类似 select sum(age) from poet-index
        SearchResponse response = client.search(searchRequestBuilder -> searchRequestBuilder
                        .index(INDEX_NAME)
                        .aggregations("age_sum", aggregationBuilder -> aggregationBuilder
                                .sum(sumAggregationBuilder -> sumAggregationBuilder
                                        .field("age")))
                , Poet.class);
        log.info(response.toString());

        //类似 select count distinct(age) from poet-index
        response = client.search(searchRequestBuilder -> searchRequestBuilder
                        .index(INDEX_NAME)
                        .aggregations("age_count", aggregationBuilder -> aggregationBuilder
                                .cardinality(cardinalityAggregationBuilder -> cardinalityAggregationBuilder.field("age")))
                , Poet.class);
        log.info(response.toString());

        //数量、最大、最小、平均、求和
        response = client.search(searchRequestBuilder -> searchRequestBuilder
                        .index(INDEX_NAME)
                        .aggregations("age_stats", aggregationBuilder -> aggregationBuilder
                                .stats(statsAggregationBuilder -> statsAggregationBuilder
                                        .field("age")))
                , Poet.class);
        log.info(response.toString());

        //select name,count(*) from poet-index group by name
        response = client.search(searchRequestBuilder -> searchRequestBuilder
                        .index(INDEX_NAME)
                        .aggregations("name_terms", aggregationBuilder -> aggregationBuilder
                                .terms(termsAggregationBuilder -> termsAggregationBuilder
                                        .field("name")))
                , Map.class);
        log.info(response.toString());

        //select name,age,count(*) from poet-index group by name,age
        response = client.search(searchRequestBuilder -> searchRequestBuilder
                        .index(INDEX_NAME)
                        .aggregations("name_terms", aggregationBuilder -> aggregationBuilder
                                .terms(termsAggregationBuilder -> termsAggregationBuilder
                                        .field("name")
                                )
                                .aggregations("age_terms", aggregationBuilder2 -> aggregationBuilder2
                                        .terms(termsAggregationBuilder -> termsAggregationBuilder
                                                .field("age")
                                        ))
                        )
                , Poet.class);
        log.info(response.toString());

        //类似 select avg(age) from poet-index where name='李白'
        response = client.search(searchRequestBuilder -> searchRequestBuilder
                        .index(INDEX_NAME)
                        .query(queryBuilder -> queryBuilder
                                .bool(boolQueryBuilder -> boolQueryBuilder
                                        .filter(queryBuilder2 -> queryBuilder2
                                                .term(termQueryBuilder -> termQueryBuilder
                                                        .field("name").value("李白")))))
                        .aggregations("ave_age", aggregationBuilder -> aggregationBuilder
                                .avg(averageAggregationBuilder -> averageAggregationBuilder.field("age")))
                , Poet.class);
        log.info(response.toString());
    }

    /**
     * suggest 查询，推荐搜索
     */
    @Test
    public void searchSuggest() throws IOException {
        SearchResponse<Poet> response = client.search(searchRequestBuilder -> searchRequestBuilder
                        .index(INDEX_NAME)
                        .suggest(suggesterBuilder -> suggesterBuilder
                                .suggesters("success_suggest", fieldSuggesterBuilder -> fieldSuggesterBuilder
                                        .text("思考")
                                        .term(termSuggesterBuilder -> termSuggesterBuilder
                                                .field("success")
                                                .suggestMode(SuggestMode.Always)
                                                .minWordLength(2)
                                        )
                                )
                        )
                , Poet.class);
        log.info(response.toString());
    }

    /**
     * 高亮显示
     */
    @Test
    public void searchHighlight() throws IOException {
        SearchResponse<Poet> response = client.search(searchRequestBuilder -> searchRequestBuilder
                        .index(INDEX_NAME)
                        .query(queryBuilder -> queryBuilder
                                .match(matchQueryBuilder -> matchQueryBuilder
                                        .field("success").query("思想艺术")))
                        .highlight(highlightBuilder -> highlightBuilder
                                .preTags("<span color='red'>")
                                .postTags("</span>")
                                .fields("success", highlightFieldBuilder -> highlightFieldBuilder))
                , Poet.class);
        log.info(response.toString());
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    static class Poet {
        private Integer age;
        private String name;
        private String poems;
        private String about;
        /**成就*/
        private String success;
    }
}
