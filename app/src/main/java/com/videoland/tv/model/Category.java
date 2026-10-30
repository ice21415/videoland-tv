package com.videoland.tv.model;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.HashSet;

public class Category {
    private String id;
    private String title;
    private final List<Category> children = new ArrayList<>();
    private List<String> subcategories = new ArrayList<>();

    public Category() {}

    public static Category fromJson(JSONObject obj) {
        Category c = new Category();
        if (obj == null) return c;
        c.id = obj.optString("id", "");

        JSONObject titleObj = obj.optJSONObject("title");
        if (titleObj != null) {
            c.title = titleObj.optString("zh_tw", titleObj.optString("en", ""));
        } else {
            c.title = obj.optString("title", "");
        }

        JSONArray children = obj.optJSONArray("children");
        if (children != null) {
            for (int i = 0; i < children.length(); i++) {
                JSONObject child = children.optJSONObject(i);
                if (child != null) {
                    c.children.add(Category.fromJson(child));
                    JSONObject childTitleObj = child.optJSONObject("title");
                    if (childTitleObj != null) {
                        c.subcategories.add(childTitleObj.optString("zh_tw", childTitleObj.optString("en", "")));
                    }
                }
            }
        }
        return c;
    }

    public List<Category> getChildren() { return children; }
    public Set<String> getAllIds() { Set<String> ids=new HashSet<>(); ids.add(id); for(Category child:children) ids.addAll(child.getAllIds()); return ids; }
    public String getId() { return id; }
    public String getTitle() { return title; }
    public List<String> getSubcategories() { return subcategories; }

    public String getSubcategoriesSummary() {
        if (subcategories.isEmpty()) return "官方專區";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < subcategories.size(); i++) {
            if (i > 0) sb.append(" │ ");
            sb.append(subcategories.get(i));
        }
        return sb.toString();
    }
}
