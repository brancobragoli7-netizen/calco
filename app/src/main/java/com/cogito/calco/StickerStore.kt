package com.cogito.calco

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Paquetes listos para WhatsApp, guardados en la memoria interna de la app:
 *   files/packs/<id>/meta.json, tray.png, 01.webp, 02.webp…
 * La interfaz (la página web) arma cada paquete y lo manda acá por partes.
 */
object StickerStore {

    class Sticker(val file: String, val emojis: List<String>)

    class Pack(
        val id: String,
        val name: String,
        val publisher: String,
        val animated: Boolean,
        val version: Int,
        val tray: String,
        val stickers: List<Sticker>,
    )

    private val unsafe = Regex("[^A-Za-z0-9_.-]")

    fun safe(s: String): String = s.replace(unsafe, "_").take(64).ifEmpty { "x" }

    private fun root(ctx: Context): File = File(ctx.filesDir, "packs").apply { mkdirs() }

    fun dir(ctx: Context, id: String): File = File(root(ctx), safe(id))

    fun staging(ctx: Context, id: String): File = File(File(ctx.filesDir, "staging"), safe(id))

    fun begin(ctx: Context, id: String): Boolean {
        val d = staging(ctx, id)
        d.deleteRecursively()
        return d.mkdirs()
    }

    fun put(ctx: Context, id: String, name: String, bytes: ByteArray) {
        File(staging(ctx, id), safe(name)).writeBytes(bytes)
    }

    /** Pasa el paquete preparado a su lugar definitivo y sube la versión para que WhatsApp lo refresque. */
    fun commit(ctx: Context, meta: JSONObject): Pack {
        val id = safe(meta.getString("id"))
        val old = read(ctx, id)
        val stage = staging(ctx, id)
        val stickers = JSONArray()
        val list = meta.getJSONArray("stickers")
        for (i in 0 until list.length()) {
            val s = list.getJSONObject(i)
            val emojis = JSONArray()
            val e = s.optJSONArray("emojis")
            if (e != null) for (k in 0 until minOf(3, e.length())) emojis.put(e.getString(k))
            if (emojis.length() == 0) emojis.put("😀")
            stickers.put(JSONObject().put("file", safe(s.getString("file"))).put("emojis", emojis))
        }
        val out = JSONObject()
            .put("id", id)
            .put("name", meta.optString("name", "Stickers").take(128))
            .put("publisher", meta.optString("publisher", "Calco").ifBlank { "Calco" }.take(128))
            .put("animated", meta.optBoolean("animated", false))
            .put("version", (old?.version ?: 0) + 1)
            .put("tray", safe(meta.optString("tray", "tray.png")))
            .put("stickers", stickers)
        File(stage, "meta.json").writeText(out.toString())

        val target = dir(ctx, id)
        target.deleteRecursively()
        if (!stage.renameTo(target)) {
            stage.copyRecursively(target, overwrite = true)
            stage.deleteRecursively()
        }
        return read(ctx, id) ?: throw IllegalStateException("No se pudo guardar el paquete")
    }

    fun read(ctx: Context, id: String): Pack? {
        val f = File(dir(ctx, id), "meta.json")
        if (!f.exists()) return null
        return try {
            val j = JSONObject(f.readText())
            val arr = j.getJSONArray("stickers")
            val stickers = ArrayList<Sticker>()
            for (i in 0 until arr.length()) {
                val s = arr.getJSONObject(i)
                val e = s.getJSONArray("emojis")
                val emojis = ArrayList<String>()
                for (k in 0 until e.length()) emojis.add(e.getString(k))
                stickers.add(Sticker(s.getString("file"), emojis))
            }
            Pack(
                id = j.getString("id"),
                name = j.getString("name"),
                publisher = j.getString("publisher"),
                animated = j.optBoolean("animated", false),
                version = j.optInt("version", 1),
                tray = j.optString("tray", "tray.png"),
                stickers = stickers,
            )
        } catch (e: Exception) {
            null
        }
    }

    fun all(ctx: Context): List<Pack> =
        root(ctx).listFiles()?.filter { it.isDirectory }?.mapNotNull { read(ctx, it.name) } ?: emptyList()
}
