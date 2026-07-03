package com.example.webdavsync;

import android.content.Context;
import com.bumptech.glide.Glide;
import com.bumptech.glide.Registry;
import com.bumptech.glide.annotation.GlideModule;
import com.bumptech.glide.integration.okhttp3.OkHttpUrlLoader;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.module.AppGlideModule;
import java.io.InputStream;
import okhttp3.OkHttpClient;

@GlideModule
public final class GlideConfig extends AppGlideModule {

    @Override
    public void registerComponents(Context context, Glide glide, Registry registry) {
        OkHttpClient client = WebDAVClient.getOkHttpClient();
        registry.replace(GlideUrl.class, InputStream.class, new OkHttpUrlLoader.Factory(client));
    }

    // 禁用解析 AndroidManifest 中的 Glide 模块（可选，避免重复）
    @Override
    public boolean isManifestParsingEnabled() {
        return false;
    }
}
