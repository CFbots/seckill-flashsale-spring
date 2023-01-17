package com.jiuzhang.seckill;

import com.jiuzhang.seckill.services.SeckillActivityService;
import com.jiuzhang.seckill.util.RedisService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;

// remember to get add the SpringBootTest!
@SpringBootTest
public class RedisDemoTest {
    @Resource
    private RedisService redisService;
    @Autowired
    SeckillActivityService seckillActivityService;

    final String STOCK_TEST_KEY = "stock:19";

    @Test
    public void setStockTest() {
        redisService.setValue(STOCK_TEST_KEY, 10L);
    }

    @Test
    public void getStockTest() {
        String stock = redisService.getValue(STOCK_TEST_KEY);
        System.out.println(stock);
        assert stock != null;
    }

    @Test
    public void stockDeductValidatorTest() {
        boolean result = redisService.stockDeductValidator(STOCK_TEST_KEY);
        System.out.println(String.format("result: %s", result));

        String stock = redisService.getValue(STOCK_TEST_KEY);
        System.out.println(String.format("stock: %s", stock));
    }

    @Test
    public void pushSeckillInfoToRedisTest() {
        seckillActivityService.pushSeckillInfoToRedis(11);
    }
}
