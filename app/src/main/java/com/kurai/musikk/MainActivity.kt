package com.kurai.musikk

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.kurai.musikk.ui.theme.MusikkTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        installCrashReporter()
        enableEdgeToEdge()
        setContent {
            MusikkTheme {
                MusikkApp(modifier = Modifier.fillMaxSize())
            }
        }
    }

    private fun installCrashReporter() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e("MusikkCrash", "Uncaught on ${thread.name}", throwable)
            try {
                Toast.makeText(
                    applicationContext,
                    "Crash: ${throwable.javaClass.simpleName}: ${throwable.message}",
                    Toast.LENGTH_LONG,
                ).show()
            } catch (_: Throwable) { }
            previous?.uncaughtException(thread, throwable)
        }
    }
}
