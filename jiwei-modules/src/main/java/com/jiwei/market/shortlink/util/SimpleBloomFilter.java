package com.jiwei.market.shortlink.util;

import java.util.BitSet;

/**
 * 布隆过滤器：BitSet + 多个不同 seed 的 MurmurHash。
 */
public class SimpleBloomFilter {

    private final BitSet bits;
    private final int bitSize;
    private final int hashCount;

    public SimpleBloomFilter(long expectedInsertions, double fpp) {
        if (expectedInsertions <= 0 || fpp <= 0 || fpp >= 1) {
            throw new IllegalArgumentException("invalid bloom filter arguments");
        }
        long m = (long) Math.ceil(-expectedInsertions * Math.log(fpp) / (Math.log(2) * Math.log(2)));
        this.bitSize = (int) Math.min(Math.max(m, 64), Integer.MAX_VALUE - 8L);
        int k = (int) Math.round(((double) bitSize / expectedInsertions) * Math.log(2));
        this.hashCount = Math.min(Math.max(k, 1), 16);
        this.bits = new BitSet(bitSize);
    }

    public void put(String value) {
        for (int i = 1; i <= hashCount; i++) {
            bits.set(indexOf(value, i));
        }
    }

    public boolean mightContain(String value) {
        for (int i = 1; i <= hashCount; i++) {
            if (!bits.get(indexOf(value, i))) {
                return false;
            }
        }
        return true;
    }

    private int indexOf(String value, int seed) {
        return (int) (MurmurHash.hash32Unsigned(value, seed) % bitSize);
    }
}
