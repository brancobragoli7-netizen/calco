package com.cogito.calco

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileNotFoundException

/**
 * El "contrato" que pide WhatsApp para leer paquetes de stickers de otra app.
 * Mismas rutas y columnas que el ejemplo oficial de WhatsApp (github.com/WhatsApp/stickers).
 */
class StickerContentProvider : ContentProvider() {

    private val matcher = UriMatcher(UriMatcher.NO_MATCH)
    private var authority = ""

    override fun onCreate(): Boolean {
        val ctx = context ?: return false
        authority = ctx.packageName + ".stickercontentprovider"
        matcher.addURI(authority, "metadata", METADATA)
        matcher.addURI(authority, "metadata/*", METADATA_SINGLE)
        matcher.addURI(authority, "stickers/*", STICKERS)
        matcher.addURI(authority, "stickers_asset/*/*", STICKER_ASSET)
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? {
        val ctx = context ?: return null
        val cursor = when (matcher.match(uri)) {
            METADATA -> metadataCursor(StickerStore.all(ctx))
            METADATA_SINGLE -> metadataCursor(listOfNotNull(StickerStore.read(ctx, uri.lastPathSegment ?: "")))
            STICKERS -> stickersCursor(StickerStore.read(ctx, uri.lastPathSegment ?: ""))
            else -> throw IllegalArgumentException("Ruta desconocida: $uri")
        }
        cursor.setNotificationUri(ctx.contentResolver, uri)
        return cursor
    }

    private fun metadataCursor(packs: List<StickerStore.Pack>): MatrixCursor {
        val c = MatrixCursor(
            arrayOf(
                "sticker_pack_identifier",
                "sticker_pack_name",
                "sticker_pack_publisher",
                "sticker_pack_icon",
                "android_play_store_link",
                "ios_app_download_link",
                "sticker_pack_publisher_email",
                "sticker_pack_publisher_website",
                "sticker_pack_privacy_policy_website",
                "sticker_pack_license_agreement_website",
                "image_data_version",
                "whatsapp_will_not_cache_stickers",
                "animated_sticker_pack",
            )
        )
        for (p in packs) {
            c.addRow(
                arrayOf<Any>(
                    p.id, p.name, p.publisher, p.tray,
                    "", "", "", "", "", "",
                    p.version.toString(),
                    0,
                    if (p.animated) 1 else 0,
                )
            )
        }
        return c
    }

    private fun stickersCursor(pack: StickerStore.Pack?): MatrixCursor {
        val c = MatrixCursor(arrayOf("sticker_file_name", "sticker_emoji", "sticker_accessibility_text"))
        pack?.stickers?.forEach { s -> c.addRow(arrayOf<Any>(s.file, s.emojis.joinToString(","), "")) }
        return c
    }

    override fun openAssetFile(uri: Uri, mode: String): AssetFileDescriptor? {
        if (matcher.match(uri) != STICKER_ASSET) throw IllegalArgumentException("Ruta desconocida: $uri")
        val ctx = context ?: return null
        val seg = uri.pathSegments
        if (seg.size != 3) throw FileNotFoundException(uri.toString())
        val file = File(StickerStore.dir(ctx, seg[1]), StickerStore.safe(seg[2]))
        if (!file.exists()) throw FileNotFoundException(uri.toString())
        val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        return AssetFileDescriptor(pfd, 0, AssetFileDescriptor.UNKNOWN_LENGTH)
    }

    override fun getType(uri: Uri): String? = when (matcher.match(uri)) {
        METADATA -> "vnd.android.cursor.dir/vnd.$authority.metadata"
        METADATA_SINGLE -> "vnd.android.cursor.item/vnd.$authority.metadata"
        STICKERS -> "vnd.android.cursor.dir/vnd.$authority.stickers"
        STICKER_ASSET -> if (uri.lastPathSegment?.endsWith(".png") == true) "image/png" else "image/webp"
        else -> null
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException()
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException()
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException()

    companion object {
        private const val METADATA = 1
        private const val METADATA_SINGLE = 2
        private const val STICKERS = 3
        private const val STICKER_ASSET = 4
    }
}
