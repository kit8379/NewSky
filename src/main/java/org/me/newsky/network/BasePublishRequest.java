package org.me.newsky.network;

import org.me.newsky.NewSky;
import org.me.newsky.redis.RedisHandler;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public abstract class BasePublishRequest {

    protected final NewSky plugin;
    protected final RedisHandler redisHandler;
    protected final String serverID;

    public BasePublishRequest(NewSky plugin, RedisHandler redisHandler, String serverID) {
        this.plugin = plugin;
        this.redisHandler = redisHandler;
        this.serverID = serverID;
    }

    // A map of pending requests
    protected static final ConcurrentHashMap<String, CompletableFuture<Void>> pendingRequests = new ConcurrentHashMap<>();


    /**
     * Subscribe to the response channel
     */
    public abstract void subscribeToResponseChannel();


    /**
     * Unsubscribe from the response channel
     */
    public abstract void unsubscribeFromResponseChannel();


    /**
     * Send a request to the target server
     *
     * @param targetServer The server to send the request to
     * @param operation    The operation to perform
     * @param args         The arguments for the operation
     * @return A CompletableFuture that will be completed when the request is acknowledged
     */
    public abstract CompletableFuture<Void> sendRequest(String targetServer, String operation, String... args);

}
