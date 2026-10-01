package com.shiostudios.dumplingrings.ui.board3d

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * Minimal binary glTF (GLB) reader: first mesh primitive with POSITION / NORMAL / TEXCOORD_0 and the PBR textures
 * (baseColor, metallicRoughness, normal) embedded as images. Enough for Meshy output. No external dependencies.
 */
class GlbModel(
    val positions: FloatBuffer, val normals: FloatBuffer?, val uvs: FloatBuffer?, val indices: java.nio.Buffer, val indexType: Int, val indexCount: Int,
    val baseColor: Bitmap?, val metallicRoughness: Bitmap?, val normalMap: Bitmap?,
    val bounds: FloatArray, /* minx,miny,minz,maxx,maxy,maxz */
    val metallicFactor: Float, val roughnessFactor: Float,
)

object GlbLoader {
    fun load(bytes: ByteArray): GlbModel? {
        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        if (bb.int != 0x46546C67) return null // "glTF"
        bb.int; bb.int // version, length
        var json: JSONObject? = null; var bin: ByteBuffer? = null
        while (bb.remaining() >= 8) {
            val len = bb.int; val type = bb.int
            val chunk = ByteArray(len); bb.get(chunk)
            if (type == 0x4E4F534A) json = JSONObject(String(chunk, Charsets.UTF_8))
            else if (type == 0x004E4942) bin = ByteBuffer.wrap(chunk).order(ByteOrder.LITTLE_ENDIAN)
        }
        val j = json ?: return null; val b = bin ?: return null
        val accessors = j.getJSONArray("accessors"); val views = j.getJSONArray("bufferViews")
        fun accessorFloats(idx: Int): FloatBuffer {
            val a = accessors.getJSONObject(idx); val v = views.getJSONObject(a.getInt("bufferView"))
            val comps = when (a.getString("type")) { "SCALAR" -> 1; "VEC2" -> 2; "VEC3" -> 3; "VEC4" -> 4; else -> 3 }
            val count = a.getInt("count"); val off = v.optInt("byteOffset", 0) + a.optInt("byteOffset", 0)
            val stride = v.optInt("byteStride", comps * 4)
            val out = ByteBuffer.allocateDirect(count * comps * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
            for (i in 0 until count) for (c in 0 until comps) out.put(b.getFloat(off + i * stride + c * 4))
            out.position(0); return out
        }
        val mesh = j.getJSONArray("meshes").getJSONObject(0)
        val prim = mesh.getJSONArray("primitives").getJSONObject(0)
        val attrs = prim.getJSONObject("attributes")
        val pos = accessorFloats(attrs.getInt("POSITION"))
        val nrm = if (attrs.has("NORMAL")) accessorFloats(attrs.getInt("NORMAL")) else null
        val uv = if (attrs.has("TEXCOORD_0")) accessorFloats(attrs.getInt("TEXCOORD_0")) else null
        val ia = accessors.getJSONObject(prim.getInt("indices")); val iv = views.getJSONObject(ia.getInt("bufferView"))
        val icount = ia.getInt("count"); val ioff = iv.optInt("byteOffset", 0) + ia.optInt("byteOffset", 0)
        val ctype = ia.getInt("componentType")
        val indices: java.nio.Buffer; val glType: Int
        if (ctype == 5125) {
            val out = ByteBuffer.allocateDirect(icount * 4).order(ByteOrder.nativeOrder()).asIntBuffer()
            for (i in 0 until icount) out.put(b.getInt(ioff + i * 4)); out.position(0); indices = out; glType = 0x1405
        } else {
            val out = ByteBuffer.allocateDirect(icount * 2).order(ByteOrder.nativeOrder()).asShortBuffer()
            for (i in 0 until icount) out.put(if (ctype == 5123) b.getShort(ioff + i * 2) else (b.get(ioff + i).toInt() and 0xFF).toShort())
            out.position(0); indices = out; glType = 0x1403
        }
        var base: Bitmap? = null; var mr: Bitmap? = null; var nm: Bitmap? = null
        var metallic = 1f; var rough = 1f
        try {
            val matIdx = prim.optInt("material", -1)
            if (matIdx >= 0) {
                val mat = j.getJSONArray("materials").getJSONObject(matIdx)
                val pbr = mat.optJSONObject("pbrMetallicRoughness")
                metallic = pbr?.optDouble("metallicFactor", 1.0)?.toFloat() ?: 1f
                rough = pbr?.optDouble("roughnessFactor", 1.0)?.toFloat() ?: 1f
                fun image(texRef: JSONObject?): Bitmap? {
                    texRef ?: return null
                    val src = j.getJSONArray("textures").getJSONObject(texRef.getInt("index")).getInt("source")
                    val img = j.getJSONArray("images").getJSONObject(src)
                    if (!img.has("bufferView")) return null
                    val v = views.getJSONObject(img.getInt("bufferView"))
                    val off = v.optInt("byteOffset", 0); val len = v.getInt("byteLength")
                    val arr = ByteArray(len); b.position(off); b.get(arr); b.position(0)
                    return BitmapFactory.decodeByteArray(arr, 0, len)
                }
                base = image(pbr?.optJSONObject("baseColorTexture"))
                mr = image(pbr?.optJSONObject("metallicRoughnessTexture"))
                nm = image(mat.optJSONObject("normalTexture"))
            }
        } catch (e: Exception) { }
        val bounds = floatArrayOf(Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE)
        for (i in 0 until pos.capacity() / 3) for (c in 0..2) { val v = pos.get(i * 3 + c); if (v < bounds[c]) bounds[c] = v; if (v > bounds[c + 3]) bounds[c + 3] = v }
        return GlbModel(pos, nrm, uv, indices, glType, icount, base, mr, nm, bounds, metallic, rough)
    }
}
