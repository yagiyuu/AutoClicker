package io.github.yagiyuu.autoclicker.util

import android.content.Context
import android.os.Build
import android.view.WindowManager

object Utils {
    fun WindowManager.LayoutParams.disableAnimation(): WindowManager.LayoutParams {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            setCanPlayMoveAnimation(false)
        } else {
            val className = "android.view.WindowManager\$LayoutParams"
            try {
                val layoutParamsClass = Class.forName(className)
                val noAnimFlagField = layoutParamsClass.getField("PRIVATE_FLAG_NO_MOVE_ANIMATION")
                val privateFlags = layoutParamsClass.getField("privateFlags")

                val currentFlags = privateFlags.getInt(this)
                val noAnimFlag = noAnimFlagField.getInt(null)

                privateFlags.setInt(this, currentFlags or noAnimFlag)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return this
    }

    fun dpToPx(context: Context, dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }
}