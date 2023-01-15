package com.jiuzhang.seckill.mq;

import com.alibaba.fastjson.JSON;
import com.jiuzhang.seckill.db.dao.OrderDao;
import com.jiuzhang.seckill.db.dao.SeckillActivityDao;
import com.jiuzhang.seckill.db.po.Order;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Date;

@Slf4j
@Component
@RocketMQMessageListener(topic = "seckill_order", consumerGroup = "seckill_order_group")
public class OrderConsumer implements RocketMQListener<MessageExt> {

    @Autowired
    private OrderDao orderDao;

    @Autowired
    private SeckillActivityDao seckillActivityDao;

    @Override
    @Transactional
    public void onMessage(MessageExt messageExt) {
        // 1. analyze createOrder request message
        String message = new String(messageExt.getBody(), StandardCharsets.UTF_8);
        log.info("Received createOrder() request: " + message);
        Order order = JSON.parseObject(message, Order.class);
        order.setCreateTime(new Date());

        // 2. deduct stock
        // orderStatus:
        //     no available stock = 0
        //     order placed, pending for payment = 1
        boolean lockStockResult = seckillActivityDao.lockStock(order.getSeckillActivityId());
        int orderStatus = lockStockResult ? 1 : 0;
        order.setOrderStatus(orderStatus);

        // 3. insert the order
        orderDao.insertOrder(order);
    }
}
