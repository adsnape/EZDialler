package com.quickdial.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.quickdial.app.databinding.ActivitySetupBinding

class SetupActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySetupBinding
    private lateinit var repository: ContactRepository
    private lateinit var adapter: ContactPickerAdapter

    private var targetSlot: Int = -1
    private var allContacts: List<Contact> = emptyList()

    companion object {
        const val EXTRA_SLOT = "extra_slot"
    }

    private val contactPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) loadContacts()
        else { Toast.makeText(this, getString(R.string.contacts_permission_denied), Toast.LENGTH_LONG).show(); finish() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySetupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = ContactRepository(this)
        targetSlot = intent.getIntExtra(EXTRA_SLOT, -1)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = if (targetSlot >= 0)
            getString(R.string.pick_contact_for_slot, targetSlot + 1)
        else getString(R.string.setup_title)

        setupRecyclerView()
        setupSearch()
        ensurePermission()
    }

    private fun setupRecyclerView() {
        adapter = ContactPickerAdapter { contact -> onContactSelected(contact) }
        binding.rvContacts.layoutManager = LinearLayoutManager(this)
        binding.rvContacts.adapter = adapter
    }

    private fun setupSearch() {
        binding.searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?) = false
            override fun onQueryTextChange(newText: String?): Boolean {
                filterContacts(newText.orEmpty()); return true
            }
        })
    }

    private fun ensurePermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS)
            == PackageManager.PERMISSION_GRANTED) loadContacts()
        else contactPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
    }

    private fun loadContacts() {
        allContacts = repository.getAllContacts()
        adapter.submitList(allContacts)
    }

    private fun filterContacts(query: String) {
        adapter.submitList(if (query.isBlank()) allContacts
        else allContacts.filter {
            it.name.contains(query, ignoreCase = true) || it.phone.contains(query, ignoreCase = true)
        })
    }

    /** Selecting a contact saves it straight away — both Phone and WhatsApp
     *  buttons are shown automatically on the home screen for any contact
     *  with a number, so no separate "mode" choice is needed here. */
    private fun onContactSelected(contact: Contact) {
        val slot = if (targetSlot >= 0) targetSlot else findNextEmptySlot()
        repository.saveContact(slot, contact)
        Toast.makeText(this,
            getString(R.string.contact_saved, contact.name, slot + 1),
            Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun findNextEmptySlot(): Int {
        val saved = repository.loadSavedContacts()
        return saved.indexOfFirst { it == null }.takeIf { it >= 0 } ?: 0
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }
}
