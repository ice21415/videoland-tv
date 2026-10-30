package com.videoland.tv.api;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.videoland.tv.model.Banner;
import com.videoland.tv.model.Category;
import com.videoland.tv.model.LiveSchedule;
import com.videoland.tv.model.StreamDetail;
import com.videoland.tv.model.UserSession;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ApiClient {
    private static final String TAG = "VideolandApiClient";
    private static final String BASE_URL = "https://vl-mobileapi.aspirapps.com";

    private static ApiClient instance;
    private final ExecutorService executor = Executors.newFixedThreadPool(4);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Context context;

    public interface Callback<T> {
        void onSuccess(T result);
        void onError(String error);
    }

    private ApiClient(Context context) {
        this.context = context.getApplicationContext();
    }

    public static synchronized ApiClient getInstance(Context context) {
        if (instance == null) {
            instance = new ApiClient(context);
        }
        return instance;
    }

    /** Verify a relayed session with the official backend before the TV stores it. */
    public void verifySessionToken(String token, Callback<Boolean> callback) {
        executor.execute(() -> {
            HttpURLConnection connection = null;
            try {
                if (token == null || token.isEmpty() || token.length() > 8192) throw new Exception();
                connection = (HttpURLConnection) new URL(BASE_URL + "/api/user_v1/subscribed_plans").openConnection();
                connection.setInstanceFollowRedirects(false);
                connection.setRequestProperty("Authorization", "Bearer " + token);
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(10000);
                boolean valid = connection.getResponseCode() == 200
                        && new JSONObject(readStream(connection.getInputStream())).optInt("code", -1) == 0;
                mainHandler.post(() -> { if (valid) callback.onSuccess(true); else callback.onError("工作階段驗證未通過"); });
            } catch (Exception ignored) {
                mainHandler.post(() -> callback.onError("無法完成登入驗證，請確認網路後重試。"));
            } finally { if (connection != null) connection.disconnect(); }
        });
    }

    public void fetchBanners(Callback<List<Banner>> callback) {
        executor.execute(() -> {
            try {
                String responseStr = executeGet("/banners", false);
                JSONObject root = new JSONObject(responseStr);
                JSONArray array = root.optJSONArray("data");
                if (array == null && root.has("banners")) {
                    array = root.optJSONArray("banners");
                }
                List<Banner> banners = new ArrayList<>();
                if (array != null) {
                    for (int i = 0; i < array.length(); i++) {
                        JSONObject obj = array.optJSONObject(i);
                        if (obj != null) {
                            banners.add(Banner.fromJson(obj));
                        }
                    }
                }
                mainHandler.post(() -> callback.onSuccess(banners));
            } catch (Exception e) {
                Log.e(TAG, "Error fetching banners", e);
                mainHandler.post(() -> callback.onError("獲取橫幅精選失敗: " + e.getMessage()));
            }
        });
    }

    public void fetchCategories(Callback<List<Category>> callback) {
        executor.execute(() -> {
            try {
                String responseStr = executeGet("/categories", false);
                JSONObject root = new JSONObject(responseStr);
                JSONArray array = root.optJSONArray("data");
                List<Category> categories = new ArrayList<>();
                if (array != null) {
                    for (int i = 0; i < array.length(); i++) {
                        JSONObject obj = array.optJSONObject(i);
                        if (obj != null) {
                            categories.add(Category.fromJson(obj));
                        }
                    }
                }
                mainHandler.post(() -> callback.onSuccess(categories));
            } catch (Exception e) {
                Log.e(TAG, "Error fetching categories", e);
                mainHandler.post(() -> callback.onError("獲取賽事分類失敗: " + e.getMessage()));
            }
        });
    }

    public void fetchLiveSchedules(long startTime, long endTime, Callback<List<LiveSchedule>> callback) {
        executor.execute(() -> {
            try {
                String path = "/live_schedules?start_time=" + startTime + "&end_time=" + endTime;
                String responseStr = executeGet(path, false);
                JSONObject root = new JSONObject(responseStr);
                JSONArray array = root.optJSONArray("data");
                List<LiveSchedule> schedules = new ArrayList<>();
                if (array != null) {
                    for (int i = 0; i < array.length(); i++) {
                        JSONObject obj = array.optJSONObject(i);
                        if (obj != null) {
                            schedules.add(LiveSchedule.fromJson(obj));
                        }
                    }
                }
                mainHandler.post(() -> callback.onSuccess(schedules));
            } catch (Exception e) {
                Log.e(TAG, "Error fetching live schedules", e);
                mainHandler.post(() -> callback.onError("獲取賽程列表失敗: " + e.getMessage()));
            }
        });
    }

    public void login(String username, String password, Callback<Boolean> callback) {
        executor.execute(() -> {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(BASE_URL + "/user/auth/login");
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("User-Agent", "VideolandTV/1.1 (Android TV)");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);
                conn.setDoOutput(true);

                JSONObject body = new JSONObject();
                body.put("username", username);
                body.put("password", password);

                byte[] postData = body.toString().getBytes(StandardCharsets.UTF_8);
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(postData);
                    os.flush();
                }

                int code = conn.getResponseCode();
                InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
                String responseBody = readStream(is);

                if (code >= 200 && code < 300) {
                    // Extract token from cookie (case-insensitive) or json
                    String token = extractCookieToken(conn);

                    if (token == null || token.isEmpty()) {
                        try {
                            JSONObject resJson = new JSONObject(responseBody);
                            if (resJson.has("data")) {
                                JSONObject dataObj = resJson.optJSONObject("data");
                                if (dataObj != null) {
                                    token = dataObj.optString("token", dataObj.optString("access_token", null));
                                }
                            }
                            if (token == null && resJson.has("token")) {
                                token = resJson.optString("token");
                            }
                        } catch (Exception ignored) {}
                    }

                    if (token != null && !token.isEmpty()) {
                        UserSession.getInstance(context).setToken(token);
                        UserSession.getInstance(context).setUsername(username);
                        mainHandler.post(() -> callback.onSuccess(true));
                        return;
                    }
                }

                // If response code is not 200 or no token found
                String errMsg = "登入失敗 (HTTP " + code + ")";
                try {
                    JSONObject errObj = new JSONObject(responseBody);
                    if (errObj.has("message")) {
                        errMsg = errObj.getString("message");
                    }
                } catch (Exception ignored) {}

                final String finalErrMsg = errMsg;
                mainHandler.post(() -> callback.onError(finalErrMsg));
            } catch (Exception e) {
                Log.e(TAG, "Login exception", e);
                mainHandler.post(() -> callback.onError("網路連線錯誤: " + e.getMessage()));
            } finally {
                if (conn != null) conn.disconnect();
            }
        });
    }

    public void loginWithGoogleIdToken(String idToken, Callback<Boolean> callback) {
        executor.execute(() -> {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(BASE_URL + "/user/auth/login/google");
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("User-Agent", "VideolandTV/1.1 (Android TV)");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);
                conn.setDoOutput(true);

                JSONObject body = new JSONObject();
                body.put("id_token", idToken);

                byte[] postData = body.toString().getBytes(StandardCharsets.UTF_8);
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(postData);
                    os.flush();
                }

                int code = conn.getResponseCode();
                InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
                String responseBody = readStream(is);

                if (code >= 200 && code < 300) {
                    // Extract token from Set-Cookie header case-insensitively
                    String token = extractCookieToken(conn);

                    // Allow non-JSON success if cookie exists
                    if (token == null || token.isEmpty()) {
                        try {
                            JSONObject resJson = new JSONObject(responseBody);
                            if (resJson.has("data")) {
                                JSONObject dataObj = resJson.optJSONObject("data");
                                if (dataObj != null) {
                                    token = dataObj.optString("token", dataObj.optString("access_token", null));
                                }
                            }
                            if (token == null && resJson.has("token")) {
                                token = resJson.optString("token", null);
                            }
                        } catch (Exception ignored) {
                            // Non-JSON response body allowed if token was present in cookie
                        }
                    }

                    if (token != null && !token.isEmpty()) {
                        UserSession session = UserSession.getInstance(context);
                        session.setToken(token);
                        session.setUsername("Google會員");
                        // Do not fake subscribed status; default false until verified profile
                        session.setSubscribed(false);
                        mainHandler.post(() -> callback.onSuccess(true));
                        return;
                    }
                }

                // Backend errors show bounded HTTP/error code without response bodies/tokens
                String errMsg = "Google 登入失敗 (HTTP " + code + ")";
                try {
                    JSONObject errObj = new JSONObject(responseBody);
                    if (errObj.has("code")) {
                        errMsg += " [代碼: " + errObj.optInt("code") + "]";
                    } else if (errObj.has("message")) {
                        String msg = errObj.optString("message", "");
                        if (!msg.isEmpty() && msg.length() <= 80 && !msg.contains("ey")) {
                            errMsg += " (" + msg + ")";
                        }
                    }
                } catch (Exception ignored) {}

                final String finalErrMsg = errMsg;
                mainHandler.post(() -> callback.onError(finalErrMsg));
            } catch (Exception e) {
                // Avoid logging secrets or tokens
                Log.e(TAG, "Google login network error: " + e.getClass().getSimpleName());
                mainHandler.post(() -> callback.onError("Google 登入網路錯誤: " + e.getMessage()));
            } finally {
                if (conn != null) conn.disconnect();
            }
        });
    }

    private String extractCookieToken(HttpURLConnection conn) {
        if (conn == null || conn.getHeaderFields() == null) {
            return null;
        }
        for (java.util.Map.Entry<String, List<String>> entry : conn.getHeaderFields().entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase("Set-Cookie")) {
                for (String cookie : entry.getValue()) {
                    if (cookie != null) {
                        for (String part : cookie.split(";")) {
                            String trimmed = part.trim();
                            if (trimmed.regionMatches(true, 0, "token=", 0, 6)) {
                                String val = trimmed.substring(6).trim();
                                if (!val.isEmpty()) {
                                    return val;
                                }
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    public void fetchLiveStreamDetail(String scheduleId, Callback<StreamDetail> callback) {
        executor.execute(() -> {
            try {
                String path = "/api/user_v1/live_schedule/" + scheduleId;
                String responseStr = executeGet(path, true);
                JSONObject root = new JSONObject(responseStr);
                StreamDetail detail = StreamDetail.fromJson(root);
                mainHandler.post(() -> callback.onSuccess(detail));
            } catch (Exception e) {
                Log.e(TAG, "Fetch live stream detail error", e);
                StreamDetail fallback = new StreamDetail();
                fallback.setSuccess(false);
                fallback.setMessage(e.getMessage());
                mainHandler.post(() -> callback.onSuccess(fallback));
            }
        });
    }

    private String executeGet(String endpointPath, boolean requireAuth) throws Exception {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(BASE_URL + endpointPath);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "VideolandTV/1.1 (Android TV)");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);

            if (requireAuth) {
                String token = UserSession.getInstance(context).getToken();
                if (token != null && !token.isEmpty()) {
                    conn.setRequestProperty("Cookie", "token=" + token);
                    conn.setRequestProperty("Authorization", "Bearer " + token);
                }
            }

            int code = conn.getResponseCode();
            InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
            String res = readStream(is);
            if (code >= 400) {
                throw new Exception("HTTP " + code + ": " + res);
            }
            return res;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private String readStream(InputStream is) throws Exception {
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
