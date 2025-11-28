package com.example.arcomtechapp.ui

import android.graphics.Bitmap
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import com.example.arcomtechapp.databinding.FragmentPhotosBinding
import com.example.arcomtechapp.storage.Storage

class PhotosFragment : Fragment() {

    private var _binding: FragmentPhotosBinding? = null
    private val binding get() = _binding!!
    private lateinit var storage: Storage

    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        bitmap?.let {
            binding.imagePreview.setImageBitmap(it)
            updateStatus("Captured preview from camera")
        } ?: updateStatus("Camera canceled")
    }

    private val galleryLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            binding.imagePreview.setImageURI(uri)
            updateStatus("Selected image: $uri")
        } else {
            updateStatus("Gallery canceled")
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentPhotosBinding.inflate(inflater, container, false)
        storage = Storage(requireContext())
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.switchInlineCompress.isChecked = storage.shouldAutoCompressPhotos()
        binding.switchInlineCompress.setOnCheckedChangeListener { _, isChecked ->
            storage.setAutoCompressPhotos(isChecked)
            updateStatus(if (isChecked) "Auto-compress enabled" else "Auto-compress disabled")
        }

        binding.buttonCamera.setOnClickListener { cameraLauncher.launch(null) }
        binding.buttonGallery.setOnClickListener { galleryLauncher.launch("image/*") }
        binding.buttonOptimize.setOnClickListener {
            // Stub: this is where Chaquopy compression/optimization would run.
            updateStatus("Optimization queued (stub)")
        }
        binding.buttonEmailUpload.setOnClickListener {
            Toast.makeText(requireContext(), "Email upload flow not wired yet", Toast.LENGTH_SHORT).show()
        }

        binding.textPhotoConfig.text = buildConfigText()
        updateStatus("Ready for photo intake")
    }

    private fun buildConfigText(): String {
        val parts = mutableListOf<String>()
        parts += storage.getBaseUrl()?.ifBlank { "No base URL" } ?: "No base URL"
        parts += if (storage.getApiKey().isNullOrBlank()) "API key missing" else "API key set"
        parts += if (storage.shouldAutoCompressPhotos()) "Auto-compress ON" else "Auto-compress OFF"
        return parts.joinToString(" • ")
    }

    private fun updateStatus(status: String) {
        binding.textPhotoStatus.isVisible = true
        binding.textPhotoStatus.text = status
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
