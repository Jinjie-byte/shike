package com.shike.controller.internal;

import com.shike.context.BaseContext;
import com.shike.dto.ShoppingCartDTO;
import com.shike.result.Result;
import com.shike.service.DishService;
import com.shike.service.SetmealService;
import com.shike.service.ShoppingCartService;
import com.shike.vo.AiDishVO;
import com.shike.vo.AiMenuVO;
import com.shike.vo.AiSetmealVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

/**
 * 【课 2】给 Python 侧的 AI Agent 调用的内部接口。
 *
 * ============================================================
 * 为什么单独开一组 /internal/** 接口，不让 Python 直接调 /admin/dish/list？
 * ============================================================
 *   1) /admin/** 挂了 JWT 拦截器，Python 手上没有 token → 调不通
 *   2) /admin/dish/list 返回 PageResult 分页对象，字段一大堆
 *      （image、createTime、createUser、updateUser……）
 *      —— 那是给人看的表格数据，喂给模型纯属浪费 token
 *   3) 所以这里开一个"精简出口"：字段只留模型判断需要的，返回结构最简单
 *
 * ============================================================
 * ⚠️ 安全提醒（面试能讲的一条）
 * ============================================================
 * /internal/** 不在任何拦截器范围内（见 WebMvcConfiguration），
 * 也就是说它是【无鉴权】的。靠三层防护兜底：
 *   ① server.address = 127.0.0.1  → 外网连端口都连不上（上线配）
 *   ② 共享密钥 header              → 后续课加
 *   ③ 云服务器安全组不放行 8080
 */
@RestController
@RequestMapping("/internal/ai")
@Slf4j
public class AiToolController {

    @Autowired
    private DishService dishService;

    @Autowired
    private SetmealService setmealService;

    @Autowired
    private ShoppingCartService shoppingCartService;

    /**
     * 获取菜单（菜品 + 套餐）。Python 侧的 get_menu 工具调这个地址。
     *
     * 用法：GET /internal/ai/menu                      ← 不传参 = 返回全部在售菜品
     *       GET /internal/ai/menu?keyword=鱼&maxPrice=50 ← 可选：带筛选
     *
     * 两个参数都是可选的，Python 侧的 get_menu 工具目前**不传任何参数**。
     *
     * 为什么返回 {dishes, setmeals} 而不是单纯的菜品数组：
     *   用户会问"套餐里有什么"。只给菜品，模型答不出套餐，只能瞎编。
     *   一次给全，模型才有判断依据。
     *
     * @param keyword  关键词，可为 null
     * @param maxPrice 价格上限，可为 null
     */
    @GetMapping("/menu")
    public Result<AiMenuVO> getMenu(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) BigDecimal maxPrice) {

        List<AiDishVO> dishes = dishService.listForAi(keyword, maxPrice);
        List<AiSetmealVO> setmeals = setmealService.listForAi();

        // 这行日志别删：将来 Python 说"查不到菜"时，靠它一眼分清
        // 是"参数没传对"还是"库里真没有"。
        log.info("AI 获取菜单：keyword={}, maxPrice={}, 菜品 {} 条 / 套餐 {} 个",
                keyword, maxPrice, dishes.size(), setmeals.size());

        return Result.success(AiMenuVO.builder()
                .dishes(dishes)
                .setmeals(setmeals)
                .build());
    }

    /**
     * 加购物车（给 Python 的 add_to_cart 工具调用）。
     *
     * ⚠️ 为什么必须显式传 userId：
     *    这个接口在 /internal/** 下，没有 JWT 拦截器帮我们填 BaseContext。
     *    而 ShoppingCartService 内部是取 BaseContext 当前用户来落库的 ——
     *    不切上下文，加购会加到"上一个请求留下的用户"身上。
     *
     * ⚠️ 为什么 finally 里要还原：
     *    Tomcat 线程是复用的。不还原，下一个请求会带着别人的身份跑 ——
     *    这和你练习 4 在拦截器里补 removeCurrentId() 是同一个道理。
     */
    @PostMapping("/cart/add")
    public Result<String> addToCart(@RequestParam Long userId,
                                    @RequestParam(required = false) Long dishId,
                                    @RequestParam(required = false) Long setmealId) {
        if (dishId == null && setmealId == null) {
            return Result.error("dishId 和 setmealId 必须传一个");
        }

        Long backup = BaseContext.getCurrentId();
        try {
            BaseContext.setCurrentId(userId);

            ShoppingCartDTO dto = new ShoppingCartDTO();
            dto.setDishId(dishId);
            dto.setSetmealId(setmealId);
            shoppingCartService.addShoppingCart(dto);

            log.info("AI 加购成功：userId={}, dishId={}, setmealId={}", userId, dishId, setmealId);
            return Result.success("ok");

        } catch (Exception e) {
            log.error("AI 加购失败：userId={}, dishId={}, setmealId={}, {}",
                    userId, dishId, setmealId, e.getMessage());
            return Result.error("加购失败：" + e.getMessage());

        } finally {
            if (backup == null) {
                BaseContext.removeCurrentId();
            } else {
                BaseContext.setCurrentId(backup);
            }
        }
    }
}
