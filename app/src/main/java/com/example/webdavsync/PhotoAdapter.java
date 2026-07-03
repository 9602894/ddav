package com.example.webdavsync;

import android.content.Context;
import android.graphics.Color;
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
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class PhotoAdapter extends RecyclerView.Adapter<PhotoAdapter.ViewHolder> {

    private Context context;
    private List<PhotoItem> items = new ArrayList<>();
    private boolean showCloudBadge = false;
    private boolean showLocalBadge = false;
    private boolean isCloudView = false;

    public interface OnItemClickListener {
        void onItemClick(PhotoItem item, int position);
    }

    public interface OnItemLongClickListener {
        void onItemLongClick(PhotoItem item, int position);
    }

    private OnItemClickListener clickListener;
    private OnItemLongClickListener longClickListener;

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
        PhotoItem item = items.get(position);

        holder.cbSelect.setChecked(item.isSelected);

        // ★ 选中边框：Pho 风格——蓝色外发光边框（用 CardView 的 elevation + 描边模拟）
        if (item.isSelected) {
            // 设置卡片背景为白色，但添加外发光（使用 elevation + 阴影颜色）
            holder.cardView.setCardBackgroundColor(Color.WHITE);
            holder.cardView.setCardElevation(16f);
            // 添加描边效果（通过设置前景或直接修改背景为带边框的 drawable，这里简单用背景色+阴影）
            // 我们使用自定义 drawable 或直接设置边框（CardView 不支持直接边框，改用背景色）
            // 替代方案：使用一个自定义 View 作为边框，但为了简单，我们强烈改变背景色并添加阴影
            holder.cardView.setCardBackgroundColor(Color.parseColor("#E3F2FD")); // 淡蓝背景
            holder.cardView.setRadius(8f);
        } else {
            holder.cardView.setCardBackgroundColor(Color.WHITE);
            holder.cardView.setCardElevation(2f);
            holder.cardView.setRadius(4f);
        }

        // ★ 云朵标记（Pho 风格：半透明蓝色背景 + 白色云朵）
        if (showCloudBadge && item.isOnCloud) {
            holder.tvCloudBadge.setVisibility(View.VISIBLE);
            holder.tvCloudBadge.setText("☁️");
            holder.tvCloudBadge.setTextColor(Color.WHITE);
            holder.tvCloudBadge.setBackgroundColor(Color.parseColor("#80 1E88E5")); // 半透明蓝
            holder.tvCloudBadge.setPadding(4, 2, 4, 2);
            holder.tvCloudBadge.setElevation(4f);
        } else {
            holder.tvCloudBadge.setVisibility(View.GONE);
        }

        // ★ 手机标记（Pho 风格：半透明绿色背景 + 白色手机）
        if (showLocalBadge && item.isOnLocal) {
            holder.tvLocalBadge.setVisibility(View.VISIBLE);
            holder.tvLocalBadge.setText("📱");
            holder.tvLocalBadge.setTextColor(Color.WHITE);
            holder.tvLocalBadge.setBackgroundColor(Color.parseColor("#80 43A047")); // 半透明绿
            holder.tvLocalBadge.setPadding(4, 2, 4, 2);
            holder.tvLocalBadge.setElevation(4f);
        } else {
            holder.tvLocalBadge.setVisibility(View.GONE);
        }

        // 视频标记
        if (item.isVideo) {
            holder.ivVideoBadge.setVisibility(View.VISIBLE);
        } else {
            holder.ivVideoBadge.setVisibility(View.GONE);
        }

        holder.tvName.setText(item.displayName);
        holder.tvName.setVisibility(View.VISIBLE);

        // 缩略图
        if (isCloudView && item.remoteUrl != null) {
            Glide.with(context)
                    .load(item.remoteUrl)
                    .apply(new RequestOptions()
                            .centerCrop()
                            .override(300, 300)
                            .placeholder(android.R.drawable.ic_menu_gallery)
                            .error(android.R.drawable.ic_menu_gallery))
                    .into(holder.ivThumbnail);
        } else if (item.file != null && item.file.exists()) {
            if (item.isVideo) {
                android.graphics.Bitmap bitmap = ThumbnailUtils.createVideoThumbnail(
                        item.file.getAbsolutePath(),
                        MediaStore.Video.Thumbnails.MINI_KIND);
                if (bitmap != null) holder.ivThumbnail.setImageBitmap(bitmap);
                else holder.ivThumbnail.setImageResource(android.R.drawable.ic_menu_gallery);
            } else {
                Glide.with(context)
                        .load(item.file)
                        .apply(new RequestOptions()
                                .centerCrop()
                                .override(300, 300)
                                .placeholder(android.R.drawable.ic_menu_gallery)
                                .error(android.R.drawable.ic_menu_gallery))
                        .into(holder.ivThumbnail);
            }
        } else {
            holder.ivThumbnail.setImageResource(android.R.drawable.ic_menu_gallery);
        }

        // 点击事件：传递给 Activity
        holder.itemView.setOnClickListener(v -> {
            if (clickListener != null) clickListener.onItemClick(item, position);
        });

        holder.cbSelect.setOnClickListener(v -> {
            if (clickListener != null) clickListener.onItemClick(item, position);
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

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

    public static class PhotoItem {
        public String name;
        public String displayName;
        public File file;
        public String remoteUrl;
        public boolean isSelected;
        public boolean isOnCloud;
        public boolean isOnLocal;
        public boolean isVideo;
        public long dateModified;

        public PhotoItem(String name) {
            this.name = name;
            this.displayName = name;
        }
    }
}
