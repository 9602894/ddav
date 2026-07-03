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
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class FileAdapter extends RecyclerView.Adapter<FileAdapter.ViewHolder> {

    private Context context;
    private List<FileItem> items = new ArrayList<>();
    private boolean isLocal;
    private OnItemClickListener listener;

    public interface OnItemClickListener {
        void onItemClick(FileItem item, int position);
    }

    public FileAdapter(Context context, boolean isLocal) {
        this.context = context;
        this.isLocal = isLocal;
    }

    public void setItems(List<FileItem> list) {
        this.items = list;
        notifyDataSetChanged();
    }

    public List<FileItem> getItems() {
        return items;
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_file, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        FileItem item = items.get(position);

        holder.tvName.setText(item.displayName);
        holder.cbSelect.setChecked(item.isSelected);

        if (isLocal && item.isOnCloud) {
            holder.tvCloudBadge.setVisibility(View.VISIBLE);
        } else {
            holder.tvCloudBadge.setVisibility(View.GONE);
        }

        if (item.fileSize > 0) {
            holder.tvSize.setText(formatFileSize(item.fileSize));
        } else {
            holder.tvSize.setText("");
        }

        if (item.isVideo) {
            holder.ivVideoBadge.setVisibility(View.VISIBLE);
        } else {
            holder.ivVideoBadge.setVisibility(View.GONE);
        }

        // 加载缩略图
        if (isLocal && item.file != null && item.file.exists()) {
            // 本地：使用 Glide 加载缩略图
            Glide.with(context)
                    .load(item.file)
                    .apply(new RequestOptions()
                            .centerCrop()
                            .override(200, 200)
                            .placeholder(android.R.drawable.ic_menu_gallery)
                            .error(android.R.drawable.ic_menu_gallery))
                    .into(holder.ivThumbnail);
        } else if (!isLocal) {
            // 云端：直接显示默认图标，不进行网络加载，避免闪退
            holder.ivThumbnail.setImageResource(android.R.drawable.ic_menu_gallery);
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

    private String formatFileSize(long size) {
        if (size < 1024) return size + "B";
        if (size < 1024 * 1024) return String.format(Locale.getDefault(), "%.1fKB", size / 1024.0);
        if (size < 1024 * 1024 * 1024) return String.format(Locale.getDefault(), "%.1fMB", size / (1024.0 * 1024));
        return String.format(Locale.getDefault(), "%.1fGB", size / (1024.0 * 1024 * 1024));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivThumbnail, ivVideoBadge;
        TextView tvName, tvCloudBadge, tvSize;
        CheckBox cbSelect;
        CardView cardView;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivThumbnail = itemView.findViewById(R.id.iv_thumbnail);
            ivVideoBadge = itemView.findViewById(R.id.iv_video_badge);
            tvName = itemView.findViewById(R.id.tv_name);
            tvCloudBadge = itemView.findViewById(R.id.tv_cloud_badge);
            tvSize = itemView.findViewById(R.id.tv_size);
            cbSelect = itemView.findViewById(R.id.cb_select);
            cardView = (CardView) itemView;
        }
    }

    public static class FileItem {
        public String name;
        public String displayName;
        public File file;
        public String remotePath;
        public String remoteUrl;
        public boolean isSelected;
        public boolean isOnCloud;
        public boolean isVideo;
        public long fileSize;
        public long lastModified;

        public FileItem(String name) {
            this.name = name;
            this.displayName = name;
        }
    }
}
