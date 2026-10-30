package com.videoland.tv.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.videoland.tv.R;
import com.videoland.tv.model.Banner;

import java.util.ArrayList;
import java.util.List;

public class BannerAdapter extends RecyclerView.Adapter<BannerAdapter.ViewHolder> {

    public interface OnItemClickListener {
        void onItemClick(Banner item);
    }

    private final Context context;
    private final List<Banner> items = new ArrayList<>();
    private final OnItemClickListener listener;

    public BannerAdapter(Context context, OnItemClickListener listener) {
        this.context = context;
        this.listener = listener;
    }

    public void setData(List<Banner> newItems) {
        items.clear();
        if (newItems != null) {
            items.addAll(newItems);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_banner, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Banner item = items.get(position);
        holder.tvTitle.setText(item.getTitle());
        holder.tvTag.setText(item.isPay() ? "會員專屬" : "官方精選");

        if (item.getCoverUrl() != null && !item.getCoverUrl().isEmpty()) {
            Glide.with(context)
                    .load(item.getCoverUrl())
                    .placeholder(R.drawable.tv_banner)
                    .error(R.drawable.tv_banner)
                    .into(holder.ivCover);
        } else {
            holder.ivCover.setImageResource(R.drawable.tv_banner);
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onItemClick(item);
            }
        });

        holder.itemView.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                v.animate().scaleX(1.05f).scaleY(1.05f).setDuration(150).start();
            } else {
                v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).start();
            }
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvTitle, tvTag;
        ImageView ivCover;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tv_banner_title);
            tvTag = itemView.findViewById(R.id.tv_banner_tag);
            ivCover = itemView.findViewById(R.id.iv_banner_cover);
        }
    }
}
