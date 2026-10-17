package com.mcp_gateway_k8s_store.backend.security;

import com.mcp_gateway_k8s_store.backend.model.AppUser;
import com.mcp_gateway_k8s_store.backend.service.ApiKeyService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {
    public static final String TOKEN_PREFIX = "mcpk_";
    private final ApiKeyService apiKeys;

    public ApiKeyAuthenticationFilter(ApiKeyService apiKeys) { this.apiKeys = apiKeys; }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization != null && authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            String token = authorization.substring(7).trim();
            if (token.startsWith(TOKEN_PREFIX)) {
                try {
                    AppUser user = apiKeys.authenticate(token);
                    var authentication = new UsernamePasswordAuthenticationToken(user.getUsername(), null,
                            List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                } catch (BadCredentialsException exception) {
                    SecurityContextHolder.clearContext();
                    response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid bearer credentials");
                    return;
                }
            }
        }
        chain.doFilter(request, response);
    }
}
