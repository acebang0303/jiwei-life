package com.jiwei.framework.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;
import java.util.Date;
import java.util.Map;

/**
 * JWT工具类
 * 使用 jjwt 0.12+ API 生成和解析令牌
 */
public class JwtUtil {

    /**
     * 生成JWT令牌
     *
     * @param secretKey 密钥(Base64编码的字符串)
     * @param ttlMillis 过期时间(毫秒)
     * @param claims    自定义声明
     * @return JWT令牌字符串
     */
    public static String createJWT(String secretKey, long ttlMillis, Map<String, Object> claims) {
        //1、生成密钥
        byte[] keyBytes = Base64.getDecoder().decode(secretKey);
        SecretKey key = new SecretKeySpec(keyBytes, "HmacSHA256");

        //2、计算过期时间
        long now = System.currentTimeMillis();
        Date issuedAt = new Date(now);
        Date expiration = new Date(now + ttlMillis);

        //3、构建JWT
        return Jwts.builder()
                .claims(claims)
                .issuedAt(issuedAt)
                .expiration(expiration)
                .signWith(key)
                .compact();
    }

    /**
     * 解析JWT令牌
     *
     * @param secretKey 密钥(Base64编码的字符串)
     * @param token     JWT令牌
     * @return JWT中的声明数据
     */
    public static Claims parseJWT(String secretKey, String token) {
        //1、生成密钥
        byte[] keyBytes = Base64.getDecoder().decode(secretKey);
        SecretKey key = new SecretKeySpec(keyBytes, "HmacSHA256");

        //2、解析JWT
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
