package com.jiuzhang.seckill.services;

import com.alibaba.fastjson.JSON;
import com.jiuzhang.seckill.db.dao.SeckillActivityDao;
import com.jiuzhang.seckill.db.po.Order;
import com.jiuzhang.seckill.db.po.SeckillActivity;
import com.jiuzhang.seckill.mq.RocketMQService;
import com.jiuzhang.seckill.util.RedisService;
import com.jiuzhang.seckill.util.SnowFlake;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class SeckillActivityService {
    @Autowired
    private RedisService redisService;

    @Autowired
    private SeckillActivityDao seckillActivityDao;

    @Autowired
    private RocketMQService rocketMQService;

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
}
