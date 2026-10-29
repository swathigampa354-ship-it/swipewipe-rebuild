package com.swipewipe.rebuild.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.swipewipe.rebuild.SettingsStore
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private var unlocked by mutableStateOf(false)
    private var requestedOnce = false
    private val needsPermissions = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        refreshPermissionState()
        setContent {
            val settingsFlow = SettingsStore(applicationContext).flow
            val settingsHolder = collectAsStateHolder(settingsFlow)
            SwipewipeTheme(settingsHolder.value.darkMode) {
                if (settingsHolder.value.appLock && !unlocked) {
                    LockedGate { unlocked = true }
                } else if (needsPermissions.value) {
                    PermissionGate { retryPermissions() }
                } else {
                    AppScaffold(settingsHolder.value, vm = viewModelHolder())
                }
            }
        }
    }

    private fun refreshPermissionState() {
        val needed = buildList {
            add(Manifest.permission.READ_MEDIA_IMAGES)
            add(Manifest.permission.READ_MEDIA_VIDEO)
        }
        val missing = needed.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        val notifications =
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
        needsPermissions.value = missing.isNotEmpty()
        if (!needsPermissions.value && notifications && android.os.Build.VERSION.SDK_INT >= 33) {
            if (!requestedOnce) {
                requestedOnce = true
                androidx.core.app.ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    4096
                )
            }
        }
    }

    private fun retryPermissions() {
        requestedOnce = true
        val needed = arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.POST_NOTIFICATIONS
        )
        val missing = needed.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            androidx.core.app.ActivityCompat.requestPermissions(this, missing.toTypedArray(), 4096)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        refreshPermissionState()
    }
}
