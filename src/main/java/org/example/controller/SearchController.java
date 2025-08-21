package org.example.controller;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import org.example.service.SearchService;
import org.example.vo.TermSuggestVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/search")
public class SearchController {
    @Autowired
    ElasticsearchClient esClient;
    @Autowired
    SearchService searchService;

    // 示例：查询索引信息
    @GetMapping("/indices")
    public List<String> listIndices() throws IOException {
        return esClient.indices().get(b -> b.index("*"))
                .result().keySet().stream().toList();
    }

    //termSuggest方法
    // 示例：查询自动补全建议
    @PostMapping ("/completionSuggest")
    public List<String> termSuggest( String field, String text) throws Exception {
        //调用completionSuggesterFix的termSuggest方法
        List<TermSuggestVO> termSuggest = searchService.completionSuggest("completion_index", field, text);

        //判空
        if (termSuggest == null) {
            return null;
        }
        //遍历termSuggest，打印文本和得分
        termSuggest.forEach(termSuggestVO -> {
            System.out.println("text: " + termSuggestVO.getText() + ", score: " + termSuggestVO.getScore());
        });
        //返回文本列表
        return termSuggest.stream().map(TermSuggestVO::getText).toList();

    }

    // 示例：查询纠错建议
    @PostMapping ("/termSuggestDoc")
    public List<String> termSuggestDoc( String field, String text) throws Exception {
        //调用completionSuggesterFix的termSuggestDoc方法
        List<TermSuggestVO> termSuggest = searchService.termSuggestDoc("term_index", field, text);

        //判空
        if (termSuggest == null) {
            return null;
        }
        //遍历termSuggest，打印文本和得分
        termSuggest.forEach(termSuggestVO -> {
            System.out.println("text: " + termSuggestVO.getText() + ", score: " + termSuggestVO.getScore());
        });
        //返回文本列表
        return termSuggest.stream().map(TermSuggestVO::getText).toList();

    }


    // 示例：拼音查询建议
    @PostMapping ("/pinyinSuggest")
    public List<String> pinyinSuggest( String field,String subField, String text) throws Exception {
        //调用completionSuggesterFix的pinyinSuggest方法
        List<TermSuggestVO> termSuggest = searchService.pinyinSuggest("term_index",field,subField, text);

        //判空
        if (termSuggest == null) {
            return null;
        }
        //打印原始文本
        System.out.println("text: " + text);
        //遍历termSuggest，打印文本和得分
        termSuggest.forEach(termSuggestVO -> {
            System.out.println("suggest: " + termSuggestVO.getText() + ", score: " + termSuggestVO.getScore());
        });
        //返回文本列表
        return termSuggest.stream().map(TermSuggestVO::getText).toList();

    }

    //中缀查询
    @PostMapping ("/infixSuggest")
    public List<String> infixSuggest( String field, String text) throws Exception {
        //调用completionSuggesterFix的infixSuggest方法
        List<TermSuggestVO> termSuggest = searchService.infixSuggest("infix_index", field, text);

        //判空
        if (termSuggest == null) {
            return null;
        }
        //遍历termSuggest，打印文本和得分
        termSuggest.forEach(termSuggestVO -> {
            System.out.println("text: " + termSuggestVO.getText() + ", score: " + termSuggestVO.getScore());
        });
        //返回文本列表
        return termSuggest.stream().map(TermSuggestVO::getText).toList();

    }
}
