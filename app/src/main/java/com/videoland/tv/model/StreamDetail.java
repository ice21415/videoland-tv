package com.videoland.tv.model;

import org.json.JSONObject;

public class StreamDetail {
    private boolean success;
    private String message;
    private String streamUrl;
    private String drmLicenseUrl;
    private String drmCustomData;
    private String videoId;
    private String cdnHeaderKey;
    private String cdnHeaderValue;

    public StreamDetail() {}

    public static StreamDetail fromJson(JSONObject obj) {
        StreamDetail d = new StreamDetail();
        if (obj == null) {
            d.success = false;
            d.message = "伺服器無回應";
            return d;
        }

        int code = obj.optInt("code", -1);
        JSONObject data = obj.optJSONObject("data");
        d.success = code == 0 && data != null;
        d.message = obj.optString("message", "");
        if (!d.success) return d;
        if (!data.optBoolean("video_permission", false)) {
            d.success = false;
            d.message = "此帳號沒有這場賽事的觀看權限，或回放已到期。";
            return d;
        }
        JSONObject urls = data.optJSONObject("video_url");
        JSONObject licenses = data.optJSONObject("UrlAndToken");
        d.streamUrl = urls == null ? "" : urls.optString("dash", "");
        d.drmLicenseUrl = licenses == null ? "" : licenses.optString("dash", "");
        d.drmCustomData = licenses == null ? "" : licenses.optString("token", "");
        d.cdnHeaderKey = data.optString("cdn_header_key", "");
        d.cdnHeaderValue = data.optString("cdn_header_value", "");
        if (d.streamUrl.isEmpty()) {
            d.success = false;
            d.message = "此場次沒有可播放的 DASH 串流，可能尚未開播或回放已下架。";
        } else {
            d.message = "";
        }
        return d;
    }

    public String getCdnHeaderKey() { return cdnHeaderKey; }
    public String getCdnHeaderValue() { return cdnHeaderValue; }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getStreamUrl() { return streamUrl; }
    public void setStreamUrl(String streamUrl) { this.streamUrl = streamUrl; }
    public String getDrmLicenseUrl() { return drmLicenseUrl; }
    public void setDrmLicenseUrl(String drmLicenseUrl) { this.drmLicenseUrl = drmLicenseUrl; }
    public String getDrmCustomData() { return drmCustomData; }
    public void setDrmCustomData(String drmCustomData) { this.drmCustomData = drmCustomData; }
    public String getVideoId() { return videoId; }
    public void setVideoId(String videoId) { this.videoId = videoId; }
}
