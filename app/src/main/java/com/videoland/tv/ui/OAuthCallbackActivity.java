package com.videoland.tv.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.videoland.tv.R;
import com.videoland.tv.api.ApiClient;
import com.videoland.tv.auth.GoogleAuthManager;

/**
 * Dedicated callback activity for external-browser Google OAuth sign-in.
 * Validates incoming custom redirect Intent, consumes in-memory single-use pending attempt,
 * exchanges authorization code with Google token endpoint, and initializes session via backend.
 */
public class OAuthCallbackActivity extends AppCompatActivity {

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private ProgressBar pbLoading;
    private TextView tvStatus;
    private Button btnReturn;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if ((getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_TYPE_MASK)
                != android.content.res.Configuration.UI_MODE_TYPE_TELEVISION) {
            setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        }
        setContentView(R.layout.activity_oauth_callback);

        pbLoading = findViewById(R.id.pb_oauth_loading);
        tvStatus = findViewById(R.id.tv_oauth_status);
        btnReturn = findViewById(R.id.btn_oauth_return);

        btnReturn.setOnClickListener(v -> returnToMain());

        handleCallbackIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleCallbackIntent(intent);
    }

    private void handleCallbackIntent(Intent intent) {
        if (intent == null || intent.getData() == null) {
            showError("未接收到有效的授權回呼 Intent");
            return;
        }

        Uri uri = intent.getData();

        // 1. Validate callback scheme and destination (permit no open redirects or arbitrary URLs)
        if (!GoogleAuthManager.isValidCallbackUri(uri)) {
            showError("拒絕非預期的回呼協定或路徑 (Open Redirect 防護)");
            return;
        }

        // 2. Check for OAuth error parameters (e.g. user cancellation)
        String oauthError = uri.getQueryParameter("error");
        if (oauthError != null && !oauthError.isEmpty()) {
            GoogleAuthManager.getInstance().clearPendingAttempt();
            if ("access_denied".equalsIgnoreCase(oauthError)) {
                showError("Google 授權已由使用者取消 (access_denied)");
            } else {
                showError("Google 授權回應錯誤: " + oauthError);
            }
            return;
        }

        // 3. Extract authorization code and state
        String code = uri.getQueryParameter("code");
        String state = uri.getQueryParameter("state");

        if (code == null || code.trim().isEmpty() || state == null || state.trim().isEmpty()) {
            showError("授權回應缺少 code 或 state 參數");
            return;
        }

        // 4. Validate and consume in-memory single-use pending attempt (fails closed on mismatch or process death)
        GoogleAuthManager.PendingAttempt attempt = GoogleAuthManager.getInstance().consumePendingAttempt(state);
        if (attempt == null) {
            showError("授權階段失效、已過期或已處理\n(State 驗證失敗 / 程序若曾終止請返回重新嘗試)");
            return;
        }

        // 5. Exchange code for tokens via Google token endpoint over TLS
        tvStatus.setText("正在完成 Google 授權…");
        GoogleAuthManager.getInstance().exchangeCodeForTokens(code, attempt.getCodeVerifier(), attempt.getNonce(),
                new GoogleAuthManager.TokenExchangeCallback() {
                    @Override
                    public void onSuccess(String idToken) {
                        mainHandler.post(() -> {
                            tvStatus.setText("正在登入緯來會員…");

                            // 6. Send ID token ONLY to observed official backend
                            ApiClient.getInstance(OAuthCallbackActivity.this).loginWithGoogleIdToken(idToken,
                                    new ApiClient.Callback<Boolean>() {
                                        @Override
                                        public void onSuccess(Boolean result) {
                                            tvStatus.setText("已登入，正在返回賽程…");
                                            pbLoading.setVisibility(View.GONE);
                                            mainHandler.postDelayed(OAuthCallbackActivity.this::returnToMain, 1200);
                                        }

                                        @Override
                                        public void onError(String error) {
                                            showError("緯來會員登入交換失敗: " + error);
                                        }
                                    });
                        });
                    }

                    @Override
                    public void onError(String error) {
                        mainHandler.post(() -> showError("Google Token 交換失敗: " + error));
                    }
                });
    }

    private void showError(String message) {
        pbLoading.setVisibility(View.GONE);
        tvStatus.setText(message);
        tvStatus.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.status_live));
        btnReturn.setVisibility(View.VISIBLE);
        btnReturn.requestFocus();
    }

    private void returnToMain() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            returnToMain();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }
}
