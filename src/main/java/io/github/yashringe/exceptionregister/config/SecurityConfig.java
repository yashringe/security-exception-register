package io.github.yashringe.exceptionregister.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Role-based access. The domain checks the finer rules (four-eyes, "only the requester may submit"); this
 * layer makes sure an auditor can never even reach a write endpoint.
 *
 * <p>CSRF protection is off because the API is stateless (HTTP Basic, no session cookie). With a browser UI
 * that uses cookies it must be switched back on.
 */
@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain api(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(Customizer.withDefaults())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health/**", "/error").permitAll()
                        .requestMatchers("/api/audit/**", "/api/exceptions/*/audit").hasAnyRole("AUDITOR", "RISK_OFFICER")
                        .requestMatchers(HttpMethod.POST, "/api/exceptions/*/approve", "/api/exceptions/*/reject")
                            .hasRole("RISK_OFFICER")
                        .requestMatchers(HttpMethod.POST, "/api/exceptions/*/close")
                            .hasAnyRole("REQUESTER", "RISK_OFFICER")
                        .requestMatchers(HttpMethod.POST, "/api/exceptions", "/api/exceptions/*/submit",
                                "/api/exceptions/*/renew").hasRole("REQUESTER")
                        .requestMatchers(HttpMethod.GET, "/api/**").authenticated()
                        .anyRequest().denyAll());
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    /**
     * Demo identities: alice and olivia file requests (olivia owns the systems), bob and carol are risk
     * officers, dave is an auditor. Turn off with DEMO_USERS_ENABLED=false and plug in OIDC instead.
     */
    @Bean
    @ConditionalOnProperty(name = "register.demo-users.enabled", havingValue = "true", matchIfMissing = true)
    UserDetailsService demoUsers(PasswordEncoder encoder, @Value("${register.demo-users.password}") String password) {
        String hash = encoder.encode(password);
        return new InMemoryUserDetailsManager(
                User.withUsername("alice").password(hash).roles("REQUESTER").build(),
                User.withUsername("olivia").password(hash).roles("REQUESTER").build(),
                User.withUsername("bob").password(hash).roles("RISK_OFFICER").build(),
                User.withUsername("carol").password(hash).roles("RISK_OFFICER").build(),
                User.withUsername("dave").password(hash).roles("AUDITOR").build());
    }
}
