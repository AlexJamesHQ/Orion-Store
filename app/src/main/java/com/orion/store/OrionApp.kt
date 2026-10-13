package com.orion.store

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.request.crossfade

class OrionApp : Application(), SingletonImageLoader.Factory {
    /** Every remote image fades in instead of popping. */
    override fun newImageLoader(context: PlatformContext) = ImageLoader.Builder(context).crossfade(150).build()
}
