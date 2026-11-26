package io.github.yashringe.exceptionregister.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Full stack against a real PostgreSQL: HTTP, security, service, SQL, Flyway, trigger. */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class ExceptionApiIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    JdbcClient jdbc;

    private static MockHttpServletRequestBuilder as(String user, MockHttpServletRequestBuilder request) {
        return request.with(httpBasic(user, "demo")).contentType(MediaType.APPLICATION_JSON);
    }

    private String createMediumRequest() throws Exception {
        String body = """
                {"title":"Legacy settlement server misses OS patch","assetId":"SRV-LEG-042",
                 "policyReference":"POL-PATCH-01 3.2","justification":"Vendor upgrade certified for Q2 only",
                 "owner":"olivia","likelihood":3,"impact":3,
                 "controls":[{"description":"Host moved to isolated VLAN","effectiveness":"PARTIAL"}],
                 "requestedExpiry":"%s"}
                """.formatted(LocalDate.now().plusDays(60));
        String response = mvc.perform(as("alice", post("/api/exceptions")).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.residualRating").value("MEDIUM"))
                .andReturn().getResponse().getContentAsString();
        return json.readTree(response).get("id").asText();
    }

    @Test
    void fullLifecycleIsAuditedAndChainVerifies() throws Exception {
        String id = createMediumRequest();

        mvc.perform(as("alice", post("/api/exceptions/" + id + "/submit")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUBMITTED"));

        mvc.perform(as("bob", post("/api/exceptions/" + id + "/approve")).content("{\"comment\":\"ok until Q2\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        String audit = mvc.perform(as("dave", get("/api/exceptions/" + id + "/audit")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode entries = json.readTree(audit);
        assertThat(entries).hasSize(3);
        assertThat(entries.get(2).get("action").asText()).isEqualTo("APPROVAL_RECORDED");

        mvc.perform(as("dave", get("/api/audit/verify")))
                .andExpect(jsonPath("$.intact").value(true));

        mvc.perform(as("dave", get("/api/kri")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.indicators.length()").value(5));

        mvc.perform(as("dave", get("/api/kri.csv")))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/csv"));
    }

    @Test
    void rolesAndRulesAreEnforced() throws Exception {
        String id = createMediumRequest();
        mvc.perform(as("alice", post("/api/exceptions/" + id + "/submit"))).andExpect(status().isOk());

        // alice has no RISK_OFFICER role: blocked by the security layer
        mvc.perform(as("alice", post("/api/exceptions/" + id + "/approve"))).andExpect(status().isForbidden());
        // dave is an auditor: may read, never write
        mvc.perform(as("dave", post("/api/exceptions"))
                .content("{}")).andExpect(status().isForbidden());
        // rejecting without a reason breaks a domain rule
        mvc.perform(as("bob", post("/api/exceptions/" + id + "/reject")).content("{\"comment\":\"\"}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void expiryLongerThanThePolicyIsRefusedOnSubmit() throws Exception {
        String body = """
                {"title":"Admin console without MFA","assetId":"APP-ADM-7","policyReference":"POL-IAM-04",
                 "justification":"SSO integration pending","owner":"olivia","likelihood":4,"impact":4,
                 "controls":[{"description":"IP allow-list","effectiveness":"UNTESTED"}],
                 "requestedExpiry":"%s"}
                """.formatted(LocalDate.now().plusDays(200));
        String response = mvc.perform(as("alice", post("/api/exceptions")).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String id = json.readTree(response).get("id").asText();

        mvc.perform(as("alice", post("/api/exceptions/" + id + "/submit")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("at most 90 days")));
    }

    @Test
    void databaseRefusesToRewriteTheAuditTrail() throws Exception {
        createMediumRequest();
        assertThatThrownBy(() -> jdbc.sql("UPDATE audit_entry SET actor = 'mallory'").update())
                .hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.sql("DELETE FROM audit_entry").update())
                .hasMessageContaining("append-only");
    }

    @Test
    void healthIsPublicButTheApiIsNot() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mvc.perform(get("/api/exceptions")).andExpect(status().isUnauthorized());
    }
}
