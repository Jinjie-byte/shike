package com.shike.service.impl;

import com.shike.context.BaseContext;
import com.shike.dto.ShoppingCartDTO;
import com.shike.entity.Dish;
import com.shike.entity.Setmeal;
import com.shike.entity.ShoppingCart;
import com.shike.mapper.DishMapper;
import com.shike.mapper.SetmealMapper;
import com.shike.mapper.ShoppingCartMapper;
import com.shike.service.ShoppingCartService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Slf4j
public class ShoppingCartServiceImpl implements ShoppingCartService {
    @Autowired
    private ShoppingCartMapper shoppingCartMapper;
    @Autowired
    private DishMapper dishMapper;
    @Autowired
    private SetmealMapper setmealMapper;
    /**
     * 添加购物车
     * @param shoppingCartDTO
     */
    @Override
    public void addShoppingCart(ShoppingCartDTO shoppingCartDTO) {
        // 1.添加购物车商品是否在购物车中
        ShoppingCart shoppingCart = new ShoppingCart();
        BeanUtils.copyProperties(shoppingCartDTO,shoppingCart);
        Long currentId = BaseContext.getCurrentId();
        shoppingCart.setUserId(currentId);
        // 2.如果在购物车中，数量+1，不存在，添加到购物车中
        List<ShoppingCart> list = shoppingCartMapper.list(shoppingCart);
        if (list != null && list.size() > 0){
            ShoppingCart cart = list.get(0);
            cart.setNumber(cart.getNumber() + 1);
            shoppingCartMapper.updateNumberById(cart);
        }else{
            //商品或者套餐信息获取
            Long dishId = shoppingCart.getDishId();
            if(dishId != null){
                //添加到购物车的是菜品
                Dish dish = dishMapper.getById(dishId);
                shoppingCart.setName(dish.getName());
                shoppingCart.setImage(dish.getImage());
                shoppingCart.setAmount(dish.getPrice());
            }else{
                //添加到购物车的是套餐
                Long setmealId = shoppingCart.getSetmealId();
                Setmeal setmeal = setmealMapper.getById(setmealId);
                shoppingCart.setName(setmeal.getName());
                shoppingCart.setImage(setmeal.getImage());
                shoppingCart.setAmount(setmeal.getPrice());
            }
            shoppingCart.setNumber(1);   ////数量默认为1
            shoppingCart.setCreateTime(LocalDateTime.now()); ///创建时间
            shoppingCartMapper.insert(shoppingCart);
        }
    }

    /**
     * 显示购物车
     * @return
     */
    @Override
    public List<ShoppingCart> showShoppingCart() {
        Long currentId = BaseContext.getCurrentId();
        ShoppingCart shoppingCart = ShoppingCart.builder()
                .userId(currentId)
                .build();
        List<ShoppingCart>  list = shoppingCartMapper.list(shoppingCart);
        return list;
    }

    /**
     * 清空购物车
     */
    @Override
    public void cleanShoppingCart() {
        Long currentId = BaseContext.getCurrentId();
        shoppingCartMapper.deleteByUserId(currentId);
    }


    /**
     * 减少购物车商品数量（减到 0 就删掉这一行）
     *
     * ★ 原实现有三个 bug，2026-10-18 修正：
     *   ① 只按 dishId 查 —— **不分用户**，张三减购会减到李四的行
     *   ② 只认 dishId —— 套餐（只有 setmealId）会查出 null 然后 NPE
     *   ③ 删除时把 dishId 当成购物车主键 id 传 —— 删错行（除非两个 id 恰好相同）
     *
     * 现在统一走 mapper.list 的动态条件（userId + dish/setmeal + 口味），
     * 删除用购物车行自己的 id。
     */
    @Override
    public void subNumber(ShoppingCartDTO shoppingCartDTO) {
        ShoppingCart query = new ShoppingCart();
        query.setUserId(BaseContext.getCurrentId());
        query.setDishId(shoppingCartDTO.getDishId());
        query.setSetmealId(shoppingCartDTO.getSetmealId());
        query.setDishFlavor(shoppingCartDTO.getDishFlavor());

        List<ShoppingCart> list = shoppingCartMapper.list(query);
        if (list == null || list.isEmpty()) {
            throw new RuntimeException("购物车里没有这件商品");
        }

        ShoppingCart cart = list.get(0);
        if (cart.getNumber() <= 1) {
            // ★ 用 deleteByCartId（按主键删），不能用 deleteById ——
            //   后者的 SQL 是 `where dish_id = ?`，套餐行没有 dish_id，删不掉
            shoppingCartMapper.deleteByCartId(cart.getId());
        } else {
            cart.setNumber(cart.getNumber() - 1);
            shoppingCartMapper.updateNumberById(cart);
        }
    }
}
