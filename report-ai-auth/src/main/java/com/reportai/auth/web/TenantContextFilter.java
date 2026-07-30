package com.reportai.auth.web;

import com.reportai.auth.context.AuthenticationContext;
import com.reportai.auth.model.TenantPrincipal;
import com.reportai.auth.port.AuthenticationRequest;
import com.reportai.auth.port.AuthenticationResolver;
import com.reportai.common.exception.BusinessException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Enumeration;
import java.util.Optional;
import java.util.Objects;

/**
 * Spring Web adapter that establishes and always clears the authenticated tenant context.
 *
 * <p>It does not read {@code tenantId} from a query parameter, path variable or HTTP header. The
 * resolver receives only the sanitized bearer credential and request metadata and must derive the
 * tenant from verified server-side identity data.</p>
 */
public final class TenantContextFilter extends OncePerRequestFilter {
    private static final String BEARER_PREFIX = "Bearer ";
    private static final int MAX_TOKEN_LENGTH = 16_384;
    private final AuthenticationResolver authenticationResolver;

    public TenantContextFilter(AuthenticationResolver authenticationResolver) {
        this.authenticationResolver = Objects.requireNonNull(authenticationResolver, "authenticationResolver");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        // Defensive cleanup also protects a reused container thread if an upstream filter failed.
        AuthenticationContext.clear();
        try {
            AuthenticationRequest authenticationRequest = toAuthenticationRequest(request);
            Optional<TenantPrincipal> principal = Objects.requireNonNull(
                    authenticationResolver.resolve(authenticationRequest), "authenticationResolver result");
            principal.ifPresent(AuthenticationContext::set);
            filterChain.doFilter(request, response);
        } finally {
            AuthenticationContext.clear();
        }
    }

    private AuthenticationRequest toAuthenticationRequest(HttpServletRequest request) {
        String authorization = readSingleAuthorizationHeader(request);
        String bearerToken = null;
        if (authorization != null) {
            if (authorization.isBlank()) {
                throw new BusinessException(com.reportai.common.api.ErrorCode.UNAUTHORIZED,
                        "invalid authorization credential");
            }
            if (!authorization.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
                throw new BusinessException(com.reportai.common.api.ErrorCode.UNAUTHORIZED,
                        "unsupported authorization scheme");
            }
            bearerToken = authorization.substring(BEARER_PREFIX.length()).trim();
            if (bearerToken.isEmpty() || bearerToken.length() > MAX_TOKEN_LENGTH) {
                throw new BusinessException(com.reportai.common.api.ErrorCode.UNAUTHORIZED,
                        "invalid authorization credential");
            }
        }
        return new AuthenticationRequest(bearerToken, request.getMethod(), request.getRequestURI(),
                request.getRemoteAddr());
    }

    private String readSingleAuthorizationHeader(HttpServletRequest request) {
        Enumeration<String> values = request.getHeaders("Authorization");
        if (values == null || !values.hasMoreElements()) {
            return null;
        }
        String value = values.nextElement();
        if (values.hasMoreElements()) {
            // Multiple credentials are ambiguous and can be abused by proxy/container disagreement.
            throw new BusinessException(com.reportai.common.api.ErrorCode.UNAUTHORIZED,
                    "ambiguous authorization credential");
        }
        return value;
    }
}
