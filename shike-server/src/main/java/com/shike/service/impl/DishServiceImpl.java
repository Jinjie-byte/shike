package com.shike.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.shike.constant.MessageConstant;
import com.shike.constant.StatusConstant;
import com.shike.dto.DishDTO;
import com.shike.dto.DishPageQueryDTO;
import com.shike.entity.Dish;
import com.shike.entity.DishFlavor;
import com.shike.entity.Setmeal;
import com.shike.exception.DeletionNotAllowedException;
import com.shike.mapper.*;
import com.shike.result.PageResult;
import com.shike.service.DishService;
import com.shike.vo.AiDishVO;
import com.shike.vo.DishVO;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
public class DishServiceImpl  implements DishService {
    @Autowired
    private SetmealMapper setmealMapper;
    @Autowired
    private DishFlavorMapper dishFlavorMapper;
    @Autowired
    private DishMapper dishMapper;
    @Autowired
    private SetmealDishMapper setmealDishMapper;
    @Override
    @Transactional
    public void saveWithFlavor(DishDTO dishDTO) {
        //菜品表插入数据
        Dish dish = new Dish();
        BeanUtils.copyProperties(dishDTO,dish);
        dishMapper.insert(dish);

        Long dishId = dish.getId();
        //口味表插入多条数据
        List<DishFlavor> flavors = dishDTO.getFlavors();
        if(flavors!= null && flavors.size() > 0){
            flavors.forEach(dishFlavor -> {
                dishFlavor.setDishId(dishId);
            });
            // 向口味表插入数据,批量插入
            dishFlavorMapper.insertBatch(flavors);
        }
    }

    @Override
    public PageResult pageQuery(DishPageQueryDTO dishPageQueryDTO) {
        PageHelper.startPage(dishPageQueryDTO.getPage(), dishPageQueryDTO.getPageSize());
        Page<DishVO> page = dishMapper.pageQuery(dishPageQueryDTO);
        return new PageResult(page.getTotal(), page.getResult());
    }

    @Override
    @Transactional
    public void deleteBatch(List<Long> ids) {
        //判断菜品是否能够被删除--是否存在起售的商品
        for (Long id : ids) {
            Dish dish = dishMapper.getById(id);
            if (StatusConstant.ENABLE == dish.getStatus()) {  //菜品起售中不能删除
                //起售中的菜品不能删除
                throw new DeletionNotAllowedException(MessageConstant.DISH_ON_SALE);
            }
        }
        //删除当前菜品是否能够被删除--是否存在套餐关联
        List<Long> setmealDishIds = setmealDishMapper.getSetmealIdsByDishIds(ids);
        if (setmealDishIds!= null && setmealDishIds.size() > 0) {  //存在套餐关联
            //当前套餐关联的菜品 不能删除
            throw new DeletionNotAllowedException(MessageConstant.DISH_BE_RELATED_BY_SETMEAL);
        }
        //删除菜品数据
        dishMapper.deleteByIds(ids);
        //删除口味数据,菜品和口味是多对多的关系，所以要删除菜品和口味的关联数据
        dishFlavorMapper.deleteByDishIds(ids);
    }

    @Override
    public DishVO getByIdWithFlavor(Long id) {
        Dish dish = dishMapper.getById(id);
        List<DishFlavor> dishFlavors = dishFlavorMapper.getByDishId(id);
        //组装数据,创建DishVO对象,并设置口味数据
        DishVO dishVO = new DishVO();
        BeanUtils.copyProperties(dish, dishVO);
        dishVO.setFlavors(dishFlavors);

        return dishVO;
    }

    @Override
    public void updateWithFlavor(DishDTO dishDTO) {
        //更新菜品表数据
        Dish dish = new Dish();
        BeanUtils.copyProperties(dishDTO, dish);
        dishMapper.update(dish);

        //先删除原有口味数据
        dishFlavorMapper.deleteByDishId(dish.getId());
        //更新口味表数据

        List<DishFlavor> flavors = dishDTO.getFlavors();
        if (flavors != null && flavors.size() > 0) {
            //再插入新口味数据
            flavors.forEach(dishFlavor -> {
                dishFlavor.setDishId(dish.getId());
            });
            dishFlavorMapper.insertBatch(flavors);
        }
    }

    @Override
    public void startOrStop(Integer status, Long id) {
        Dish dish = Dish.builder().id(id).status(status).build(); //创建一个dish对象
        dishMapper.update(dish);
        //停售菜品，同时停售该菜品下的所有套餐
        if (StatusConstant.DISABLE == status) {
            //停售菜品，同时停售该菜品下的所有套餐
            List<Long> dishIds = new ArrayList<>();
            dishIds.add(id);
            List<Long> setmealIds = setmealDishMapper.getSetmealIdsByDishIds(dishIds);
            if (setmealIds != null && setmealIds.size() > 0) {
                for (Long setmealId : setmealIds) {
                    Setmeal setmeal = Setmeal.builder().id(setmealId).status(status).build();
                    setmealMapper.update(setmeal);
                }
            }
        }
    }
    /**
     * 根据分类id查询菜品
     * @param categoryId
     * @return
     */
    @Override
    public List<Dish> list(Long categoryId) {
        Dish dish = Dish.builder()
                .categoryId(categoryId)
                .status(StatusConstant.ENABLE)
                .build();
        return dishMapper.list(dish);
    }

    /**
     * 条件查询菜品和口味
     * @param dish
     * @return
     */
    @Override
    public List<DishVO> listWithFlavor(Dish dish) {
        List<Dish> dishList = dishMapper.list(dish);

        List<DishVO> dishVOList = new ArrayList<>();

        for (Dish d : dishList) {
            DishVO dishVO = new DishVO();
            BeanUtils.copyProperties(d,dishVO);

            //根据菜品id查询对应的口味
            List<DishFlavor> flavors = dishFlavorMapper.getByDishId(d.getId());

            dishVO.setFlavors(flavors);
            dishVOList.add(dishVO);
        }

        return dishVOList;
    }

    /**
     * 给 AI 用的菜单查询：只返回起售菜品，keyword / maxPrice 传 null 表示该维度不限。
     */
    @Override
    public List<AiDishVO> listForAi(String keyword, BigDecimal maxPrice) {
        // 这一层只做转发，没有业务逻辑。
        // 保留它的理由：将来要加过滤、打点、缓存，都改这里，Controller 不用动。
        return dishMapper.listForAi(keyword, maxPrice);
    }

}
