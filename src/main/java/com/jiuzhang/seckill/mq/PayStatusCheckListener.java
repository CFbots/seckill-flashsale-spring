package com.jiuzhang.seckill.mq;

import com.alibaba.fastjson.JSON;
import com.jiuzhang.seckill.db.dao.OrderDao;
import com.jiuzhang.seckill.db.dao.SeckillActivityDao;
import com.jiuzhang.seckill.db.po.Order;
import com.jiuzhang.seckill.util.RedisService;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.nio.charset.StandardCharsets;

@Slf4j
@Component
@RocketMQMessageListener(topic = "pay_check", consumerGroup = "pay_check_group")
public class PayStatusCheckListener implements RocketMQListener<MessageExt> {
    @Autowired
    private OrderDao orderDao;
    @Autowired
    private SeckillActivityDao seckillActivityDao;
    @Resource
    private RedisService redisService;

    @Override
    public void onMessage(MessageExt messageExt) {
        String message = new String(messageExt.getBody(), StandardCharsets.UTF_8);
        log.info("Received order payment check status message: " + message);
        Order order = JSON.parseObject(message, Order.class);

        // 1. checkOrder
        Order orderInfo = orderDao.queryOrder(order.getOrderNo());

        // 2. determine if order has completed payment
        if (orderInfo.getOrderStatus() != 2) {
            // 3. close incomplete orders
            log.info(String.format("Order has not been completed, closing the order (Order No: )", orderInfo.getOrderNo()));
            orderInfo.setOrderStatus(99);
            orderDao.updateOrder(orderInfo);

            // 4. restore stock in database
            seckillActivityDao.revertStock(order.getSeckillActivityId());
            redisService.revertStock("stock:" + order.getSeckillActivityId());
        }
    }
}
