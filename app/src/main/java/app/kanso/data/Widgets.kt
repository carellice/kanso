package app.kanso.data

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import kotlin.math.abs

/** I widget di Android ospitati nella pagina Consapevolezza. */
object Widgets {
    private const val HOST_ID = 0x4b414e // "KAN"
    private var host: AppWidgetHost? = null

    fun host(context: Context) = host ?: object : AppWidgetHost(context.applicationContext, HOST_ID) {
        override fun onCreateView(context: Context, appWidgetId: Int, appWidget: AppWidgetProviderInfo?): AppWidgetHostView = HostView(context)
    }.also { host = it }
    fun manager(context: Context): AppWidgetManager = AppWidgetManager.getInstance(context)

    /** Widget in bianco e nero, con pressione lunga per ridimensionarlo come in un launcher. */
    class HostView(context: Context) : AppWidgetHostView(context) {
        var onLongPress: (() -> Unit)? = null
        private var downX = 0f
        private var downY = 0f
        private var fired = false
        private val slop = ViewConfiguration.get(context).scaledTouchSlop
        private val longPress = Runnable {
            fired = true
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            onLongPress?.invoke()
        }

        init {
            setLayerType(View.LAYER_TYPE_HARDWARE, Paint().apply { colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) }) })
        }

        // I figli del widget consumano i tocchi: la pressione lunga si intercetta qui, prima di loro.
        override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    fired = false; downX = ev.x; downY = ev.y
                    postDelayed(longPress, ViewConfiguration.getLongPressTimeout().toLong())
                }
                MotionEvent.ACTION_MOVE -> if (abs(ev.x - downX) > slop || abs(ev.y - downY) > slop) removeCallbacks(longPress)
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> removeCallbacks(longPress)
            }
            return fired
        }

        // Il widget si ridisegna per la misura che ha davvero.
        @Suppress("DEPRECATION")
        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            super.onSizeChanged(w, h, oldw, oldh)
            val d = resources.displayMetrics.density
            val wd = (w / d).toInt()
            val hd = (h / d).toInt()
            if (wd > 0 && hd > 0) updateAppWidgetSize(Bundle(), wd, hd, wd, hd)
        }
    }
}
