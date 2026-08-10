package com.example.notesketch

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebResourceError
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.notesketch.databinding.ActivityAiTutorBinding
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AiTutorActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAiTutorBinding
    private val chatAdapter = AiChatAdapter(
        onSaveNote = { content -> openNoteWithContent(content) },
        onCopy = { content -> copyToClipboard(content) }
    )
    private val chatMessages = mutableListOf<ChatMessage>()
    private var settingsExpanded = false
    private var webTabActive = false
    /** 最近一次成功加载的 URL，用于 Tab 切回时避免误重载。 */
    private var lastLoadedWebUrl: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAiTutorBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupImeHandling()

        binding.btnBackRow.setOnClickListener { finish() }
        binding.btnBack.setOnClickListener { finish() }

        binding.tabMode.addTab(binding.tabMode.newTab().setText("API 对话"))
        binding.tabMode.addTab(binding.tabMode.newTab().setText("网页访问"))
        binding.tabMode.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                showTab(tab.position)
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        binding.btnToggleSettings.setOnClickListener {
            settingsExpanded = !settingsExpanded
            binding.settingsPanel.visibility = if (settingsExpanded) View.VISIBLE else View.GONE
            binding.btnToggleSettings.text = if (settingsExpanded) "收起 API 设置 ▲" else "展开 API 设置 ▼"
        }

        binding.recyclerChat.layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true
        }
        binding.recyclerChat.adapter = chatAdapter

        binding.btnSend.setOnClickListener { sendMessage() }
        binding.btnSaveSettings.setOnClickListener { saveSettings() }
        binding.btnTestApi.setOnClickListener { testApiConnection() }
        binding.btnPasteToNote.setOnClickListener { pasteClipboardToNote() }
        binding.btnWebBack.setOnClickListener { if (binding.webView.canGoBack()) binding.webView.goBack() }
        binding.btnWebForward.setOnClickListener { if (binding.webView.canGoForward()) binding.webView.goForward() }
        binding.btnWebRefresh.setOnClickListener { binding.webView.reload() }
        binding.btnWebGo.setOnClickListener { loadWebUrl() }
        binding.etWebUrl.setOnEditorActionListener { _, actionId, event ->
            val enter = event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN
            if (actionId == EditorInfo.IME_ACTION_GO || enter) {
                loadWebUrl()
                true
            } else {
                false
            }
        }
        setupWebView()
        loadSettings()
        if (savedInstanceState != null) {
            binding.webView.restoreState(savedInstanceState)
        }
        showTab(0)
        applyChrome()

        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.panelWeb.visibility == View.VISIBLE && binding.webView.canGoBack()) {
                    binding.webView.goBack()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    override fun onResume() {
        super.onResume()
        if (webTabActive) binding.webView.onResume()
        applyChrome()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        binding.webView.saveState(outState)
        super.onSaveInstanceState(outState)
    }

    override fun onPause() {
        binding.webView.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        binding.webView.apply {
            stopLoading()
            (parent as? ViewGroup)?.removeView(this)
            destroy()
        }
        super.onDestroy()
    }

    private fun setupImeHandling() {
        WindowCompat.setDecorFitsSystemWindows(window, true)
        val baseCardPaddingBottom = binding.contentCard.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(binding.contentCard) { v, insets ->
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            v.updatePadding(bottom = baseCardPaddingBottom + ime.bottom)
            if (webTabActive) {
                val urlFocused = binding.etWebUrl.hasFocus()
                val hideWeb = ime.bottom > 0 && urlFocused
                binding.webView.visibility = if (hideWeb) View.GONE else View.VISIBLE
                if (hideWeb) binding.webProgress.visibility = View.GONE
                if (ime.bottom > 0 && urlFocused) {
                    binding.webUrlScroll.post {
                        binding.webUrlScroll.smoothScrollTo(0, 0)
                        binding.etWebUrl.requestRectangleOnScreen(
                            Rect(0, 0, binding.etWebUrl.width, binding.etWebUrl.height),
                            true
                        )
                    }
                }
            }
            insets
        }
        ViewCompat.requestApplyInsets(binding.contentCard)
        binding.etWebUrl.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus && webTabActive) {
                binding.webView.visibility = View.VISIBLE
            }
        }
    }

    /** 加载前确保 WebView 可见且地址栏失焦，避免在 GONE 状态下加载导致白屏。 */
    private fun prepareWebViewForLoad() {
        binding.etWebUrl.clearFocus()
        binding.root.requestFocus()
        hideKeyboard(binding.etWebUrl)
        binding.webView.visibility = View.VISIBLE
    }

    private fun showTab(index: Int) {
        webTabActive = index == 1
        binding.panelApi.visibility = if (index == 0) View.VISIBLE else View.GONE
        binding.panelWeb.visibility = if (index == 1) View.VISIBLE else View.GONE
        if (index == 1) {
            binding.webView.visibility = View.VISIBLE
            binding.webView.onResume()
            binding.panelWeb.post { maybeAutoLoadWeb() }
        } else {
            binding.webView.onPause()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(binding.webView, true)

        binding.webView.setBackgroundColor(Color.WHITE)
        binding.webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            builtInZoomControls = true
            displayZoomControls = false
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            javaScriptCanOpenWindowsAutomatically = true
            setSupportMultipleWindows(true)
            loadsImagesAutomatically = true
            cacheMode = WebSettings.LOAD_DEFAULT
            mediaPlaybackRequiresUserGesture = false
            userAgentString = desktopUserAgent()
        }
        binding.webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                return handleInAppLink(view, request.url?.toString().orEmpty())
            }

            @Deprecated("Deprecated in Java")
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                return handleInAppLink(view, url)
            }

            override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
                updateWebProgress(5)
            }

            override fun onPageFinished(view: WebView, url: String?) {
                updateWebProgress(100)
                val current = url?.trim().orEmpty()
                if (current.isNotBlank() && current != "about:blank") {
                    lastLoadedWebUrl = current
                    binding.etWebUrl.setText(current)
                    AiTutorPrefs.setWebUrl(this@AiTutorActivity, current)
                }
                updateWebNavButtons()
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError
            ) {
                if (request.isForMainFrame) {
                    updateWebProgress(100)
                    Toast.makeText(
                        this@AiTutorActivity,
                        "页面加载失败，请检查网址或网络",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
        binding.webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                updateWebProgress(newProgress)
            }

            override fun onCreateWindow(
                view: WebView,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: android.os.Message
            ): Boolean {
                val href = view.hitTestResult.extra
                if (!href.isNullOrBlank()) {
                    view.loadUrl(normalizeWebUrl(href))
                    return false
                }
                val transport = resultMsg.obj as WebView.WebViewTransport
                transport.webView = view
                resultMsg.sendToTarget()
                return true
            }
        }
        binding.webView.setDownloadListener { _, _, _, _, _ ->
            Toast.makeText(this, "下载请在网页内长按链接操作", Toast.LENGTH_SHORT).show()
        }
        binding.webView.loadUrl("about:blank")
    }

    private fun desktopUserAgent(): String {
        return "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"
    }

    private fun updateWebProgress(progress: Int) {
        binding.webProgress.progress = progress
        binding.webProgress.visibility =
            if (progress in 1..99) View.VISIBLE else View.GONE
    }

    private fun updateWebNavButtons() {
        binding.btnWebBack.isEnabled = binding.webView.canGoBack()
        binding.btnWebForward.isEnabled = binding.webView.canGoForward()
    }

    /** 所有网页链接尽量留在应用内 WebView 打开，不跳转系统浏览器。 */
    private fun handleInAppLink(view: WebView, url: String): Boolean {
        if (url.isBlank()) return true
        val uri = Uri.parse(url)
        when (uri.scheme?.lowercase()) {
            "http", "https", "about" -> return false
            "intent" -> {
                try {
                    val intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
                    val fallback = intent.getStringExtra("browser_fallback_url")
                    if (!fallback.isNullOrBlank()) {
                        view.loadUrl(fallback)
                        return true
                    }
                    if (intent.resolveActivity(packageManager) != null) {
                        startActivity(intent)
                    } else {
                        Toast.makeText(this, "该操作需要在应用内网页完成", Toast.LENGTH_SHORT).show()
                    }
                } catch (_: Exception) {
                    Toast.makeText(this, "无法打开链接", Toast.LENGTH_SHORT).show()
                }
                return true
            }
            else -> {
                Toast.makeText(this, "该链接暂不支持，请尝试在网页内操作", Toast.LENGTH_SHORT).show()
                return true
            }
        }
    }

    private fun maybeAutoLoadWeb() {
        val target = normalizeWebUrl(binding.etWebUrl.text?.toString().orEmpty())
        val current = binding.webView.url?.trim().orEmpty()
        val effectiveCurrent = current.ifBlank { lastLoadedWebUrl.orEmpty() }
        val needsLoad = target.isNotBlank() &&
            (effectiveCurrent.isBlank() || effectiveCurrent == "about:blank" || !urlsEquivalent(effectiveCurrent, target))
        if (target.isBlank()) return
        if (needsLoad) {
            loadWebUrl()
        } else {
            prepareWebViewForLoad()
        }
    }

    private fun normalizeWebUrl(raw: String): String {
        var url = raw.trim()
        if (url.isBlank()) return ""
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "https://$url"
        }
        return url
    }

    private fun urlsEquivalent(a: String, b: String): Boolean {
        return normalizeWebUrl(a).trimEnd('/') == normalizeWebUrl(b).trimEnd('/')
    }

    private fun loadSettings() {
        binding.etBaseUrl.setText(AiTutorPrefs.apiBaseUrl(this))
        binding.etApiKey.setText(AiTutorPrefs.apiKey(this))
        binding.etModel.setText(AiTutorPrefs.apiModel(this))
        binding.etSystemPrompt.setText(AiTutorPrefs.systemPrompt(this))
        binding.etWebUrl.setText(AiTutorPrefs.webUrl(this))
    }

    private fun saveSettings() {
        AiTutorPrefs.setApiBaseUrl(this, binding.etBaseUrl.text?.toString().orEmpty())
        AiTutorPrefs.setApiKey(this, binding.etApiKey.text?.toString().orEmpty())
        AiTutorPrefs.setApiModel(this, binding.etModel.text?.toString().orEmpty())
        AiTutorPrefs.setSystemPrompt(this, binding.etSystemPrompt.text?.toString().orEmpty())
        AiTutorPrefs.setWebUrl(this, binding.etWebUrl.text?.toString().orEmpty())
        Toast.makeText(this, "已保存", Toast.LENGTH_SHORT).show()
    }

    private fun testApiConnection() {
        saveSettings()
        if (!AiTutorPrefs.isApiConfigured(this)) {
            Toast.makeText(this, "请先填写 Base URL、API Key 和 Model", Toast.LENGTH_SHORT).show()
            return
        }
        binding.btnTestApi.isEnabled = false
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                AiApiClient.chat(
                    AiTutorPrefs.apiBaseUrl(this@AiTutorActivity),
                    AiTutorPrefs.apiKey(this@AiTutorActivity),
                    AiTutorPrefs.apiModel(this@AiTutorActivity),
                    listOf(ChatMessage("user", "回复 OK 两个字母即可"))
                )
            }
            binding.btnTestApi.isEnabled = true
            when (result) {
                is AiApiResult.Success -> Toast.makeText(this@AiTutorActivity, "连接成功", Toast.LENGTH_SHORT).show()
                is AiApiResult.Error -> Toast.makeText(this@AiTutorActivity, result.message, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun sendMessage() {
        val input = binding.etInput.text?.toString()?.trim().orEmpty()
        if (input.isBlank()) return
        saveSettings()
        if (!AiTutorPrefs.isApiConfigured(this)) {
            Toast.makeText(this, "请先在 API 设置中填写配置", Toast.LENGTH_SHORT).show()
            settingsExpanded = true
            binding.settingsPanel.visibility = View.VISIBLE
            binding.btnToggleSettings.text = "收起 API 设置 ▲"
            return
        }

        hideKeyboard(binding.etInput)
        binding.etInput.setText("")
        appendMessage(ChatMessage("user", input))
        binding.btnSend.isEnabled = false
        appendMessage(ChatMessage("assistant", "思考中…"))

        lifecycleScope.launch {
            val apiMessages = buildApiMessages()
            val result = withContext(Dispatchers.IO) {
                AiApiClient.chat(
                    AiTutorPrefs.apiBaseUrl(this@AiTutorActivity),
                    AiTutorPrefs.apiKey(this@AiTutorActivity),
                    AiTutorPrefs.apiModel(this@AiTutorActivity),
                    apiMessages
                )
            }
            if (chatMessages.lastOrNull()?.content == "思考中…") {
                chatMessages.removeAt(chatMessages.lastIndex)
            }
            when (result) {
                is AiApiResult.Success -> appendMessage(ChatMessage("assistant", result.content))
                is AiApiResult.Error -> appendMessage(ChatMessage("assistant", "⚠ ${result.message}"))
            }
            binding.btnSend.isEnabled = true
            binding.recyclerChat.scrollToPosition(chatAdapter.itemCount - 1)
        }
    }

    private fun buildApiMessages(): List<ChatMessage> {
        val list = mutableListOf<ChatMessage>()
        val system = AiTutorPrefs.systemPrompt(this)
        if (system.isNotBlank()) list += ChatMessage("system", system)
        list += chatMessages.filter { it.content != "思考中…" }
        return list
    }

    private fun appendMessage(msg: ChatMessage) {
        if (msg.role == "assistant" && chatMessages.lastOrNull()?.content == "思考中…") {
            chatMessages.removeAt(chatMessages.lastIndex)
        }
        chatMessages.add(msg)
        chatAdapter.submit(chatMessages.toList())
        binding.recyclerChat.scrollToPosition(chatAdapter.itemCount - 1)
    }

    private fun loadWebUrl() {
        val url = normalizeWebUrl(binding.etWebUrl.text?.toString().orEmpty())
        if (url.isBlank()) {
            Toast.makeText(this, "请填写 AI 网址", Toast.LENGTH_SHORT).show()
            return
        }
        binding.etWebUrl.setText(url)
        AiTutorPrefs.setWebUrl(this, url)
        prepareWebViewForLoad()
        binding.webView.loadUrl(url)
    }

    private fun pasteClipboardToNote() {
        val clip = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val text = clip.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString()?.trim().orEmpty()
        if (text.isBlank()) {
            Toast.makeText(this, "剪贴板为空，请先在网页中复制 AI 回答", Toast.LENGTH_LONG).show()
            return
        }
        openNoteWithContent(text)
    }

    private fun openNoteWithContent(content: String) {
        startActivity(AddNoteActivity.prefillIntent(this, content, title = "AI 笔记"))
    }

    private fun copyToClipboard(text: String) {
        val clip = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clip.setPrimaryClip(ClipData.newPlainText("ai_reply", text))
        Toast.makeText(this, "已复制", Toast.LENGTH_SHORT).show()
    }

    private fun hideKeyboard(view: View = binding.etInput) {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }

    private fun applyChrome() {
        val theme = UiPrefs.theme(this)
        val panelBg = Color.parseColor("#FFFEF8")
        val panelInk = ThemeUi.contrastText(panelBg)
        val panelMuted = ThemeUi.contrastMuted(panelBg)

        ThemeUi.applyScrapbook(this, binding.paperBg)
        binding.root.setBackgroundColor(theme.bg)
        ThemeUi.colorTexts(theme.ink, binding.tvHeader)
        ThemeUi.colorTexts(theme.muted, binding.btnBack)
        ThemeUi.colorLines(0x597A6F62, binding.headerLine)

        ThemeUi.colorTexts(panelInk, binding.btnToggleSettings)
        listOf(
            binding.etBaseUrl,
            binding.etApiKey,
            binding.etModel,
            binding.etSystemPrompt,
            binding.etInput,
            binding.etWebUrl
        ).forEach { ThemeUi.styleLightPanelEdit(it, panelBg) }
        listOf(
            binding.btnSaveSettings,
            binding.btnTestApi,
            binding.btnSend,
            binding.btnWebGo,
            binding.btnWebBack,
            binding.btnWebForward,
            binding.btnWebRefresh,
            binding.btnPasteToNote
        ).forEach {
            ThemeUi.styleLightPanelButton(it, panelBg)
            it.setTextColor(panelInk)
        }
        binding.tabMode.setTabTextColors(panelMuted, panelInk)
        binding.tabMode.setSelectedTabIndicatorColor(theme.accent)
    }
}

private class AiChatAdapter(
    private val onSaveNote: (String) -> Unit,
    private val onCopy: (String) -> Unit
) : RecyclerView.Adapter<AiChatAdapter.Holder>() {

    private val items = mutableListOf<ChatMessage>()

    fun submit(list: List<ChatMessage>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): Holder {
        val ctx = parent.context
        val d = ctx.resources.displayMetrics.density
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = RecyclerView.LayoutParams(
                RecyclerView.LayoutParams.MATCH_PARENT,
                RecyclerView.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = (10 * d).toInt()
            }
            setPadding((12 * d).toInt(), (10 * d).toInt(), (12 * d).toInt(), (10 * d).toInt())
        }
        return Holder(root, d)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(items[position], onSaveNote, onCopy)
    }

    override fun getItemCount(): Int = items.size

    class Holder(
        private val root: LinearLayout,
        private val d: Float
    ) : RecyclerView.ViewHolder(root) {

        private val label = TextView(root.context)
        private val body = TextView(root.context)
        private val actions = LinearLayout(root.context)

        init {
            root.addView(label)
            root.addView(body)
            root.addView(actions)
            body.textSize = 15f
            label.textSize = 12f
            actions.orientation = LinearLayout.HORIZONTAL
            actions.gravity = Gravity.END
        }

        fun bind(msg: ChatMessage, onSaveNote: (String) -> Unit, onCopy: (String) -> Unit) {
            val isUser = msg.role == "user"
            val bubbleBg = if (isUser) Color.parseColor("#E8F0EA") else Color.parseColor("#FFFEF8")
            label.text = when (msg.role) {
                "user" -> "我"
                "assistant" -> "AI 导师"
                else -> msg.role
            }
            body.text = msg.content
            label.setTextColor(ThemeUi.contrastMuted(bubbleBg))
            body.setTextColor(
                if (msg.content.startsWith("⚠")) Color.parseColor("#9A4A32")
                else ThemeUi.contrastText(bubbleBg)
            )
            root.background = GradientDrawable().apply {
                cornerRadius = 10 * d
                setColor(bubbleBg)
                setStroke((1 * d).toInt().coerceAtLeast(1), Color.parseColor("#337A6F62"))
            }
            actions.removeAllViews()
            if (msg.role == "assistant" && !msg.content.startsWith("⚠") && msg.content != "思考中…") {
                actions.addView(actionBtn("存为便签", bubbleBg) { onSaveNote(msg.content) })
                actions.addView(actionBtn("复制", bubbleBg) { onCopy(msg.content) })
            }
        }

        private fun actionBtn(text: String, bubbleBg: Int, onClick: () -> Unit): TextView {
            return TextView(root.context).apply {
                this.text = text
                textSize = 13f
                setTextColor(ThemeUi.contrastText(bubbleBg))
                setPadding((10 * d).toInt(), (4 * d).toInt(), (10 * d).toInt(), (4 * d).toInt())
                setOnClickListener { onClick() }
            }
        }
    }
}
