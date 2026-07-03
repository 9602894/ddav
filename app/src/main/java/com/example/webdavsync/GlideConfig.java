package com.example.webdavsync;

import com.bumptech.glide.annotation.GlideModule;
import com.bumptech.glide.module.AppGlideModule;
import okhttp3.OkHttpClient;

@GlideModule
public class GlideConfig extends AppGlideModule {
    @Override
    public void registerComponents(android.content.Context context, Glide glide, Registry registry) {
        // 使用 WebDAVClient 提供的带认证的 OkHttpClient
        OkHttpClient client = WebDAVClient.getOkHttpClient();
        registry.replace(GlideUrl.class, InputStream.class, new OkHttpUrlLoader.Factory(client));
    }
}
