package io.github.yashringe.exceptionregister.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AuditChainTest {

    private static final UUID ID = UUID.fromString("7f1c2d4e-0000-4000-8000-000000000001");
    private static final Instant T = Instant.parse("2026-12-01T09:00:00Z");

    private static List<AuditEntry> chainOf(int n) {
        List<AuditEntry> chain = new ArrayList<>();
        AuditEntry prev = null;
        for (int i = 0; i < n; i++) {
            prev = AuditChain.next(prev, ID, "ACTION_" + i, "alice", T.plusSeconds(i), "detail " + i);
            chain.add(prev);
        }
        return chain;
    }

    @Test
    void firstEntryPointsToGenesis() {
        AuditEntry first = AuditChain.next(null, ID, "CREATED", "alice", T, "");
        assertEquals(1, first.sequence());
        assertEquals(AuditChain.GENESIS, first.previousHash());
        assertEquals(64, first.hash().length());
    }

    @Test
    void intactChainVerifies() {
        assertEquals(Optional.empty(), AuditChain.firstBrokenEntry(chainOf(5)));
    }

    @Test
    void editedDetailsAreDetected() {
        List<AuditEntry> chain = chainOf(5);
        AuditEntry e = chain.get(2);
        chain.set(2, new AuditEntry(e.sequence(), e.exceptionId(), e.action(), "mallory", e.at(), e.details(),
                e.previousHash(), e.hash()));
        assertEquals(Optional.of(3L), AuditChain.firstBrokenEntry(chain));
    }

    @Test
    void deletedEntryIsDetected() {
        List<AuditEntry> chain = chainOf(5);
        chain.remove(1);
        assertEquals(Optional.of(3L), AuditChain.firstBrokenEntry(chain));
    }

    @Test
    void rehashedEntryStillBreaksTheNextLink() {
        List<AuditEntry> chain = chainOf(4);
        AuditEntry old = chain.get(1);
        AuditEntry forged = AuditChain.next(chain.get(0), ID, "APPROVED", "mallory", old.at(), old.details());
        chain.set(1, forged);
        assertEquals(Optional.of(3L), AuditChain.firstBrokenEntry(chain));
    }

    @Test
    void fieldBoundariesCannotBeShifted() {
        String a = AuditChain.hash(1, ID, "ab", "c", T, "", AuditChain.GENESIS);
        String b = AuditChain.hash(1, ID, "a", "bc", T, "", AuditChain.GENESIS);
        assertNotEquals(a, b);
    }
}
