package com.example.webdavsync;

import android.content.Context;
import android.media.ThumbnailUtils;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class PhotoAdapter extends RecyclerView.Adapter<PhotoAdapter.ViewHolder> {

    private Context context;
    private List<PhotoItem> items = new ArrayList<>();
    private OnItemClickListener listener;
    private boolean showCloudBadge = false;

    public interface OnItemClickListener {
        void onItemClick(PhotoItem item, int position);
    }

    public PhotoAdapter(Context context) {
        this.context = context;
    }

    public void setItems(List<PhotoItem> list) {
        this.items = list;
        notifyDataSetChanged();
    }

    public List<PhotoItem> getItems() {
        return items;
    }

    public void setShowCloudBadge(boolean show) {
        this.showCloudBadge = show;
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_photo, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        PhotoItem item = items.get(position);

        holder.cbSelect.setChecked(item.isSelected);

        // 云端标记
        if (showCloudBadge && item.isOnCloud) {
            holder.tvCloudBadge.setVisibility(View.VISIBLE);
        } else {
            holder.tvCloudBadge.setVisibility(View.GONE);
        }

        // 视频标记
        if (item.isVideo) {
            holder.ivVideoBadge.setVisibility(View.VISIBLE);
        } else {
            holder.ivVideoBadge.setVisibility(View.GONE);
        }

        // 加载缩略图
        if (item.file != null && item.file.exists()) {
            Glide.with(context)
                    .load(item.file)
                    .apply(new RequestOptions()
                            .centerCrop()
                            .override(300, 300)
                            .placeholder(android.R.drawable.ic_menu_gallery)
                            .error(android.R.drawable.ic_menu_gallery))
                    .into(holder.ivThumbnail);
        } else {
            holder.ivThumbnail.setImageResource(android.R.drawable.ic_menu_gallery);
        }

        // 点击事件
        holder.itemView.setOnClickListener(v -> {
            item.isSelected = !item.isSelected;
            holder.cbSelect.setChecked(item.isSelected);
            if (listener != null) listener.onItemClick(item, position);
        });

        holder.cbSelect.setOnClickListener(v -> {
            item.isSelected = !item.isSelected;
            holder.cbSelect.setChecked(item.isSelected);
            if (listener != null) listener.onItemClick(item, position);
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivThumbnail, ivVideoBadge;
        TextView tvName, tvCloudBadge;
        CheckBox cbSelect;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivThumbnail = itemView.findViewById(R.id.iv_thumbnail);
            ivVideoBadge = itemView.findViewById(R.id.iv_video_badge);
            tvName = itemView.findViewById(R.id.tv_name);
            tvCloudBadge = itemView.findViewById(R.id.tv_cloud_badge);
            cbSelect = itemView.findViewById(R.id.cb_select);
        }
    }

    public static class PhotoItem {
        public String name;
        public File file;
        public boolean isSelected;
        public boolean isOnCloud;
        public boolean isVideo;
        public long dateModified;

        public PhotoItem(String name) {
            this.name = name;
        }
    }
}
