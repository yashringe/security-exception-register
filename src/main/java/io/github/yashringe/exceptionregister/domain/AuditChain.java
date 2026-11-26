package io.github.yashringe.exceptionregister.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Builds and checks the hash chain of the audit trail.
 *
 * <p>This makes tampering <em>detectable</em>, not impossible: someone with database access could rewrite the
 * whole chain. Real deployments anchor the latest hash somewhere else (a WORM bucket, a signed daily export).
 * See docs/adr/0003-hash-chained-audit-log.md.
 */
public final class AuditChain {

    /** Hash used as "previous" for the very first entry. */
    public static final String GENESIS = "0".repeat(64);

    private AuditChain() {
    }

    public static AuditEntry next(AuditEntry previous, UUID exceptionId, String action, String actor,
                                  Instant at, String details) {
        long sequence = previous == null ? 1 : previous.sequence() + 1;
        String previousHash = previous == null ? GENESIS : previous.hash();
        String safeDetails = details == null ? "" : details;
        String hash = hash(sequence, exceptionId, action, actor, at, safeDetails, previousHash);
        return new AuditEntry(sequence, exceptionId, action, actor, at, safeDetails, previousHash, hash);
    }

    /**
     * Checks the chain in sequence order.
     *
     * @return the sequence number of the first entry that does not match, or empty if the chain is intact
     */
    public static Optional<Long> firstBrokenEntry(List<AuditEntry> entriesInOrder) {
        String expectedPrevious = GENESIS;
        long expectedSequence = 1;
        for (AuditEntry e : entriesInOrder) {
            String recomputed = hash(e.sequence(), e.exceptionId(), e.action(), e.actor(), e.at(), e.details(),
                    e.previousHash());
            boolean ok = e.sequence() == expectedSequence
                    && e.previousHash().equals(expectedPrevious)
                    && e.hash().equals(recomputed);
            if (!ok) {
                return Optional.of(e.sequence());
            }
            expectedPrevious = e.hash();
            expectedSequence++;
        }
        return Optional.empty();
    }

    static String hash(long sequence, UUID exceptionId, String action, String actor, Instant at, String details,
                       String previousHash) {
        // Length-prefix every field so that ("ab","c") and ("a","bc") can never produce the same input.
        StringBuilder canonical = new StringBuilder();
        for (String field : new String[] {Long.toString(sequence), String.valueOf(exceptionId), action, actor,
                at.toString(), details, previousHash}) {
            String value = field == null ? "" : field;
            canonical.append(value.length()).append(':').append(value).append('|');
        }
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(sha256.digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available on the JVM", e);
        }
    }
}
