package com.rakku.app.ui.anime

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper

/**
 * LocalContext.current di Compose kadang berupa ContextWrapper (bukan Activity
 * langsung), jadi perlu ditelusuri ke atas rantai wrapper-nya buat nemuin Activity
 * asli. Dibutuhin buat kontrol orientasi layar & system bars (fullscreen player).
 */
fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return ctx as? Activity
}
