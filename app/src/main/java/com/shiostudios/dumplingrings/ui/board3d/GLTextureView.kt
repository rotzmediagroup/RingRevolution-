package com.shiostudios.dumplingrings.ui.board3d

import android.content.Context
import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLSurfaceView
import android.util.Log
import android.view.TextureView

/**
 * A translucent GLES 3 view that composes like any other view: Compose content below it (the painted scene) shows through
 * the transparent pixels and Compose content above it (HUD, FX, dialogs) is drawn on top. A SurfaceView cannot do that —
 * it punches a hole in the window — which is why the board background turned black. Drives a [GLSurfaceView.Renderer] on
 * its own EGL thread, continuously, vsync-paced by eglSwapBuffers.
 */
open class GLTextureView(context: Context) : TextureView(context), TextureView.SurfaceTextureListener {
    private var renderer: GLSurfaceView.Renderer? = null
    private var thread: RenderThread? = null

    init { isOpaque = false; surfaceTextureListener = this }

    fun setRenderer(r: GLSurfaceView.Renderer) { renderer = r }

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        val r = renderer ?: return
        thread = RenderThread(surface, r, width, height).also { it.start() }
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) { thread?.resize(width, height) }

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean { thread?.finish(); thread = null; return true }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}

    fun onPause() { thread?.finish(); thread = null }

    private class RenderThread(private val surface: SurfaceTexture, private val renderer: GLSurfaceView.Renderer, @Volatile private var w: Int, @Volatile private var h: Int) : Thread("Board3D-GL") {
        @Volatile private var running = true
        @Volatile private var sizeChanged = true
        fun resize(width: Int, height: Int) { w = width; h = height; sizeChanged = true }
        fun finish() { running = false; try { join(1500) } catch (e: InterruptedException) { } }

        override fun run() {
            val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            val version = IntArray(2)
            if (!EGL14.eglInitialize(display, version, 0, version, 1)) { Log.e("Board3D", "eglInitialize failed"); return }
            val attribs = intArrayOf(
                EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_DEPTH_SIZE, 16, EGL14.EGL_RENDERABLE_TYPE, 0x40 /* EGL_OPENGL_ES3_BIT_KHR */,
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT, EGL14.EGL_NONE)
            val configs = arrayOfNulls<EGLConfig>(1); val num = IntArray(1)
            if (!EGL14.eglChooseConfig(display, attribs, 0, configs, 0, 1, num, 0) || num[0] == 0) { Log.e("Board3D", "no GLES3 RGBA8 config"); return }
            val config = configs[0]!!
            val ctx: EGLContext = EGL14.eglCreateContext(display, config, EGL14.EGL_NO_CONTEXT, intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 3, EGL14.EGL_NONE), 0)
            val eglSurface: EGLSurface = EGL14.eglCreateWindowSurface(display, config, surface, intArrayOf(EGL14.EGL_NONE), 0)
            if (!EGL14.eglMakeCurrent(display, eglSurface, eglSurface, ctx)) { Log.e("Board3D", "eglMakeCurrent failed"); return }
            EGL14.eglSwapInterval(display, 1)
            try {
                renderer.onSurfaceCreated(null, null)
                while (running) {
                    if (sizeChanged) { sizeChanged = false; renderer.onSurfaceChanged(null, w, h) }
                    renderer.onDrawFrame(null)
                    if (!EGL14.eglSwapBuffers(display, eglSurface)) { sleep(16) }
                }
            } catch (e: Exception) { Log.e("Board3D", "render thread: ${e.message}", e) }
            finally {
                EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
                EGL14.eglDestroySurface(display, eglSurface); EGL14.eglDestroyContext(display, ctx); EGL14.eglTerminate(display)
                surface.release()
            }
        }
    }
}
