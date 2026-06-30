package com.quickdial.app

import android.net.Uri

enum class CallMode { PHONE, WHATSAPP }

data class Contact(
    val id: Long,
    val name: String,
    val phone: String,
    val mobilePhone: String? = null,
    val photoUri: Uri? = null,
    val callMode: CallMode = CallMode.PHONE
) {
    val initials: String
        get() {
            val parts = name.trim().split(" ").filter { it.isNotEmpty() }
            return when {
                parts.size >= 2 -> "${parts.first().first()}${parts.last().first()}".uppercase()
                parts.size == 1 -> parts.first().take(2).uppercase()
                else -> "?"
            }
        }

    /** True only when this contact has a genuine mobile number — WhatsApp
     *  should never be offered for landline / work / other number types. */
    val hasMobileNumber: Boolean
        get() = !mobilePhone.isNullOrBlank()
}
