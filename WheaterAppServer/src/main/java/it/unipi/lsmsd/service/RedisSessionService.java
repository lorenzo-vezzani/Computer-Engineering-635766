package it.unipi.lsmsd.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import redis.clients.jedis.JedisCluster;

@Service
public class RedisSessionService {

    @Autowired
    private JedisCluster jedisCluster;

    // Saves a user session in Redis
    public void saveSession(String token, String username) {
        jedisCluster.setex(token, 3600, username); // 1-hour expiration
    }

    // Retrieves the username from given session token
    public String getUsernameFromSession(String token) {
        return jedisCluster.get(token);
    }

    // Deletes the session when the user logs out
    public void deleteSession(String token) {
        jedisCluster.del(token);
    }
}