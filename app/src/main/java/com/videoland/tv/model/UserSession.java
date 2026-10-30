package com.videoland.tv.model;

import android.content.Context;
import android.content.SharedPreferences;

public class UserSession {
    private static final String PREF_NAME = "videoland_tv_pref";
    private static final String KEY_TOKEN = "jwt_token";
    private static final String KEY_USERNAME = "username";
    private static final String KEY_IS_SUBSCRIBED = "is_subscribed";

    private static UserSession instance;
    private final SharedPreferences prefs;

    private UserSession(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public static synchronized UserSession getInstance(Context context) {
        if (instance == null) {
            instance = new UserSession(context);
        }
        return instance;
    }

    public boolean isLoggedIn() {
        String token = getToken();
        return token != null && !token.trim().isEmpty();
    }

    public String getToken() {
        return prefs.getString(KEY_TOKEN, "");
    }

    public void setToken(String token) {
        prefs.edit().putString(KEY_TOKEN, token).apply();
    }

    public String getUsername() {
        return prefs.getString(KEY_USERNAME, "");
    }

    public void setUsername(String username) {
        prefs.edit().putString(KEY_USERNAME, username).apply();
    }

    public boolean isSubscribed() {
        return prefs.getBoolean(KEY_IS_SUBSCRIBED, false);
    }

    public void setSubscribed(boolean subscribed) {
        prefs.edit().putBoolean(KEY_IS_SUBSCRIBED, subscribed).apply();
    }

    public void clear() {
        prefs.edit().clear().apply();
    }
}
