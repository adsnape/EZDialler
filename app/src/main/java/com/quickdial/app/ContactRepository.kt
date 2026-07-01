package com.quickdial.app

import android.content.ContentUris
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.provider.ContactsContract
import androidx.core.content.edit

class ContactRepository(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "quickdial_contacts"
        const val MAX_CONTACTS = 6
        private const val KEY_CONTACT_ID     = "contact_id_"
        private const val KEY_LOOKUP_KEY     = "contact_lookup_"
        private const val KEY_CONTACT_NAME   = "contact_name_"
        private const val KEY_CONTACT_PHONE  = "contact_phone_"
        private const val KEY_CONTACT_MOBILE = "contact_mobile_"
        private const val KEY_CALL_MODE      = "call_mode_"

        // WhatsApp's MIME types for its synced Contacts Provider rows.
        // These only exist if WhatsApp has contact-sync / shortcuts enabled
        // and the contact is on WhatsApp.
        private const val WHATSAPP_VIDEO_CALL_MIME = "vnd.android.cursor.item/vnd.com.whatsapp.video.call"
        private const val WHATSAPP_VOICE_CALL_MIME = "vnd.android.cursor.item/vnd.com.whatsapp.voice.call"
    }

    /** Save a chosen contact into a slot (0–5). */
    fun saveContact(slot: Int, contact: Contact) {
        prefs.edit {
            putLong("$KEY_CONTACT_ID$slot", contact.id)
            putString("$KEY_LOOKUP_KEY$slot", contact.lookupKey)
            putString("$KEY_CONTACT_NAME$slot", contact.name)
            putString("$KEY_CONTACT_PHONE$slot", contact.phone)
            putString("$KEY_CONTACT_MOBILE$slot", contact.mobilePhone)
            putString("$KEY_CALL_MODE$slot", contact.callMode.name)
        }
    }

    /** Update only the call mode for an existing slot. */
    fun saveCallMode(slot: Int, mode: CallMode) {
        prefs.edit { putString("$KEY_CALL_MODE$slot", mode.name) }
    }

    /** Clear a contact slot. */
    fun clearContact(slot: Int) {
        prefs.edit {
            remove("$KEY_CONTACT_ID$slot")
            remove("$KEY_LOOKUP_KEY$slot")
            remove("$KEY_CONTACT_NAME$slot")
            remove("$KEY_CONTACT_PHONE$slot")
            remove("$KEY_CONTACT_MOBILE$slot")
            remove("$KEY_CALL_MODE$slot")
        }
    }

    /**
     * Load the saved contacts (null for empty slots).
     *
     * Contact photos are looked up via the contact's stable LOOKUP_KEY
     * rather than the raw numeric CONTACT_ID. Android's Contacts Provider
     * can re-aggregate a person's record (assigning a new CONTACT_ID) when
     * another app — e.g. WhatsApp — syncs its own raw contact data for that
     * person. If we only remembered the old numeric ID, that ID can go
     * stale and silently stop resolving a photo. The LOOKUP_KEY is
     * Android's documented identifier designed specifically to survive
     * these re-aggregations, so we re-resolve the live CONTACT_ID from it
     * on every load before fetching anything.
     */
    fun loadSavedContacts(): List<Contact?> {
        return (0 until MAX_CONTACTS).map { slot ->
            val savedId   = prefs.getLong("$KEY_CONTACT_ID$slot", -1L)
            val lookupKey = prefs.getString("$KEY_LOOKUP_KEY$slot", null)
            val name      = prefs.getString("$KEY_CONTACT_NAME$slot", null)
            val phone     = prefs.getString("$KEY_CONTACT_PHONE$slot", null)
            val mobile    = prefs.getString("$KEY_CONTACT_MOBILE$slot", null)
            val modeName  = prefs.getString("$KEY_CALL_MODE$slot", CallMode.PHONE.name)
            val mode = runCatching { CallMode.valueOf(modeName!!) }.getOrDefault(CallMode.PHONE)

            if (savedId != -1L && name != null && phone != null) {
                // Re-resolve the current CONTACT_ID from the stable lookup key.
                // Falls back to the originally saved ID if re-resolution fails
                // (e.g. contact was deleted, or running on a very old OS quirk).
                val liveId = lookupKey?.let { resolveCurrentContactId(it, savedId) } ?: savedId
                val photoUri = getContactPhotoUri(liveId)
                Contact(liveId, lookupKey ?: "", name, phone, mobile, photoUri, mode)
            } else null
        }
    }

    /**
     * Query all device contacts that have a phone number.
     * For each contact we keep one "primary" number for regular calls
     * (preferring mobile, falling back to whatever's first) and a separate
     * [Contact.mobilePhone] that is ONLY populated when a genuine
     * TYPE_MOBILE number exists — this is what gates the WhatsApp button.
     */
    fun getAllContacts(): List<Contact> {
        // contactId -> list of (number, type)
        val numbersByContact = LinkedHashMap<Long, MutableList<Pair<String, Int>>>()
        val namesByContact = HashMap<Long, String>()
        val lookupKeysByContact = HashMap<Long, String>()

        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.TYPE
        )
        val cursor = context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            projection,
            null,
            null,
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
        )
        cursor?.use {
            val idIdx     = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
            val lookupIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY)
            val nameIdx   = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val phoneIdx  = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            val typeIdx   = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.TYPE)
            while (it.moveToNext()) {
                val id = it.getLong(idIdx)
                val name = it.getString(nameIdx) ?: continue
                val number = it.getString(phoneIdx) ?: continue
                val type = if (typeIdx >= 0) it.getInt(typeIdx) else -1
                val lookupKey = if (lookupIdx >= 0) it.getString(lookupIdx) else null
                namesByContact.putIfAbsent(id, name)
                if (lookupKey != null) lookupKeysByContact.putIfAbsent(id, lookupKey)
                numbersByContact.getOrPut(id) { mutableListOf() }.add(number to type)
            }
        }

        val contacts = mutableListOf<Contact>()
        for ((id, numbers) in numbersByContact) {
            val name = namesByContact[id] ?: continue
            val lookupKey = lookupKeysByContact[id] ?: continue

            val mobileNumber = numbers.firstOrNull {
                it.second == ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE
            }?.first

            // Primary number for regular phone calls: prefer mobile, else first available.
            val primaryNumber = mobileNumber ?: numbers.first().first

            val photoUri = getContactPhotoUri(id)
            contacts.add(Contact(id, lookupKey, name, primaryNumber, mobileNumber, photoUri))
        }
        return contacts
    }

    /**
     * Looks up WhatsApp's own synced "video call" Data row for this contact,
     * returning the Intent action URI ("content://com.android.contacts/data/<id>")
     * that WhatsApp registers when it syncs contacts and the person is on
     * WhatsApp. Returns null if WhatsApp isn't synced for this contact
     * (e.g. WhatsApp doesn't have contacts permission, or the person isn't
     * on WhatsApp), in which case callers should fall back to opening a chat.
     */
    fun getWhatsAppVideoCallDataId(contactId: Long): Long? {
        val projection = arrayOf(ContactsContract.Data._ID, ContactsContract.Data.MIMETYPE)
        val selection = "${ContactsContract.Data.CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?"
        val selectionArgs = arrayOf(contactId.toString(), WHATSAPP_VIDEO_CALL_MIME)
        context.contentResolver.query(
            ContactsContract.Data.CONTENT_URI, projection, selection, selectionArgs, null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idIdx = cursor.getColumnIndex(ContactsContract.Data._ID)
                return cursor.getLong(idIdx)
            }
        }
        return null
    }

    /**
     * Resolves the current, live CONTACT_ID for a previously-saved
     * LOOKUP_KEY. This is the documented mechanism for surviving Android's
     * contact re-aggregation (e.g. triggered when WhatsApp or another app
     * syncs additional raw contact data for the same person, which can
     * cause the Contacts Provider to merge/re-link records under a new
     * CONTACT_ID). Falls back to [fallbackId] if the lookup key no longer
     * resolves to anything (e.g. the contact was deleted).
     */
    private fun resolveCurrentContactId(lookupKey: String, fallbackId: Long): Long {
        return try {
            val lookupUri = Uri.withAppendedPath(
                ContactsContract.Contacts.CONTENT_LOOKUP_URI,
                Uri.encode(lookupKey)
            )
            val resolvedUri = ContactsContract.Contacts.lookupContact(context.contentResolver, lookupUri)
            if (resolvedUri != null) {
                ContentUris.parseId(resolvedUri)
            } else fallbackId
        } catch (e: Exception) {
            fallbackId
        }
    }

    private fun getContactPhotoUri(contactId: Long): Uri? {
        val uri = ContentUris.withAppendedId(
            ContactsContract.Contacts.CONTENT_URI, contactId
        )
        return try {
            val photoStream = ContactsContract.Contacts.openContactPhotoInputStream(
                context.contentResolver, uri
            )
            photoStream?.close()
            if (photoStream != null)
                Uri.withAppendedPath(uri, ContactsContract.Contacts.Photo.CONTENT_DIRECTORY)
            else null
        } catch (e: Exception) { null }
    }
}
