package com.videoland.tv.auth;

import android.net.Uri;
import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Bounded experimental Google OAuth sign-in manager using observed official flow.
 * Note: Interoperability experiment using known iOS public client ID and server audience.
 * Does not claim official authorization or native Android sign-in support.
 */
public class GoogleAuthManager {
    private static final String TAG = "GoogleAuthManager";

    // Known observed public iOS client ID & backend server audience from runtime evidence
    public static final String CLIENT_ID = "31016857607-1sojlm96c9s5op9ovcsokmesl2rb5gr4.apps.googleusercontent.com";
    public static final String SERVER_AUDIENCE = "31016857607-3t8pfj1rv3grrf8dhg2tjsf6v50qaiks.apps.googleusercontent.com";
    public static final String REDIRECT_URI = "com.googleusercontent.apps.31016857607-1sojlm96c9s5op9ovcsokmesl2rb5gr4:/oauth2callback";
    public static final String CUSTOM_SCHEME = "com.googleusercontent.apps.31016857607-1sojlm96c9s5op9ovcsokmesl2rb5gr4";
    public static final String CUSTOM_PATH = "/oauth2callback";

    private static final String AUTH_ENDPOINT = "https://accounts.google.com/o/oauth2/v2/auth";
    private static final String TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token";
    private static final String SCOPE = "email profile";

    // Bounded expiry for pending authorization attempt (10 minutes)
    public static final long ATTEMPT_EXPIRY_MS = 10 * 60 * 1000L;

    private static GoogleAuthManager instance;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final SecureRandom secureRandom = new SecureRandom();

    // In-memory pending attempt holder. Never persisted to disk or SharedPreferences.
    // Process death will fail closed, requiring user retry.
    private PendingAttempt currentAttempt;

    public static class PendingAttempt {
        private final String state;
        private final String codeVerifier;
        private final String nonce;
        private final long createdAt;

        public PendingAttempt(String state, String codeVerifier, String nonce, long createdAt) {
            this.state = state;
            this.codeVerifier = codeVerifier;
            this.nonce = nonce;
            this.createdAt = createdAt;
        }

        public String getState() {
            return state;
        }

        public String getCodeVerifier() {
            return codeVerifier;
        }

        public String getNonce() {
            return nonce;
        }

        public long getCreatedAt() {
            return createdAt;
        }

        public boolean isExpired(long maxAgeMs, long nowMs) {
            return (nowMs - createdAt) > maxAgeMs;
        }
    }

    public interface TokenExchangeCallback {
        void onSuccess(String idToken);
        void onError(String error);
    }

    private GoogleAuthManager() {
    }

    public static synchronized GoogleAuthManager getInstance() {
        if (instance == null) {
            instance = new GoogleAuthManager();
        }
        return instance;
    }

    /**
     * Creates a new pending authorization attempt with State, PKCE (S256), and Nonce.
     * Rotates any existing attempt and stores only in memory.
     *
     * @return Formatted authorization URL string to be opened in external browser.
     */
    public synchronized String buildAuthorizationUrl() throws Exception {
        // 1. Generate code_verifier (32 random bytes -> 43 base64url chars)
        byte[] verifierBytes = new byte[32];
        secureRandom.nextBytes(verifierBytes);
        String codeVerifier = Base64Url.encode(verifierBytes);

        // 2. Generate code_challenge = Base64Url(SHA256(ASCII(code_verifier)))
        MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
        byte[] challengeBytes = sha256.digest(codeVerifier.getBytes(StandardCharsets.US_ASCII));
        String codeChallenge = Base64Url.encode(challengeBytes);

        // 3. Generate cryptographic state and nonce (32 random bytes each)
        byte[] stateBytes = new byte[32];
        secureRandom.nextBytes(stateBytes);
        String state = Base64Url.encode(stateBytes);

        byte[] nonceBytes = new byte[32];
        secureRandom.nextBytes(nonceBytes);
        String nonce = Base64Url.encode(nonceBytes);

        // 4. Store in-memory pending attempt with rotation
        this.currentAttempt = new PendingAttempt(state, codeVerifier, nonce, System.currentTimeMillis());

        // 5. Construct Google OAuth 2.0 authorization URL
        return AUTH_ENDPOINT + "?client_id=" + URLEncoder.encode(CLIENT_ID, "UTF-8")
                + "&response_type=code"
                + "&redirect_uri=" + URLEncoder.encode(REDIRECT_URI, "UTF-8")
                + "&scope=" + URLEncoder.encode(SCOPE, "UTF-8")
                + "&state=" + URLEncoder.encode(state, "UTF-8")
                + "&code_challenge=" + URLEncoder.encode(codeChallenge, "UTF-8")
                + "&code_challenge_method=S256"
                + "&nonce=" + URLEncoder.encode(nonce, "UTF-8")
                + "&audience=" + URLEncoder.encode(SERVER_AUDIENCE, "UTF-8");
    }

    /**
     * Creates a new pending authorization attempt and returns Android Uri.
     */
    public synchronized Uri createAuthorizationUri() throws Exception {
        return Uri.parse(buildAuthorizationUrl());
    }

    /**
     * Consumes and clears the pending attempt matching the supplied state.
     * Strictly single-use: once consumed, subsequent calls return null to prevent replay attacks.
     *
     * @param callbackState State parameter received from callback.
     * @return Matched PendingAttempt, or null if invalid, expired, or mismatched.
     */
    public synchronized PendingAttempt consumePendingAttempt(String callbackState) {
        if (currentAttempt == null || callbackState == null || callbackState.isEmpty()) {
            currentAttempt = null;
            return null;
        }

        long now = System.currentTimeMillis();
        if (currentAttempt.isExpired(ATTEMPT_EXPIRY_MS, now)) {
            currentAttempt = null;
            return null;
        }

        // Constant-time state comparison to mitigate timing attacks
        byte[] expected = currentAttempt.getState().getBytes(StandardCharsets.UTF_8);
        byte[] actual = callbackState.getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expected, actual)) {
            currentAttempt = null;
            return null;
        }

        PendingAttempt matched = currentAttempt;
        currentAttempt = null; // Single-use consumption
        return matched;
    }

    public synchronized void clearPendingAttempt() {
        this.currentAttempt = null;
    }

    /**
     * Validates callback URL string to permit only the expected scheme and path.
     * Prevents open redirects or loading arbitrary browser URLs.
     */
    public static boolean isValidCallbackUrl(String url) {
        if (url == null || url.isEmpty()) {
            return false;
        }
        return url.equals(REDIRECT_URI) || url.startsWith(REDIRECT_URI + "?");
    }

    public static boolean isValidCallbackUri(Uri uri) {
        if (uri == null) {
            return false;
        }
        return isValidCallbackUrl(uri.toString());
    }

    /**
     * Executes standard authorization_code exchange to Google token endpoint over TLS.
     * Validates ID token claims (issuer, audience, expiry, nonce) from trusted Google TLS response.
     */
    public void exchangeCodeForTokens(String code, String codeVerifier, String expectedNonce, TokenExchangeCallback callback) {
        executor.execute(() -> {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(TOKEN_ENDPOINT);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
                conn.setRequestProperty("User-Agent", "VideolandTV/1.0");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);
                conn.setDoOutput(true);

                String body = "grant_type=" + URLEncoder.encode("authorization_code", "UTF-8")
                        + "&client_id=" + URLEncoder.encode(CLIENT_ID, "UTF-8")
                        + "&redirect_uri=" + URLEncoder.encode(REDIRECT_URI, "UTF-8")
                        + "&code=" + URLEncoder.encode(code, "UTF-8")
                        + "&code_verifier=" + URLEncoder.encode(codeVerifier, "UTF-8")
                        + "&audience=" + URLEncoder.encode(SERVER_AUDIENCE, "UTF-8");

                byte[] postData = body.getBytes(StandardCharsets.UTF_8);
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(postData);
                    os.flush();
                }

                int codeStatus = conn.getResponseCode();
                InputStream is = (codeStatus >= 200 && codeStatus < 300) ? conn.getInputStream() : conn.getErrorStream();
                String responseBody = readStream(is);

                if (codeStatus < 200 || codeStatus >= 300) {
                    // Extract bounded error description without logging or leaking tokens
                    String boundedErr = "HTTP " + codeStatus;
                    try {
                        JSONObject errJson = new JSONObject(responseBody);
                        String err = errJson.optString("error", null);
                        if (err != null && !err.isEmpty()) {
                            boundedErr += " (" + err + ")";
                        }
                    } catch (Exception ignored) {
                    }
                    callback.onError(boundedErr);
                    return;
                }

                JSONObject tokenJson = new JSONObject(responseBody);
                String idToken = tokenJson.optString("id_token", null);
                if (idToken == null || idToken.isEmpty()) {
                    callback.onError("Google Token 回應缺少 id_token 欄位");
                    return;
                }

                // Validate ID token claims from trusted Google TLS response
                validateIdTokenClaims(idToken, expectedNonce);

                // Validation succeeded. Do not log idToken or credentials.
                callback.onSuccess(idToken);
            } catch (SecurityException se) {
                callback.onError("ID Token 檢驗失敗: " + se.getMessage());
            } catch (Exception e) {
                Log.e(TAG, "Token exchange network exception: " + e.getClass().getSimpleName());
                callback.onError("Token 交換網路錯誤: " + e.getMessage());
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }
        });
    }

    /**
     * Validates ID token issuer, audience, expiry, and nonce claims.
     * Note: Claims are parsed from trusted direct TLS response from https://oauth2.googleapis.com/token.
     * Base64 parsing is claim validation, not cryptographic offline RSA signature verification.
     * Authoritative signature verification is performed by official Videoland backend.
     */
    public static void validateIdTokenClaims(String idToken, String expectedNonce) throws SecurityException {
        if (idToken == null || idToken.isEmpty()) {
            throw new SecurityException("ID Token 為空");
        }
        String[] parts = idToken.split("\\.");
        if (parts.length != 3) {
            throw new SecurityException("ID Token 結構不符 JWT 規範");
        }

        try {
            byte[] payloadBytes = Base64Url.decode(parts[1]);
            String payloadJson = new String(payloadBytes, StandardCharsets.UTF_8);

            String iss = "";
            long exp = 0;
            String aud = "";
            String azp = "";
            String tokenNonce = null;

            JSONObject claims = new JSONObject(payloadJson);
            iss = claims.optString("iss", "");
            exp = claims.optLong("exp", 0);
            aud = claims.optString("aud", "");
            tokenNonce = claims.optString("nonce", null);

            // 1. Issuer check
            if (!"https://accounts.google.com".equals(iss) && !"accounts.google.com".equals(iss)) {
                throw new SecurityException("發行者 (iss) 不符預期: " + iss);
            }

            // 2. Expiration check
            long nowSec = System.currentTimeMillis() / 1000L;
            if (exp <= nowSec) {
                throw new SecurityException("Token 已過期 (exp: " + exp + ", now: " + nowSec + ")");
            }

            // 3. Audience / azp check
            boolean audValid = SERVER_AUDIENCE.equals(aud);
            if (!audValid) {
                throw new SecurityException("受眾 (aud/azp) 不符預期");
            }

            // 4. Nonce check
            if (expectedNonce != null && !expectedNonce.isEmpty()) {
                if (tokenNonce == null || !MessageDigest.isEqual(expectedNonce.getBytes(StandardCharsets.UTF_8), tokenNonce.getBytes(StandardCharsets.UTF_8))) {
                    throw new SecurityException("Nonce 比對不符");
                }
            }
        } catch (SecurityException se) {
            throw se;
        } catch (Exception e) {
            throw new SecurityException("解析 ID Token Claims 失敗: " + e.getMessage());
        }
    }

    private static String extractJsonStringClaim(String json, String key) {
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("\"" + java.util.regex.Pattern.quote(key) + "\"\\s*:\\s*\"([^\"]*)\"");
        java.util.regex.Matcher m = p.matcher(json);
        return m.find() ? m.group(1) : "";
    }

    private static long extractJsonLongClaim(String json, String key) {
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("\"" + java.util.regex.Pattern.quote(key) + "\"\\s*:\\s*(\\d+)");
        java.util.regex.Matcher m = p.matcher(json);
        if (m.find()) {
            try {
                return Long.parseLong(m.group(1));
            } catch (NumberFormatException ignored) {}
        }
        return 0;
    }

    private static String readStream(InputStream is) throws Exception {
        if (is == null) return "";
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();
        }
    }
}
