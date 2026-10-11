package com.jiwei.market.shortlink.util;

/**
 * Base62 编解码：把无符号 64 位整数转成 0-9a-zA-Z 组成的短码。
 */
public final class Base62 {

    private static final String ALPHABET = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final int BASE = ALPHABET.length();

    private Base62() {
    }

    public static String encode(long value) {
        if (value == 0L) {
            return String.valueOf(ALPHABET.charAt(0));
        }
        StringBuilder sb = new StringBuilder();
        long remaining = value;
        while (remaining != 0L) {
            int digit = (int) Long.remainderUnsigned(remaining, BASE);
            sb.append(ALPHABET.charAt(digit));
            remaining = Long.divideUnsigned(remaining, BASE);
        }
        return sb.reverse().toString();
    }

    public static long decode(String code) {
        if (code == null || code.isEmpty()) {
            throw new IllegalArgumentException("code must not be empty");
        }
        long value = 0L;
        for (int i = 0; i < code.length(); i++) {
            int digit = ALPHABET.indexOf(code.charAt(i));
            if (digit < 0) {
                throw new IllegalArgumentException("invalid base62 char: " + code.charAt(i));
            }
            value = value * BASE + digit;
        }
        return value;
    }
}
