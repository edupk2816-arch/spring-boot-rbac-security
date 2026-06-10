package com.rbac.config;

import com.rbac.repository.UserRepository;
import com.rbac.security.OAuth2LoginSuccessHandler;
import com.rbac.security.UserAccessFilter;
import jakarta.servlet.Filter;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final Logger logger = LoggerFactory.getLogger(SecurityConfig.class);

    @Autowired
    private OAuth2LoginSuccessHandler oauth2LoginSuccessHandler;

    @Value("${cors.origin.pattern}")
    private String allowedOrigin;

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            UserAccessFilter userAccessFilter) throws Exception {

        http
                // Logout config — same as your original
                .logout(logout -> logout
                        .logoutUrl("/api/auth/logout")
                        .logoutSuccessUrl("/login")
                        .invalidateHttpSession(true)
                        .deleteCookies("JSESSIONID")
                )

                // Disable CSRF — stateless JWT app
                .csrf(csrf -> csrf.disable())

                // Stateless session — JWT handles auth
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )

                // Return 401 for unauthenticated requests
                .exceptionHandling(ex ->
                        ex.authenticationEntryPoint(
                                (request, response, authException) ->
                                        response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized")
                        )
                )

                // OAuth2 login with your success handler
                .oauth2Login(oauth2 ->
                        oauth2.successHandler(oauth2LoginSuccessHandler)
                )

                // Public endpoints — same whitelist as your original
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/oauth2/**",
                                "/login/**",
                                "/public/**",
                                "/api/auth/logout",
                                "/favicon.ico",
                                "/assets/**",
                                "/static/**"
                        ).permitAll()
                        .anyRequest().authenticated()
                )

                // Register UserAccessFilter before UsernamePasswordAuthenticationFilter
                .addFilterBefore(userAccessFilter, UsernamePasswordAuthenticationFilter.class)

                // Request logging filter — same as your original (uncomment logger line to enable)
                .addFilterBefore(requestLoggingFilter(), UsernamePasswordAuthenticationFilter.class)

                // CORS config
                .cors(cors -> cors.configurationSource(corsConfigurationSource()));

        return http.build();
    }

    // Request logging filter — same as your original
    @Bean
    public Filter requestLoggingFilter() {
        return (request, response, chain) -> {
            jakarta.servlet.http.HttpServletRequest req = (jakarta.servlet.http.HttpServletRequest) request;
            logger.debug(">>> Incoming request: {} {}", req.getMethod(), req.getRequestURI());
            chain.doFilter(request, response);
        };
    }

    // CORS — same as your original
    @Bean
    public UrlBasedCorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowCredentials(true);
        config.addAllowedOrigin(allowedOrigin);
        config.addAllowedHeader("*");
        config.addAllowedMethod("*");

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    // Custom OAuth2 user service — validates user exists in DB before allowing login
    @Bean
    public OAuth2UserService<OAuth2UserRequest, OAuth2User> customOAuth2UserService(
            UserRepository userRepository) {
        return userRequest -> {
            OAuth2User oAuth2User = new DefaultOAuth2UserService().loadUser(userRequest);
            String email = oAuth2User.getAttribute("email");

            if (email == null) {
                throw new OAuth2AuthenticationException("Email not provided by OAuth2 provider");
            }

            // Validate user exists in DB — same guard as your original
            userRepository.findByEmail(email)
                    .orElseThrow(() -> new OAuth2AuthenticationException("User not registered in system"));

            return oAuth2User;
        };
    }

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    // UserAccessFilter bean — same @Bean pattern as your original
    @Bean
    public UserAccessFilter userAccessFilter(AuthenticationManager authenticationManager) {
        return new UserAccessFilter(authenticationManager);
    }
}
