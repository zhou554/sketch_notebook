package com.example.notesketch

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.webkit.WebChromeClient
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAiTutorBinding.inflate(layoutInflater)
        setContentView(binding.root)

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
        binding.btnWebOpenExternal.setOnClickListener { openWebExternal() }
        binding.btnWebGo.setOnClickListener { loadWebUrl() }

        setupWebView()
        loadSettings()
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
        applyChrome()
    }

    private fun showTab(index: Int) {
        binding.panelApi.visibility = if (index == 0) View.VISIBLE else View.GONE
        binding.panelWeb.visibility = if (index == 1) View.VISIBLE else View.GONE
        if (index == 1) {
            val url = binding.etWebUrl.text?.toString()?.trim().orEmpty()
            if (url.isNotBlank() && binding.webView.url.isNullOrBlank()) {
                loadWebUrl()
            }
        }
    }

    private fun setupWebView() {
        binding.webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            builtInZoomControls = true
            displayZoomControls = false
        }
        binding.webView.webViewClient = WebViewClient()
        binding.webView.webChromeClient = WebChromeClient()
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

        hideKeyboard()
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
        var url = binding.etWebUrl.text?.toString()?.trim().orEmpty()
        if (url.isBlank()) {
            Toast.makeText(this, "请填写 AI 网址", Toast.LENGTH_SHORT).show()
            return
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "https://$url"
            binding.etWebUrl.setText(url)
        }
        AiTutorPrefs.setWebUrl(this, url)
        binding.webView.loadUrl(url)
    }

    private fun openWebExternal() {
        val url = binding.etWebUrl.text?.toString()?.trim().orEmpty()
        if (url.isBlank()) {
            Toast.makeText(this, "请填写 AI 网址", Toast.LENGTH_SHORT).show()
            return
        }
        val full = if (url.startsWith("http")) url else "https://$url"
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(full)))
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

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.etInput.windowToken, 0)
    }

    private fun applyChrome() {
        val theme = UiPrefs.theme(this)
        ThemeUi.applyScrapbook(this, binding.paperBg)
        binding.root.setBackgroundColor(theme.bg)
        ThemeUi.colorTexts(theme.ink, binding.tvHeader)
        ThemeUi.colorTexts(theme.muted, binding.btnBack)
        ThemeUi.colorLines(0x597A6F62, binding.headerLine)
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
            label.text = when (msg.role) {
                "user" -> "我"
                "assistant" -> "AI 导师"
                else -> msg.role
            }
            body.text = msg.content
            root.background = GradientDrawable().apply {
                cornerRadius = 10 * d
                setColor(if (isUser) Color.parseColor("#E8F0EA") else Color.parseColor("#FFFEF8"))
                setStroke((1 * d).toInt().coerceAtLeast(1), Color.parseColor("#337A6F62"))
            }
            actions.removeAllViews()
            if (msg.role == "assistant" && !msg.content.startsWith("⚠") && msg.content != "思考中…") {
                actions.addView(actionBtn("存为便签") { onSaveNote(msg.content) })
                actions.addView(actionBtn("复制") { onCopy(msg.content) })
            }
        }

        private fun actionBtn(text: String, onClick: () -> Unit): TextView {
            return TextView(root.context).apply {
                this.text = text
                textSize = 13f
                setPadding((10 * d).toInt(), (4 * d).toInt(), (10 * d).toInt(), (4 * d).toInt())
                setOnClickListener { onClick() }
            }
        }
    }
}
