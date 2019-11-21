package vn.focal.warper

import android.annotation.SuppressLint
import android.os.AsyncTask
import android.util.Log
import android.view.View
import com.facebook.react.uimanager.ThemedReactContext
import com.facebook.react.uimanager.ViewGroupManager
import com.facebook.react.uimanager.annotations.ReactProp
import vn.focal.protos.Protos
import java.io.InputStream
import java.net.URL
import java.nio.ByteBuffer
import java.nio.ByteOrder

class WarpViewManager : ViewGroupManager<WarpView>() {

    companion object {
        private const val LOG_TAG = "WarpView"

        private const val REACT_CLASS = "WarpView"
    }

    override fun getName(): String {
        return REACT_CLASS
    }

    override fun createViewInstance(reactContext: ThemedReactContext): WarpView {
        return WarpView(reactContext)
    }

    override fun addView(parent: WarpView?, child: View?, index: Int) {
        Log.d(LOG_TAG, "addView $parent $child $index")
        if (parent == null) {
            throw NullPointerException("view parent is null")
        }
        if (child == null) {
            throw NullPointerException("view child is null")
        }
        parent.publicViewRoot.addView(child, index)
    }

    override fun removeViewAt(parent: WarpView?, index: Int) {
        Log.d(LOG_TAG, "removeViewAt $parent $index")
        if (parent == null) {
            throw NullPointerException("view parent is null")
        }
        parent.publicViewRoot.removeViewAt(index)
    }

    override fun removeView(parent: WarpView?, view: View?) {
        if (parent == null) {
            throw NullPointerException("view parent is null")
        }
        if (view == null) {
            throw NullPointerException("view child is null")
        }
        parent.publicViewRoot.removeView(view)
    }

    override fun getChildAt(parent: WarpView?, index: Int): View {
        if (parent == null) {
            throw NullPointerException("view parent is null")
        }
        return parent.publicViewRoot.getChildAt(index)
    }

    override fun getChildCount(parent: WarpView?): Int {
        if (parent == null) {
            throw NullPointerException("view parent is null")
        }
        return parent.publicViewRoot.childCount
    }

    @ReactProp(name = "uri")
    public fun setUri(view: WarpView, uri: String): Unit {
        Log.d(LOG_TAG, "setUri $uri")
        val task = @SuppressLint("StaticFieldLeak")
        object : AsyncTask<Unit, Unit, List<WarpMesh>>() {

            private val resourceHelper = RCTResourceDrawableIdHelper()

            private fun loadLocalResource(name: String): InputStream {
                Log.d(LOG_TAG, "loadLocalResource $name")
                val context = view.context
                val rid = resourceHelper.getResourceDrawableId(context, name)

                return context.resources.openRawResource(rid)
            }

            private fun loadRemoteResource(uri: String): InputStream {
                Log.d(LOG_TAG, "loadRemoteResource $uri")
                return URL(uri).openStream()
            }

            override fun doInBackground(vararg params: Unit?): List<WarpMesh> {
                Log.d(LOG_TAG, "doInBackground")
                val inputStream = when {
                    uri.startsWith("http://") -> loadRemoteResource(uri)
                    else -> loadLocalResource(uri)
                }

                val protoRenderItems = Protos.RenderItems.parseFrom(inputStream)

                return protoRenderItems.itemsList.map {
                    WarpMesh(
                            vertexCount = it.vertexCount,
                            positionBuffer = ByteBuffer
                                    .allocateDirect(it.positionBuffer.size())
                                    .order(ByteOrder.nativeOrder())
                                    .apply {
                                        it.positionBuffer.copyTo(this)
                                        this.rewind()
                                    }
                                    .asFloatBuffer()
                                    .asReadOnlyBuffer(),
                            uvBuffer = ByteBuffer
                                    .allocateDirect(it.uvBuffer.size())
                                    .order(ByteOrder.nativeOrder())
                                    .apply {
                                        it.uvBuffer.copyTo(this)
                                        this.rewind()
                                    }
                                    .asFloatBuffer()
                                    .asReadOnlyBuffer(),
                            indexCount = it.indexCount,
                            indexBuffer = ByteBuffer
                                    .allocateDirect(it.indexBuffer.size())
                                    .order(ByteOrder.nativeOrder())
                                    .apply {
                                        it.indexBuffer.copyTo(this)
                                        this.rewind()
                                    }
                                    .asShortBuffer()
                    )
                }
            }

            override fun onPostExecute(meshes: List<WarpMesh>) {
                view.setMeshes(meshes)
            }
        }

        task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR)
    }

}