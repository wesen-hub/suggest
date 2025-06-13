package org.example.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
// 声明 Document 类
public class TermSuggestDocPO {
    private String id;
    private String name;
    private Integer age;
}
