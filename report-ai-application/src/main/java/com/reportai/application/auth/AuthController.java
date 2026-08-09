package com.reportai.application.auth;

import com.reportai.api.auth.AuthTokenResponse;
import com.reportai.api.auth.LoginRequest;
import com.reportai.api.auth.LogoutRequest;
import com.reportai.api.auth.RefreshTokenRequest;
import com.reportai.api.auth.RegisterRequest;
import com.reportai.common.api.Result;
import com.reportai.common.trace.TraceContext;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public local-authentication endpoints. */
@RestController
@RequestMapping("/api/v1/auth")
@ConditionalOnProperty(prefix = "report-ai.auth.local", name = "enabled", havingValue = "true")
public final class AuthController {
    private final AuthenticationApplicationService service;

    public AuthController(AuthenticationApplicationService service) {
        this.service = service;
    }

    @PostMapping("/register")
    public Result<AuthTokenResponse> register(@Valid @RequestBody RegisterRequest request,
                                              @RequestHeader(name = "User-Agent", required = false) String userAgent) {
        return Result.success(service.register(request, userAgent), TraceContext.getOrCreate());
    }

    @PostMapping("/login")
    public Result<AuthTokenResponse> login(@Valid @RequestBody LoginRequest request,
                                           @RequestHeader(name = "User-Agent", required = false) String userAgent) {
        return Result.success(service.login(request, userAgent), TraceContext.getOrCreate());
    }

    @PostMapping("/refresh")
    public Result<AuthTokenResponse> refresh(@Valid @RequestBody RefreshTokenRequest request,
                                             @RequestHeader(name = "User-Agent", required = false) String userAgent) {
        return Result.success(service.refresh(request, userAgent), TraceContext.getOrCreate());
    }

    @PostMapping("/logout")
    public Result<Boolean> logout(@Valid @RequestBody LogoutRequest request) {
        service.logout(request);
        return Result.success(Boolean.TRUE, TraceContext.getOrCreate());
    }
}
