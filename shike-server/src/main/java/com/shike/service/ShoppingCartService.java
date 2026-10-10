package com.shike.service;

import com.shike.dto.ShoppingCartDTO;
import com.shike.entity.ShoppingCart;

import java.util.List;

public interface ShoppingCartService {

    void addShoppingCart(ShoppingCartDTO shoppingCartDTO);

    List<ShoppingCart> showShoppingCart();


    void cleanShoppingCart();

    void subNumber(ShoppingCartDTO shoppingCartDTO);
}
