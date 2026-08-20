package org.me.newsky.test;

import org.json.JSONObject;
import org.me.newsky.messaging.CrossServerMessage;

/**
 * Wire-format guarantees the messenger relies on: the timestamp survives a round trip (the
 * stale-request filter depends on it), a message from a sender that predates the field reads
 * back as timestamp 0 (which disables the filter instead of misfiring), and responses keep the
 * correlation and error identity needed to restore domain exceptions across servers.
 */
public final class CrossServerMessageTest {

    public static void main(String[] args) {
        requestRoundTrip();
        legacyMessageWithoutTimestamp();
        failedResponseCarriesErrorIdentity();
        System.out.println("CrossServerMessageTest: ALL PASS");
    }

    private static void requestRoundTrip() {
        JSONObject payload = new JSONObject().put("islandUuid", "11111111-2222-3333-4444-555555555555");
        CrossServerMessage original = CrossServerMessage.request("server-a", "server-b", "island.load", payload);

        CrossServerMessage restored = CrossServerMessage.fromJson(original.toJson());

        Check.that(original.getMessageId().equals(restored.getMessageId()), "messageId survives round trip");
        Check.that(original.getCorrelationId().equals(restored.getCorrelationId()), "correlationId survives round trip");
        Check.that("server-a".equals(restored.getSource()) && "server-b".equals(restored.getTarget()), "source and target survive round trip");
        Check.that("island.load".equals(restored.getAction()), "action survives round trip");
        Check.that(restored.getPayload().getString("islandUuid").equals(payload.getString("islandUuid")), "payload survives round trip");
        Check.that(original.getTimestamp() > 0, "request is stamped with a creation time");
        Check.that(original.getTimestamp() == restored.getTimestamp(), "timestamp survives round trip exactly");
    }

    private static void legacyMessageWithoutTimestamp() {
        JSONObject legacy = new JSONObject();
        legacy.put("messageId", "legacy-id");
        legacy.put("type", CrossServerMessage.TYPE_REQUEST);
        legacy.put("source", "server-a");
        legacy.put("target", "server-b");
        legacy.put("action", "island.load");
        legacy.put("payload", new JSONObject());

        CrossServerMessage restored = CrossServerMessage.fromJson(legacy.toString());

        Check.that(restored.getTimestamp() == 0L, "message without timestamp reads back as 0 (stale filter disabled, not misfiring)");
        Check.that("legacy-id".equals(restored.getCorrelationId()), "correlationId falls back to messageId");
    }

    private static void failedResponseCarriesErrorIdentity() {
        CrossServerMessage request = CrossServerMessage.request("server-a", "server-b", "island.lock.toggle", new JSONObject());
        CrossServerMessage response = CrossServerMessage.failedResponse(request, new IllegalStateException("nope"));

        CrossServerMessage restored = CrossServerMessage.fromJson(response.toJson());

        Check.that(CrossServerMessage.TYPE_RESPONSE.equals(restored.getType()), "response type survives");
        Check.that(CrossServerMessage.STATUS_FAILED.equals(restored.getStatus()), "failed status survives");
        Check.that(request.getMessageId().equals(restored.getCorrelationId()), "response correlates to the request");
        Check.that(IllegalStateException.class.getName().equals(restored.getErrorType()), "error class name survives (needed to restore the exception remotely)");
        Check.that("nope".equals(restored.getErrorMessage()), "error message survives");
        Check.that("server-b".equals(restored.getSource()) && "server-a".equals(restored.getTarget()), "response routes back to the requester");
    }
}
