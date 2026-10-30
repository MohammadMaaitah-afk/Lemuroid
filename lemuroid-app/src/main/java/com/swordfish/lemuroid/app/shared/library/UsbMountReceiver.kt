package com.swordfish.lemuroid.app.shared.library

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Rescans the library whenever a USB drive or SD card is plugged in or removed. */
class UsbMountReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        LibraryIndexScheduler.scheduleLibrarySync(context.applicationContext)
    }
}
