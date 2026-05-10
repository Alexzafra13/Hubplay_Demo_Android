package com.alex.hubplay

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.alex.hubplay.ui.HubplayApp
import com.alex.hubplay.ui.theme.HubPlayTheme

/**
 * Single-Activity host. All navigation lives inside Compose
 * (see [com.alex.hubplay.ui.nav.HubplayNavGraph]) — there are no
 * fragments, no extra Activities. This keeps lifecycle reasoning simple
 * and matches the patterns in Now in Android / official Compose samples.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container = (application as HubplayApp).container

        setContent {
            HubPlayTheme {
                HubplayApp(container = container)
            }
        }
    }
}
