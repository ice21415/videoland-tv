package com.videoland.tv.auth;

import org.junit.After;
import org.junit.Test;
import org.json.JSONObject;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import static org.junit.Assert.*;

public class GoogleAuthManagerTest {
    @After public void clearAttempt() { GoogleAuthManager.getInstance().clearPendingAttempt(); }

    private String parameter(String url, String name) throws Exception {
        for (String pair : url.substring(url.indexOf('?') + 1).split("&")) {
            String[] parts = pair.split("=", 2);
            if (parts[0].equals(name)) return URLDecoder.decode(parts[1], "UTF-8");
        }
        throw new AssertionError("Missing parameter: " + name);
    }

    @Test public void generatedPkceMatchesVerifierAndStateIsSingleUse() throws Exception {
        GoogleAuthManager manager = GoogleAuthManager.getInstance();
        String url = manager.buildAuthorizationUrl();
        String state = parameter(url, "state");
        GoogleAuthManager.PendingAttempt pending = manager.consumePendingAttempt(state);
        assertNotNull(pending);
        String challenge = Base64Url.encode(MessageDigest.getInstance("SHA-256")
                .digest(pending.getCodeVerifier().getBytes(StandardCharsets.US_ASCII)));
        assertEquals(challenge, parameter(url, "code_challenge"));
        assertEquals("S256", parameter(url, "code_challenge_method"));
        assertNull(manager.consumePendingAttempt(state));
    }

    @Test public void newAttemptInvalidatesPreviousState() throws Exception {
        GoogleAuthManager manager = GoogleAuthManager.getInstance();
        String oldState = parameter(manager.buildAuthorizationUrl(), "state");
        String newState = parameter(manager.buildAuthorizationUrl(), "state");
        assertNotEquals(oldState, newState);
        assertNull(manager.consumePendingAttempt(oldState));
        assertNull(manager.consumePendingAttempt(newState));
    }

    @Test public void onlyExactCallbackIsAccepted() {
        String redirect = GoogleAuthManager.REDIRECT_URI;
        assertTrue(GoogleAuthManager.isValidCallbackUrl(redirect + "?code=test&state=test"));
        assertFalse(GoogleAuthManager.isValidCallbackUrl(redirect + "/extra?code=test"));
        assertFalse(GoogleAuthManager.isValidCallbackUrl("https://example.com/?next=" + redirect));
        assertFalse(GoogleAuthManager.isValidCallbackUrl(null));
    }

    private String claims(String audience, String nonce, long expiry) throws Exception {
        JSONObject data = new JSONObject().put("iss", "https://accounts.google.com")
                .put("aud", audience).put("exp", expiry);
        if (nonce != null) data.put("nonce", nonce);
        // Synthetic claim fixture: deliberately not a valid signed ID token.
        return "test." + Base64Url.encode(data.toString().getBytes(StandardCharsets.UTF_8)) + ".test";
    }

    @Test public void validatesExpectedClaims() throws Exception {
        GoogleAuthManager.validateIdTokenClaims(claims(GoogleAuthManager.SERVER_AUDIENCE,
                "test-nonce", System.currentTimeMillis()/1000 + 600), "test-nonce");
    }

    @Test(expected=SecurityException.class) public void rejectsWrongAudience() throws Exception {
        GoogleAuthManager.validateIdTokenClaims(claims("other-client", "test-nonce",
                System.currentTimeMillis()/1000 + 600), "test-nonce");
    }

    @Test(expected=SecurityException.class) public void rejectsMissingNonce() throws Exception {
        GoogleAuthManager.validateIdTokenClaims(claims(GoogleAuthManager.SERVER_AUDIENCE,
                null, System.currentTimeMillis()/1000 + 600), "test-nonce");
    }

    @Test(expected=SecurityException.class) public void rejectsExpiredClaims() throws Exception {
        GoogleAuthManager.validateIdTokenClaims(claims(GoogleAuthManager.SERVER_AUDIENCE,
                "test-nonce", System.currentTimeMillis()/1000 - 60), "test-nonce");
    }
}
