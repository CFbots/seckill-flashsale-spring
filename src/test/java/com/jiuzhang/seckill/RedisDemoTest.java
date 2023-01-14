package com.jiuzhang.seckill;

import com.jiuzhang.seckill.util.RedisService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;

// remember to get add the SpringBootTest!
@SpringBootTest
public class RedisDemoTest {
    @Resource
    private RedisService redisService;

    final String STOCK_TEST_KEY = "stock:19";

    @Test
    public void setStockTest() {
        redisService.setValue(STOCK_TEST_KEY, 10L);
    }

    @Test
    public void getStockTest() {
        String stock = redisService.getValue(STOCK_TEST_KEY);
        System.out.println(stock);
        assert stock.equals("10");
    }
}
