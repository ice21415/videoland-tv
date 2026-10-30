package com.videoland.tv.adapter;
import android.content.Context;
import android.view.*;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.videoland.tv.R;
import com.videoland.tv.model.LiveSchedule;
import java.util.*;
public class ScheduleAdapter extends RecyclerView.Adapter<ScheduleAdapter.Holder> {
 public interface OnItemClickListener { void onItemClick(LiveSchedule item); }
 private final List<LiveSchedule> items=new ArrayList<>(); private final OnItemClickListener listener;
 public ScheduleAdapter(Context context,OnItemClickListener listener){this.listener=listener;}
 public void setData(List<LiveSchedule> data){items.clear();if(data!=null)items.addAll(data);notifyDataSetChanged();}
 @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup p,int type){return new Holder(LayoutInflater.from(p.getContext()).inflate(R.layout.item_schedule,p,false));}
 @Override public void onBindViewHolder(@NonNull Holder h,int position){
  LiveSchedule i=items.get(position);long now=System.currentTimeMillis()/1000L;
  String state=i.getStartTime()>now?"即將開賽":i.getEndTime()>0&&i.getEndTime()<now?"觀看回放":"直播中";
  h.title.setText(i.getTitle());h.league.setText(i.getSubtitle());h.time.setText(i.getFormattedTime());h.state.setText(state);
  h.cover.setScaleType(i.getCoverUrl()==null||i.getCoverUrl().isEmpty()?ImageView.ScaleType.CENTER_INSIDE:ImageView.ScaleType.CENTER_CROP);
  Glide.with(h.cover).load(i.getCoverUrl()).placeholder(R.drawable.tv_banner).error(R.drawable.tv_banner).into(h.cover);
  h.itemView.setContentDescription(i.getTitle()+"，"+i.getSubtitle()+"，"+i.getFormattedTime()+"，"+state);
  if(android.os.Build.VERSION.SDK_INT>=26)h.itemView.setTooltipText(i.getTitle());
  h.itemView.setOnClickListener(v->listener.onItemClick(i));
  h.itemView.setOnFocusChangeListener((v,focused)->{if(focused)v.post(()->v.requestRectangleOnScreen(new android.graphics.Rect(0,0,v.getWidth(),v.getHeight()),true));});
 }
 @Override public int getItemCount(){return items.size();}
 static class Holder extends RecyclerView.ViewHolder {
  final TextView title,league,time,state;final ImageView cover;
  Holder(View v){super(v);title=v.findViewById(R.id.tv_title);league=v.findViewById(R.id.tv_league);time=v.findViewById(R.id.tv_time);state=v.findViewById(R.id.tv_play_btn);cover=v.findViewById(R.id.iv_cover);}
 }
}