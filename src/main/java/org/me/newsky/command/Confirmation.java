package org.me.newsky.command;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class Confirmation {
    private final Cache<UUID, String> confirmations;

    public Confirmation() {
        confirmations = CacheBuilder.newBuilder().expireAfterWrite(10, TimeUnit.SECONDS).build();
    }

    public void put(UUID key, String value) {
        confirmations.put(key, value);
    }

    public String getIfPresent(UUID key) {
        return confirmations.getIfPresent(key);
    }

    public void invalidate(UUID key) {
        confirmations.invalidate(key);
    }
}
