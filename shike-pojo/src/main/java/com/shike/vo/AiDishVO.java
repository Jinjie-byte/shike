package com.shike.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 给 AI 用的【精简】菜品。
 *
 * 为什么不直接复用已有的 DishVO？
 *   那个是"给人看的"（管理端表格要显示图片、上下架状态、更新时间……），字段一大堆。
 *   模型只需要"能判断这道菜是不是用户想要"的那几个字段。
 *   字段越少 → 喂给模型的 token 越少 → 更便宜、也更快。
 *
 * 每个字段为什么留：
 *   id           模型将来要用它"加购物车"，必须给
 *   name         菜名
 *   price        价格，用户最爱按这个筛（"30 块以内"）
 *   description  ★ 最关键。口味、食材都在这里（"原料：鲢鱼，剁椒"），
 *                模型判断"辣不辣"全靠它 —— 你的菜名里一个"辣"字都没有
 *   categoryName 给模型一张"菜单地图"：看到"蜀味 / 水煮"就知道是川菜
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiDishVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private String name;

    private BigDecimal price;

    // 描述信息（口味、食材）
    private String description;

    // 分类名称
    private String categoryName;
}
