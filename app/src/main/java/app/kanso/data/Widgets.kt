package app.kanso.data

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.Context

/** I widget di Android ospitati nella pagina Consapevolezza. */
object Widgets {
    private const val HOST_ID = 0x4b414e // "KAN"
    private var host: AppWidgetHost? = null

    fun host(context: Context) = host ?: AppWidgetHost(context.applicationContext, HOST_ID).also { host = it }
    fun manager(context: Context): AppWidgetManager = AppWidgetManager.getInstance(context)
}
