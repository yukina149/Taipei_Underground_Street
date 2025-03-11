package com.example.cameraproject_2;

import android.content.Context;

import com.bumptech.glide.Glide;
import com.bumptech.glide.GlideBuilder;
import com.bumptech.glide.Registry;
import com.bumptech.glide.annotation.GlideModule;
import com.bumptech.glide.module.AppGlideModule;

@GlideModule
public class AppGlideModuleDatabase extends AppGlideModule {
    @Override
    public void applyOptions(Context context, GlideBuilder builder) {
        //  配置 Glide，緩存大小
    }

    @Override
    public void registerComponents(Context context, Glide glide, Registry registry) {
        //  注册组件，自定義加載數據
    }
}