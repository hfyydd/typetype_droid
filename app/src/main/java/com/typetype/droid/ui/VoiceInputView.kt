package com.typetype.droid.ui

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.typetype.droid.R
import com.typetype.droid.session.VoiceSessionState

class VoiceInputView(context: Context) : LinearLayout(context) {
    var onMicClicked: (() -> Unit)? = null
    var onDeleteClicked: (() -> Unit)? = null
    var onDeleteAllClicked: (() -> Unit)? = null
    private val baseBottomPadding = dp(12)
    private val handler = Handler(Looper.getMainLooper())
    private var deleteRepeating = false
    private var deleteAllPopup: PopupWindow? = null

    private val statusDot = View(context).apply {
        background = ovalDrawable(COLOR_IDLE)
    }
    private val statusView = TextView(context).apply {
        gravity = Gravity.CENTER_VERTICAL
        textSize = 12.5f
        setTextColor(COLOR_MUTED)
        includeFontPadding = false
    }
    private val deleteButton = ImageButton(context).apply {
        contentDescription = context.getString(R.string.delete_key)
        setImageResource(R.drawable.ic_backspace_24)
        setColorFilter(COLOR_DELETE_ICON)
        scaleType = ImageView.ScaleType.CENTER
        background = keyBackground(COLOR_DELETE_KEY)
        setPadding(dp(13), dp(10), dp(13), dp(10))
        setOnTouchListener { _, event -> handleDeleteTouch(event) }
    }
    private val micButton = ImageButton(context).apply {
        contentDescription = context.getString(R.string.mic_start)
        setImageResource(R.drawable.ic_mic_line)
        setColorFilter(Color.WHITE)
        scaleType = ImageView.ScaleType.CENTER
        background = ovalDrawable(COLOR_ACCENT)
        setPadding(dp(20), dp(20), dp(20), dp(20))
        setOnClickListener { onMicClicked?.invoke() }
    }

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER
        setPadding(dp(18), dp(10), dp(18), baseBottomPadding)
        minimumHeight = dp(112)
        background = panelBackground()

        val topRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        topRow.addView(
            LinearLayout(context).apply {
                orientation = HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = roundedDrawable(Color.rgb(231, 235, 242), dp(14))
                setPadding(dp(10), 0, dp(12), 0)
                addView(statusDot, LayoutParams(dp(7), dp(7)).apply { rightMargin = dp(7) })
                addView(statusView, LayoutParams(LayoutParams.WRAP_CONTENT, dp(28)))
            },
            LayoutParams(0, dp(32), 1f),
        )
        topRow.addView(deleteButton, LayoutParams(dp(56), dp(40)).apply { leftMargin = dp(12) })

        val micRow = LinearLayout(context).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, 0)
        }
        micRow.addView(micButton, LayoutParams(dp(72), dp(72)))

        addView(topRow, LayoutParams(LayoutParams.MATCH_PARENT, dp(40)))
        addView(micRow, LayoutParams(LayoutParams.MATCH_PARENT, dp(80)))

        ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
            val navigationBarInset = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            view.updatePadding(
                bottom = VoiceInputInsets.bottomPadding(
                    baseBottomPadding = baseBottomPadding,
                    navigationBarInset = navigationBarInset,
                ),
            )
            insets
        }
        ViewCompat.requestApplyInsets(this)
    }

    fun render(state: VoiceSessionState) {
        val statusColor: Int
        statusView.text = when {
            state.error != null -> {
                statusColor = COLOR_RECORDING
                context.getString(R.string.status_error)
            }
            state.phase == VoiceSessionState.Phase.PREPARING -> {
                statusColor = COLOR_PREPARING
                context.getString(R.string.status_preparing)
            }
            state.isDecoding -> {
                statusColor = COLOR_ACCENT
                context.getString(R.string.status_decoding)
            }
            state.isActive -> {
                statusColor = COLOR_ACCENT
                context.getString(R.string.status_listening)
            }
            else -> {
                statusColor = COLOR_IDLE
                context.getString(R.string.status_idle)
            }
        }
        statusDot.background = ovalDrawable(statusColor)
        micButton.contentDescription = context.getString(if (state.isActive) R.string.mic_stop else R.string.mic_start)
        micButton.background = roundedDrawable(
            if (state.isActive) COLOR_RECORDING else COLOR_ACCENT,
            dp(33),
        )
    }

    override fun onDetachedFromWindow() {
        stopDeleteRepeat()
        dismissDeleteAllPopup()
        super.onDetachedFromWindow()
    }

    private fun handleDeleteTouch(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                deleteButton.isPressed = true
                onDeleteClicked?.invoke()
                startDeleteRepeat()
                return true
            }

            MotionEvent.ACTION_UP -> {
                val popupVisible = deleteAllPopup?.isShowing == true
                stopDeleteRepeat()
                deleteButton.isPressed = false
                parent?.requestDisallowInterceptTouchEvent(false)
                if (!popupVisible) {
                    dismissDeleteAllPopup()
                }
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                stopDeleteRepeat()
                deleteButton.isPressed = false
                dismissDeleteAllPopup()
                parent?.requestDisallowInterceptTouchEvent(false)
                return true
            }
        }
        return true
    }

    private fun startDeleteRepeat() {
        deleteRepeating = true
        handler.postDelayed(deleteRepeatRunnable, DELETE_REPEAT_START_MS)
        handler.postDelayed(showDeleteAllRunnable, DELETE_ALL_POPUP_DELAY_MS)
    }

    private fun stopDeleteRepeat() {
        deleteRepeating = false
        handler.removeCallbacks(deleteRepeatRunnable)
        handler.removeCallbacks(showDeleteAllRunnable)
    }

    private val deleteRepeatRunnable = object : Runnable {
        override fun run() {
            if (!deleteRepeating) return
            onDeleteClicked?.invoke()
            handler.postDelayed(this, DELETE_REPEAT_INTERVAL_MS)
        }
    }

    private val showDeleteAllRunnable = Runnable {
        if (deleteRepeating) {
            showDeleteAllPopup()
        }
    }

    private fun showDeleteAllPopup() {
        if (deleteAllPopup?.isShowing == true || !deleteButton.isAttachedToWindow) return
        val button = TextView(context).apply {
            text = context.getString(R.string.delete_all_key)
            textSize = 14f
            gravity = Gravity.CENTER
            includeFontPadding = false
            setTextColor(Color.WHITE)
            background = roundedDrawable(COLOR_DELETE_ALL, dp(18))
            setPadding(dp(18), 0, dp(18), 0)
            elevation = dp(8).toFloat()
            setOnClickListener {
                onDeleteAllClicked?.invoke()
                dismissDeleteAllPopup()
            }
        }
        deleteAllPopup = PopupWindow(
            button,
            dp(104),
            dp(44),
            false,
        ).apply {
            isOutsideTouchable = true
            elevation = dp(8).toFloat()
            showAsDropDown(deleteButton, -dp(24), -dp(94), Gravity.NO_GRAVITY)
        }
    }

    private fun dismissDeleteAllPopup() {
        deleteAllPopup?.dismiss()
        deleteAllPopup = null
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun panelBackground(): GradientDrawable {
        return GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Color.rgb(239, 241, 247), Color.rgb(213, 217, 226)),
        ).apply {
            cornerRadius = dp(22).toFloat()
            setStroke(dp(1), Color.rgb(199, 204, 214))
        }
    }

    private fun roundedDrawable(color: Int, radius: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(color)
            cornerRadius = radius.toFloat()
        }
    }

    private fun ovalDrawable(color: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
        }
    }

    private fun keyBackground(color: Int): GradientDrawable {
        return roundedDrawable(color, dp(12)).apply {
            setStroke(dp(1), Color.rgb(207, 212, 220))
        }
    }

    private companion object {
        val COLOR_TEXT: Int = Color.rgb(29, 32, 35)
        val COLOR_MUTED: Int = Color.rgb(99, 106, 116)
        val COLOR_DELETE_KEY: Int = Color.rgb(244, 246, 250)
        val COLOR_DELETE_ICON: Int = Color.rgb(104, 112, 123)
        val COLOR_DELETE_ALL: Int = Color.rgb(33, 37, 43)
        val COLOR_PREPARING: Int = Color.rgb(232, 149, 44)
        val COLOR_ACCENT: Int = Color.rgb(15, 194, 147)
        val COLOR_RECORDING: Int = Color.rgb(203, 72, 63)
        val COLOR_IDLE: Int = Color.rgb(142, 150, 161)
        const val DELETE_REPEAT_START_MS = 320L
        const val DELETE_REPEAT_INTERVAL_MS = 58L
        const val DELETE_ALL_POPUP_DELAY_MS = 720L
    }
}
