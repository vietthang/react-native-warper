package vn.focal.warper

import android.content.Context
import android.graphics.Color
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.os.Parcel
import android.util.Log
import android.view.Surface
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.WindowManager
import android.widget.FrameLayout
import com.facebook.react.views.view.ReactViewGroup

class WindowInjector(private val context: Context) : ViewTreeObserver.OnPreDrawListener {

    companion object {
        private const val LOG_TAG = "WindowInjector"
    }

    private val mWindowViewRoot: ViewGroup

    private var mWindowAttached = false

    private var mSurface: Surface? = null

    public val publicViewRoot: ViewGroup

    init {
        mWindowViewRoot = FrameLayout(context)
        publicViewRoot = ReactViewGroup(context)
        publicViewRoot.setBackgroundColor(Color.GREEN)
        mWindowViewRoot.addView(
                publicViewRoot,
                ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                )
        )
    }

    fun updateWindow(surface: Surface, viewport: Rect, format: Int) {
        mWindowViewRoot.viewTreeObserver.addOnPreDrawListener(this)

        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val params = WindowManager.LayoutParams(
                viewport.width(),
                viewport.height(),
                viewport.left,
                viewport.top,
                WindowManager.LayoutParams.TYPE_APPLICATION,
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
                        or WindowManager.LayoutParams.FLAG_FULLSCREEN,
                format
        )

        if (!mWindowAttached) {
            wm.addView(mWindowViewRoot, params)
        } else {
            wm.updateViewLayout(mWindowViewRoot, params)
        }

        mSurface = surface
    }

    private fun getSurfaceNativeObject(surface: Surface): Long {
        val field = Surface::class.java.getDeclaredField("mNativeObject")
        if (!field.isAccessible) {
            field.isAccessible = true
        }
        return when (val nativePointer = field.get(surface)) {
            is Int -> nativePointer.toLong()
            is Long -> nativePointer
            else -> throw RuntimeException("Invalid return value type")
        }
    }

    override fun onPreDraw(): Boolean {
        val surface = mSurface ?: return false

        Log.d(LOG_TAG, "Has surface")

        // rootViewImpl is not ready yet
        val rootViewImpl = mWindowViewRoot.rootView.parent ?: return false
        val surfaceField = rootViewImpl.javaClass.getDeclaredField("mSurface")
        surfaceField.isAccessible = true
        val windowSurface = surfaceField.get(rootViewImpl) as Surface
        surfaceField.isAccessible = false

        // if surface is already injected, just skipped
        if (getSurfaceNativeObject(surface) == getSurfaceNativeObject(windowSurface)) {
            Log.d(LOG_TAG, "Same surface, skipped")
            return true
        }

        val parcel = Parcel.obtain()
        surface.writeToParcel(parcel, 0)
        parcel.setDataPosition(0)
        windowSurface.readFromParcel(parcel)

        val attachInfoField = rootViewImpl.javaClass.getDeclaredField("mAttachInfo")
        attachInfoField.isAccessible = true
        val attachInfo = attachInfoField.get(rootViewImpl)
        attachInfoField.isAccessible = false

        val hardwareRendererField = attachInfo.javaClass.getDeclaredField("mHardwareRenderer")
        hardwareRendererField.isAccessible = true
        val hardwareRenderer = hardwareRendererField.get(attachInfo)
        hardwareRendererField.isAccessible = false

        val updateSurfaceMethod =
                hardwareRenderer.javaClass.getDeclaredMethod("updateSurface", Surface::class.java)
        updateSurfaceMethod.isAccessible = true
        updateSurfaceMethod.invoke(hardwareRenderer, windowSurface)
        updateSurfaceMethod.isAccessible = false

        Log.d(LOG_TAG, "Overwrited surface")

        return true
    }

}