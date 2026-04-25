package com.typetype.droid

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 64, 48, 48)
        }

        val title = TextView(this).apply {
            text = getString(R.string.setup_title)
            textSize = 26f
        }
        val body = TextView(this).apply {
            text = getString(R.string.setup_body)
            textSize = 16f
            setPadding(0, 24, 0, 32)
        }
        val micButton = Button(this).apply {
            text = getString(R.string.grant_mic)
            setOnClickListener {
                if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_RECORD_AUDIO)
                }
            }
        }
        val settingsButton = Button(this).apply {
            text = getString(R.string.open_input_settings)
            setOnClickListener { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) }
        }
        val pickerButton = Button(this).apply {
            text = getString(R.string.choose_input_method)
            setOnClickListener {
                getSystemService(InputMethodManager::class.java).showInputMethodPicker()
            }
        }

        root.addView(title)
        root.addView(body)
        root.addView(micButton)
        root.addView(settingsButton)
        root.addView(pickerButton)
        setContentView(root)
    }

    private companion object {
        const val REQUEST_RECORD_AUDIO = 1001
    }
}
