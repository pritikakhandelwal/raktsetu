package com.raktsetu.server.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;

/**
 * Session-based sign-in for the single-page app. Every {@code /api} call needs a signed-in user except signing in
 * and first-time setup; unauthenticated calls get 401 (not a redirect). Health checks and the API documentation are
 * public; metrics are for administrators. Accounts with a one-time password can only
 * change their password until they do. CSRF protection uses the standard single-page-app setup: the
 * token is in the {@code XSRF-TOKEN} cookie and the front end echoes it in the {@code X-XSRF-TOKEN} header.
 */
@Configuration
public class SecurityConfig {

    /**
     * Only this site's own scripts run; the map tiles are the one outside resource. Inline styles are allowed because
     * React and the map set element styles directly.
     */
    static final String CONTENT_SECURITY_POLICY = "default-src 'self'; script-src 'self'; "
            + "style-src 'self' 'unsafe-inline'; img-src 'self' data: https://tile.openstreetmap.org; "
            + "connect-src 'self'; font-src 'self' data:; object-src 'none'; base-uri 'self'; form-action 'self'; "
            + "frame-ancestors 'none'";

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, SecurityContextRepository contexts, UserRepository users)
            throws Exception {
        http
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/api/auth/login", "/api/auth/me", "/api/setup", "/api/privacy").permitAll()
                        .requestMatchers("/api/**").authenticated()
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers("/actuator/**").hasRole(Role.ADMIN.name())
                        .anyRequest().permitAll())
                .headers(h -> h
                        .contentSecurityPolicy(c -> c.policyDirectives(CONTENT_SECURITY_POLICY))
                        .referrerPolicy(r -> r.policy(ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                        .permissionsPolicyHeader(p -> p.policy("camera=(), microphone=(), geolocation=()")))
                .csrf(c -> c.spa())
                .addFilterAfter(new CsrfCookieFilter(), CsrfFilter.class)
                .addFilterAfter(new PasswordChangeFilter(users), AuthorizationFilter.class)
                .securityContext(s -> s.securityContextRepository(contexts))
                .exceptionHandling(e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(l -> l.logoutUrl("/api/auth/logout")
                        .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT)));
        return http.build();
    }

    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    UserDetailsService userDetailsService(UserRepository users) {
        return username -> users.find(username)
                .map(a -> User.withUsername(a.user().username()).password(a.passwordHash())
                        .roles(a.user().role().name()).disabled(!a.enabled()).build())
                .orElseThrow(() -> new UsernameNotFoundException(username));
    }

    @Bean
    AuthenticationManager authenticationManager(UserDetailsService users, PasswordEncoder encoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(users);
        provider.setPasswordEncoder(encoder);
        return new ProviderManager(provider);
    }
}
