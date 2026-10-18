package com.shike.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * AI 菜单：菜品 + 套餐，一次返回。
 *
 * 为什么把两者合在一个对象里，而不是开两个接口：
 *   模型判断"该推荐菜还是套餐"必须同时看到两者。
 *   分两个接口就要调两次、两次之间还可能漏掉一次 —— 工具越少，模型越稳。
 *
 * 对应 Python 侧 get_menu() 的返回值，结构一一对应，直接透传不用转换。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiMenuVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private List<AiDishVO> dishes;

    private List<AiSetmealVO> setmeals;
}
