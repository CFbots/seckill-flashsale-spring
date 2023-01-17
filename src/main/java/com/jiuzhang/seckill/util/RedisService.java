package com.jiuzhang.seckill.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.util.Collections;

@Slf4j
@Service
public class RedisService {
    @Autowired
    private JedisPool jedisPool;

    public void setValue(String key, Long value) {
        Jedis jedisClient = jedisPool.getResource();
        jedisClient.set(key, value.toString());
        jedisClient.close();
    }

    public void setValue(String key, String value) {
        Jedis jedisClient = jedisPool.getResource();
        jedisClient.set(key, value);
        jedisClient.close();
    }

    public String getValue(String key) {
        Jedis jedisClient = jedisPool.getResource();
        String value = jedisClient.get(key);
        jedisClient.close();
        return value;
    }

    public boolean stockDeductValidator(String key) {
        try (Jedis jedisClient = jedisPool.getResource()) {
            String script = "if redis.call('exists',KEYS[1]) == 1 then\n" +
                    "                 local stock = tonumber(redis.call('get', KEYS[1]))\n" +
                    "                 if( stock <=0 ) then\n" +
                    "                    return -1\n" +
                    "                 end;\n" +
                    "                 redis.call('decr',KEYS[1]);\n" +
                    "                 return stock - 1;\n" +
                    "             end;\n" +
                    "             return -1;";

            Long stock = (Long) jedisClient.eval(
                    script,
                    Collections.singletonList(key),
                    Collections.emptyList()
            );

            if (stock < 0) {
                System.out.println("Stock is insufficient");
                return false;
            } else {
                System.out.println("Congratulations! Purchase is successful");
            }
            return true;
        } catch (Throwable throwable) {
            System.out.println(String.format("Stock deduction failed: %s", throwable.toString()));
            return false;
        }
    }

    public void revertStock(String key) {
        Jedis jedisClient = jedisPool.getResource();
        jedisClient.incr(key);
        jedisClient.close();
    }

    public boolean isInLimitMember(long activityId, long userId) {
        Jedis jedisClient = jedisPool.getResource();
        boolean sismember = jedisClient.sismember("seckillActivity_users:" + activityId, String.valueOf(userId));
        jedisClient.close();

        log.info("userId:{}  activityId:{}  is already in the limit list:{}", userId, activityId, sismember);
        return sismember;
    }

    public void addLimitMember(long activityId, long userId) {
        Jedis jedisClient = jedisPool.getResource();
        jedisClient.sadd("seckillActivity_users:" + activityId, String.valueOf(userId));
        jedisClient.close();
    }

    public void removeLimitMember(long activityId, long userId) {
        Jedis jedisClient = jedisPool.getResource();
        jedisClient.srem("seckillActivity_users:" + activityId, String.valueOf(userId));
        jedisClient.close();
    }

    /**
     * nxxx has to possible options:
     *   - NX = not exist: only set key & value to redis when key does not exist
     *   - XX = is existed: only set key & value to redis when key is already existed
     * expx has to possible options:
     *   - EX = seconds
     *   - PX = milliseconds
     * @param lockKey
     * @param requestId
     * @param expiredTime
     * @return
     */
    public boolean tryGetDistributedLock(String lockKey, String requestId, int expiredTime) {
        Jedis jedisClient = jedisPool.getResource();
        String result = jedisClient.set(lockKey, requestId, "NX", "PX", expiredTime);
        jedisClient.close();

        return "OK".equals(result);
    }

    public boolean releaseDistributedLock(String lockKey, String requestId) {
        Jedis jedisClient = jedisPool.getResource();
        String script = "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end";
        Long result = (Long) jedisClient.eval(
                script,
                Collections.singletonList(lockKey),
                Collections.singletonList(requestId)
        );
        jedisClient.close();

        return result == 1L;
    }
}
