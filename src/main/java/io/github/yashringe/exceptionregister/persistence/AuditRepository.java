package io.github.yashringe.exceptionregister.persistence;

import io.github.yashringe.exceptionregister.domain.AuditChain;
import io.github.yashringe.exceptionregister.domain.AuditEntry;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Repository
public class AuditRepository {

    /** Arbitrary constant for the PostgreSQL advisory lock that serialises appends. */
    private static final long APPEND_LOCK = 4_242_001L;

    private static final RowMapper<AuditEntry> ROW = (rs, n) -> new AuditEntry(
            rs.getLong("sequence"),
            rs.getObject("exception_id", UUID.class),
            rs.getString("action"),
            rs.getString("actor"),
            rs.getObject("at", OffsetDateTime.class).toInstant(),
            rs.getString("details"),
            rs.getString("previous_hash"),
            rs.getString("hash"));

    private final JdbcClient jdbc;

    public AuditRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Appends one entry. Must run inside the caller's transaction: the advisory lock is held until commit, so
     * two writers can never both read the same "last entry" and fork the chain.
     */
    public AuditEntry append(UUID exceptionId, String action, String actor, Instant at, String details) {
        jdbc.sql("SELECT pg_advisory_xact_lock(:key)").param("key", APPEND_LOCK).query().singleRow();
        AuditEntry last = jdbc.sql("SELECT * FROM audit_entry ORDER BY sequence DESC LIMIT 1")
                .query(ROW)
                .optional()
                .orElse(null);
        AuditEntry entry = AuditChain.next(last, exceptionId, action, actor, at, details);
        jdbc.sql("""
                INSERT INTO audit_entry (sequence, exception_id, action, actor, at, details, previous_hash, hash)
                VALUES (:seq, :exceptionId, :action, :actor, :at, :details, :prev, :hash)
                """)
                .param("seq", entry.sequence())
                .param("exceptionId", entry.exceptionId())
                .param("action", entry.action())
                .param("actor", entry.actor())
                .param("at", entry.at().atOffset(ZoneOffset.UTC))
                .param("details", entry.details())
                .param("prev", entry.previousHash())
                .param("hash", entry.hash())
                .update();
        return entry;
    }

    public List<AuditEntry> findByException(UUID exceptionId) {
        return jdbc.sql("SELECT * FROM audit_entry WHERE exception_id = :id ORDER BY sequence")
                .param("id", exceptionId)
                .query(ROW)
                .list();
    }

    public List<AuditEntry> findAllInOrder() {
        return jdbc.sql("SELECT * FROM audit_entry ORDER BY sequence").query(ROW).list();
    }
}
