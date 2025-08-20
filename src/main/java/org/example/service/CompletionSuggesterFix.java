package org.example.service;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SuggestMode;
import co.elastic.clients.elasticsearch._types.query_dsl.*;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.CompletionSuggestOption;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.Suggestion;
import org.example.vo.TermSuggestVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class CompletionSuggesterFix {
    @Autowired
    ElasticsearchClient client;


    //搜索自动补全建议
    //对输入的查询词,先进行前缀匹配,如果前缀匹配有结果,则进行中缀匹配后返回,如果前缀匹配没有结果,则进行纠错匹配,将纠错匹配的

    public List<TermSuggestVO> completionSuggest(String index, String field, String text) throws IOException {
        SearchRequest request1 = SearchRequest.of(sr -> sr
                .index(index)
                .suggest(sb -> sb
                        .suggesters("completion_suggestion", csb -> csb
                                .prefix(text)
                                .completion(c -> c
                                        .field(field)
                                        .size(5)
                                        .fuzzy(fb -> fb.fuzziness("0"))  // 整型参数
                                        .skipDuplicates(true)
                                )
                        )
                )
        );
        SearchResponse<?> response = null;
        try {
            System.out.println("前缀匹配DSL:  ");
            response = client.search(request1, Object.class);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
        if (response.suggest().get("completion_suggestion").stream().flatMap(entry -> entry.completion().options().stream()).collect(Collectors.toSet()).isEmpty()) {
            return null;
        }
        return parseSuggest(response);
    }
    //解析response
    public List<TermSuggestVO> parseSuggest(SearchResponse<?> response) {
        List<TermSuggestVO> resultList = new ArrayList<>();
        List<? extends Suggestion<?>> suggestions = response.suggest().get("completion_suggestion");
        if (suggestions != null) {
            for (Suggestion<?> suggestion : suggestions) {
                List<? extends CompletionSuggestOption<?>> options = suggestion.completion().options();
                for (CompletionSuggestOption option : options) {
                    String inputFirst = ((List<String>)((Map<String,Object>)((LinkedHashMap<String,Object>)option.source()).get("title_suggest")).get("input")).get(0);
                    TermSuggestVO termSuggestVO = new TermSuggestVO(option.score(), inputFirst);
                    resultList.add(termSuggestVO);
                }
            }
        }
        // 5. 按 score 降序排序
        return resultList.stream()
                .sorted((o1, o2) -> Double.compare(o2.getScore(), o1.getScore()))
                .collect(Collectors.toList());
    }

    public List<TermSuggestVO> termSuggestDoc(String index, String field, String text) throws Exception {
        SearchRequest request = SearchRequest.of(s -> s
                .index(index)
                .suggest(sb -> sb
                        .suggesters("chinese_suggest", tsb -> tsb
                                .text(text)
                                .term(t -> t
                                        .field(field)
                                        .suggestMode(SuggestMode.Missing)  // 对应 suggest_mode: "always"
                                        .prefixLength(1)                  // 设置 prefix_length: 0
                                        .minWordLength(1)                // 设置 min_word_length: 1
                                )
                        )
                )
        );
        SearchResponse<?> response = client.search(request, Object.class);
        if (response.suggest() == null) {
            return null;
        }
        return response.suggest().get("chinese_suggest").stream()
                .flatMap(entry -> entry.term().options().stream())
                .sorted((o1, o2) -> Double.compare(o2.score(), o1.score()))
                .map(option -> new TermSuggestVO(option.score(), option.text()))
                .collect(Collectors.toList());
    }

    // 示例：拼音查询建议
    public List<TermSuggestVO> pinyinSuggest(String index, String field, String subField,String text)  {

        SearchRequest request = SearchRequest.of(s -> s
                .index(index)
                .query(q -> q
                        .multiMatch(m -> m
                                .query(text)                // 搜索关键词
                                .fields(List.of(field+ (StringUtils.isEmpty(subField) ?"":("."+subField))))       // 匹配字段列表
                                .type(TextQueryType.BestFields)  // 匹配模式
                        )
                )
        );
        SearchResponse<Object> response = null;
        try {
            response = client.search(request, Object.class);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
        //声明一个List集合
        List<TermSuggestVO> termSuggestVOList = new ArrayList<>();
        //判空
        if (response.hits().total()==null||response.hits().total().value() == 0L) {
            return null;
        }
        double maxScore = response.hits().maxScore();
        for (Hit<Object> hit : response.hits().hits()) {
            double score = hit.score();
            //保留得分大于一半的结果的前五个
            //ToDo 评分过滤规则优化
            if (score < maxScore / 2 || termSuggestVOList.size() >= 5) {
                break;
            }
                //获取文档中的字段值
                LinkedHashMap<String, Object> source = (LinkedHashMap<String, Object>) hit.source();
                TermSuggestVO termSuggestVO = new TermSuggestVO();
                //如果source.get(field)是list<String>类型
            try{
                String res=(String)source.get(field);
                termSuggestVO.setText(res);
            }catch (Exception e){
                termSuggestVO.setText(((List<?>) source.get(field)).get(0).toString());
            }
            //设置得分
            termSuggestVO.setScore(score);
            //添加到集合中
            termSuggestVOList.add(termSuggestVO);
        }
        //按得分降序排序,并返回
        return termSuggestVOList.stream().sorted((o1, o2) -> Double.compare(o2.getScore(), o1.getScore())).collect(Collectors.toList());
    }
    //拼音查询,接受多个词查询
    //ToDo 最长不超过十个
    public List<TermSuggestVO> pinyinSuggest(String index, String field, String subField, List<String> text) throws Exception {
        //循环构建query
        List<Query>queries=new ArrayList<>();
        for(String word:text){
            Query query1 = QueryBuilders.multiMatch()
                    .query(word)
                    .fields(List.of(field+ (subField==null?"":"."+subField)))
                    .type(TextQueryType.BestFields)
                    .build()._toQuery();
            queries.add(query1);
        }

        // 2. 组合 Bool 查询
        Query boolQuery = BoolQuery.of(b -> b
                .should(queries)
                .minimumShouldMatch("1")
        )._toQuery();
        // 3. 构造 SearchRequest
        SearchRequest request = SearchRequest.of(s -> s
                .index(index)
                .query(boolQuery)
        );
        SearchResponse<Object> response = client.search(request, Object.class);

        //声明一个List集合
        List<TermSuggestVO> termSuggestVOList = new ArrayList<>();
        //判空
        if (response.hits().total()==null||response.hits().total().value() == 0L) {
            return null;
        }
        for (Hit<Object> hit : response.hits().hits()) {
            //获取文档中的字段值
            LinkedHashMap<String, Object> source = (LinkedHashMap<String, Object>) hit.source();
            TermSuggestVO termSuggestVO = new TermSuggestVO();
            //如果source.get(field)是list<String>类型
            try{
                String res=(String)source.get(field);
                termSuggestVO.setText(res);
            }catch (Exception e){
                termSuggestVO.setText(((List<?>) source.get(field)).get(0).toString());
            }
            //设置得分
            termSuggestVO.setScore(hit.score());
            //添加到集合中
            termSuggestVOList.add(termSuggestVO);

        }
        //按得分降序排序,并返回
        return termSuggestVOList.stream().sorted((o1, o2) -> Double.compare(o2.getScore(), o1.getScore())).collect(Collectors.toList());

    }
    // 中缀查询
    public List<TermSuggestVO> infixSuggest(String index, String field, String text) throws Exception {
        // 2. 构建 match_bool_prefix 查询
        MatchBoolPrefixQuery matchBoolPrefixQuery = MatchBoolPrefixQuery.of(m -> m
                .field(field)
                .query(text)
        );

        // 3. 构造 SearchRequest
        SearchRequest searchRequest = SearchRequest.of(s -> s
                .index(index)
                .query(q -> q.matchBoolPrefix(matchBoolPrefixQuery))
        );

        SearchResponse<Object> response = client.search(searchRequest, Object.class);

        //声明一个List集合
        List<TermSuggestVO> termSuggestVOList = new ArrayList<>();
        //判空
        if (response.hits().total()==null||response.hits().total().value() == 0L) {
            return null;
        }
        for (Hit<Object> hit : response.hits().hits()) {
            //获取文档中的字段值
            LinkedHashMap<String, Object> source = (LinkedHashMap<String, Object>) hit.source();
            TermSuggestVO termSuggestVO = new TermSuggestVO();
            //如果source.get(field)是list<String>类型
            try{
                String res=(String)source.get(field);
                termSuggestVO.setText(res);
            }catch (Exception e){
                termSuggestVO.setText(((List<?>) source.get(field)).get(0).toString());
            }
            //设置得分
            termSuggestVO.setScore(hit.score());
            //添加到集合中
            termSuggestVOList.add(termSuggestVO);
        }
        //按得分降序排序,并返回
        return termSuggestVOList.stream().sorted((o1, o2) -> Double.compare(o2.getScore(), o1.getScore())).collect(Collectors.toList());
    }
    //中缀查询,接受多个词查询
    //ToDo 最长不超过十个
    public List<TermSuggestVO> infixSuggest(String index, String field, List<String> text) throws Exception {
        //循环构建query
        List<Query>queries=new ArrayList<>();
        for(String word:text){
            Query query1 = QueryBuilders.matchBoolPrefix()
                    .field(field)
                    .query(word)
                    .build()._toQuery();
            queries.add(query1);
        }
        // 组合为 bool.should 查询
        BoolQuery boolQuery = QueryBuilders.bool()
                .should(queries)
                .build();
        SearchRequest request = SearchRequest.of(s -> s
                .index(index)
                .query(boolQuery._toQuery())
        );
        SearchResponse<Object> response = client.search(request, Object.class);
        //声明一个List集合
        List<TermSuggestVO> termSuggestVOList = new ArrayList<>();
        //判空
        if (response.hits().total()==null||response.hits().total().value() == 0L) {
            return null;
        }
        for (Hit<Object> hit : response.hits().hits()) {
            //获取文档中的字段值
            LinkedHashMap<String, Object> source = (LinkedHashMap<String, Object>) hit.source();
            TermSuggestVO termSuggestVO = new TermSuggestVO();
            //如果source.get(field)是list<String>类型
            try{
                String res=(String)source.get(field);
                termSuggestVO.setText(res);
            }catch (Exception e){
                termSuggestVO.setText(((List<?>) source.get(field)).get(0).toString());
            }
            //设置得分
            termSuggestVO.setScore(hit.score());
            //添加到集合中
            termSuggestVOList.add(termSuggestVO);
        }

        //按得分降序排序,并返回
        return termSuggestVOList.stream().sorted((o1, o2) -> Double.compare(o2.getScore(), o1.getScore())).collect(Collectors.toList());
    }
}
