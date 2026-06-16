package com.readmeeditor.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import java.time.Duration;

/**
 * Manages the Redis connection pool using Jedis.
 * <p>
 * Provides a thread-safe pool of Jedis connections configured
 * from {@link AppConfig}. Implements {@link AutoCloseable} for
 * proper resource cleanup on application shutdown.
 */
public class RedisConfig implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(RedisConfig.class);

    private final JedisPool jedisPool;
    private static RedisConfig instance;

    /**
     * Returns the singleton {@code RedisConfig} instance, initialising
     * the connection pool on first call.
     */
    public static synchronized RedisConfig getInstance() {
        if (instance == null || instance.jedisPool.isClosed()) {
            instance = new RedisConfig();
        }
        return instance;
    }

    private RedisConfig() {
        AppConfig config = AppConfig.getInstance();

        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(16);
        poolConfig.setMaxIdle(8);
        poolConfig.setMinIdle(2);
        poolConfig.setMaxWait(Duration.ofSeconds(5));
        poolConfig.setTestOnBorrow(true);
        poolConfig.setTestOnReturn(true);
        poolConfig.setTestWhileIdle(true);

        String host = config.getRedisHost();
        int port = config.getRedisPort();
        String password = config.getRedisPassword();
        int timeout = config.getRedisTimeout();
        int database = config.getRedisDatabase();

        if (password != null && !password.isEmpty()) {
            jedisPool = new JedisPool(poolConfig, host, port, timeout, password, database);
        } else {
            jedisPool = new JedisPool(poolConfig, host, port, timeout, null, database);
        }

        LOG.info("Redis connection pool initialized: {}:{}/{} (pool size: {}/{})",
                host, port, database, poolConfig.getMaxTotal(), poolConfig.getMaxIdle());
    }

    /**
     * Returns a Jedis connection from the pool.
     * <p>
     * Always use in a try-with-resources block to ensure the connection
     * is returned to the pool:
     * <pre>{@code
     * try (Jedis jedis = redisConfig.getConnection()) {
     *     jedis.set("key", "value");
     * }
     * }</pre>
     */
    public Jedis getConnection() {
        return jedisPool.getResource();
    }

    /**
     * Tests the Redis connection by executing a PING command.
     *
     * @return true if the connection is healthy
     */
    public boolean isConnected() {
        try (Jedis jedis = getConnection()) {
            return "PONG".equals(jedis.ping());
        } catch (Exception e) {
            LOG.error("Redis connection test failed", e);
            return false;
        }
    }

    @Override
    public void close() {
        if (jedisPool != null && !jedisPool.isClosed()) {
            jedisPool.close();
            LOG.info("Redis connection pool closed");
        }
    }
}
