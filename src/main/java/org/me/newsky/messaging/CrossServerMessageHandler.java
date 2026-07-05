package org.me.newsky.messaging;

import org.json.JSONObject;

import java.util.concurrent.CompletableFuture;

@FunctionalInterface
public interface CrossServerMessageHandler {

    CompletableFuture<JSONObject> handle(JSONObject payload);
}
