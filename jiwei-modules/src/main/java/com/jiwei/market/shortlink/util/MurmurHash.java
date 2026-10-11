package com.jiwei.market.shortlink.util;

import java.nio.charset.StandardCharsets;

/**
 * MurmurHash3 x86_32 实现，并提供 64 位组合哈希（消除 url_hash 32 位碰撞）。
 */
public final class MurmurHash {

    private static final int C1 = 0xcc9e2d51;
    private static final int C2 = 0x1b873593;

    private MurmurHash() {
    }

    /** 64 位哈希：两个不同 seed 的 32 位哈希组合，避免 32 位哈希碰撞 */
    public static long hash64(String input) {
        long h1 = hash32Unsigned(input, 0);
        long h2 = hash32Unsigned(input, 1);
        return (h1 << 32) | h2;
    }

    public static long hash32Unsigned(String input) {
        return hash32(input.getBytes(StandardCharsets.UTF_8), 0) & 0xffffffffL;
    }

    public static long hash32Unsigned(String input, int seed) {
        return hash32(input.getBytes(StandardCharsets.UTF_8), seed) & 0xffffffffL;
    }

    public static int hash32(byte[] data, int seed) {
        int hash = seed;
        int length = data.length;
        int blockCount = length >>> 2;

        for (int i = 0; i < blockCount; i++) {
            int offset = i << 2;
            int k = (data[offset] & 0xff)
                    | ((data[offset + 1] & 0xff) << 8)
                    | ((data[offset + 2] & 0xff) << 16)
                    | ((data[offset + 3] & 0xff) << 24);
            k *= C1;
            k = Integer.rotateLeft(k, 15);
            k *= C2;

            hash ^= k;
            hash = Integer.rotateLeft(hash, 13);
            hash = hash * 5 + 0xe6546b64;
        }

        int k1 = 0;
        int tail = blockCount << 2;
        switch (length & 3) {
            case 3:
                k1 ^= (data[tail + 2] & 0xff) << 16;
            case 2:
                k1 ^= (data[tail + 1] & 0xff) << 8;
            case 1:
                k1 ^= data[tail] & 0xff;
                k1 *= C1;
                k1 = Integer.rotateLeft(k1, 15);
                k1 *= C2;
                hash ^= k1;
            default:
        }

        hash ^= length;
        hash = fmix(hash);
        return hash;
    }

    private static int fmix(int hash) {
        hash ^= hash >>> 16;
        hash *= 0x85ebca6b;
        hash ^= hash >>> 13;
        hash *= 0xc2b2ae35;
        hash ^= hash >>> 16;
        return hash;
    }
}
