package com.shike.task;

import com.shike.entity.Orders;
import com.shike.mapper.OrderMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@Slf4j
public class OrderTask {
    @Autowired
    private OrderMapper orderMapper;
    /**
     * 每分钟执行一次，处理 15 分钟未支付的超时订单。
     * 原为 "1/5 * * * * ?"（每 5 秒，教程演示用的节奏），上线改成每分钟，
     * 否则日志会被这两条任务刷屏。
     */
    @Scheduled(cron = "0 * * * * ?")
    public void processTimeoutOrders() {
        log.info("定时处理超时订单：{}", LocalDateTime.now());
        // 业务逻辑
        LocalDateTime time = LocalDateTime.now().plusMinutes(-15); // 15分钟前的时间
        List<Orders> orders = orderMapper.getByStatusAndOrderTime(Orders.PENDING_PAYMENT, time);
        if (orders!= null && orders.size() > 0) {
            for (Orders order : orders) {
                order.setStatus(Orders.CANCELLED);
                order.setCancelReason("订单超时，自动取消");
                order.setCancelTime(LocalDateTime.now());
                orderMapper.update(order);
            }
        }
    }

    /**
     * 每天凌晨 1 点执行一次，把超过 1 小时仍在派送中的订单置为已完成。
     * 原为 "0/5 * * * * ?"（每 5 秒，教程演示用），上线改成每天一次。
     */
    @Scheduled(cron = "0 0 1 * * ?")
    public  void processDeliveryOrders() {
        log.info("定时处理派送中国的订单：{}",LocalDateTime.now());
        LocalDateTime time = LocalDateTime.now().plusMinutes(-60); // 1小时前的时间
        List<Orders> orders =orderMapper.getByStatusAndOrderTime(Orders.DELIVERY_IN_PROGRESS, time);
        if (orders!= null && orders.size() > 0) {
            for (Orders order : orders) {
                order.setStatus(Orders.COMPLETED); //  完成
                orderMapper.update(order);
            }
        }
    }
}
