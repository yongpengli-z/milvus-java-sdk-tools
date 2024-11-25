package custom.utils;

import lombok.extern.slf4j.Slf4j;
import redis.clients.jedis.Jedis;

import static custom.BaseTest.redisPassword;

@Slf4j
public class RedisUtils {
    public static String getValueByKey(String key){
        // 创建 Jedis 实例，连接到 Redis 服务器
        try (Jedis jedis = new Jedis("10.102.6.160", 6379)) {
            // 设置键值对
            jedis.auth(redisPassword);
            return jedis.get(key);
        } catch (Exception e) {
            log.error("Redis exception: "+e.getMessage());
        }
        return "-1";
    }

}
