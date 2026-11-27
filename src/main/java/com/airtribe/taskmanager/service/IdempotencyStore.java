package com.airtribe.taskmanager.service;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import com.airtribe.taskmanager.exception.ConflictException;

/**
 * Singleton store for idempotent operations.
 *
 * <p>Each entry remembers the fingerprint of the request that created it and
 * the successful response. Retrying with the same key and fingerprint returns
 * the stored response; retrying with the same key but different contents throws
 * a 422 {@link ConflictException}.
 *
 * <p>{@link #execute} is synchronized so two concurrent requests with the same
 * key cannot both create a resource.
 */
public final class IdempotencyStore {

    private static final IdempotencyStore INSTANCE = new IdempotencyStore();

    private final Map<String, StoredRecord> records = new HashMap<>();

    private IdempotencyStore() {
    }

    public static IdempotencyStore getInstance() {
        return INSTANCE;
    }

    /**
     * Runs {@code operation} at most once for a given key.
     *
     * <p>A null or blank key disables idempotency and simply runs the operation.
     * If the operation throws, nothing is stored, so the client can fix the
     * request and retry with the same key.
     */
    public synchronized <T> T execute(String key, String fingerprint, Supplier<T> operation) {
        if (key == null || key.isBlank()) {
            return operation.get();
        }

        var existing = records.get(key);
        if (existing != null) {
            if (!existing.fingerprint().equals(fingerprint)) {
                throw new ConflictException(
                        "Idempotency key '" + key + "' was already used with a different request");
            }

            return castResponse(existing.response());
        }

        var response = operation.get();
        records.put(key, new StoredRecord(fingerprint, response));
        return response;
    }

    public synchronized Optional<StoredRecord> get(String key) {
        return Optional.ofNullable(records.get(key));
    }

    public synchronized int size() {
        return records.size();
    }

    /** Primarily useful for tests and demos. */
    public synchronized void clear() {
        records.clear();
    }

    /**
     * Builds a collision-resistant fingerprint from the supplied values. Each
     * value is length-prefixed so that values containing the separator cannot
     * produce the same fingerprint as a different combination.
     */
    public static String fingerprint(Object... parts) {
        var builder = new StringBuilder();

        for (Object part : parts) {
            var value = part == null ? "<null>" : String.valueOf(part);
            builder.append(value.length()).append(':').append(value).append('|');
        }

        return builder.toString();
    }

    @SuppressWarnings("unchecked")
    private static <T> T castResponse(Object response) {
        return (T) response;
    }

    /** Fingerprint of the request plus the response produced by that request. */
    public record StoredRecord(String fingerprint, Object response) {
    }
}
