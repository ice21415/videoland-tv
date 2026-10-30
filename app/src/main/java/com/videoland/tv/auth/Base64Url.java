package com.videoland.tv.auth;

import java.util.Arrays;

/**
 * Standard RFC 4648 URL-safe Base64 encoder and decoder without padding.
 * Provides consistent behavior across Android API levels and standard JVM testing.
 */
public final class Base64Url {

    private static final char[] ENCODE_TABLE =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_".toCharArray();

    private static final byte[] DECODE_TABLE = new byte[128];

    static {
        Arrays.fill(DECODE_TABLE, (byte) -1);
        for (int i = 0; i < 64; i++) {
            DECODE_TABLE[ENCODE_TABLE[i]] = (byte) i;
        }
    }

    private Base64Url() {
    }

    public static String encode(byte[] data) {
        if (data == null || data.length == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder((data.length * 4 + 2) / 3);
        int i = 0;
        int len = data.length;
        while (i < len) {
            int b0 = data[i++] & 0xFF;
            int b1 = (i < len) ? (data[i++] & 0xFF) : -1;
            int b2 = (i < len) ? (data[i++] & 0xFF) : -1;

            sb.append(ENCODE_TABLE[b0 >>> 2]);
            if (b1 != -1) {
                sb.append(ENCODE_TABLE[((b0 & 0x03) << 4) | (b1 >>> 4)]);
                if (b2 != -1) {
                    sb.append(ENCODE_TABLE[((b1 & 0x0F) << 2) | (b2 >>> 6)]);
                    sb.append(ENCODE_TABLE[b2 & 0x3F]);
                } else {
                    sb.append(ENCODE_TABLE[(b1 & 0x0F) << 2]);
                }
            } else {
                sb.append(ENCODE_TABLE[(b0 & 0x03) << 4]);
            }
        }
        return sb.toString();
    }

    public static byte[] decode(String str) {
        if (str == null || str.isEmpty()) {
            return new byte[0];
        }
        String clean = str.trim().replace("=", "");
        int len = clean.length();
        int outLen = (len * 3) / 4;
        byte[] out = new byte[outLen];
        int inIdx = 0;
        int outIdx = 0;

        while (inIdx < len) {
            int c0 = clean.charAt(inIdx++);
            int c1 = (inIdx < len) ? clean.charAt(inIdx++) : -1;
            int c2 = (inIdx < len) ? clean.charAt(inIdx++) : -1;
            int c3 = (inIdx < len) ? clean.charAt(inIdx++) : -1;

            int v0 = (c0 >= 0 && c0 < 128) ? DECODE_TABLE[c0] : -1;
            int v1 = (c1 >= 0 && c1 < 128) ? DECODE_TABLE[c1] : -1;
            int v2 = (c2 >= 0 && c2 < 128) ? DECODE_TABLE[c2] : -1;
            int v3 = (c3 >= 0 && c3 < 128) ? DECODE_TABLE[c3] : -1;

            if (v0 == -1 || v1 == -1) {
                break;
            }

            out[outIdx++] = (byte) ((v0 << 2) | (v1 >>> 4));
            if (v2 != -1) {
                if (outIdx < outLen) {
                    out[outIdx++] = (byte) (((v1 & 0x0F) << 4) | (v2 >>> 2));
                }
                if (v3 != -1) {
                    if (outIdx < outLen) {
                        out[outIdx++] = (byte) (((v2 & 0x03) << 6) | v3);
                    }
                }
            }
        }

        if (outIdx < outLen) {
            byte[] trimmed = new byte[outIdx];
            System.arraycopy(out, 0, trimmed, 0, outIdx);
            return trimmed;
        }
        return out;
    }
}
