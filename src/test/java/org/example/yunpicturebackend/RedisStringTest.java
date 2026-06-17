package org.example.yunpicturebackend;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Redis 字符串 (String) 数据结构操作集成测试
 * <p>
 * 测试目标：验证 Spring Data Redis 核心组件 StringRedisTemplate 的可用性。
 * 测试场景：针对 Redis 最基础的 String 类型，模拟完整的 CRUD（增删改查）生命周期，
 * 确保客户端与本地/远程 Redis 服务端的连接和数据读写行为符合预期。
 * </p>
 */
@SpringBootTest
public class RedisStringTest {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Test
    public void testRedisStringOperations() {
        // 获取 Redis 中 String 类型数据的专用操作句柄
        ValueOperations<String, String> valueOps = stringRedisTemplate.opsForValue();

        // 预定义测试用的 Key 和 Value
        String key = "testKey";
        String value = "testValue";

        // 1. 测试新增 (Create) 操作
        // 动作：将键值对写入 Redis，并立即通过 get 获取。
        // 预期：能够无延迟地读取出刚刚存入的 value。
        valueOps.set(key, value);
        String storedValue = valueOps.get(key);
        assertEquals(value, storedValue, "存储的值与预期不一致");

        // 2. 测试修改 (Update) 操作
        // 动作：对已存在的 key 再次执行 set 写入操作。
        // 预期：Redis 的 String 结构具备覆盖特性，旧值会被新值 (updatedValue) 成功替换。
        String updatedValue = "updatedValue";
        valueOps.set(key, updatedValue);
        storedValue = valueOps.get(key);
        assertEquals(updatedValue, storedValue, "更新后的值与预期不一致");

        // 3. 测试查询 (Read) 操作
        // 动作：单独调用 get 读取该 key。
        // 预期：返回值不为 null，且等于最后一次更新的值。
        storedValue = valueOps.get(key);
        assertNotNull(storedValue, "查询的值为空");
        assertEquals(updatedValue, storedValue, "查询的值与预期不一致");

        // 4. 测试删除 (Delete) 操作
        // 动作：调用底层 template 清理该 key，并尝试再次获取。
        // 预期：key 被物理销毁，再次 get 查询时返回 null。
        stringRedisTemplate.delete(key);
        storedValue = valueOps.get(key);
        assertNull(storedValue, "删除后的值不为空");
    }
}