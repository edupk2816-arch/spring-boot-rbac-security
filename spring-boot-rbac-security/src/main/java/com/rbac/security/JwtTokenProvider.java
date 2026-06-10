package com.rbac.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Collections;
import java.util.Date;

@Component
public class JwtTokenProvider {

    private static final Logger logger = LoggerFactory.getLogger(JwtTokenProvider.class);

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.expiration}")
    private long jwtExpiration;

    // Called from OAuth2LoginSuccessHandler — same signature as your original
    public String generateToken(Integer userId, String email, Integer roleId) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + jwtExpiration);

        return Jwts.builder()
                .setSubject(email)
                .claim("userId", userId)
                .claim("roleId", roleId)
                .setIssuedAt(now)
                .setExpiration(expiry)
                .signWith(getSignKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parserBuilder()
                    .setSigningKey(getSignKey())
                    .build()
                    .parseClaimsJws(token);
            return true;
        } catch (ExpiredJwtException e) {
            logger.warn("JWT expired: {}", e.getMessage());
        } catch (UnsupportedJwtException e) {
            logger.warn("Unsupported JWT: {}", e.getMessage());
        } catch (MalformedJwtException e) {
            logger.warn("Malformed JWT: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            logger.warn("JWT claims empty: {}", e.getMessage());
        }
        return false;
    }

    // Used in UserAccessFilter to set SecurityContext — same as your original
    public Authentication getAuthentication(String token) {
        Claims claims = extractAllClaims(token);
        String email = claims.getSubject();
        Integer roleId = claims.get("roleId", Integer.class);

        String roleAuthority = "ROLE_" + roleId;
        SimpleGrantedAuthority authority = new SimpleGrantedAuthority(roleAuthority);

        return new UsernamePasswordAuthenticationToken(
                email,
                null,
                Collections.singletonList(authority)
        );
    }

    public String getEmailFromToken(String token) {
        try {
            return extractAllClaims(token).getSubject();
        } catch (Exception e) {
            logger.error("Could not extract email from token: {}", e.getMessage());
            return null;
        }
    }

    public Integer getRoleIdFromToken(String token) {
        return extractAllClaims(token).get("roleId", Integer.class);
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSignKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    private Key getSignKey() {
        byte[] keyBytes = Decoders.BASE64.decode(jwtSecret);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
