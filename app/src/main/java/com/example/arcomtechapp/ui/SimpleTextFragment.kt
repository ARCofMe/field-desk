package com.example.arcomtechapp.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.example.arcomtechapp.R

class SimpleTextFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val root = inflater.inflate(R.layout.fragment_simple_text, container, false)
        val titleView: TextView = root.findViewById(R.id.text_title)
        titleView.text = arguments?.getString(ARG_TITLE) ?: "Screen"
        return root
    }

    companion object {
        private const val ARG_TITLE = "arg_title"

        fun newInstance(title: String): SimpleTextFragment {
            val fragment = SimpleTextFragment()
            fragment.arguments = Bundle().apply {
                putString(ARG_TITLE, title)
            }
            return fragment
        }
    }
}
