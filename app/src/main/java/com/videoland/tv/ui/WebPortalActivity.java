package com.videoland.tv.ui;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.view.KeyEvent;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

public class WebPortalActivity extends AppCompatActivity {

    public static final String EXTRA_URL = "extra_url";
    private static final String DEFAULT_URL = "https://sport.videoland.com.tw/";

    private WebView webView;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF101216);

        webView = new WebView(this);
        webView.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));
        root.addView(webView);
        setContentView(root);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);

        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient());

        String targetUrl = getIntent().getStringExtra(EXTRA_URL);
        if (targetUrl == null || targetUrl.isEmpty()) {
            targetUrl = DEFAULT_URL;
        }
        webView.loadUrl(targetUrl);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // Handle TV D-pad scrolling & remote back key
        if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
            webView.scrollBy(0, 150);
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
            webView.scrollBy(0, -150);
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (webView.canGoBack()) {
                webView.goBack();
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.destroy();
        }
        super.onDestroy();
    }
}
