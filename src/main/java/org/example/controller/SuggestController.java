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
    //接受查询词,返回提示列表
    @RequestMapping("/suggest")
    public List<String> suggest( String text) throws Exception {
       return suggestService.suggest(text);
    }
}
