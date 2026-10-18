package com.shike.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 给 AI 用的【精简】套餐。
 *
 * 为什么要单独做：用户会问"套餐里有什么菜"。如果只给套餐名和价格，
 * 模型答不出"里面包含什么"，只能瞎猜 —— 这就是幻觉的来源。
 * 所以这里把 setmeal_dish 关联的菜品名和份数一并带上。
 *
 * 为什么 items 里只留 name / copies：
 *   图片 URL、菜品 id 对"回答套餐内容"这个场景没用，纯占 token。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiSetmealVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private String name;

    private BigDecimal price;

    private String description;

    /** 套餐里包含的菜品 */
    private List<Item> items;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Item implements Serializable {

        private static final long serialVersionUID = 1L;

        private String name;

        /** 份数 */
        private Integer copies;
    }
}
