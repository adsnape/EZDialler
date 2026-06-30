package com.quickdial.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.quickdial.app.databinding.ActivityMainBinding
import com.quickdial.app.databinding.BottomSheetContactOptionsBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var repository: ContactRepository
    private var contacts: List<Contact?> = List(6) { null }

    // Pending action awaiting CALL_PHONE permission
    private var pendingCallPhone: String? = null

    private val callPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) pendingCallPhone?.let { dialNumber(it) }
        else Toast.makeText(this, getString(R.string.call_permission_denied), Toast.LENGTH_SHORT).show()
        pendingCallPhone = null
    }

    private val contactPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) refreshContacts()
        else Toast.makeText(this, getString(R.string.contacts_permission_denied), Toast.LENGTH_LONG).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = ContactRepository(this)
        binding.btnSetup.setOnClickListener {
            startActivity(Intent(this, SetupActivity::class.java))
        }
        setupContactRows()
    }

    override fun onResume() {
        super.onResume()
        ensureContactsPermission()
    }

    private fun ensureContactsPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS)
            == PackageManager.PERMISSION_GRANTED) refreshContacts()
        else contactPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
    }

    private fun refreshContacts() {
        contacts = repository.loadSavedContacts()
        bindContactRows()
    }

    private fun cards() = listOf(
        binding.contactCard0, binding.contactCard1, binding.contactCard2,
        binding.contactCard3, binding.contactCard4, binding.contactCard5
    )

    private fun setupContactRows() {
        cards().forEachIndexed { index, card ->
            // Tapping the row itself (not the action buttons) opens setup for
            // empty slots, or shows the options sheet for filled ones.
            card.root.setOnClickListener {
                val contact = contacts.getOrNull(index)
                if (contact == null) startSetupForSlot(index) else showContactOptions(index)
            }
            card.root.setOnLongClickListener { showContactOptions(index); true }

            // Phone and WhatsApp buttons are independently and immediately tappable.
            card.btnCallPhone.setOnClickListener {
                contacts.getOrNull(index)?.let { c -> initiateCall(c.phone) }
            }
            card.btnCallWhatsapp.setOnClickListener {
                contacts.getOrNull(index)?.let { c -> startWhatsAppVideoCall(c) }
            }
        }
    }

    private fun bindContactRows() {
        val whatsAppInstalled = isWhatsAppInstalled()
        cards().forEachIndexed { index, card ->
            val contact = contacts.getOrNull(index)
            if (contact != null) {
                card.tvName.text     = contact.name
                card.tvInitials.text = contact.initials
                card.tvInitials.visibility = View.VISIBLE
                card.tvAddLabel.visibility = View.GONE
                card.ivPhoto.visibility    = View.GONE

                if (contact.photoUri != null) {
                    card.ivPhoto.visibility    = View.VISIBLE
                    card.tvInitials.visibility = View.GONE
                    com.bumptech.glide.Glide.with(this)
                        .load(contact.photoUri)
                        .circleCrop()
                        .placeholder(android.R.color.transparent)
                        .into(card.ivPhoto)
                }

                // Phone button: shown (and tappable) when the contact has a number
                card.btnCallPhone.visibility =
                    if (contact.phone.isNotBlank()) View.VISIBLE else View.GONE

                // WhatsApp button: only for genuine mobile numbers, and only
                // when WhatsApp is installed on this device.
                card.btnCallWhatsapp.visibility =
                    if (whatsAppInstalled && contact.hasMobileNumber) View.VISIBLE else View.GONE

                card.root.alpha = 1f
            } else {
                card.tvName.text  = getString(R.string.empty_slot_name)
                card.tvInitials.text = "+"
                card.tvInitials.visibility  = View.VISIBLE
                card.tvAddLabel.visibility  = View.VISIBLE
                card.ivPhoto.visibility     = View.GONE
                card.btnCallPhone.visibility    = View.GONE
                card.btnCallWhatsapp.visibility = View.GONE
                card.root.alpha = 0.55f
            }
        }
    }

    /** Options sheet: change contact or remove it from the slot. */
    private fun showContactOptions(slot: Int) {
        val contact = contacts.getOrNull(slot) ?: run { startSetupForSlot(slot); return }

        val sheet = BottomSheetDialog(this)
        val sheetBinding = BottomSheetContactOptionsBinding.inflate(layoutInflater)
        sheet.setContentView(sheetBinding.root)

        sheetBinding.tvSheetName.text = contact.name

        sheetBinding.btnChangeContact.setOnClickListener {
            sheet.dismiss()
            startSetupForSlot(slot)
        }
        sheetBinding.btnRemoveContact.setOnClickListener {
            repository.clearContact(slot)
            refreshContacts()
            sheet.dismiss()
        }

        sheet.show()
    }

    private fun startSetupForSlot(slot: Int) {
        startActivity(Intent(this, SetupActivity::class.java).apply {
            putExtra(SetupActivity.EXTRA_SLOT, slot)
        })
    }

    // ── Calling ────────────────────────────────────────────────────────────

    private fun initiateCall(phone: String) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE)
            == PackageManager.PERMISSION_GRANTED) dialNumber(phone)
        else { pendingCallPhone = phone; callPermissionLauncher.launch(Manifest.permission.CALL_PHONE) }
    }

    private fun dialNumber(phone: String) {
        startActivity(Intent(Intent.ACTION_CALL, Uri.parse("tel:${Uri.encode(phone)}")))
    }

    /**
     * Attempts to start a WhatsApp video call directly. This relies on
     * WhatsApp's own synced "video call" action, which only exists in the
     * Contacts Provider if WhatsApp has contact-sync permission and the
     * person is on WhatsApp. When that shortcut isn't available we fall
     * back to opening the WhatsApp chat for the contact's mobile number,
     * since WhatsApp does not expose a public, reliable deep link to start
     * a video call directly by phone number.
     */
    private fun startWhatsAppVideoCall(contact: Contact) {
        val mobile = contact.mobilePhone
        if (mobile.isNullOrBlank()) {
            Toast.makeText(this, getString(R.string.whatsapp_needs_mobile), Toast.LENGTH_LONG).show()
            return
        }

        val dataId = repository.getWhatsAppVideoCallDataId(contact.id)
        if (dataId != null) {
            try {
                val callUri = Uri.withAppendedPath(ContactsContract.Data.CONTENT_URI, dataId.toString())
                val intent = Intent(Intent.ACTION_VIEW, callUri).apply {
                    setPackage("com.whatsapp")
                }
                startActivity(intent)
                return
            } catch (e: Exception) {
                // fall through to chat fallback below
            }
        }

        // Fallback: WhatsApp hasn't synced a call shortcut for this contact
        // (e.g. just added, or WhatsApp lacks contacts permission). Open the
        // chat instead so the user can still start the call themselves.
        openWhatsAppChat(mobile)
        Toast.makeText(this, getString(R.string.whatsapp_video_call_fallback), Toast.LENGTH_SHORT).show()
    }

    private fun openWhatsAppChat(phone: String) {
        // Strip all non-digit chars for the wa.me deep-link
        val digits = phone.replace(Regex("[^\\d+]"), "")
        val intent = Intent(Intent.ACTION_VIEW,
            Uri.parse("https://wa.me/$digits")).apply {
            setPackage("com.whatsapp")
        }
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.whatsapp_not_installed), Toast.LENGTH_LONG).show()
        }
    }

    private fun isWhatsAppInstalled(): Boolean = try {
        packageManager.getPackageInfo("com.whatsapp", 0)
        true
    } catch (e: PackageManager.NameNotFoundException) { false }
}
