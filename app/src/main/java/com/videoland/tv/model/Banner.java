package com.videoland.tv.model;

import org.json.JSONObject;

public class Banner {
    private String id;
    private String title;
    private String coverUrl;
    private String link;
    private boolean isPay;

    public Banner() {}

    public static Banner fromJson(JSONObject obj) {
        Banner b = new Banner();
        if (obj == null) return b;
        b.id = obj.optString("id", "");
        b.title = obj.optString("title", "");
        b.link = obj.optString("link", "");
        b.isPay = obj.optBoolean("is_pay", false);

        // Extract landscape or portrait cover
        JSONObject landscape = obj.optJSONObject("cover_landscape");
        if (landscape != null && landscape.has("url")) {
            b.coverUrl = landscape.optString("url", "");
        } else {
            JSONObject portrait = obj.optJSONObject("cover_portrait");
            if (portrait != null && portrait.has("url")) {
                b.coverUrl = portrait.optString("url", "");
            } else {
                b.coverUrl = "";
            }
        }
        return b;
    }

    public String getId() { return id; }
    public String getTitle() { return title; }
    public String getCoverUrl() { return coverUrl; }
    public String getLink() { return link; }
    public boolean isPay() { return isPay; }
}
