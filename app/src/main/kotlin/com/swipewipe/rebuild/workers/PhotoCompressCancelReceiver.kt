package com.swipewipe.rebuild.workers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class PhotoCompressCancelReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION = "com.swipewipe.rebuild.PHOTO_COMPRESS_CANCEL"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION) {
            CompressState.cancelled.set(true)
        }
    }
}
