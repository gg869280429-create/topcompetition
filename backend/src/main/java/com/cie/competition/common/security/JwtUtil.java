package com.cie.competition.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 生成与解析工具。
 */
@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expire-hours:24}")
    private long expireHours;

    @Value("${jwt.header:Authorization}")
    private String header;

    @Value("${jwt.prefix:Bearer }")
    private String prefix;

    private SecretKey key;

    @PostConstruct
    public void init() {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(Long userId, String username, String role) {
        return generateToken(userId, username, role, 0L);
    }

    public String generateToken(Long userId, String username, String role, Long tokenVersion) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("username", username)
                .claim("role", role)
                .claim("tokenVersion", tokenVersion == null ? 0L : tokenVersion)
                .issuedAt(new Date(now))
                .expiration(new Date(now + expireHours * 3600 * 1000))
                .signWith(key)
                .compact();
    }

    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String resolveToken(HttpServletRequest request) {
        String bearer = request.getHeader(header);
        if (StringUtils.hasText(bearer) && bearer.startsWith(prefix)) {
            return bearer.substring(prefix.length());
        }
        return null;
    }

    public boolean validateToken(String token) {
        try {
            Claims claims = parseToken(token);
            return claims.getExpiration().after(new Date());
        } catch (Exception e) {
            return false;
        }
    }
}
