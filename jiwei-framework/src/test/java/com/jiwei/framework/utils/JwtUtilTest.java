package com.jiwei.framework.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JwtUtilTest {

    private static final String SECRET = Base64.getEncoder()
            .encodeToString("jiwei-life-test-secret-key-0123456789".getBytes());

    @Test
    void createAndParseRoundTrip() {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", 1001L);
        claims.put("role", "user");

        String token = JwtUtil.createJWT(SECRET, 60_000L, claims);
        Claims parsed = JwtUtil.parseJWT(SECRET, token);

        assertEquals(1001L, ((Number) parsed.get("userId")).longValue());
        assertEquals("user", parsed.get("role"));
    }

    @Test
    void parseExpiredTokenThrows() {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", 1L);

        String token = JwtUtil.createJWT(SECRET, -1000L, claims);

        assertThrows(ExpiredJwtException.class, () -> JwtUtil.parseJWT(SECRET, token));
    }
}
