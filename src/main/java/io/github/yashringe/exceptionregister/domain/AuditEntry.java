package io.github.yashringe.exceptionregister.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * One line of the audit trail. {@code hash} covers this entry's fields plus the previous entry's hash, so
 * changing or deleting any older line breaks every hash after it.
 */
public record AuditEntry(long sequence, UUID exceptionId, String action, String actor, Instant at,
                         String details, String previousHash, String hash) {
}
