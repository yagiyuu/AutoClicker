package io.github.yagiyuu.autoclicker

import android.annotation.SuppressLint
import android.content.Intent
import android.hardware.input.InputManager
import android.os.IBinder
import android.os.SystemClock
import android.view.InputEvent
import android.view.MotionEvent
import com.topjohnwu.superuser.ipc.RootService
import java.lang.reflect.Method

class TouchService : RootService() {

    companion object {
        private const val DEFAULT_SIZE = 1.0f
        private const val DEFAULT_META_STATE = 0
        private const val DEFAULT_PRECISION_X = 1.0f
        private const val DEFAULT_PRECISION_Y = 1.0f
        private const val DEFAULT_DEVICE_ID = 0
        private const val DEFAULT_EDGE_FLAGS = 0
    }

    private lateinit var inputManager: InputManager
    private lateinit var injectInputEventMethod: Method
    private var inputEventMode: Int = 0

    @SuppressLint("SoonBlockedPrivateApi")
    private fun setup() {
        val inputManagerClass = InputManager::class.java

        inputManager = getSystemService(INPUT_SERVICE) as InputManager

        injectInputEventMethod = inputManagerClass
            .getDeclaredMethod("injectInputEvent", InputEvent::class.java, Int::class.java)
            .apply { isAccessible = true }

        inputEventMode = inputManagerClass
            .getDeclaredField("INJECT_INPUT_EVENT_MODE_ASYNC")
            .apply { isAccessible = true }
            .get(null) as Int
    }

    override fun onBind(intent: Intent): IBinder {
        setup()
        return object : ITouchService.Stub() {
            @SuppressLint("PrivateApi")
            override fun touch(x: Float, y: Float) {
                val now = SystemClock.uptimeMillis()
                injectTouchEvent(inputManager, MotionEvent.ACTION_DOWN, now, x, y, 1.0f)
                injectTouchEvent(inputManager, MotionEvent.ACTION_UP, now, x, y, 0.0f)
            }
        }
    }

    private fun injectTouchEvent(
        inputManager: InputManager,
        action: Int,
        eventTime: Long,
        x: Float,
        y: Float,
        pressure: Float
    ) {
        val motionEvent = createMotionEvent(action, eventTime, x, y, pressure)
        injectInputEventMethod.invoke(inputManager, motionEvent, inputEventMode)
        motionEvent.recycle()
    }

    private fun createMotionEvent(
        action: Int,
        eventTime: Long,
        x: Float,
        y: Float,
        pressure: Float
    ): MotionEvent {
        return MotionEvent.obtain(
            eventTime,
            eventTime,
            action,
            x,
            y,
            pressure,
            DEFAULT_SIZE,
            DEFAULT_META_STATE,
            DEFAULT_PRECISION_X,
            DEFAULT_PRECISION_Y,
            DEFAULT_DEVICE_ID,
            DEFAULT_EDGE_FLAGS
        )
    }
}