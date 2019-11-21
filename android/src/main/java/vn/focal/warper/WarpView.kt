package vn.focal.warper

import android.content.Context
import android.graphics.Rect
import android.graphics.SurfaceTexture
import android.util.Log
import android.view.*
import android.widget.FrameLayout
import com.google.android.filament.*
import com.google.android.filament.View
import java.nio.ByteBuffer
import java.nio.FloatBuffer
import java.nio.ShortBuffer

data class WarpMesh(
        val vertexCount: Int,
        val positionBuffer: FloatBuffer,
        val uvBuffer: FloatBuffer,
        val indexCount: Int,
        val indexBuffer: ShortBuffer)

class WarpView(context: Context) : FrameLayout(context), SurfaceHolder.Callback, Choreographer.FrameCallback {
    companion object {

        private const val LOG_TAG = "WarpView"

        init {
            Filament.init()
        }
    }

    private val mSurfaceView = SurfaceView(context)

    private val mResourceManager = ResourceManager()

    private val mEngine: Engine

    private val mRenderer: Renderer

    private val mScene: Scene

    private val mView: View

    private val mCamera: Camera

    private val mSurfaceTexture: SurfaceTexture

    private val mInputSurface: Surface

    private val mTexture: Texture

    private val mMaterial: Material

    private val mMaterialInstance: MaterialInstance

    private var mSwapChain: SwapChain? = null

    private var mEntities: List<Int> = emptyList()

    private var mWindowInjector = WindowInjector(context)

    init {
        Log.d(LOG_TAG, "Init")
        mSurfaceView.holder.addCallback(this)
        this.addView(mSurfaceView, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        mEngine = mResourceManager.addResource(
                { Engine.create() },
                { it.destroy() }
        )
        mRenderer = mResourceManager.addResource(
                { mEngine.createRenderer() },
                { mEngine.destroyRenderer(it) }
        )
        mScene = mResourceManager.addResource(
                { mEngine.createScene() },
                { mEngine.destroyScene(it) }
        )
        mCamera = mResourceManager.addResource(
                { mEngine.createCamera() },
                { mEngine.destroyCamera(it) }
        )
        mView = mResourceManager.addResource(
                {
                    mEngine.createView().apply {
                        this.setClearColor(0.035f, 0.035f, 0.035f, 1.0f)
                        this.camera = mCamera
                        this.scene = mScene
                    }
                },
                { mEngine.destroyView(it) }
        )

        mSurfaceTexture = mResourceManager.addResource(
                { SurfaceTexture(0) },
                { it.release() }
        )

        mInputSurface = mResourceManager.addResource(
                { Surface(mSurfaceTexture) },
                { it.release() }
        )

        mTexture = mResourceManager.addResource(
                {
                    val stream = mResourceManager.addResource(
                            {
                                Stream.Builder().stream(mSurfaceTexture).build(mEngine)
                            },
                            { mEngine.destroyStream(it) }
                    )

                    Texture.Builder()
                            .sampler(Texture.Sampler.SAMPLER_EXTERNAL)
                            .build(mEngine)
                            .apply {
                                this.setExternalStream(mEngine, stream)
                            }
                },
                {}
        )

        mMaterial = mResourceManager.addResource({
            loadMaterialFromAsset("materials/baked_color.filamat")
        }, {
            mEngine.destroyMaterial(it)
        })

        mMaterialInstance = mResourceManager.addResource({
            mMaterial.createInstance().apply {
                this.setParameter("baseColor", mTexture, TextureSampler(
                        TextureSampler.MinFilter.LINEAR,
                        TextureSampler.MagFilter.LINEAR,
                        TextureSampler.WrapMode.CLAMP_TO_EDGE
                ))
                this.setCullingMode(Material.CullingMode.NONE)
            }
        }, {
            mEngine.destroyMaterialInstance(it)
        })
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        Log.d(LOG_TAG, "surfaceCreated")

        Choreographer.getInstance().postFrameCallback(this)
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        Log.d(LOG_TAG, "surfaceChanged w=$width h=$height")
        val coords = IntArray(2)
        mSurfaceView.getLocationInWindow(coords)
        Log.d(LOG_TAG, "position x=${coords[0]} y=${coords[1]}")

        mSurfaceTexture.setDefaultBufferSize(width, height)

        mSwapChain?.let {
            mEngine.destroySwapChain(it)
        }

        mSwapChain = mEngine.createSwapChain(holder.surface)

        mCamera.setProjection(
                Camera.Projection.ORTHO,
                (-1280 / 2).toDouble(),
                (1280 / 2).toDouble(),
                (720 / 2).toDouble(),
                (-720 / 2).toDouble(),
                -10.0,
                10.0
        )
        mCamera.lookAt(
                (1280 / 2).toDouble(),
                (720 / 2).toDouble(),
                1.0,
                (1280 / 2).toDouble(),
                (720 / 2).toDouble(),
                0.0,
                0.0,
                0.1,
                0.0
        )

        mView.viewport = Viewport(0, 0, width, height)

        mWindowInjector.updateWindow(
                surface = mInputSurface,
                viewport = Rect(coords[0], coords[1], coords[0] + width, coords[1] + height),
                format = format
        )
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        Log.d(LOG_TAG, "surfaceDestroyed w=$width h=$height")

        Choreographer.getInstance().removeFrameCallback(this)
    }

    fun destroy() {
        mResourceManager.destroy()
    }

    private fun loadMaterialFromAsset(assetName: String): Material {
        val bytes = this.context.assets.open(assetName).readBytes()
        val buffer = ByteBuffer
                .allocateDirect(bytes.size)
                .apply {
                    this.put(bytes, 0, bytes.size)
                    this.rewind()
                }

        return Material.Builder()
                .payload(buffer, buffer.remaining())
                .build(mEngine)
    }

    override fun doFrame(frameTimeNanos: Long) {
        Choreographer.getInstance().postFrameCallback(this)

        mSwapChain?.let {
            if (mRenderer.beginFrame(it)) {
                mRenderer.render(mView)
                mRenderer.endFrame()
            }
        }
    }

    fun setMeshes(meshes: List<WarpMesh>) {
        mEntities.forEach {
            mScene.removeEntity(it)
            mEngine.destroyEntity(it)
            EntityManager.get().destroy(it)
        }

        mEntities = meshes.map {
            val entity = EntityManager.get().create()

            // Declare the layout of our mesh
            val vertexBuffer = VertexBuffer.Builder()
                    .bufferCount(2)
                    .vertexCount(it.vertexCount)
                    .attribute(VertexBuffer.VertexAttribute.POSITION, 0, VertexBuffer.AttributeType.FLOAT3, 0, 3 * 4)
                    .attribute(VertexBuffer.VertexAttribute.UV0, 1, VertexBuffer.AttributeType.FLOAT2, 0, 2 * 4)
                    .build(mEngine)
                    .apply {
                        this.setBufferAt(mEngine, 0, it.positionBuffer)
                        this.setBufferAt(mEngine, 1, it.uvBuffer)
                    }

            val indexBuffer = IndexBuffer.Builder()
                    .indexCount(it.indexCount)
                    .bufferType(IndexBuffer.Builder.IndexType.USHORT)
                    .build(mEngine)
                    .apply {
                        this.setBuffer(mEngine, it.indexBuffer)
                    }

            // We then create a renderable component on that entity
            // A renderable is made of several primitives; in this case we declare only 1
            RenderableManager.Builder(1)
                    // Overall bounding box of the renderable
                    .boundingBox(Box(0.0f, 0.0f, 0.0f, 1.0f, 1.0f, 0.01f))
                    // Sets the mesh data of the first primitive
                    .geometry(0, RenderableManager.PrimitiveType.TRIANGLES, vertexBuffer, indexBuffer, 0, it.indexCount)
                    // Sets the material of the first primitive
                    .material(0, mMaterialInstance)
                    .build(mEngine, entity)

            mScene.addEntity(entity)

            entity
        }
    }

    public val publicViewRoot: ViewGroup
        get() {
            return mWindowInjector.publicViewRoot
        }

}
