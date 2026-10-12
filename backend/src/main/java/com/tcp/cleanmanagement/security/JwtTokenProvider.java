package com.tcp.cleanmanagement.security;

import com.tcp.cleanmanagement.entity.User;
import com.tcp.cleanmanagement.enums.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;

/**
 * HS256 JWT 발급/검증.
 * 토큰에는 식별 정보(userId, username, role)만 담고, 담당 구역은 요청마다 DB 에서 조회한다.
 * (구역 권한을 바꿨을 때 재로그인 없이 즉시 반영되도록)
 */
@Component
public class JwtTokenProvider {

    private final SecretKey key;
    private final Duration validity;

    public JwtTokenProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration-minutes:480}") long expirationMinutes) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException("jwt.secret 은 최소 32바이트(256bit) 이상이어야 합니다.");
        }
        this.key = Keys.hmacShaKeyFor(bytes);
        this.validity = Duration.ofMinutes(expirationMinutes);
    }

    public String createToken(User user) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim("username", user.getUsername())
                .claim("role", user.getRole().name())
                .issuedAt(now)
                .expiration(new Date(now.getTime() + validity.toMillis()))
                .signWith(key)
                .compact();
    }

    public long getValiditySeconds() {
        return validity.toSeconds();
    }

    /**
     * 서명/만료를 검증하고 클레임을 반환한다.
     * @throws JwtException 위조, 만료, 형식 오류
     */
    public ParsedToken parse(String token) {
        Claims claims = Jwts.parser().verifyWith(key).build()
                .parseSignedClaims(token)
                .getPayload();
        try {
            return new ParsedToken(
                    Long.valueOf(claims.getSubject()),
                    claims.get("username", String.class),
                    Role.valueOf(claims.get("role", String.class)));
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new JwtException("Invalid token claims", e);
        }
    }

    public record ParsedToken(Long userId, String username, Role role) {}
}
