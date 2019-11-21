package vn.focal.warper

import android.content.Context
import android.graphics.drawable.Drawable
import android.net.Uri
import com.facebook.common.util.UriUtil
import java.util.*


class RCTResourceDrawableIdHelper {
    private val mResourceDrawableIdMap: MutableMap<String, Int?>
    fun getResourceDrawableId(context: Context, name: String?): Int {
        var name = name
        if (name == null || name.isEmpty()) {
            return 0
        }
        name = name.toLowerCase().replace("-", "_")
        if (mResourceDrawableIdMap.containsKey(name)) {
            return mResourceDrawableIdMap[name]!!
        }
        val id = context.resources.getIdentifier(
                name,
                "drawable",
                context.packageName)
        mResourceDrawableIdMap[name] = id
        return id
    }

    fun getResourceDrawable(context: Context, name: String?): Drawable? {
        val resId = getResourceDrawableId(context, name)
        return if (resId > 0) context.resources.getDrawable(resId) else null
    }

    fun getResourceDrawableUri(context: Context, name: String?): Uri {
        val resId = getResourceDrawableId(context, name)
        return if (resId > 0) Uri.Builder()
                .scheme(UriUtil.LOCAL_RESOURCE_SCHEME)
                .path(resId.toString())
                .build() else Uri.EMPTY
    }

    init {
        mResourceDrawableIdMap = HashMap()
    }
}