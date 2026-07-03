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
public class GlideConfig extends AppGlideModule {

    @Override
    public void registerComponents(Context context, Glide glide, Registry registry) {
        // 使用 WebDAVClient 提供的带认证的 OkHttpClient
        OkHttpClient client = WebDAVClient.getOkHttpClient();
        // 替换默认的 OkHttpUrlLoader.Factory
        registry.replace(GlideUrl.class, InputStream.class, new OkHttpUrlLoader.Factory(client));
    }

    // 为了加快加载速度，可以禁用 Manifest 解析（可选）
    @Override
    public boolean isManifestParsingEnabled() {
        return false;
    }
}
