package com.videoland.tv.ui;

import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.videoland.tv.R;
import com.videoland.tv.adapter.BannerAdapter;
import com.videoland.tv.adapter.CategoryAdapter;
import com.videoland.tv.adapter.ScheduleAdapter;
import com.videoland.tv.api.ApiClient;
import com.videoland.tv.auth.GoogleAuthManager;
import com.videoland.tv.model.Banner;
import com.videoland.tv.model.Category;
import com.videoland.tv.model.LiveSchedule;
import com.videoland.tv.model.UserSession;

import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import androidx.appcompat.app.AlertDialog;

public class MainActivity extends AppCompatActivity {

    private static final int TAB_SCHEDULE = 0;
    private static final int TAB_BANNERS = 1;
    private static final int TAB_CATEGORIES = 2;

    private int currentTab = TAB_SCHEDULE;
    private Category selectedCategory;
    private int requestVersion = 0;

    private TextView tvUserStatus;
    private Button btnAuth;
    private Button tabSchedule, tabBanners, tabCategories, tabWeb;
    private RecyclerView rvContent;
    private ProgressBar pbLoading;
    private TextView tvEmpty, sectionTitle, sectionDetail;
    private Button retryButton, clearFilterButton;

    private ScheduleAdapter scheduleAdapter;
    private BannerAdapter bannerAdapter;
    private CategoryAdapter categoryAdapter;

    private ApiClient apiClient;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        apiClient = ApiClient.getInstance(this);

        initViews();
        setupAdapters();
        setupListeners();
        updateUserUI();

        // Default: load live schedules
        selectTab(TAB_SCHEDULE);
        tabSchedule.requestFocus();
    }

    private void initViews() {
        tvUserStatus = findViewById(R.id.tv_user_status);
        btnAuth = findViewById(R.id.btn_auth);
        tabSchedule = findViewById(R.id.tab_schedule);
        tabBanners = findViewById(R.id.tab_banners);
        tabCategories = findViewById(R.id.tab_categories);
        tabWeb = findViewById(R.id.tab_web);
        rvContent = findViewById(R.id.rv_content);
        pbLoading = findViewById(R.id.pb_loading);
        tvEmpty = findViewById(R.id.tv_empty);
        sectionTitle=findViewById(R.id.tv_section_title);
        sectionDetail=findViewById(R.id.tv_section_detail);
        retryButton=findViewById(R.id.btn_retry);
        clearFilterButton=findViewById(R.id.btn_clear_filter);
        retryButton.setOnClickListener(v->refreshCurrentView());
        clearFilterButton.setOnClickListener(v->{selectedCategory=null;selectTab(TAB_SCHEDULE);});
    }

    private int gridColumns() {
        int available = getResources().getConfiguration().screenWidthDp - 68;
        return Math.max(1, Math.min(5, available / 260));
    }

    @Override
    public void onConfigurationChanged(android.content.res.Configuration config) {
        super.onConfigurationChanged(config);
        if (rvContent != null && rvContent.getLayoutManager() instanceof GridLayoutManager) {
            ((GridLayoutManager) rvContent.getLayoutManager()).setSpanCount(gridColumns());
        }
    }

    private void setupAdapters() {
        scheduleAdapter = new ScheduleAdapter(this, item -> {
            if(item.getStartTime()>System.currentTimeMillis()/1000L){
                new AlertDialog.Builder(this).setTitle(item.getTitle()).setMessage(item.getSubtitle()+"\n"+item.getFormattedTime()+" 開賽\n直播尚未開始，請於開賽後再試。").setPositiveButton("返回賽程",null).show();
                return;
            }
            Intent intent = new Intent(MainActivity.this, PlayerActivity.class);
            intent.putExtra(PlayerActivity.EXTRA_SCHEDULE_ID, item.getId());
            intent.putExtra(PlayerActivity.EXTRA_TITLE, item.getTitle());
            startActivity(intent);
        });

        bannerAdapter = new BannerAdapter(this, item -> {
            if (item.getLink() != null && item.getLink().startsWith("http")) {
                Intent intent = new Intent(MainActivity.this, WebPortalActivity.class);
                intent.putExtra(WebPortalActivity.EXTRA_URL, item.getLink());
                startActivity(intent);
            } else {
                Toast.makeText(this, item.getTitle(), Toast.LENGTH_SHORT).show();
            }
        });

        categoryAdapter = new CategoryAdapter(this, this::chooseCategory);
    }

    private void chooseCategory(Category category) {
        if (category.getChildren().isEmpty()) { showCategorySchedules(category); return; }
        String[] labels = new String[category.getChildren().size()+1];
        labels[0] = "全部：" + category.getTitle();
        for(int i=0;i<category.getChildren().size();i++) labels[i+1]=category.getChildren().get(i).getTitle();
        new AlertDialog.Builder(this).setTitle(category.getTitle()).setItems(labels,(dialog,index)-> {
            if(index==0) showCategorySchedules(category); else chooseCategory(category.getChildren().get(index-1));
        }).setNegativeButton("返回",null).show();
    }

    private void showCategorySchedules(Category category) {
        selectedCategory = category;
        requestVersion++;
        rvContent.setLayoutManager(new GridLayoutManager(this, gridColumns()));
        rvContent.setAdapter(scheduleAdapter);
        pbLoading.setVisibility(View.VISIBLE);
        tvEmpty.setVisibility(View.GONE);
        sectionTitle.setText(category.getTitle());
        sectionDetail.setText("正在載入分類賽程…");
        clearFilterButton.setVisibility(View.VISIBLE);
        loadSchedules();
    }

    private void setupListeners() {
        tabSchedule.setOnClickListener(v -> selectTab(TAB_SCHEDULE));
        tabBanners.setOnClickListener(v -> selectTab(TAB_BANNERS));
        tabCategories.setOnClickListener(v -> selectTab(TAB_CATEGORIES));

        tabWeb.setOnClickListener(v -> refreshCurrentView());

        btnAuth.setOnClickListener(v -> {
            UserSession session = UserSession.getInstance(this);
            if (session.isLoggedIn()) {
                new AlertDialog.Builder(this).setTitle("會員帳號").setMessage("已使用 "+session.getUsername()+" 登入。觀看權限依各賽事的訂閱方案而定。")
                    .setPositiveButton("繼續觀看",null).setNegativeButton("登出帳號",(d,w)->{
                        new AlertDialog.Builder(this).setTitle("登出帳號？").setMessage("登出後需重新登入，才能觀看會員內容。")
                            .setPositiveButton("登出",(d2,w2)->{session.clear();updateUserUI();}).setNegativeButton("取消",null).show();
                    }).show();
            } else {
                showLoginDialog();
            }
        });
    }

    private void updateUserUI() {
        UserSession session = UserSession.getInstance(this);
        if (session.isLoggedIn()) {
            tvUserStatus.setText(getString(R.string.user_logged_in, session.getUsername()));
            btnAuth.setText("會員帳號");
        } else {
            tvUserStatus.setText(R.string.user_guest);
            btnAuth.setText(R.string.btn_login);
        }
    }

    private void refreshCurrentView(){
        requestVersion++;
        retryButton.setVisibility(View.GONE);pbLoading.setVisibility(View.VISIBLE);tvEmpty.setVisibility(View.GONE);
        if(currentTab==TAB_CATEGORIES&&selectedCategory==null)loadCategories();else loadSchedules();
    }

    private void selectTab(int tab) {
        requestVersion++;
        if(tab==TAB_CATEGORIES)selectedCategory=null;
        tabCategories.setText("運動分類");
        currentTab = tab;
        tabSchedule.setSelected(tab == TAB_SCHEDULE);
        tabBanners.setSelected(tab == TAB_BANNERS);
        tabCategories.setSelected(tab == TAB_CATEGORIES);

        pbLoading.setVisibility(View.VISIBLE);
        tvEmpty.setVisibility(View.GONE);
        retryButton.setVisibility(View.GONE);
        clearFilterButton.setVisibility(selectedCategory==null?View.GONE:View.VISIBLE);
        sectionTitle.setText(tab==TAB_CATEGORIES?"選擇運動":selectedCategory!=null?selectedCategory.getTitle():tab==TAB_BANNERS?"最近回放":"所有賽事");
        sectionDetail.setText(tab==TAB_CATEGORIES?"選擇運動及聯賽，查看對應賽程":"正在載入賽程…");

        switch (tab) {
            case TAB_SCHEDULE:
                rvContent.setLayoutManager(new GridLayoutManager(this, gridColumns()));
                rvContent.setAdapter(scheduleAdapter);
                loadSchedules();
                break;
            case TAB_BANNERS:
                rvContent.setLayoutManager(new GridLayoutManager(this, gridColumns()));
                rvContent.setAdapter(scheduleAdapter);
                loadSchedules();
                break;
            case TAB_CATEGORIES:
                rvContent.setLayoutManager(new GridLayoutManager(this, gridColumns()));
                rvContent.setAdapter(categoryAdapter);
                loadCategories();
                break;
        }
    }

    private void loadSchedules() {
        long now = System.currentTimeMillis() / 1000L;
        long startTime = now - 5 * 86400L; // Available recent replay window
        long endTime = now + (7 * 86400L); // Next 7 days

        final int version = requestVersion;
        final Category filter = selectedCategory;
        final boolean replayOnly=currentTab==TAB_BANNERS;
        apiClient.fetchLiveSchedules(startTime, endTime, new ApiClient.Callback<List<LiveSchedule>>() {
            @Override
            public void onSuccess(List<LiveSchedule> result) {
                if (version != requestVersion || isFinishing()) return;
                List<LiveSchedule> visible = new ArrayList<>();
                for (LiveSchedule item:result) {
                    boolean ended=item.getEndTime()>0&&item.getEndTime()<now;
                    if((filter==null||item.matchesCategories(filter.getAllIds()))&&(!replayOnly||ended))visible.add(item);
                }
                java.util.Collections.sort(visible, (a,b)->{
                    if(replayOnly)return Long.compare(b.getStartTime(),a.getStartTime());
                    int pa=a.getStartTime()>now?1:a.getEndTime()>0&&a.getEndTime()<now?2:0;
                    int pb=b.getStartTime()>now?1:b.getEndTime()>0&&b.getEndTime()<now?2:0;
                    return pa!=pb?Integer.compare(pa,pb):pa==2?Long.compare(b.getStartTime(),a.getStartTime()):Long.compare(a.getStartTime(),b.getStartTime());
                });
                sectionDetail.setText((filter==null?"全部運動":filter.getTitle())+" · "+visible.size()+" 場 · "+(replayOnly?"最近五日回放":"直播、即將開賽與最近五日回放"));
                scheduleAdapter.setData(visible);
                pbLoading.setVisibility(View.GONE);
                if (visible.isEmpty()) {
                    tvEmpty.setText(filter==null ? "最近五日及未來七日沒有賽程" : "此分類最近五日及未來七日沒有賽程");
                    tvEmpty.setVisibility(View.VISIBLE);
                } else {
                    tvEmpty.setVisibility(View.GONE);

                }
            }

            @Override
            public void onError(String error) {
                if (version != requestVersion || isFinishing()) return;
                pbLoading.setVisibility(View.GONE);
                tvEmpty.setText("賽程暫時無法載入，請確認網路後重試。");
                retryButton.setVisibility(View.VISIBLE);
                tvEmpty.setVisibility(View.VISIBLE);
            }
        });
    }

    private void loadBanners() {
        apiClient.fetchBanners(new ApiClient.Callback<List<Banner>>() {
            @Override
            public void onSuccess(List<Banner> result) {
                pbLoading.setVisibility(View.GONE);
                if (result.isEmpty()) {
                    tvEmpty.setVisibility(View.VISIBLE);
                } else {
                    tvEmpty.setVisibility(View.GONE);
                    bannerAdapter.setData(result);
                }
            }

            @Override
            public void onError(String error) {
                pbLoading.setVisibility(View.GONE);
                tvEmpty.setText("載入精選橫幅失敗: " + error);
                tvEmpty.setVisibility(View.VISIBLE);
            }
        });
    }

    private void loadCategories() {
        final int version=requestVersion;
        apiClient.fetchCategories(new ApiClient.Callback<List<Category>>() {
            @Override
            public void onSuccess(List<Category> result) {
                if(version!=requestVersion||isFinishing())return;
                sectionDetail.setText("選擇運動及聯賽 · "+result.size()+" 種運動");
                pbLoading.setVisibility(View.GONE);
                if (result.isEmpty()) {
                    tvEmpty.setVisibility(View.VISIBLE);
                } else {
                    tvEmpty.setVisibility(View.GONE);
                    categoryAdapter.setData(result);
                }
            }

            @Override
            public void onError(String error) {
                if(version!=requestVersion||isFinishing())return;
                retryButton.setVisibility(View.VISIBLE);
                pbLoading.setVisibility(View.GONE);
                tvEmpty.setText("載入分類失敗: " + error);
                tvEmpty.setVisibility(View.VISIBLE);
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (tvUserStatus != null) updateUserUI();
    }

    private void showLoginDialog() {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_login);

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        EditText etUsername = dialog.findViewById(R.id.et_username);
        EditText etPassword = dialog.findViewById(R.id.et_password);
        TextView tvMsg = dialog.findViewById(R.id.tv_login_msg);
        Button btnCancel = dialog.findViewById(R.id.btn_dialog_cancel);
        Button btnConfirm = dialog.findViewById(R.id.btn_dialog_login);
        Button btnGoogleLogin = dialog.findViewById(R.id.btn_google_login);

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnConfirm.setOnClickListener(v -> {
            String username = etUsername.getText().toString().trim();
            String password = etPassword.getText().toString();

            if (username.isEmpty() || password.isEmpty()) {
                tvMsg.setText("請輸入帳號與密碼");
                tvMsg.setVisibility(View.VISIBLE);
                return;
            }

            tvMsg.setText("登入驗證中...");
            tvMsg.setVisibility(View.VISIBLE);
            btnConfirm.setEnabled(false);

            apiClient.login(username, password, new ApiClient.Callback<Boolean>() {
                @Override
                public void onSuccess(Boolean result) {
                    dialog.dismiss();
                    updateUserUI();
                    Toast.makeText(MainActivity.this, "已登入", Toast.LENGTH_SHORT).show();
                    // Refresh data
                    selectTab(currentTab);
                }

                @Override
                public void onError(String error) {
                    btnConfirm.setEnabled(true);
                    tvMsg.setText(error);
                    tvMsg.setVisibility(View.VISIBLE);
                }
            });
        });

        if (btnGoogleLogin != null) {
            btnGoogleLogin.setOnClickListener(v -> {
                dialog.dismiss();
                try {
                    Intent browser = new Intent(Intent.ACTION_VIEW,
                            com.videoland.tv.auth.GoogleAuthManager.getInstance().createAuthorizationUri());
                    browser.setPackage("com.phlox.tvwebbrowser");
                    startActivity(browser);
                } catch (android.content.ActivityNotFoundException e) {
                    Toast.makeText(this, "請先安裝 TV Bro 瀏覽器以使用 Google 登入", Toast.LENGTH_LONG).show();
                } catch (Exception e) {
                    Toast.makeText(this, "無法啟動 Google 登入，請重試", Toast.LENGTH_LONG).show();
                }
            });
        }

        dialog.show();
        if (dialog.getWindow() != null) {
            int width = Math.min((int)(540 * getResources().getDisplayMetrics().density),
                    getResources().getDisplayMetrics().widthPixels * 9 / 10);
            dialog.getWindow().setLayout(width, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        btnGoogleLogin.requestFocus();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (selectedCategory != null) { selectTab(TAB_CATEGORIES); tabCategories.requestFocus(); return true; }
            if (currentTab != TAB_SCHEDULE) {
                selectTab(TAB_SCHEDULE);
                tabSchedule.requestFocus();
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }
}
