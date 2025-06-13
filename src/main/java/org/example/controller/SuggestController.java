package org.example.controller;

import org.example.service.CompletionSuggesterFix;
import org.example.service.SuggestService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;
@RestController
@RequestMapping("/suggest")
public class SuggestController {
    @Autowired
    CompletionSuggesterFix completionSuggesterFix;
    @Autowired
    SuggestService suggestService;
    final String termIndex = "products9";
    final String termField = "name";
    final String completionIndex = "products5";
    final String completionField = "title_suggest";
    //中缀补全索引名称
    final String infixIndex = "chinese_search_demo5";
    //中缀补全字段名称
    final String infixField = "title";
    //接受查询词,返回提示列表
    @RequestMapping("/suggest")
    public List<String> suggest( String text) throws Exception {
       return suggestService.suggest(text);
    }
}
