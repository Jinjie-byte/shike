package com.shike.service;

import com.shike.dto.SetmealDTO;
import com.shike.dto.SetmealPageQueryDTO;
import com.shike.entity.Setmeal;
import com.shike.result.PageResult;
import com.shike.vo.AiSetmealVO;
import com.shike.vo.DishItemVO;
import com.shike.vo.SetmealVO;
import java.util.List;

public interface SetmealService {

//    /**
//     * 条件查询
//     * @param setmeal
//     * @return
//     */
//    List<Setmeal> list(Setmeal setmeal);
    void saveWithDish(SetmealDTO setmealDTO);

    PageResult pageQuery(SetmealPageQueryDTO setmealPageQueryDTO);

    void deleteBatch(List<Long> ids);

    SetmealVO getByIdWithDish(Long id);

    void update(SetmealDTO setmealDTO);

    void startOrStop(Integer status, Long id);
    /**
     * 条件查询
     * @param setmeal
     * @return
     */
    List<Setmeal> list(Setmeal setmeal);

    /**
     * 根据id查询菜品选项
     * @param id
     * @return
     */
    List<DishItemVO> getDishItemById(Long id);

    /**
     * 给 AI 用的套餐列表（只含起售套餐 + 内含菜品）。
     *
     * @return 精简套餐集合
     */
    List<AiSetmealVO> listForAi();

}
