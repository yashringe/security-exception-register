package io.github.yashringe.exceptionregister.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.yashringe.exceptionregister.domain.Approval;
import io.github.yashringe.exceptionregister.domain.CompensatingControl;
import io.github.yashringe.exceptionregister.domain.ExceptionRequest;
import io.github.yashringe.exceptionregister.domain.ExceptionStatus;
import io.github.yashringe.exceptionregister.domain.RiskScore;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Plain JDBC on purpose: the domain object has no JPA annotations, and every write is an explicit SQL statement
 * that a reviewer can read. Updates use optimistic locking on {@code version}, so two risk officers approving
 * at the same moment cannot overwrite each other.
 */
@Repository
public class ExceptionRepository {

    private static final TypeReference<List<CompensatingControl>> CONTROLS = new TypeReference<>() { };
    private static final TypeReference<List<Approval>> APPROVALS = new TypeReference<>() { };

    private final JdbcClient jdbc;
    private final ObjectMapper json;
    private final RowMapper<ExceptionRequest> rowMapper;

    public ExceptionRepository(JdbcClient jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
        this.rowMapper = (rs, n) -> ExceptionRequest.restore(
                rs.getObject("id", UUID.class),
                rs.getString("title"),
                rs.getString("asset_id"),
                rs.getString("policy_reference"),
                rs.getString("justification"),
                rs.getString("requester"),
                rs.getString("owner"),
                new RiskScore(rs.getInt("inherent_likelihood"), rs.getInt("inherent_impact")),
                read(rs.getString("controls"), CONTROLS),
                rs.getObject("requested_expiry", LocalDate.class),
                instant(rs.getObject("created_at", OffsetDateTime.class)),
                ExceptionStatus.valueOf(rs.getString("status")),
                read(rs.getString("approvals"), APPROVALS),
                instant(rs.getObject("submitted_at", OffsetDateTime.class)),
                instant(rs.getObject("decided_at", OffsetDateTime.class)),
                rs.getString("decision_note"),
                rs.getInt("renewal_count"),
                rs.getLong("version"));
    }

    public void insert(ExceptionRequest e) {
        jdbc.sql("""
                INSERT INTO exception_request (id, title, asset_id, policy_reference, justification, requester, owner,
                    inherent_likelihood, inherent_impact, controls, approvals, requested_expiry, status, created_at,
                    submitted_at, decided_at, decision_note, renewal_count, version)
                VALUES (:id, :title, :assetId, :policy, :justification, :requester, :owner, :likelihood, :impact,
                    CAST(:controls AS jsonb), CAST(:approvals AS jsonb), :expiry, :status, :createdAt,
                    :submittedAt, :decidedAt, :note, :renewals, 0)
                """)
                .param("id", e.id())
                .param("title", e.title())
                .param("assetId", e.assetId())
                .param("policy", e.policyReference())
                .param("justification", e.justification())
                .param("requester", e.requester())
                .param("owner", e.owner())
                .param("likelihood", e.inherentRisk().likelihood())
                .param("impact", e.inherentRisk().impact())
                .param("controls", write(e.controls()))
                .param("approvals", write(e.approvals()))
                .param("expiry", e.requestedExpiry())
                .param("status", e.status().name())
                .param("createdAt", offset(e.createdAt()))
                .param("submittedAt", offset(e.submittedAt()))
                .param("decidedAt", offset(e.decidedAt()))
                .param("note", e.decisionNote())
                .param("renewals", e.renewalCount())
                .update();
    }

    /** Writes the mutable fields. Fails if someone else changed the row since it was loaded. */
    public void update(ExceptionRequest e) {
        int rows = jdbc.sql("""
                UPDATE exception_request
                   SET approvals = CAST(:approvals AS jsonb), requested_expiry = :expiry, status = :status,
                       submitted_at = :submittedAt, decided_at = :decidedAt, decision_note = :note,
                       renewal_count = :renewals, version = version + 1
                 WHERE id = :id AND version = :version
                """)
                .param("approvals", write(e.approvals()))
                .param("expiry", e.requestedExpiry())
                .param("status", e.status().name())
                .param("submittedAt", offset(e.submittedAt()))
                .param("decidedAt", offset(e.decidedAt()))
                .param("note", e.decisionNote())
                .param("renewals", e.renewalCount())
                .param("id", e.id())
                .param("version", e.version())
                .update();
        if (rows != 1) {
            throw new OptimisticLockingFailureException(
                    "Exception " + e.id() + " was changed by someone else, reload and try again");
        }
    }

    public Optional<ExceptionRequest> findById(UUID id) {
        return jdbc.sql("SELECT * FROM exception_request WHERE id = :id")
                .param("id", id)
                .query(rowMapper)
                .optional();
    }

    public List<ExceptionRequest> findAll(Optional<ExceptionStatus> status) {
        if (status.isPresent()) {
            return jdbc.sql("SELECT * FROM exception_request WHERE status = :status ORDER BY created_at DESC")
                    .param("status", status.get().name())
                    .query(rowMapper)
                    .list();
        }
        return jdbc.sql("SELECT * FROM exception_request ORDER BY created_at DESC").query(rowMapper).list();
    }

    /** Approved exceptions whose expiry date lies before {@code today}. */
    public List<ExceptionRequest> findApprovedExpiringBefore(LocalDate today) {
        return jdbc.sql("SELECT * FROM exception_request WHERE status = 'APPROVED' AND requested_expiry < :today")
                .param("today", today)
                .query(rowMapper)
                .list();
    }

    private String write(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not serialise " + value, ex);
        }
    }

    private <T> T read(String value, TypeReference<T> type) {
        try {
            return json.readValue(value, type);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Corrupt JSON column: " + value, ex);
        }
    }

    private static OffsetDateTime offset(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }

    private static Instant instant(OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }
}
