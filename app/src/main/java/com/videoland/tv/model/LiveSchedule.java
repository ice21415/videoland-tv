package com.videoland.tv.model;

import org.json.JSONObject;
import org.json.JSONArray;
import java.util.HashSet;
import java.util.Set;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class LiveSchedule {
    private String id;
    private String vlLiveId;
    private String title;
    private String subtitle;
    private String homeTeamName;
    private String homeTeamLogo;
    private String awayTeamName;
    private String awayTeamLogo;
    private long startTime;
    private long endTime;
    private boolean isSubscribed;
    private boolean hasTeams;
    private String coverUrl;
    private final Set<String> categoryIds = new HashSet<>();

    public LiveSchedule() {}

    public static LiveSchedule fromJson(JSONObject obj) {
        LiveSchedule s = new LiveSchedule();
        if (obj == null) return s;

        s.id = obj.optString("id", "");
        JSONObject cover=obj.optJSONObject("cover_landscape");
        s.coverUrl=cover==null?"":cover.optString("url", "");
        s.hasTeams = obj.optJSONObject("home_team") != null && obj.optJSONObject("away_team") != null;
        JSONArray categories = obj.optJSONArray("category_ids");
        if (categories != null) for (int i=0;i<categories.length();i++) s.categoryIds.add(categories.optString(i));
        s.vlLiveId = obj.optString("vl_live_id", "");
        s.startTime = obj.optLong("start_time", 0);
        s.endTime = obj.optLong("end_time", 0);
        s.isSubscribed = obj.optBoolean("is_subscribed", false);

        JSONObject titleObj = obj.optJSONObject("title");
        if (titleObj != null) {
            s.title = titleObj.optString("zh_tw", titleObj.optString("en", ""));
        } else {
            s.title = obj.optString("title", "");
        }

        JSONObject subObj = obj.optJSONObject("subtitle");
        if (subObj != null) {
            s.subtitle = subObj.optString("zh_tw", subObj.optString("en", ""));
        } else {
            s.subtitle = obj.optString("subtitle", "");
        }

        JSONObject homeObj = obj.optJSONObject("home_team");
        if (homeObj != null) {
            JSONObject homeNameObj = homeObj.optJSONObject("name");
            if (homeNameObj != null) {
                s.homeTeamName = homeNameObj.optString("zh_tw", homeNameObj.optString("en", ""));
            } else {
                s.homeTeamName = homeObj.optString("name", "");
            }
            s.homeTeamLogo = homeObj.optString("imageUrl", "");
        } else {
            s.homeTeamName = "主隊";
            s.homeTeamLogo = "";
        }

        JSONObject awayObj = obj.optJSONObject("away_team");
        if (awayObj != null) {
            JSONObject awayNameObj = awayObj.optJSONObject("name");
            if (awayNameObj != null) {
                s.awayTeamName = awayNameObj.optString("zh_tw", awayNameObj.optString("en", ""));
            } else {
                s.awayTeamName = awayObj.optString("name", "");
            }
            s.awayTeamLogo = awayObj.optString("imageUrl", "");
        } else {
            s.awayTeamName = "客隊";
            s.awayTeamLogo = "";
        }

        return s;
    }

    public boolean matchesCategories(Set<String> ids) { for (String id:ids) if (categoryIds.contains(id)) return true; return false; }
    public String getId() { return id; }
    public String getCoverUrl() { return coverUrl; }
    public boolean hasTeams() { return hasTeams; }
    public String getVlLiveId() { return vlLiveId; }
    public String getTitle() { return title; }
    public String getSubtitle() { return subtitle; }
    public String getHomeTeamName() { return homeTeamName; }
    public String getHomeTeamLogo() { return homeTeamLogo; }
    public String getAwayTeamName() { return awayTeamName; }
    public String getAwayTeamLogo() { return awayTeamLogo; }
    public long getStartTime() { return startTime; }
    public long getEndTime() { return endTime; }
    public boolean isSubscribed() { return isSubscribed; }

    public String getFormattedTime() {
        if (startTime <= 0) return "";
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("MM/dd HH:mm", Locale.getDefault());
            return sdf.format(new Date(startTime * 1000L));
        } catch (Exception e) {
            return String.valueOf(startTime);
        }
    }
}
