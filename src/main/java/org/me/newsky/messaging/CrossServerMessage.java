package org.me.newsky.messaging;

import org.json.JSONObject;

import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

public final class CrossServerMessage {

    public static final String TYPE_REQUEST = "REQUEST";
    public static final String TYPE_RESPONSE = "RESPONSE";

    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_FAILED = "FAILED";

    private static final int VERSION = 1;

    private final String messageId;
    private final String correlationId;
    private final String type;
    private final String source;
    private final String target;
    private final String action;
    private final JSONObject payload;
    private final String status;
    private final String errorType;
    private final String errorMessage;

    private CrossServerMessage(String messageId, String correlationId, String type, String source, String target, String action, JSONObject payload, String status, String errorType, String errorMessage) {
        this.messageId = messageId;
        this.correlationId = correlationId;
        this.type = type;
        this.source = source;
        this.target = target;
        this.action = action;
        this.payload = payload == null ? new JSONObject() : new JSONObject(payload.toString());
        this.status = status;
        this.errorType = errorType;
        this.errorMessage = errorMessage;
    }

    public static CrossServerMessage request(String source, String target, String action, JSONObject payload) {
        String messageId = UUID.randomUUID().toString();
        return new CrossServerMessage(messageId, messageId, TYPE_REQUEST, source, target, action, payload, null, null, null);
    }

    public static CrossServerMessage successResponse(CrossServerMessage request, JSONObject payload) {
        return new CrossServerMessage(UUID.randomUUID().toString(), request.messageId, TYPE_RESPONSE, request.target, request.source, request.action, payload, STATUS_SUCCESS, null, null);
    }

    public static CrossServerMessage failedResponse(CrossServerMessage request, String errorMessage) {
        return new CrossServerMessage(UUID.randomUUID().toString(), request.messageId, TYPE_RESPONSE, request.target, request.source, request.action, new JSONObject(), STATUS_FAILED, null, errorMessage);
    }

    public static CrossServerMessage failedResponse(CrossServerMessage request, Throwable throwable) {
        Throwable cause = unwrap(throwable);
        String message = cause.getMessage() == null ? cause.toString() : cause.getMessage();
        return new CrossServerMessage(UUID.randomUUID().toString(), request.messageId, TYPE_RESPONSE, request.target, request.source, request.action, new JSONObject(), STATUS_FAILED, cause.getClass().getName(), message);
    }

    public static CrossServerMessage fromJson(String raw) {
        JSONObject json = new JSONObject(raw);
        return new CrossServerMessage(
                json.getString("messageId"),
                json.optString("correlationId", json.getString("messageId")),
                json.getString("type"),
                json.getString("source"),
                json.getString("target"),
                json.getString("action"),
                json.optJSONObject("payload"),
                json.optString("status", null),
                json.optString("errorType", null),
                json.optString("errorMessage", null)
        );
    }

    private static Throwable unwrap(Throwable throwable) {
        Throwable current = throwable;
        while ((current instanceof CompletionException || current instanceof ExecutionException) && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    public String toJson() {
        JSONObject json = new JSONObject();
        json.put("version", VERSION);
        json.put("messageId", messageId);
        json.put("correlationId", correlationId);
        json.put("type", type);
        json.put("source", source);
        json.put("target", target);
        json.put("action", action);
        json.put("payload", payload);

        if (status != null) {
            json.put("status", status);
        }

        if (errorType != null) {
            json.put("errorType", errorType);
        }

        if (errorMessage != null) {
            json.put("errorMessage", errorMessage);
        }

        return json.toString();
    }

    public String getMessageId() {
        return messageId;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public String getType() {
        return type;
    }

    public String getSource() {
        return source;
    }

    public String getTarget() {
        return target;
    }

    public String getAction() {
        return action;
    }

    public JSONObject getPayload() {
        return new JSONObject(payload.toString());
    }

    public String getStatus() {
        return status;
    }

    public String getErrorType() {
        return errorType;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
