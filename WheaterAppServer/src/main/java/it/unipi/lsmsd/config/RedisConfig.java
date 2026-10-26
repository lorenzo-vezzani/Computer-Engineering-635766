package it.unipi.lsmsd.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisCluster;

import java.util.HashSet;
import java.util.Set;

// Configuration class where Spring will look for bean definitions
@Configuration
public class RedisConfig {

    // Tells Spring to treat this method as a bean factory method so Spring 
    // manages the lifecycle of the returned JedisPool instance
    @Bean
    public JedisPool jedisPool() {
        // Configuration object for customizing connection pool settings
        JedisPoolConfig poolConfig = new JedisPoolConfig();
        
        // Connection pool configuration
        poolConfig.setMaxTotal(50); // maximum number of total connections allowed in the pool
        poolConfig.setMaxIdle(10); // maximum number of idle (unused) connections in the pool
        poolConfig.setMinIdle(5); // minimum number of idle connections to keep in the pool
        poolConfig.setTestOnBorrow(true); // connections are validated(checks if alive) before being borrowed from the pool

        // Disable JMX monitoring to avoid conflicts
        poolConfig.setJmxEnabled(false);

        String host = "localhost";
        int port = 6379;
        //A thread-safe pool of Redis connections
        return new JedisPool(poolConfig, host, port);
    }

    // method to connect with a Redis Cluster
    @Bean
    public JedisCluster jedisCluster() {
        Set<HostAndPort> clusterNodes = new HashSet<>();
        clusterNodes.add(new HostAndPort("10.1.1.9", 6379));
        clusterNodes.add(new HostAndPort("10.1.1.9", 6380));
        clusterNodes.add(new HostAndPort("10.1.1.84", 6379));
        clusterNodes.add(new HostAndPort("10.1.1.84", 6380));
        clusterNodes.add(new HostAndPort("10.1.1.87", 6379));
        clusterNodes.add(new HostAndPort("10.1.1.87", 6380));

        return new JedisCluster(clusterNodes);
    }
}