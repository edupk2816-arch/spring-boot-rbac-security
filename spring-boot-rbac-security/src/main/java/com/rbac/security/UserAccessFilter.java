package com.rbac.security;

import com.rbac.model.Page;
import com.rbac.model.User;
import com.rbac.repository.PageRepository;
import com.rbac.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.util.AntPathMatcher;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class UserAccessFilter extends BasicAuthenticationFilter {

    private static final Logger logger = LoggerFactory.getLogger(UserAccessFilter.class);

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PageRepository pageRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    public UserAccessFilter(AuthenticationManager authenticationManager) {
        super(authenticationManager);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws IOException, ServletException {

        String requestPath = request.getRequestURI();

        // Skip public endpoints — same as your original isPublicApi()
        if (isPublicApi(requestPath)) {
            chain.doFilter(request, response);
            return;
        }

        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Missing JWT token");
            return;
        }

        String token = authHeader.substring(7);

        if (!jwtTokenProvider.validateToken(token)) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid or expired JWT");
            return;
        }

        String email = jwtTokenProvider.getEmailFromToken(token);

        if (email == null) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid JWT claims");
            return;
        }

        // Set authentication in SecurityContext — same as your original
        Authentication authentication = jwtTokenProvider.getAuthentication(token);
        SecurityContextHolder.getContext().setAuthentication(authentication);

        // RBAC check — same hasApiAccess logic as your original
        boolean hasAccess = hasApiAccess(email, requestPath);
        if (!hasAccess) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.getWriter().write("Access Denied: You do not have permission to access this resource.");
            return;
        }

        chain.doFilter(request, response);
    }

    private boolean hasApiAccess(String email, String endpoint) {
        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isEmpty()) {
            logger.warn("User not found for email: {}", email);
            return false;
        }

        User user = userOpt.get();

        if (user.getRole() == null) {
            logger.warn("No role assigned to user: {}", email);
            return false;
        }

        // Fetch allowed pages for this role — same approach as your original
        List<Integer> roleIds = List.of(user.getRole().getId());
        List<Page> allowedPages = pageRepository.findPagesByRoleIds(roleIds);

        Set<String> allowedApis = allowedPages.stream()
                .flatMap(p -> p.getApiUrlsList().stream())
                .collect(Collectors.toSet());

        // AntPathMatcher for wildcard matching e.g. /api/users/**
        AntPathMatcher pathMatcher = new AntPathMatcher();
        for (String allowedApi : allowedApis) {
            if (pathMatcher.match(allowedApi, endpoint)) {
                return true;
            }
        }

        logger.warn("Access denied for user: {} on endpoint: {}", email, endpoint);
        return false;
    }

    private boolean isPublicApi(String path) {
        return path.startsWith("/oauth2")
                || path.startsWith("/login")
                || path.startsWith("/public")
                || path.equals("/favicon.ico")
                || path.startsWith("/assets")
                || path.startsWith("/static")
                || path.equals("/api/auth/logout");
    }
}
