package org.example.task;

import jakarta.annotation.PostConstruct;
import org.example.service.DocService;
import org.example.service.ProductIndexService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/*
* 每次服务启动同步词库到索引
* */
@Component
public class SyncWortask {
    @Autowired
    DocService docService;
    @Autowired
    ProductIndexService productIndexService;

    @Value("${productIndex.filePath}")
    private String product_docPath;

    @Value("${completionIndex.filePath}")
    private String docPath;
    @PostConstruct
    public void sync(){
        try {
            System.out.println("删除索引中的旧数据...");
            productIndexService.deleteAllDocuments("products9");
            productIndexService.deleteAllDocuments("products5");
            System.out.println("开始同步词库到索引...");
            productIndexService.termIndexWordsFromFile(product_docPath);
            docService.bulkInsertFromFile(docPath);
            System.out.println("词库同步完成");
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("词库同步失败: " + e.getMessage());
        }
    }
    //
}
