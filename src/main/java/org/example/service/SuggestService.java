package org.example.service;

import org.example.vo.TermSuggestVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.LinkedList;
import java.util.List;

@Service
public class SuggestService {
    @Autowired
    CompletionSuggesterFix completionSuggesterFix;
    //纠错索引名称
    final String termIndex = "term_index";
    //纠错字段名称
    final String termField = "name";

    //前缀补全索引名称
    final String completionIndex = "completion_index";
    //前缀补全字段名称
    final String completionField = "title_suggest";

    //中缀补全索引名称
    final String infixIndex = "infix_index";
    //中缀补全字段名称
    final String infixField = "title";
    //对输入的查询词,先进行前缀匹配,如果前缀匹配有结果,则进行中缀匹配后返回,如果前缀匹配没有结果,则进行纠错匹配,将纠错匹配的
    //结果返回


    public List<String> suggest(String text) throws Exception {
        //提示结果列表
        List<String> suggestList = new LinkedList<>();
        //调用completionSuggesterFix的termSuggest方法
        List<TermSuggestVO> termSuggest = completionSuggesterFix.completionSuggest(completionIndex, completionField, text);
        //判空
        if (termSuggest != null) {
            System.out.print("前缀补全结果:");
            for(TermSuggestVO termSuggestVO:termSuggest){
                System.out.println(termSuggestVO.getText()+" ");
                suggestList.add(termSuggestVO.getText());
            }
        }else{
            //进行拼音纠错匹配
            List<TermSuggestVO> infixSuggest = completionSuggesterFix.pinyinSuggest(termIndex,termField,null, text);
            //如果中缀匹配的结果不为空,则把中缀匹配的结果写入返回列表中
            if (infixSuggest != null) {
                System.out.println("拼音纠错匹配结果 1:");
               for(TermSuggestVO termSuggestVO:infixSuggest){
                     System.out.print(termSuggestVO.getText()+" ");
                   suggestList.add(termSuggestVO.getText());
               }

            }else{//如果为空,则进行纠错匹配
                //调用completionSuggesterFix的termSuggest方法
                termSuggest = completionSuggesterFix.termSuggestDoc(termIndex, termField, text);
                //判空,如果纠错匹配的结果仍为空,则返回
                if (termSuggest != null&&!termSuggest.isEmpty()) {

                    //将纠错匹配的结果进行拼音中缀匹配
                    List<String>termString = termSuggest.stream().map(TermSuggestVO::getText).toList();
                    System.out.println("纠错补全结果:");
                    for(TermSuggestVO termSuggestVO:termSuggest){
                        System.out.println(termSuggestVO.getText()+" ");
                    }
                    System.out.println(" ");
                    List<TermSuggestVO> termSuggestVOList = completionSuggesterFix.pinyinSuggest(termIndex,termField,null, termString);
                    //如果中缀匹配的结果不为空,则把中缀匹配的结果写入返回列表中
                    if (termSuggestVOList != null) {
                        System.out.println("中缀补全结果:");
                        for(TermSuggestVO termSuggestVO:termSuggestVOList){
                            System.out.print(termSuggestVO.getText()+" ");
                            suggestList.add(termSuggestVO.getText());
                        }
                        System.out.println(" ");
                    }
                }
            }
        }
        //如果纠错结果为空,则直接用原始提示词查询提问库,然后返回
        if(suggestList.size() == 0){
            List<TermSuggestVO> infixSuggest = completionSuggesterFix.infixSuggest(infixIndex, infixField, text);
            if (infixSuggest != null) {
                System.out.print("中缀补全结果 1:");
                for(TermSuggestVO termSuggestVO:infixSuggest){
                    System.out.print(termSuggestVO.getText()+" ");
                    suggestList.add(termSuggestVO.getText());
                }
                System.out.println(" ");
                return suggestList;
            }
            return null;
        }
        //对补全的结果进行中缀匹配
        List<TermSuggestVO> infixSuggest = completionSuggesterFix.infixSuggest(infixIndex, infixField, suggestList);
        //如果中缀匹配的结果不为空,则把中缀匹配的结果写入返回列表中
        if (infixSuggest != null) {
            System.out.print("中缀补全结果 2:");
            for(TermSuggestVO termSuggestVO:infixSuggest){
                System.out.print(termSuggestVO.getText()+" ");
                suggestList.add(termSuggestVO.getText());
            }
            System.out.println(" ");
        }
        //去重
        suggestList = suggestList.stream().distinct().toList();
        //将中缀匹配的结果写入返回列表中
        return suggestList;
    }
}
