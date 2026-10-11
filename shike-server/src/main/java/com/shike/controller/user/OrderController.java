package com.shike.controller.user;

import com.shike.context.BaseContext;
import com.shike.dto.OrdersPaymentDTO;
import com.shike.dto.OrdersSubmitDTO;
import com.shike.exception.OrderBusinessException;
import com.shike.result.PageResult;
import com.shike.result.Result;
import com.shike.service.OrderService;
import com.shike.vo.OrderPaymentVO;
import com.shike.vo.OrderSubmitVO;
import com.shike.vo.OrderVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@RestController("userOrderController")
@RequestMapping("/user/order")
@Api(tags = "用户d端订单相关接口")
@Slf4j
public class OrderController {
    @Autowired
    private OrderService orderService;

    /**
     * Redis 用来实现「下单幂等」的分布式锁。
     * 注入方式与项目里 DishController / ShopController 保持一致（裸类型 RedisTemplate）。
     */
    @Autowired
    private RedisTemplate redisTemplate;

    /**
     * 用户下单
     * @param orderSubmitDTO
     * @return
     */
    @PostMapping("/submit")
    @ApiOperation("用户下单")
    public Result<OrderSubmitVO> submit(@RequestBody OrdersSubmitDTO orderSubmitDTO) {
        log.info("订单提交参数：{}", orderSubmitDTO);

        // ==================== 改造 2：下单幂等（分布式锁）====================
        // 为什么这段写在 Controller，而不写在 Service 的 submit() 里？
        //   Service 的 submit() 上有 @Transactional（代理层管事务）。
        //   如果把锁加在方法体内：finally 释放锁 → 方法返回 → 【代理才提交事务】，
        //   中间存在「锁已释放、数据还没提交」的窗口：第二个请求拿到锁时读到的是旧数据
        //   （购物车还没被清空），于是又生成一张订单 —— 锁等于白加。
        //   → 所以锁必须加在事务的【外层】，也就是 Controller 这一层。
        //
        // 这里能拿到 userId，靠的是拦截器提前放进 ThreadLocal 的（练习 2 学过 BaseContext）。
        Long userId = BaseContext.getCurrentId();

        // ── 空① ────────────────────────────────────────────────
        //   拼出这把锁的 key。要求：同一个用户共用一把锁 → 把 userId 拼进去。
        //   命名建议："order:lock:" + userId   （分层写法，方便在 redis-cli 里排查）
        String lockKey = "order:lock:" + userId;
        String value = UUID.randomUUID().toString();

        // ── 空② ────────────────────────────────────────────────
        //   用 Redis「key 不存在时才设置」（SETNX，原子操作）来抢锁。
        //   RedisTemplate 的写法：redisTemplate.opsForValue().setIfAbsent(key, value, 超时, 时间单位)
        //   ★ 必须带超时时间！否则程序一旦崩在中间，这把锁永远不释放 = 死锁，这个用户再也下不了单。
        //   返回值是 Boolean：抢到 = true，没抢到 = false
        Boolean locked = redisTemplate.opsForValue().setIfAbsent(lockKey,value,30, TimeUnit.SECONDS) ;      // ← 把 null 换成上面那个调用

        if (!Boolean.TRUE.equals(locked)) {
            // 没抢到锁 → 该用户上一次下单还在处理中，判定为重复提交
            throw new OrderBusinessException("请勿重复提交订单");
        }

        try {
            // 订单提交业务逻辑
            OrderSubmitVO orderSubmitVO = orderService.submit(orderSubmitDTO);
            // 返回订单信息
            return Result.success(orderSubmitVO);
        } finally {
            // ── 空③ ────────────────────────────────────────────
            //   释放锁。不管成功还是抛异常都必须删掉 —— 所以放在 finally 里。
            //   提示：redisTemplate.delete(lockKey)
            redisTemplate.delete(lockKey);

        }
    }
    /**
     * 订单支付
     *
     * @param ordersPaymentDTO
     * @return
     */
    @PutMapping("/payment")
    @ApiOperation("订单支付")
    public Result<OrderPaymentVO> payment(@RequestBody OrdersPaymentDTO ordersPaymentDTO) throws Exception {
        log.info("订单支付：{}", ordersPaymentDTO);
        OrderPaymentVO orderPaymentVO = orderService.payment(ordersPaymentDTO);
        log.info("生成预支付交易单：{}", orderPaymentVO);

        // 业务处理，修改订单转态、来单状态 模拟微信支付接口
        orderService.paySuccess(ordersPaymentDTO.getOrderNumber());
        log.info("订单支付成功：{}", ordersPaymentDTO.getOrderNumber());
        return Result.success(orderPaymentVO);
    }
    /**
     * 历史订单查询
     *
     * @param page
     * @param pageSize
     * @param status   订单状态 1待付款 2待接单 3已接单 4派送中 5已完成 6已取消
     * @return
     */
    @GetMapping("/historyOrders")
    @ApiOperation("历史订单查询")
    public Result<PageResult> page(int page, int pageSize, Integer status) {
        PageResult pageResult = orderService.pageQuery4User(page, pageSize, status);
        return Result.success(pageResult);
    }
    /**
     * 查询订单详情
     *
     * @param id
     * @return
     */
    @GetMapping("/orderDetail/{id}")
    @ApiOperation("查询订单详情")
    public Result<OrderVO> details(@PathVariable("id") Long id) {
        OrderVO orderVO = orderService.details(id);
        return Result.success(orderVO);
    }

    /**
     * 用户取消订单
     *
     * @return
     */
    @PutMapping("/cancel/{id}")
    @ApiOperation("取消订单")
    public Result cancel(@PathVariable("id") Long id) throws Exception {
        orderService.userCancelById(id);
        return Result.success();
    }
    /**
     * 再来一单
     *
     * @param id
     * @return
     */
    @PostMapping("/repetition/{id}")
    @ApiOperation("再来一单")
    public Result repetition(@PathVariable Long id) {
        orderService.repetition(id);
        return Result.success();
    }

    /**
     * 客户催单
     * @param id
     * @return
     */
    @GetMapping("/reminder/{id}")
    @ApiOperation("客户催单")
    public Result reminder(@PathVariable("id") Long id){
        orderService.reminder(id);
        return Result.success();
    }

}
