package dev.voxpilot.assistant.live

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import android.util.Log
import androidx.core.content.ContextCompat

data class ContactMatch(val displayName: String, val number: String, val contactId: Long) {
    /** Digits and a leading '+' only - what tel: / wa.me want. */
    val dialable: String get() = number.filter { it.isDigit() || it == '+' }
}

sealed interface ContactLookup {
    data class Found(val best: ContactMatch) : ContactLookup
    /** Several different people match and none is an exact name. */
    data class Ambiguous(val names: List<String>) : ContactLookup
    data object NoPermission : ContactLookup
    data class NotFound(val name: String) : ContactLookup
}

/** Turns a spoken name (or number) into a real phone number. */
object Contacts {
    private const val TAG = "Contacts"

    fun lookup(context: Context, query: String): ContactLookup {
        val cleaned = query.trim()
        if (cleaned.isEmpty()) return ContactLookup.NotFound(query)
        if (looksLikeNumber(cleaned)) return ContactLookup.Found(ContactMatch(cleaned, cleaned, -1))
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return ContactLookup.NoPermission
        }

        var matches = queryByName(context, cleaned)
        // "Rahul bhai" -> try progressively shorter forms ("Rahul") before giving up.
        if (matches.isEmpty() && cleaned.contains(' ')) {
            for (word in cleaned.split(' ').filter { it.length >= 3 }) {
                matches = queryByName(context, word)
                if (matches.isNotEmpty()) break
            }
        }
        if (matches.isEmpty()) return ContactLookup.NotFound(cleaned)

        val rank = { m: ContactMatch ->
            when {
                m.displayName.equals(cleaned, true) -> 3
                m.displayName.startsWith(cleaned, true) -> 2
                else -> 1
            }
        }
        val sorted = matches.sortedByDescending(rank)
        val best = sorted.first()
        val distinctPeople = sorted.map { it.contactId }.distinct()
        if (distinctPeople.size > 1 && rank(best) < 3) {
            // Same-rank different people: let the user pick instead of calling the wrong one.
            val top = sorted.filter { rank(it) == rank(best) }.map { it.contactId }.distinct()
            if (top.size > 1) return ContactLookup.Ambiguous(sorted.map { it.displayName }.distinct().take(4))
        }
        return ContactLookup.Found(best)
    }

    fun looksLikeNumber(value: String): Boolean {
        val digits = value.count { it.isDigit() }
        return digits >= 5 && value.all { it.isDigit() || it in " +-()." }
    }

    private fun queryByName(context: Context, name: String): List<ContactMatch> {
        val found = ArrayList<ContactMatch>()
        try {
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                    ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                ),
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
                arrayOf("%$name%"),
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC",
            )?.use { c ->
                while (c.moveToNext() && found.size < 12) {
                    val display = c.getString(0) ?: continue
                    val number = c.getString(1) ?: continue
                    found.add(ContactMatch(display, number, c.getLong(2)))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Contact lookup failed for '$name'", e)
        }
        return found
    }

    /**
     * The data row WhatsApp registers for a contact's voice/video call - launching it places the
     * call directly, no UI tapping. Null if the contact isn't on WhatsApp.
     */
    fun whatsappCallDataId(context: Context, contactId: Long, video: Boolean): Long? {
        if (contactId < 0) return null
        val mime = if (video) "vnd.android.cursor.item/vnd.com.whatsapp.video.call"
        else "vnd.android.cursor.item/vnd.com.whatsapp.voip.call"
        return try {
            context.contentResolver.query(
                ContactsContract.Data.CONTENT_URI,
                arrayOf(ContactsContract.Data._ID),
                "${ContactsContract.Data.CONTACT_ID}=? AND ${ContactsContract.Data.MIMETYPE}=?",
                arrayOf(contactId.toString(), mime),
                null,
            )?.use { if (it.moveToFirst()) it.getLong(0) else null }
        } catch (e: Exception) {
            Log.w(TAG, "WhatsApp call row lookup failed: ${e.message}")
            null
        }
    }
}
