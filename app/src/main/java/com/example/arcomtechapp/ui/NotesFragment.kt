package com.example.arcomtechapp.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.arcomtechapp.databinding.FragmentNotesBinding
import com.example.arcomtechapp.storage.Storage

class NotesFragment : Fragment() {

    private var _binding: FragmentNotesBinding? = null
    private val binding get() = _binding!!
    private lateinit var storage: Storage

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentNotesBinding.inflate(inflater, container, false)
        storage = Storage(requireContext())
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.textNotesMeta.text = buildMeta()
        binding.inputNote.setText(storage.getNotesDraft().orEmpty())
        updateDraftStatus()

        binding.buttonSaveDraft.setOnClickListener {
            storage.setNotesDraft(binding.inputNote.text?.toString().orEmpty())
            updateDraftStatus()
            Toast.makeText(requireContext(), "Draft saved locally", Toast.LENGTH_SHORT).show()
        }

        binding.buttonClearDraft.setOnClickListener {
            storage.clearNotesDraft()
            binding.inputNote.setText("")
            updateDraftStatus()
        }

        binding.buttonSendNote.setOnClickListener {
            val note = binding.inputNote.text?.toString().orEmpty()
            if (note.isBlank()) {
                Toast.makeText(requireContext(), "Nothing to send yet", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), "Would send note via BlueFolder (stub)", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun buildMeta(): String {
        val parts = mutableListOf<String>()
        parts += storage.getTechnicianName()?.ifBlank { "Technician" } ?: "Technician"
        storage.getTechnicianId()?.let { if (it.isNotBlank()) parts += "ID: $it" }
        parts += storage.getBaseUrl()?.ifBlank { "No subdomain" } ?: "No subdomain"
        parts += if (storage.getApiKey().isNullOrBlank()) "API key missing" else "API ready"
        return parts.joinToString(" • ")
    }

    private fun updateDraftStatus() {
        val draft = storage.getNotesDraft()
        binding.textDraftStatus.text = if (draft.isNullOrBlank()) {
            "No draft saved"
        } else {
            "Draft saved (${draft.length} chars)"
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
