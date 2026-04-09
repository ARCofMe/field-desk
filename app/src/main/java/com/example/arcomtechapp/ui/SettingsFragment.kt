package com.example.arcomtechapp.ui

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.example.arcomtechapp.R
import com.example.arcomtechapp.databinding.FragmentSettingsBinding
import com.example.arcomtechapp.storage.Storage
import android.widget.Toast

class SettingsFragment : Fragment() {

    private lateinit var binding: FragmentSettingsBinding
    private lateinit var storage: Storage

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentSettingsBinding.inflate(inflater, container, false)
        storage = Storage(requireContext())
        bindData()
        binding.buttonSaveRoutePrefs.setOnClickListener { saveRoutePrefs() }
        return binding.root
    }

    private fun bindData() {
        binding.inputRouteOrigin.setText(storage.getRouteOrigin().orEmpty())
        binding.inputRouteDestination.setText(storage.getRouteDestination().orEmpty())
        binding.inputRouteDeskUrl.setText(storage.getRouteDeskUrl().orEmpty())
        binding.inputPartsDeskUrl.setText(storage.getPartsDeskUrl().orEmpty())
    }

    private fun saveRoutePrefs() {
        storage.saveRouteOrigin(binding.inputRouteOrigin.text?.toString())
        storage.saveRouteDestination(binding.inputRouteDestination.text?.toString())
        storage.saveRouteDeskUrl(binding.inputRouteDeskUrl.text?.toString())
        storage.savePartsDeskUrl(binding.inputPartsDeskUrl.text?.toString())
        Toast.makeText(requireContext(), getString(R.string.fielddesk_settings_saved), Toast.LENGTH_SHORT).show()
    }
}
