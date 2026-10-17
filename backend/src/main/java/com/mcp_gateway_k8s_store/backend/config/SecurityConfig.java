package com.mcp_gateway_k8s_store.backend.config;

import com.mcp_gateway_k8s_store.backend.security.ApiKeyAuthenticationFilter;
import com.mcp_gateway_k8s_store.backend.service.ApiKeyService;
import com.mcp_gateway_k8s_store.backend.service.AppUserDetailsService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandlerImpl;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.authentication.DisabledException;

import java.util.ArrayList;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
    @Bean
    PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }

    @Bean
    AuthenticationManager authenticationManager(AppUserDetailsService users, PasswordEncoder encoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(users);
        provider.setPasswordEncoder(encoder);
        return new ProviderManager(provider);
    }

    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter(AppUserDetailsService users) {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            var user = users.loadUserByUsername(jwt.getSubject());
            if (!user.isEnabled()) throw new DisabledException("Account is disabled");
            return new ArrayList<GrantedAuthority>(user.getAuthorities());
        });
        return converter;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationConverter jwtAuthenticationConverter,
                                            ApiKeyService apiKeys) throws Exception {
        BearerTokenResolver jwtOnlyResolver = request -> {
            String authorization = request.getHeader("Authorization");
            if (authorization != null && authorization.regionMatches(true, 0, "Bearer ", 0, 7)
                    && authorization.substring(7).trim().startsWith(ApiKeyAuthenticationFilter.TOKEN_PREFIX)) return null;
            return new DefaultBearerTokenResolver().resolve(request);
        };
        http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
                        .accessDeniedHandler(new AccessDeniedHandlerImpl()))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/api/auth/login", "/api/auth/accept-invitation", "/api/health", "/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers("/mcp", "/mcp/**").authenticated()
                        .requestMatchers("/api/keys/**").authenticated()
                        .requestMatchers("/api/team/**").hasRole("ADMIN")
                        .requestMatchers("/api/stores/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/github/tools/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/woocommerce/products").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/woocommerce/orders/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .oauth2ResourceServer(resource -> resource
                        .bearerTokenResolver(jwtOnlyResolver)
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)))
                .addFilterBefore(new ApiKeyAuthenticationFilter(apiKeys), BearerTokenAuthenticationFilter.class);
        return http.build();
    }
}
