package com.example.arcomtechapp.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebResourceError
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.fragment.app.Fragment
import com.example.arcomtechapp.databinding.FragmentWebFielddeskBinding
import com.example.arcomtechapp.storage.Storage

class WebFieldDeskFragment : Fragment() {

    private var _binding: FragmentWebFielddeskBinding? = null
    private val binding get() = _binding!!
    private lateinit var storage: Storage
    private lateinit var bridge: WebFieldDeskBridge
    private var allowedHost: String? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentWebFielddeskBinding.inflate(inflater, container, false)
        storage = Storage(requireContext())
        bridge = WebFieldDeskBridge(requireContext().applicationContext, storage)
        setupStaticState()
        configureWebView(binding.webFielddesk)
        return binding.root
    }

    override fun onDestroyView() {
        binding.webFielddesk.destroy()
        _binding = null
        super.onDestroyView()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView(webView: WebView) {
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.allowFileAccess = false
        webView.settings.allowContentAccess = false
        webView.addJavascriptInterface(bridge, "FieldDeskNativeBridge")
        webView.webChromeClient = WebChromeClient()
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val target = request?.url ?: return false
                if (target.scheme !in setOf("http", "https")) return true
                if (allowedHost != null && target.host != allowedHost) {
                    openExternalBrowser(target)
                    return true
                }
                return false
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                binding.progressWebFielddesk.visibility = View.GONE
                binding.textWebFielddeskState.visibility = View.GONE
            }

            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                super.onReceivedError(view, request, error)
                if (request?.isForMainFrame != true) return
                binding.progressWebFielddesk.visibility = View.GONE
                binding.textWebFielddeskState.visibility = View.VISIBLE
                binding.textWebFielddeskState.text = error?.description ?: "FieldDesk web host failed to load."
            }
        }
        val targetUrl = storage.getFieldDeskWebUrl()?.trim().orEmpty()
        if (targetUrl.isBlank()) {
            binding.progressWebFielddesk.visibility = View.GONE
            binding.textWebFielddeskState.visibility = View.VISIBLE
            binding.textWebFielddeskState.text = "Set a FieldDesk web URL in Settings to use the wrapper host."
            return
        }
        val parsed = runCatching { Uri.parse(targetUrl) }.getOrNull()
        if (parsed?.scheme !in setOf("http", "https") || parsed?.host.isNullOrBlank()) {
            binding.progressWebFielddesk.visibility = View.GONE
            binding.textWebFielddeskState.visibility = View.VISIBLE
            binding.textWebFielddeskState.text = "FieldDesk web URL must use http or https."
            return
        }
        allowedHost = parsed?.host
        binding.buttonOpenWebFielddesk.setOnClickListener { webView.reload() }
        webView.loadUrl(targetUrl)
    }

    private fun setupStaticState() {
        val snapshot = storage.getSnapshot()
        binding.textWebFielddeskSummary.text =
            "Android is hosting the FieldDesk web client. Device-native hooks are exposed through the bridge for camera, offline queue, push, and external navigation."
        binding.textWebFielddeskConfig.text =
            "Web URL: ${snapshot.fieldDeskWebUrl ?: "missing"}\nSubject: ${snapshot.technicianId ?: "missing"}\nOps Hub: ${snapshot.opsHubBaseUrl ?: "missing"}\nOffline queued: ${storage.getOfflineActions().size}"
    }

    private fun openExternalBrowser(uri: Uri) {
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        }
    }
}
