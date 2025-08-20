package org.example.controller;

import org.example.service.DocService;
import org.example.service.TermIndexService;
import org.example.service.SearchTermService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/doc")
public class DocController {

    @Autowired
    TermIndexService termIndexService;
    @Autowired
    DocService docService;

    @Autowired
    SearchTermService searchTermService;
    //添加数据
    @RequestMapping("/addTerm")
    public String insertProduct(String filePath) {
        try{
            termIndexService.termIndexWordsFromFile(filePath);
        }catch (Exception e){
            e.printStackTrace();
            return "add product failed";
        }
        return "add product success";
    }

    //添加completionindex 数据
    @RequestMapping("/addCompletion")
    public String insertCompletionProduct(String filePath) {
        try{
            docService.bulkInsertFromFile(filePath);
        }catch (Exception e){
            e.printStackTrace();
            return "add completion failed";
        }
        return "add completion success";
    }

    //添加中缀补全数据
    @RequestMapping("/addInfix")
    public String insertInfixProduct(String term) {
            try{
                searchTermService.upsertTermWeight(term);
                return "add product success";
            }catch (Exception e){
                e.printStackTrace();
                return e.getMessage();
            }
    }

    //删除数据
    @RequestMapping("/delete")
    public String deleteProduct(String index) {
        try{
            termIndexService.deleteAllDocuments(index);
        }catch (Exception e){
            e.printStackTrace();
            return "delete product failed";
        }
        return "delete product success";
    }
}
