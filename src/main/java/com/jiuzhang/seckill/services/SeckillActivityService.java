package com.jiuzhang.seckill.services;

import com.alibaba.fastjson.JSON;
import com.jiuzhang.seckill.db.dao.OrderDao;
import com.jiuzhang.seckill.db.dao.SeckillActivityDao;
import com.jiuzhang.seckill.db.po.Order;
import com.jiuzhang.seckill.db.po.SeckillActivity;
import com.jiuzhang.seckill.mq.RocketMQService;
import com.jiuzhang.seckill.util.RedisService;
import com.jiuzhang.seckill.util.SnowFlake;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;

@Slf4j
@Service
public class SeckillActivityService {
    @Autowired
    private RedisService redisService;
    @Autowired
    private SeckillActivityDao seckillActivityDao;
    @Autowired
    private RocketMQService rocketMQService;
    @Autowired
    OrderDao orderDao;

    /**
     * Can read machine config in a distributive environment
     * set to 1 in dev environment
     */
    private SnowFlake snowFlake = new SnowFlake(1, 1);

    public Order createOrder(long seckillActivityId, long userId) throws Exception {
        // 1. create the order
        SeckillActivity seckillActivity = seckillActivityDao.querySeckillActivityById(seckillActivityId);
        Order order = new Order();

        // use SnowFlake to generate order id
        order.setOrderNo(String.valueOf(snowFlake.nextId()));
        order.setSeckillActivityId(seckillActivity.getId());
        order.setUserId(userId);
        order.setOrderAmount(seckillActivity.getSeckillPrice().longValue());

        // 2. send createOrder message
        rocketMQService.sendMessage("seckill_order", JSON.toJSONString(order));

        // 3. send order pay_check message
        // RocketMQ supports delayed message, but does not support second-level accuracy.
        // default is to support 18 levels of delayed message, configured by broker-side field
        // messageDelayLevel, specific is as followed:
        // messageDelayLevel=1s 5s 10s 30s 1m 2m 3m 4m 5m 6m 7m 8m 9m 10m 20m 30m 1h 2h
        rocketMQService.sendDelayedMessage(
                "pay_check",
                JSON.toJSONString(order),
                3 // correspond to 10s
        );

        return order;
    }

    /**
     * Determine if there's stock remaining
     * @param activityId
     * @return
     */
    public boolean seckillStockValidator(long activityId) {
        String key = "stock:" + activityId;
        return redisService.stockDeductValidator(key);
    }

    public void payOrderProcess(String orderNo) throws Exception {
        log.info(String.format("Finishing payment order (order No: %s)", orderNo));
        Order order = orderDao.queryOrder(orderNo);

        // 1. Determine if order already exists or is "pending payment"
        if (order == null) {
            log.error("The orderNo provided does not exist: " + orderNo);
            return;
        } else if (order.getOrderStatus() != 1) {
            log.error("Order status is not valid: " + orderNo);
            return;
        }

        // 2. Order payment is complete
        // Order status:
        //     0 = out of stock, invalid order
        //     1 = order created, pending for payment
        //     2 = payment finished
        order.setPayTime(new Date());
        order.setOrderStatus(2);
        orderDao.updateOrder(order);

        rocketMQService.sendMessage("pay_done", JSON.toJSONString(order));
    }
}
