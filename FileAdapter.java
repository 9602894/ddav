package com.example.webdavsync;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
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

public class FileAdapter extends RecyclerView.Adapter<FileAdapter.ViewHolder> {

    private Context context;
    private List<FileItem> items = new ArrayList<>();
    private boolean isLocal; // true: 本地文件（显示缩略图），false: 云端文件（只显示图标）

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

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_file, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        FileItem item = items.get(position);
        holder.tvName.setText(item.name);
        holder.cbSelect.setChecked(item.isSelected);

        if (isLocal && item.file != null && item.file.exists()) {
            // 加载本地缩略图
            loadLocalThumbnail(holder.ivThumb, item.file);
        } else {
            // 云端或非图片文件显示默认图标
            holder.ivThumb.setImageResource(android.R.drawable.ic_menu_gallery);
        }

        // 点击条目切换选中状态
        holder.itemView.setOnClickListener(v -> {
            item.isSelected = !item.isSelected;
            holder.cbSelect.setChecked(item.isSelected);
            // 可以添加回调
        });
        // 点击复选框同样切换
        holder.cbSelect.setOnCheckedChangeListener(null); // 避免循环
        holder.cbSelect.setOnCheckedChangeListener((buttonView, isChecked) -> {
            item.isSelected = isChecked;
        });
    }

    private void loadLocalThumbnail(ImageView iv, File file) {
        String path = file.getAbsolutePath();
        String mimeType = getMimeType(path);
        if (mimeType != null && mimeType.startsWith("video/")) {
            // 视频缩略图
            Bitmap bitmap = ThumbnailUtils.createVideoThumbnail(path, MediaStore.Video.Thumbnails.MINI_KIND);
            if (bitmap != null) {
                iv.setImageBitmap(bitmap);
                return;
            }
        }
        // 图片缩略图，使用 Glide
        Glide.with(context)
                .load(file)
                .apply(new RequestOptions().centerCrop().override(160, 160))
                .into(iv);
    }

    private String getMimeType(String path) {
        // 简单通过扩展名判断
        String ext = path.substring(path.lastIndexOf('.') + 1).toLowerCase();
        if (ext.matches("jpg|jpeg|png|gif|bmp")) return "image/";
        if (ext.matches("mp4|3gp|avi|mkv")) return "video/";
        return null;
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivThumb;
        TextView tvName;
        CheckBox cbSelect;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivThumb = itemView.findViewById(R.id.iv_thumbnail);
            tvName = itemView.findViewById(R.id.tv_name);
            cbSelect = itemView.findViewById(R.id.cb_select);
        }
    }

    public static class FileItem {
        public String name;
        public File file; // 本地文件对象，云端可为 null
        public String remotePath; // 远程路径
        public boolean isSelected;

        public FileItem(String name) {
            this.name = name;
        }
    }
}
