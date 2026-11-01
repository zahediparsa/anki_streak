package app.ankistreak

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import org.json.JSONArray

/**
 * Read-only access to AnkiDroid through its official ContentProvider API.
 * Nothing here ever writes to AnkiDroid.
 */
object AnkiBridge {
    const val PACKAGE = "com.ichi2.anki"
    const val PERMISSION = "com.ichi2.anki.permission.READ_WRITE_DATABASE"
    private val DECKS_URI: Uri = Uri.parse("content://com.ichi2.anki.flashcards/decks")

    data class Deck(
        val id: Long,
        val name: String,
        val learn: Int,
        val review: Int,
        val new: Int,
        val filtered: Boolean,
    )

    fun isInstalled(context: Context): Boolean = try {
        context.packageManager.getPackageInfo(PACKAGE, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    fun hasPermission(context: Context): Boolean =
        context.checkSelfPermission(PERMISSION) == PackageManager.PERMISSION_GRANTED

    /**
     * Every deck (including subdecks) with today's remaining counts.
     * Parent decks already include their subdecks' cards, exactly like
     * AnkiDroid's deck list. Returns null if AnkiDroid can't be read.
     */
    fun readDecks(context: Context): List<Deck>? {
        if (!isInstalled(context) || !hasPermission(context)) return null
        return try {
            val projection = arrayOf("deck_id", "deck_name", "deck_count", "deck_dyn")
            context.contentResolver.query(DECKS_URI, projection, null, null, null)?.use { c ->
                val iId = c.getColumnIndexOrThrow("deck_id")
                val iName = c.getColumnIndexOrThrow("deck_name")
                val iCount = c.getColumnIndexOrThrow("deck_count")
                val iDyn = c.getColumnIndex("deck_dyn")
                val decks = ArrayList<Deck>()
                while (c.moveToNext()) {
                    // Format documented by AnkiDroid: [learn, review, new]
                    val counts = JSONArray(c.getString(iCount) ?: "[0,0,0]")
                    val dyn = if (iDyn >= 0) c.getString(iDyn) else null
                    decks += Deck(
                        id = c.getLong(iId),
                        name = c.getString(iName) ?: "?",
                        learn = counts.optInt(0, 0),
                        review = counts.optInt(1, 0),
                        new = counts.optInt(2, 0),
                        filtered = dyn == "true" || dyn == "1",
                    )
                }
                decks
            }
        } catch (e: Exception) {
            Log.w("AnkiStreak", "Could not read AnkiDroid decks", e)
            null
        }
    }
}
