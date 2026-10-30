package com.videoland.tv.ui;

import android.net.Uri;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.widget.TextView;
import android.widget.Button;
import androidx.appcompat.app.AlertDialog;
import androidx.media3.common.Tracks;
import androidx.media3.common.Format;
import androidx.media3.common.TrackSelectionOverride;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;

import com.videoland.tv.R;
import com.videoland.tv.api.ApiClient;
import com.videoland.tv.model.StreamDetail;
import com.videoland.tv.model.UserSession;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;

@androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
public class PlayerActivity extends AppCompatActivity {

    public static final String EXTRA_STREAM_URL = "extra_stream_url";
    public static final String EXTRA_TITLE = "extra_title";
    public static final String EXTRA_SCHEDULE_ID = "extra_schedule_id";
    public static final String EXTRA_DRM_LICENSE_URL = "extra_drm_license_url";
    public static final String EXTRA_DRM_CUSTOM_DATA = "extra_drm_custom_data";

    private PlayerView playerView;
    private ExoPlayer player;
    private TextView tvTitle;
    private TextView tvStatus;
    private TextView tvDrmBadge;
    private Button qualityButton;
    private int qualityChoice=-1, lastVideoHeight=0;
    private boolean qualityApplied=false;
    private final TreeMap<Integer,TrackSelectionOverride> qualityTracks=new TreeMap<>(java.util.Collections.reverseOrder());

    private String streamUrl;
    private String title;
    private String scheduleId;
    private String drmLicenseUrl;
    private String drmCustomData;
    private final Map<String,String> mediaHeaders = new HashMap<>();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);

        playerView = findViewById(R.id.player_view);
        tvTitle = findViewById(R.id.tv_player_title);
        tvStatus = findViewById(R.id.tv_player_status);
        tvDrmBadge = findViewById(R.id.tv_drm_badge);
        qualityButton=findViewById(R.id.btn_quality);
        qualityChoice=getSharedPreferences("playback_settings",MODE_PRIVATE).getInt("quality",-1);
        qualityButton.setOnClickListener(v->showQualityDialog());
        playerView.setKeepScreenOn(true);

        streamUrl = getIntent().getStringExtra(EXTRA_STREAM_URL);
        title = getIntent().getStringExtra(EXTRA_TITLE);
        scheduleId = getIntent().getStringExtra(EXTRA_SCHEDULE_ID);
        drmLicenseUrl = getIntent().getStringExtra(EXTRA_DRM_LICENSE_URL);
        drmCustomData = getIntent().getStringExtra(EXTRA_DRM_CUSTOM_DATA);

        if (title != null && !title.isEmpty()) {
            tvTitle.setText(title);
        }

        initPlayer();

        if (streamUrl != null && !streamUrl.isEmpty()) {
            startPlayback(streamUrl, drmLicenseUrl, drmCustomData);
        } else if (scheduleId != null && !scheduleId.isEmpty()) {
            requestStreamDetail(scheduleId);
        } else {
            showStatusMessage("未提供有效直播流地址。\n請按【返回鍵】返回賽程列表。");
        }
    }

    private void initPlayer() {
        player = new ExoPlayer.Builder(this).build();
        playerView.setPlayer(player);
        player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon()
            .setViewportSize(Integer.MAX_VALUE,Integer.MAX_VALUE,false)
            .setForceHighestSupportedBitrate(qualityChoice==-1).build());
        playerView.setControllerVisibilityListener((PlayerView.ControllerVisibilityListener) visibility -> {
            findViewById(R.id.ll_player_overlay).setVisibility(visibility);
        });
        playerView.requestFocus();

        player.addListener(new Player.Listener() {
            @Override
            public void onTracksChanged(Tracks tracks) {
                qualityTracks.clear();
                java.util.Map<Integer,Integer> bitrates=new java.util.HashMap<>();
                for(Tracks.Group group:tracks.getGroups()) {
                    if(group.getType()!=C.TRACK_TYPE_VIDEO)continue;
                    for(int i=0;i<group.length;i++) {
                        Format f=group.getTrackFormat(i);
                        if(!group.isTrackSupported(i)||f.height<=0)continue;
                        if(!bitrates.containsKey(f.height)||f.bitrate>bitrates.get(f.height)) {
                            bitrates.put(f.height,f.bitrate);
                            qualityTracks.put(f.height,new TrackSelectionOverride(group.getMediaTrackGroup(),i));
                        }
                    }
                }
                if(!qualityApplied&&!qualityTracks.isEmpty()){qualityApplied=true;applyQuality();}
                updateQualityLabel();
            }

            @Override
            public void onPlaybackStateChanged(int playbackState) {
                android.util.Log.i("VideolandPlayback", "state=" + playbackState);
                if (playbackState == Player.STATE_READY) {
                    tvStatus.setVisibility(View.GONE);
                } else if (playbackState == Player.STATE_BUFFERING) {
                    // Buffering spinner handled by PlayerView
                }
            }

            @Override
            public void onPlayerError(PlaybackException error) {
                android.util.Log.i("VideolandPlayback", "error=" + error.getErrorCodeName());
                String desc = "播放發生錯誤 (代碼: " + error.getErrorCodeName() + ")";
                if (error.errorCode == PlaybackException.ERROR_CODE_DRM_LICENSE_ACQUISITION_FAILED) {
                    desc = "【Widevine DRM 授權失敗】\n請確認您的帳號已訂閱此賽事直播方案，或該裝置授權數量已達上限。\n按【返回鍵】返回主頁。";
                }
                showStatusMessage(desc);
            }
        });
        player.addListener(new Player.Listener() {
            @Override public void onIsPlayingChanged(boolean playing) {
                android.util.Log.i("VideolandPlayback", "playing=" + playing);
            }
            @Override public void onVideoSizeChanged(androidx.media3.common.VideoSize size) {
                android.util.Log.i("VideolandPlayback", "video=" + size.width + "x" + size.height);
                lastVideoHeight=size.height;updateQualityLabel();
            }
        });
    }

    private void requestStreamDetail(String id) {
        showStatusMessage("正向官方伺服器請求串流授權資訊...");
        ApiClient.getInstance(this).fetchLiveStreamDetail(id, new ApiClient.Callback<StreamDetail>() {
            @Override
            public void onSuccess(StreamDetail detail) {
                if (detail.isSuccess() && detail.getStreamUrl() != null && !detail.getStreamUrl().isEmpty()) {
                    if (isFinishing() || isDestroyed()) return;
                    if (detail.getCdnHeaderKey() != null && !detail.getCdnHeaderKey().isEmpty()) {
                        mediaHeaders.put(detail.getCdnHeaderKey(), detail.getCdnHeaderValue());
                    }
                    startPlayback(detail.getStreamUrl(), detail.getDrmLicenseUrl(), detail.getDrmCustomData());
                } else {
                    String msg = detail.getMessage();
                    if (msg == null || msg.isEmpty() || msg.contains("401") || msg.contains("token")) {
                        boolean loggedIn = UserSession.getInstance(PlayerActivity.this).isLoggedIn();
                        if (!loggedIn) {
                            msg = "【需要登入訂閱會員】\n此賽事為會員專屬直播串流。\n請在首頁點擊【登入】輸入緯來體育官方會員帳號。\n按【返回鍵】返回主頁。";
                        } else {
                            msg = "【需有效賽事方案】\n此帳號目前無該場次觀看授權，或賽事尚未開播。\n按【返回鍵】返回主頁。";
                        }
                    }
                    showStatusMessage(msg);
                }
            }

            @Override
            public void onError(String error) {
                showStatusMessage("請求直播串流失敗: " + error + "\n請按【返回鍵】重試。");
            }
        });
    }

    private void startPlayback(String url, String licenseUrl, String customData) {
        tvStatus.setVisibility(View.GONE);

        MediaItem.Builder mediaItemBuilder = new MediaItem.Builder()
                .setUri(Uri.parse(url));

        if (licenseUrl != null && !licenseUrl.isEmpty()) {
            tvDrmBadge.setVisibility(View.GONE);
            MediaItem.DrmConfiguration.Builder drmBuilder = new MediaItem.DrmConfiguration.Builder(C.WIDEVINE_UUID)
                    .setLicenseUri(Uri.parse(licenseUrl));

            if (customData != null && !customData.isEmpty()) {
                Map<String,String> licenseHeaders = new HashMap<>();
                licenseHeaders.put("customdata", customData);
                licenseHeaders.put("Accept", "application/octet-stream");
                drmBuilder.setLicenseRequestHeaders(licenseHeaders);
            }
            mediaItemBuilder.setDrmConfiguration(drmBuilder.build());
        } else {
            tvDrmBadge.setVisibility(View.GONE);
        }

        DefaultHttpDataSource.Factory http = new DefaultHttpDataSource.Factory()
                .setDefaultRequestProperties(mediaHeaders);
        player.setMediaSource(new DefaultMediaSourceFactory(http).createMediaSource(mediaItemBuilder.build()));
        player.prepare();
        player.play();
    }

    private void showStatusMessage(String message) {
        tvStatus.setText(message);
        tvStatus.setVisibility(View.VISIBLE);
    }

    private void updateQualityLabel(){
        String mode=qualityChoice<0?"最高":qualityChoice==0?"自動":qualityChoice+"p";
        qualityButton.setText("畫質："+mode+(lastVideoHeight>0?" · "+lastVideoHeight+"p":""));
    }

    private void applyQuality(){
        if(player==null)return;
        androidx.media3.common.TrackSelectionParameters.Builder params=player.getTrackSelectionParameters().buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_VIDEO).setForceHighestSupportedBitrate(qualityChoice<0);
        if(qualityChoice>0&&qualityTracks.containsKey(qualityChoice))params.setOverrideForType(qualityTracks.get(qualityChoice));
        player.setTrackSelectionParameters(params.build());updateQualityLabel();
    }

    private void showQualityDialog(){
        if(player==null)return;
        List<String> labels=new ArrayList<>();List<Integer> values=new ArrayList<>();
        labels.add("最高可用畫質");values.add(-1);
        labels.add("自動（依網路調整）");values.add(0);
        for(int height:qualityTracks.keySet()){labels.add(height+"p");values.add(height);}
        int checked=values.indexOf(qualityChoice);
        new AlertDialog.Builder(this).setTitle("選擇畫質").setSingleChoiceItems(labels.toArray(new String[0]),Math.max(0,checked),(dialog,index)->{
            qualityChoice=values.get(index);getSharedPreferences("playback_settings",MODE_PRIVATE).edit().putInt("quality",qualityChoice).apply();
            applyQuality();dialog.dismiss();qualityButton.requestFocus();
        }).setNegativeButton("返回",null).show();
    }

    @Override public boolean onKeyDown(int keyCode,KeyEvent event){
        if(keyCode==KeyEvent.KEYCODE_MENU){showQualityDialog();return true;}
        if(keyCode==KeyEvent.KEYCODE_DPAD_UP&&!playerView.isControllerFullyVisible()){
            playerView.showController();qualityButton.requestFocus();return true;
        }
        if((keyCode==KeyEvent.KEYCODE_DPAD_CENTER||keyCode==KeyEvent.KEYCODE_ENTER)&&!playerView.isControllerFullyVisible()){
            playerView.showController();return true;
        }
        if(keyCode==KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE&&player!=null){if(player.isPlaying())player.pause();else player.play();return true;}
        if(keyCode==KeyEvent.KEYCODE_BACK){
            if(playerView.isControllerFullyVisible()){playerView.hideController();return true;}
            finish();return true;
        }
        return super.onKeyDown(keyCode,event);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (player != null) {
            player.pause();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (player != null) {
            player.release();
            player = null;
        }
    }
}
