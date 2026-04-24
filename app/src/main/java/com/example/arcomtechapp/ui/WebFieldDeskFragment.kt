package com.example.arcomtechapp.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.example.arcomtechapp.databinding.FragmentWebFielddeskBinding
import com.example.arcomtechapp.storage.Storage
import android.util.Base64
import java.io.ByteArrayOutputStream

class WebFieldDeskFragment : Fragment() {

    private var _binding: FragmentWebFielddeskBinding? = null
    private val binding get() = _binding!!
    private lateinit var storage: Storage
    private lateinit var bridge: WebFieldDeskBridge
    private var allowedHost: String? = null
    private var pendingCaptureLabel: String = "job photo"
    private var pendingCaptureSrId: String = ""
    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        if (bitmap == null) {
            dispatchNativePhotoResult(success = false, message = "Camera capture canceled.")
            return@registerForActivityResult
        }
        storage.recordJobPhotoCapture(pendingCaptureSrId, pendingCaptureLabel)
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)
        dispatchNativePhotoResult(
            success = true,
            message = "Native camera capture complete.",
            dataBase64 = Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP),
            filename = buildPhotoFilename(pendingCaptureSrId, pendingCaptureLabel),
            contentType = "image/jpeg",
        )
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentWebFielddeskBinding.inflate(inflater, container, false)
        storage = Storage(requireContext())
        bridge = WebFieldDeskBridge(requireContext().applicationContext, storage) { label, srId ->
            binding.webFielddesk.post {
                pendingCaptureLabel = label
                pendingCaptureSrId = srId
                cameraLauncher.launch(null)
            }
        }
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

    private fun dispatchNativePhotoResult(
        success: Boolean,
        message: String,
        dataBase64: String? = null,
        filename: String? = null,
        contentType: String? = null,
    ) {
        val detail = org.json.JSONObject()
            .put("success", success)
            .put("srId", pendingCaptureSrId)
            .put("label", pendingCaptureLabel)
            .put("message", message)
        dataBase64?.let { detail.put("dataBase64", it) }
        filename?.let { detail.put("filename", it) }
        contentType?.let { detail.put("contentType", it) }
        binding.webFielddesk.evaluateJavascript(
            "window.dispatchEvent(new CustomEvent('fielddesk:native-photo', { detail: $detail }));",
            null
        )
    }

    private fun buildPhotoFilename(srId: String, label: String): String {
        val normalizedLabel = label.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').ifBlank { "photo" }
        return "sr-${srId.ifBlank { "local" }}-$normalizedLabel.jpg"
    }
}
