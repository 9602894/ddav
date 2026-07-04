package com.example.webdavsync;

import android.content.Context;
import android.graphics.Color;
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
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class AllFilesAdapter extends RecyclerView.Adapter<AllFilesAdapter.ViewHolder> {

    private Context context;
    private List<FileItem> items = new ArrayList<>();
    private boolean showCloudBadge = false;
    private boolean showLocalBadge = false;
    private boolean isCloudView = false;

    public interface OnItemClickListener {
        void onItemClick(FileItem item, int position);
    }
    public interface OnItemLongClickListener {
        void onItemLongClick(FileItem item, int position);
    }

    private OnItemClickListener clickListener;
    private OnItemLongClickListener longClickListener;

    public AllFilesAdapter(Context context) {
        this.context = context;
    }

    public void setItems(List<FileItem> list) {
        this.items = list;
        notifyDataSetChanged();
    }
    public List<FileItem> getItems() { return items; }

    public void setShowCloudBadge(boolean show) { this.showCloudBadge = show; }
    public void setShowLocalBadge(boolean show) { this.showLocalBadge = show; }
    public void setCloudView(boolean isCloud) { this.isCloudView = isCloud; }

    public void setOnItemClickListener(OnItemClickListener listener) { this.clickListener = listener; }
    public void setOnItemLongClickListener(OnItemLongClickListener listener) { this.longClickListener = listener; }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_photo, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        FileItem item = items.get(position);

        holder.cbSelect.setChecked(item.isSelected);

        if (item.isSelected) {
            holder.cardView.setCardBackgroundColor(Color.parseColor("#FFE0B2"));
            holder.cardView.setCardElevation(16f);
        } else {
            holder.cardView.setCardBackgroundColor(Color.WHITE);
            holder.cardView.setCardElevation(2f);
        }

        if (showCloudBadge && item.isOnCloud) {
            holder.tvCloudBadge.setVisibility(View.VISIBLE);
            holder.tvCloudBadge.setText("☁️");
            holder.tvCloudBadge.setTextColor(Color.WHITE);
            holder.tvCloudBadge.setBackgroundResource(R.drawable.badge_cloud_bg);
        } else {
            holder.tvCloudBadge.setVisibility(View.GONE);
        }

        if (showLocalBadge && item.isOnLocal) {
            holder.tvLocalBadge.setVisibility(View.VISIBLE);
            holder.tvLocalBadge.setText("📱");
            holder.tvLocalBadge.setTextColor(Color.WHITE);
            holder.tvLocalBadge.setBackgroundResource(R.drawable.badge_local_bg);
        } else {
            holder.tvLocalBadge.setVisibility(View.GONE);
        }

        holder.ivVideoBadge.setVisibility(View.GONE);
        holder.tvName.setText(item.displayName);

        String ext = "";
        if (item.name.contains(".")) {
            ext = item.name.substring(item.name.lastIndexOf('.') + 1).toLowerCase();
        }
        if (isImageFile(ext) || isVideoFile(ext)) {
            if (isCloudView && item.file == null) {
                holder.ivThumbnail.setImageResource(android.R.drawable.ic_menu_gallery);
            } else if (item.file != null && item.file.exists()) {
                Glide.with(context)
                        .load(item.file)
                        .centerCrop()
                        .override(300, 300)
                        .placeholder(android.R.drawable.ic_menu_gallery)
                        .error(android.R.drawable.ic_menu_gallery)
                        .into(holder.ivThumbnail);
            } else {
                holder.ivThumbnail.setImageResource(android.R.drawable.ic_menu_gallery);
            }
        } else {
            holder.ivThumbnail.setImageResource(getFileTypeIcon(ext));
        }

        holder.itemView.setOnClickListener(v -> {
            if (clickListener != null) clickListener.onItemClick(item, position);
        });

        holder.itemView.setOnLongClickListener(v -> {
            if (longClickListener != null) {
                longClickListener.onItemLongClick(item, position);
                return true;
            }
            return false;
        });

        holder.cbSelect.setOnClickListener(v -> {
            if (clickListener != null) clickListener.onItemClick(item, position);
        });
    }

    private boolean isImageFile(String ext) {
        return ext.matches("jpg|jpeg|png|gif|bmp|webp");
    }

    private boolean isVideoFile(String ext) {
        return ext.matches("mp4|3gp|avi|mkv|mov|webm");
    }

    private int getFileTypeIcon(String ext) {
        switch (ext) {
            case "pdf": return android.R.drawable.ic_menu_agenda;
            case "doc":
            case "docx": return android.R.drawable.ic_menu_edit;
            case "xls":
            case "xlsx": return android.R.drawable.ic_menu_manage;
            case "ppt":
            case "pptx": return android.R.drawable.ic_menu_slideshow;
            case "zip":
            case "rar":
            case "7z": return android.R.drawable.ic_menu_gallery;
            case "mp3":
            case "wav":
            case "flac": return android.R.drawable.ic_menu_my_calendar;
            case "txt":
            case "log": return android.R.drawable.ic_menu_info_details;
            default: return android.R.drawable.ic_menu_gallery;
        }
    }

    @Override
    public int getItemCount() { return items.size(); }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivThumbnail, ivVideoBadge;
        TextView tvName, tvCloudBadge, tvLocalBadge;
        CheckBox cbSelect;
        CardView cardView;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivThumbnail = itemView.findViewById(R.id.iv_thumbnail);
            ivVideoBadge = itemView.findViewById(R.id.iv_video_badge);
            tvName = itemView.findViewById(R.id.tv_name);
            tvCloudBadge = itemView.findViewById(R.id.tv_cloud_badge);
            tvLocalBadge = itemView.findViewById(R.id.tv_local_badge);
            cbSelect = itemView.findViewById(R.id.cb_select);
            cardView = itemView.findViewById(R.id.card_view);
        }
    }

    public static class FileItem {
        public String name;
        public String displayName;
        public File file;
        public boolean isSelected;
        public boolean isOnCloud;
        public boolean isOnLocal;
        public long dateModified;

        public FileItem(String name) {
            this.name = name;
            this.displayName = name;
        }
    }
}
