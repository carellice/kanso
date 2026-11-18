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

    /** Widget in bianco e nero; se non scorrevole, i trascinamenti vanno alla pagina e non al widget. */
    class HostView(context: Context) : AppWidgetHostView(context) {
        var scrollable = true
        private var downX = 0f
        private var downY = 0f
        private val slop = ViewConfiguration.get(context).scaledTouchSlop

        init {
            setLayerType(View.LAYER_TYPE_HARDWARE, Paint().apply { colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) }) })
        }

        // Non scorrevole: oltre la soglia il gesto si toglie ai figli (ricevono CANCEL), i tocchi restano.
        override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
            if (scrollable) return false
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> { downX = ev.x; downY = ev.y }
                MotionEvent.ACTION_MOVE -> return abs(ev.x - downX) > slop || abs(ev.y - downY) > slop
            }
            return false
        }

        // Non scorrevole: le liste del widget non possono impedire alla pagina di scorrere.
        override fun requestDisallowInterceptTouchEvent(disallow: Boolean) {
            if (scrollable || !disallow) super.requestDisallowInterceptTouchEvent(disallow)
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
