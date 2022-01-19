package com.meituan.sample

import android.content.Context
import android.support.multidex.MultiDexApplication
import com.google.android.play.core.splitcompat.SplitCompat

/**
 * Author errysuprayogi on 27,January,2020
 */
class AppAplication : MultiDexApplication() {

    override fun onCreate() {
        super.onCreate()
    }

    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        SplitCompat.install(this)
    }
}