<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:background="#f5f5f5">

    <!-- 顶部标题栏 -->
    <androidx.appcompat.widget.Toolbar
        android:id="@+id/toolbar"
        android:layout_width="match_parent"
        android:layout_height="?attr/actionBarSize"
        android:background="?attr/colorPrimary"
        android:theme="@style/ThemeOverlay.AppCompat.Dark.ActionBar">

        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="本地相册"
            android:textColor="#ffffff"
            android:textSize="18sp" />
    </androidx.appcompat.widget.Toolbar>

    <!-- 路径和统计 -->
    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="horizontal"
        android:padding="8dp"
        android:background="#ffffff">

        <TextView
            android:id="@+id/tv_local_path"
            android:layout_width="0dp"
            android:layout_weight="1"
            android:layout_height="wrap_content"
            android:text="相册"
            android:textSize="14sp"
            android:textColor="#333333" />

        <TextView
            android:id="@+id/tv_local_status"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="0 项"
            android:textSize="14sp"
            android:textColor="#888888" />
    </LinearLayout>

    <!-- 网格列表 -->
    <androidx.recyclerview.widget.RecyclerView
        android:id="@+id/rv_local_files"
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="1"
        android:padding="4dp"
        android:clipToPadding="false"
        android:background="#f5f5f5" />

    <!-- 底部操作栏 -->
    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="horizontal"
        android:padding="8dp"
        android:background="#ffffff"
        android:elevation="4dp">

        <TextView
            android:id="@+id/tv_selected_count"
            android:layout_width="0dp"
            android:layout_weight="1"
            android:layout_height="wrap_content"
            android:text="已选 0 项"
            android:textSize="14sp"
            android:textColor="#333333"
            android:gravity="center_vertical" />

        <Button
            android:id="@+id/btn_local_upload"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="上传选中"
            android:backgroundTint="#4CAF50"
            android:textColor="#ffffff" />
    </LinearLayout>
</LinearLayout>
