package com.rbac.security;

import com.rbac.model.User;
import com.rbac.repository.UserRepository;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Optional;

@Component
public class OAuth2LoginSuccessHandler extends SavedRequestAwareAuthenticationSuccessHandler {

    private static final Logger logger = LoggerFactory.getLogger(OAuth2LoginSuccessHandler.class);

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private UserRepository userRepository;

    @Value("${cors.origin.pattern}")
    private String frontendUrl;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {

        OAuth2AuthenticationToken authToken = (OAuth2AuthenticationToken) authentication;
        OAuth2User oAuth2User = authToken.getPrincipal();

        String email = oAuth2User.getAttribute("email");
        String fullName = oAuth2User.getAttribute("name");

        logger.info("OAuth2 login attempt for email: {}", email);

        // Verify user exists and is active — same guard as your original
        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isEmpty() || !userOpt.get().getIsActive()) {
            logger.warn("Unauthorized login attempt for email: {}", email);
            response.sendRedirect(frontendUrl + "/login?error=unauthorized");
            return;
        }

        User user = userOpt.get();

        // Auto-update name if changed in Google profile
        if (fullName != null && !fullName.equals(user.getFullName())) {
            user.setFullName(fullName);
            userRepository.save(user);
        }

        // Generate JWT — same call signature as your original generateToken()
        String jwt = jwtTokenProvider.generateToken(
                user.getId(),
                user.getEmail(),
                user.getRole().getId()
        );

        logger.info("JWT generated successfully for user: {}", email);

        // Redirect to frontend with token — same pattern as your original
        response.sendRedirect(frontendUrl + "/login-success?token=" + jwt);
    }
}
