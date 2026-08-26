package com.group11.compostsystem.config;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class SecurityHeadersFilter implements Filter {

    private static final String CONTENT_SECURITY_POLICY = String.join("; ",
            "default-src 'self'",
            "script-src 'self'",
            "style-src 'self' 'unsafe-inline'",
            "img-src 'self' data:",
            "font-src 'self' data:",
            "connect-src 'self' http://localhost:8080 http://127.0.0.1:8080",
            "base-uri 'self'",
            "frame-ancestors 'none'",
            "form-action 'self'"
    );

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (response instanceof HttpServletResponse httpResponse) {
            httpResponse.setHeader("Content-Security-Policy", CONTENT_SECURITY_POLICY);
            httpResponse.setHeader("X-Content-Type-Options", "nosniff");
            httpResponse.setHeader("X-Frame-Options", "DENY");
            httpResponse.setHeader("Referrer-Policy", "no-referrer");
            httpResponse.setHeader("Permissions-Policy", "camera=(), microphone=(), geolocation=()");
            httpResponse.setHeader("X-XSS-Protection", "0");
        }

        chain.doFilter(request, response);
    }
}
