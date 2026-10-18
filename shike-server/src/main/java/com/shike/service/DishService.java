package com.shike.service;

import com.shike.dto.DishDTO;
import com.shike.dto.DishPageQueryDTO;
import com.shike.entity.Dish;
import com.shike.result.PageResult;
import com.shike.vo.AiDishVO;
import com.shike.vo.DishVO;

import java.math.BigDecimal;
import java.util.List;

public interface DishService {
    void saveWithFlavor(DishDTO dishDTO);

    PageResult pageQuery(DishPageQueryDTO dishPageQueryDTO);

    void deleteBatch(List<Long> ids);

    DishVO getByIdWithFlavor(Long id);

    void updateWithFlavor(DishDTO dishDTO);

    void startOrStop(Integer status, Long id);

    List<Dish> list(Long categoryId);

    List<DishVO> listWithFlavor(Dish dish);

    /**
     * 【课 2】给 AI 用的菜品搜索。
     *   只返回起售的菜；keyword / maxPrice 传 null 表示该维度不限。
     */
    List<AiDishVO> listForAi(String keyword, BigDecimal maxPrice);
}
