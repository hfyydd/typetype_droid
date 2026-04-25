package com.typetype.droid.ui

import android.content.Context
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import com.typetype.droid.R
import com.typetype.droid.session.DictationMode
import com.typetype.droid.session.VoiceSessionState

class VoiceInputView(context: Context) : LinearLayout(context) {
    var onMicClicked: (() -> Unit)? = null
    var onModeChanged: ((DictationMode) -> Unit)? = null

    private val statusView = TextView(context).apply {
        gravity = Gravity.CENTER
        textSize = 14f
    }
    private val micButton = Button(context).apply {
        text = context.getString(R.string.mic_start)
        minHeight = dp(52)
        setOnClickListener { onMicClicked?.invoke() }
    }
    private val modeGroup = RadioGroup(context).apply {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER
    }
    private val streamingMode = RadioButton(context).apply {
        id = generateViewId()
        text = context.getString(R.string.mode_streaming)
    }
    private val offlineMode = RadioButton(context).apply {
        id = generateViewId()
        text = context.getString(R.string.mode_offline)
    }

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER
        setPadding(dp(16), dp(10), dp(16), dp(10))
        minimumHeight = dp(116)

        modeGroup.addView(streamingMode)
        modeGroup.addView(offlineMode)
        modeGroup.check(streamingMode.id)
        modeGroup.setOnCheckedChangeListener { _, checkedId ->
            val mode = if (checkedId == offlineMode.id) DictationMode.OFFLINE else DictationMode.STREAMING
            onModeChanged?.invoke(mode)
        }

        addView(statusView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        addView(micButton, LayoutParams(LayoutParams.MATCH_PARENT, dp(56)))
        addView(modeGroup, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
    }

    fun render(state: VoiceSessionState) {
        statusView.text = when {
            state.error != null -> context.getString(R.string.status_error)
            state.isDecoding -> context.getString(R.string.status_decoding)
            state.isActive -> context.getString(R.string.status_listening)
            else -> context.getString(R.string.status_idle)
        }
        micButton.text = context.getString(if (state.isActive) R.string.mic_stop else R.string.mic_start)
        val targetId = if (state.mode == DictationMode.OFFLINE) offlineMode.id else streamingMode.id
        if (modeGroup.checkedRadioButtonId != targetId) {
            modeGroup.check(targetId)
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
