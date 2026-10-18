package com.shike.mapper;

import com.github.pagehelper.Page;
import com.shike.annotation.AutoFill;
import com.shike.dto.DishPageQueryDTO;
import com.shike.entity.Dish;
import com.shike.enumeration.OperationType;
import com.shike.vo.AiDishVO;
import com.shike.vo.DishVO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Mapper
public interface DishMapper {

    /**
     * 根据分类id查询菜品数量
     * @param categoryId
     * @return
     */
    @Select("select count(id) from dish where category_id = #{categoryId}")
    Integer countByCategoryId(Long categoryId);

    /**
     * 插入菜品数据
     * @param dish
     */
    @AutoFill(value = OperationType.INSERT)
    void insert(Dish dish);

    Page<DishVO> pageQuery(DishPageQueryDTO dishPageQueryDTO);

    void deleteByIds(List<Long> ids);

    @Select("select * from dish where id = #{id}")
    Dish getById(Long id);


    /**
     * 更新菜品数据
     * @param dish
     */
    @AutoFill(value = OperationType.UPDATE)
    void update(Dish dish);

    List<Dish> list(Dish dish);

    @Select("select a.* from dish a left join setmeal_dish b on a.id = b.dish_id where b.setmeal_id = #{setmealId}")
    List<Dish> getBySetmealId(Long id);

    /**
     * 根据条件统计菜品数量
     * @param map
     * @return
     */
    Integer countByMap(Map map);

    /**
     * 【课 2】给 AI 用的菜品搜索。
     *
     * 和已有的 list(Dish) 有什么不同：
     *   - 只查【起售】的菜（status = 1 写死在 SQL 里，不给调用方传）
     *   - 价格是"上限"过滤（price <= maxPrice），不是精确匹配
     *   - 关键词要同时在 name 和 description 里找
     *     ★ 这条是关键：你的菜名里一个"辣"字都没有，口味全藏在 description 里
     *   - 要带出 categoryName（所以 SQL 里得 join category 表）
     *
     * @param keyword  关键词，可为 null（null = 该维度不限）
     * @param maxPrice 价格上限，可为 null（null = 该维度不限）
     */
    List<AiDishVO> listForAi(@Param("keyword") String keyword,
                               @Param("maxPrice") BigDecimal maxPrice);
}
