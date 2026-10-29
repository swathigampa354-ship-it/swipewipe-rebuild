package com.swipewipe.rebuild.graph

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.swipewipe.rebuild.MainActivity
import com.swipewipe.rebuild.SettingsStore
import kotlinx.coroutines.launch

class GraphAuthActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val data = intent?.data
        lifecycleScope.launch {
            val ctx = this@GraphAuthActivity
            try {
                val clientId = SettingsStore(applicationContext).flow.first().graphClientId
                if (clientId.isEmpty()) {
                    toast(ctx, "Set your Microsoft client id in Settings first")
                } else when {
                    data == null -> {
                        val url = GraphManager(applicationContext).beginSignIn(clientId)
                        val i = Intent(Intent.ACTION_VIEW).apply {
                            setData(android.net.Uri.parse(url))
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        try {
                            startActivity(i)
                        } catch (t: Throwable) {
                            toast(ctx, "No browser available to complete sign-in")
                        }
                    }
                    else -> {
                        GraphManager(applicationContext).handleRedirect(data, clientId)
                        toast(ctx, "OneDrive connected")
                        startActivity(
                            Intent(ctx, MainActivity::class.java)
                                .setFlags(
                                    Intent.FLAG_ACTIVITY_NEW_TASK or
                                        Intent.FLAG_ACTIVITY_CLEAR_TOP
                                )
                        )
                    }
                }
            } catch (t: Throwable) {
                toast(ctx, "Sign-in failed: ${t.message ?: "unknown error"}")
            } finally {
                if (data != null) finish()
            }
        }
    }

    private fun toast(ctx: GraphAuthActivity, msg: String) {
        Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
    }
}
