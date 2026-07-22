package takagi.ru.monica.service

import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.text.InputType
import android.view.accessibility.AccessibilityManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import takagi.ru.monica.data.PasswordDatabase
import takagi.ru.monica.repository.PasswordRepository
import takagi.ru.monica.autofill_ng.ActiveFillNotificationHelper

class MonicaAccessibilityService : AccessibilityService() {
    private var lastPackageName: String = ""
    private var lastUrl: String = ""
    private var lastScanTime = 0L
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var passwordRepository: PasswordRepository
    private var lastActiveFillPackage: String = ""
    private var lastActiveFillTime = 0L

    companion object {
        private const val TAG = "MonicaAccessibility"
        private const val SCAN_THROTTLE_MS = 400L
        private const val MAX_SCAN_DEPTH = 8
        private const val MAX_SCAN_NODES = 300
        private const val MAX_FILL_SCAN_NODES = 400
        private const val SCORE_PASSWORD_SIGNAL = 40
        private const val SCORE_USERNAME_SIGNAL = 24
        private const val SCORE_FOCUSED_BONUS = 12
        private const val SCORE_PASSWORD_FLAG = 90
        private const val SCORE_CONFIRM_PENALTY = 35
        private const val ACTIVE_FILL_THROTTLE_MS = 5000L

        @Volatile
        private var activeInstance: MonicaAccessibilityService? = null

        private data class BrowserSpec(
            val packageName: String,
            val urlFieldIds: Set<String>,
        )

        private val browserSpecsByPackage = listOf(
            BrowserSpec(
                packageName = "org.mozilla.fenix",
                urlFieldIds = setOf("mozac_browser_toolbar_url_view", "url_bar_title"),
            ),
            BrowserSpec(
                packageName = "org.mozilla.firefox",
                urlFieldIds = setOf("mozac_browser_toolbar_url_view", "url_bar_title"),
            ),
            BrowserSpec(
                packageName = "org.mozilla.firefox_beta",
                urlFieldIds = setOf("mozac_browser_toolbar_url_view", "url_bar_title"),
            ),
            BrowserSpec(
                packageName = "org.mozilla.fenix.nightly",
                urlFieldIds = setOf("mozac_browser_toolbar_url_view", "url_bar_title"),
            ),
            BrowserSpec(
                packageName = "io.github.forkmaintainers.iceraven",
                urlFieldIds = setOf("mozac_browser_toolbar_url_view", "url_bar_title"),
            ),
            BrowserSpec(
                packageName = "com.android.chrome",
                urlFieldIds = setOf("url_bar"),
            ),
            BrowserSpec(
                packageName = "com.chrome.beta",
                urlFieldIds = setOf("url_bar"),
            ),
            BrowserSpec(
                packageName = "com.chrome.dev",
                urlFieldIds = setOf("url_bar"),
            ),
            BrowserSpec(
                packageName = "com.chrome.canary",
                urlFieldIds = setOf("url_bar"),
            ),
            BrowserSpec(
                packageName = "com.google.android.apps.chrome",
                urlFieldIds = setOf("url_bar"),
            ),
        ).associateBy { it.packageName }

        private val trackedEventTypes = setOf(
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.TYPE_VIEW_FOCUSED,
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED,
        )

        fun requestCredentialFill(
            targetPackageName: String?,
            username: String,
            password: String,
            preferPasswordField: Boolean,
        ): Boolean {
            return activeInstance?.fillCredentialsInActiveWindow(
                targetPackageName = targetPackageName,
                username = username,
                password = password,
                preferPasswordField = preferPasswordField
            ) ?: false
        }

        fun getActiveWindowPackageName(): String? {
            return activeInstance?.rootInActiveWindow?.packageName?.toString()
        }

        fun isCredentialFillAvailable(context: Context): Boolean {
            return isServiceEnabled(context) && activeInstance != null
        }

        fun isServiceEnabled(context: Context): Boolean {
            val manager = context.getSystemService(AccessibilityManager::class.java) ?: return false
            return manager
                .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                .any { info ->
                    val serviceInfo = info.resolveInfo?.serviceInfo ?: return@any false
                    serviceInfo.packageName == context.packageName &&
                        serviceInfo.name == MonicaAccessibilityService::class.java.name
                }
        }
    }

    private enum class FillFieldType {
        USERNAME,
        PASSWORD,
        UNKNOWN
    }

    private data class FillCandidate(
        val node: AccessibilityNodeInfo,
        val type: FillFieldType,
        val score: Int,
        val isFocused: Boolean,
        val top: Int,
        val left: Int
    )

    override fun onServiceConnected() {
        super.onServiceConnected()
        activeInstance = this
        runCatching {
            passwordRepository = PasswordRepository(PasswordDatabase.getDatabase(this).passwordEntryDao())
            ActiveFillNotificationHelper.createChannel(this)
        }.onFailure { e ->
            Log.w(TAG, "Failed to init active fill repository", e)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        runCatching {
            event ?: return
            if (event.eventType !in trackedEventTypes) return

            val packageName = event.packageName?.toString().orEmpty()
            val browserSpec = browserSpecsByPackage[packageName]
            if (browserSpec != null) {
                val now = System.currentTimeMillis()
                if (packageName == lastPackageName && now - lastScanTime < SCAN_THROTTLE_MS) return
                lastScanTime = now

                val root = rootInActiveWindow ?: event.source ?: return
                val url = findBrowserUrl(root, browserSpec) ?: return

                if (packageName == lastPackageName && url == lastUrl) return

                lastPackageName = packageName
                lastUrl = url
                BrowserAutofillContextStore.update(packageName, url)
                ValidatorContextManager.updateContext(packageName, url)
                Log.d(TAG, "Updated browser context: pkg=$packageName, hasUrl=${url.isNotBlank()}")
                return
            }

            // 阶段1：非浏览器 App 的登录字段聚焦 -> 主动发填充通知
            if (event.eventType == AccessibilityEvent.TYPE_VIEW_FOCUSED) {
                maybePromptActiveFill(packageName, event.source)
            }
        }.onFailure { e ->
            Log.w(TAG, "onAccessibilityEvent failed", e)
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "Accessibility Service Interrupted")
    }

    override fun onDestroy() {
        if (activeInstance === this) {
            activeInstance = null
        }
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun fillCredentialsInActiveWindow(
        targetPackageName: String?,
        username: String,
        password: String,
        preferPasswordField: Boolean,
    ): Boolean = runCatching {
        val root = rootInActiveWindow ?: return@runCatching false
        val activePackageName = root.packageName?.toString().orEmpty()
        if (
            !targetPackageName.isNullOrBlank() &&
            activePackageName.isNotBlank() &&
            !activePackageName.equals(targetPackageName, ignoreCase = true)
        ) {
            Log.d(TAG, "Skip accessibility fill: active package mismatch ($activePackageName != $targetPackageName)")
            return@runCatching false
        }

        val focusedNode = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            ?: root.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)
        val candidates = collectFillCandidates(root, focusedNode)
        if (candidates.isEmpty()) {
            Log.d(TAG, "Skip accessibility fill: no editable candidates")
            return@runCatching false
        }

        val focusedCandidate = candidates.firstOrNull { it.isFocused }
        val passwordCandidate = selectBestCandidate(
            candidates = candidates,
            preferredType = FillFieldType.PASSWORD,
            excludedNode = null,
            fallback = if (preferPasswordField) {
                focusedCandidate?.node
            } else {
                nearestCandidate(candidates, focusedCandidate?.node, null)?.node
            }
        )
        val usernameCandidate = selectBestCandidate(
            candidates = candidates,
            preferredType = FillFieldType.USERNAME,
            excludedNode = passwordCandidate?.node,
            fallback = if (!preferPasswordField) {
                focusedCandidate?.node
            } else {
                nearestCandidate(candidates, focusedCandidate?.node, passwordCandidate?.node)?.node
            }
        )

        var usernameFilled = false
        var passwordFilled = false

        if (username.isNotBlank()) {
            usernameFilled = setNodeText(usernameCandidate?.node, username)
        }
        if (password.isNotBlank()) {
            passwordFilled = setNodeText(passwordCandidate?.node, password)
        }

        if (!usernameFilled && !passwordFilled) {
            Log.d(TAG, "Accessibility fill failed: no fields accepted text")
            return@runCatching false
        }

        Log.d(
            TAG,
            "Accessibility fill success: usernameFilled=$usernameFilled, passwordFilled=$passwordFilled, preferPassword=$preferPasswordField"
        )
        if (preferPasswordField) {
            passwordFilled || (password.isBlank() && usernameFilled)
        } else {
            usernameFilled || (username.isBlank() && passwordFilled)
        }
    }.onFailure { e ->
        Log.w(TAG, "fillCredentialsInActiveWindow failed", e)
    }.getOrDefault(false)

    private fun maybePromptActiveFill(packageName: String, source: AccessibilityNodeInfo?) {
        if (packageName.isBlank() || source == null) return
        if (!isLikelyLoginField(source)) return

        val now = System.currentTimeMillis()
        if (packageName == lastActiveFillPackage && now - lastActiveFillTime < ACTIVE_FILL_THROTTLE_MS) return

        serviceScope.launch {
            try {
                val entries = passwordRepository.getAllPasswordEntries().first()
                val match = entries.firstOrNull { entry ->
                    entry.appPackageName
                        .split("[,;| ]".toRegex())
                        .any { it.equals(packageName, ignoreCase = true) }
                }
                if (match != null) {
                    lastActiveFillPackage = packageName
                    lastActiveFillTime = now
                    val shown = ActiveFillNotificationHelper.showActiveFillNotification(
                        this@MonicaAccessibilityService,
                        packageName,
                        match.appName.ifBlank { packageName }
                    )
                    Log.d(TAG, "Active fill prompt for pkg=$packageName shown=$shown")
                } else {
                    Log.d(TAG, "No matching credential for pkg=$packageName, skip active fill")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Active fill query failed", e)
            }
        }
    }

    private fun isLikelyLoginField(node: AccessibilityNodeInfo): Boolean {
        if (node.isPassword) return true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val inputType = node.inputType
            val inputClass = inputType and InputType.TYPE_MASK_CLASS
            val variation = inputType and InputType.TYPE_MASK_VARIATION
            if (
                (inputClass == InputType.TYPE_CLASS_TEXT && (
                    variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                        variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD ||
                        variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                        variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS ||
                        variation == InputType.TYPE_TEXT_VARIATION_PERSON_NAME ||
                        variation == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS
                    )) ||
                (inputClass == InputType.TYPE_CLASS_NUMBER &&
                    variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD)
            ) {
                return true
            }
        }
        if (node.isEditable) {
            val signals = buildList {
                add(node.hintText?.toString().orEmpty())
                add(node.contentDescription?.toString().orEmpty())
                add(node.viewIdResourceName.orEmpty())
            }.joinToString(" ").lowercase()
            if (
                signals.contains("password") || signals.contains("passwd") || signals.contains("pwd") ||
                signals.contains("username") || signals.contains("user_name") ||
                signals.contains("email") || signals.contains("login") || signals.contains("account")
            ) {
                return true
            }
        }
        return false
    }

    private fun findBrowserUrl(root: AccessibilityNodeInfo, browserSpec: BrowserSpec): String? {
        val queue = ArrayDeque<Pair<AccessibilityNodeInfo, Int>>()
        queue.add(root to 0)
        var visited = 0

        while (queue.isNotEmpty() && visited < MAX_SCAN_NODES) {
            val (node, depth) = queue.removeFirst()
            visited += 1

            runCatching {
                val viewId = node.viewIdResourceName.orEmpty()
                if (browserSpec.urlFieldIds.any { viewId.endsWith(it) }) {
                    extractNodeText(node)?.let { return it }
                }

                if (depth < MAX_SCAN_DEPTH) {
                    for (index in 0 until node.childCount) {
                        node.getChild(index)?.let { child ->
                            queue.add(child to (depth + 1))
                        }
                    }
                }
            }
        }

        return null
    }

    private fun extractNodeText(node: AccessibilityNodeInfo): String? {
        return sequenceOf(
            node.text?.toString(),
            node.contentDescription?.toString(),
            node.hintText?.toString(),
        ).mapNotNull { it?.trim() }
            .firstOrNull { candidate ->
                candidate.isNotBlank() &&
                    candidate.any { it == '.' || it == ':' || it == '/' } &&
                    !candidate.startsWith("Search", ignoreCase = true)
            }
    }

    private fun collectFillCandidates(
        root: AccessibilityNodeInfo,
        focusedNode: AccessibilityNodeInfo?
    ): List<FillCandidate> {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        val candidates = mutableListOf<FillCandidate>()
        queue.add(root)
        var visited = 0

        while (queue.isNotEmpty() && visited < MAX_FILL_SCAN_NODES) {
            val node = queue.removeFirst()
            visited += 1

            runCatching {
                if (node.isVisibleToUser && node.isEnabled && supportsSetText(node)) {
                    candidates += classifyFillCandidate(node)
                }

                for (index in 0 until node.childCount) {
                    node.getChild(index)?.let(queue::addLast)
                }
            }
        }

        if (
            focusedNode != null &&
            runCatching { supportsSetText(focusedNode) }.getOrDefault(false) &&
            candidates.none { it.node == focusedNode }
        ) {
            runCatching { candidates += classifyFillCandidate(focusedNode) }
        }

        return runCatching {
            candidates.distinctBy { candidate ->
                val bounds = Rect().also(candidate.node::getBoundsInScreen)
                listOf(
                    candidate.node.viewIdResourceName.orEmpty(),
                    bounds.left.toString(),
                    bounds.top.toString(),
                    bounds.right.toString(),
                    bounds.bottom.toString()
                ).joinToString("|")
            }
        }.getOrDefault(candidates)
    }

    private fun supportsSetText(node: AccessibilityNodeInfo): Boolean {
        if (node.isEditable) return true
        return runCatching {
            node.actionList.orEmpty().any { action -> action.id == AccessibilityNodeInfo.ACTION_SET_TEXT }
        }.getOrDefault(false)
    }

    private fun classifyFillCandidate(node: AccessibilityNodeInfo): FillCandidate {
        val bounds = Rect().also { runCatching { node.getBoundsInScreen(it) } }
        val signals = buildList {
            add(node.viewIdResourceName.orEmpty())
            add(node.hintText?.toString().orEmpty())
            add(node.contentDescription?.toString().orEmpty())
            add(node.text?.toString().orEmpty())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                add(node.tooltipText?.toString().orEmpty())
            }
        }.joinToString(" ").lowercase()

        var passwordScore = 0
        var usernameScore = 0

        if (node.isPassword) {
            passwordScore += SCORE_PASSWORD_FLAG
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val inputType = node.inputType
            val inputClass = inputType and InputType.TYPE_MASK_CLASS
            val variation = inputType and InputType.TYPE_MASK_VARIATION
            if (
                (inputClass == InputType.TYPE_CLASS_TEXT && (
                    variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                        variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD ||
                        variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                    )) ||
                (inputClass == InputType.TYPE_CLASS_NUMBER &&
                    variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD)
            ) {
                passwordScore += SCORE_PASSWORD_FLAG
            }
            if (
                inputClass == InputType.TYPE_CLASS_TEXT &&
                (
                    variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS ||
                        variation == InputType.TYPE_TEXT_VARIATION_PERSON_NAME ||
                        variation == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS
                    )
            ) {
                usernameScore += SCORE_USERNAME_SIGNAL
            }
        }

        if (signals.contains("password") || signals.contains("passwd") || signals.contains("pwd")) {
            passwordScore += SCORE_PASSWORD_SIGNAL
        }
        if (signals.contains("passcode") || signals.contains("pin")) {
            passwordScore += SCORE_PASSWORD_SIGNAL / 2
        }
        if (
            signals.contains("confirm") ||
            signals.contains("re-enter") ||
            signals.contains("repeat")
        ) {
            passwordScore -= SCORE_CONFIRM_PENALTY
        }

        if (signals.contains("username") || signals.contains("user_name")) {
            usernameScore += SCORE_USERNAME_SIGNAL + 12
        }
        if (
            signals.contains("email") ||
            signals.contains("e-mail") ||
            signals.contains("login") ||
            signals.contains("account")
        ) {
            usernameScore += SCORE_USERNAME_SIGNAL
        }
        if (signals.contains("phone") || signals.contains("mobile")) {
            usernameScore += SCORE_USERNAME_SIGNAL / 2
        }

        if (node.isFocused) {
            passwordScore += SCORE_FOCUSED_BONUS
            usernameScore += SCORE_FOCUSED_BONUS
        }

        val type = when {
            passwordScore > usernameScore && passwordScore > 0 -> FillFieldType.PASSWORD
            usernameScore > passwordScore && usernameScore > 0 -> FillFieldType.USERNAME
            else -> FillFieldType.UNKNOWN
        }
        val resolvedScore = maxOf(passwordScore, usernameScore)

        return FillCandidate(
            node = node,
            type = type,
            score = resolvedScore,
            isFocused = node.isFocused,
            top = bounds.top,
            left = bounds.left
        )
    }

    private fun selectBestCandidate(
        candidates: List<FillCandidate>,
        preferredType: FillFieldType,
        excludedNode: AccessibilityNodeInfo?,
        fallback: AccessibilityNodeInfo?,
    ): FillCandidate? {
        val exactMatch = candidates
            .asSequence()
            .filter { candidate -> candidate.type == preferredType }
            .filterNot { candidate -> excludedNode != null && candidate.node == excludedNode }
            .sortedWith(
                compareByDescending<FillCandidate> { it.score }
                    .thenByDescending { it.isFocused }
                    .thenBy { it.top }
                    .thenBy { it.left }
            )
            .firstOrNull()
        if (exactMatch != null) return exactMatch

        val fallbackNode = fallback
            ?.takeIf { node -> excludedNode == null || node != excludedNode }
            ?.let { node -> candidates.firstOrNull { it.node == node } }
        if (fallbackNode != null) return fallbackNode

        return nearestCandidate(candidates, fallback, excludedNode)
            ?: candidates.firstOrNull { candidate -> excludedNode == null || candidate.node != excludedNode }
    }

    private fun nearestCandidate(
        candidates: List<FillCandidate>,
        anchorNode: AccessibilityNodeInfo?,
        excludedNode: AccessibilityNodeInfo?
    ): FillCandidate? {
        val anchor = anchorNode ?: return null
        val anchorRect = Rect().also(anchor::getBoundsInScreen)
        val anchorCenterX = anchorRect.centerX()
        val anchorCenterY = anchorRect.centerY()
        return candidates
            .asSequence()
            .filterNot { candidate -> candidate.node == anchor }
            .filterNot { candidate -> excludedNode != null && candidate.node == excludedNode }
            .sortedBy { candidate ->
                val dx = candidate.left - anchorCenterX
                val dy = candidate.top - anchorCenterY
                dx * dx + dy * dy
            }
            .firstOrNull()
    }

    private fun setNodeText(node: AccessibilityNodeInfo?, text: String): Boolean {
        if (node == null || text.isBlank()) return false
        if (!node.isFocused) {
            node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        }
        node.performAction(AccessibilityNodeInfo.ACTION_CLICK)

        // WebView (H5 / X5·TBS 等内核) 的 HTML <input> 对 ACTION_SET_TEXT
        // 常表现为"接受命令但不刷新界面"——它只改无障碍节点的 text 属性，
        // 不触发 JS input 事件，框架(React/Vue)因此读不到新值、界面仍空白。
        // 优先用 ACTION_PASTE：粘贴会触发 input 事件，HTML 框架才能捕获并刷新 UI。
        // 副作用：临时覆盖系统剪贴板（主流密码管理器的通用做法）。
        runCatching {
            val clipboard =
                getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            clipboard.setPrimaryClip(
                android.content.ClipData.newPlainText("monica_fill", text)
            )
        }
        val pasted = runCatching {
            node.performAction(AccessibilityNodeInfo.ACTION_PASTE)
        }.getOrDefault(false)
        if (pasted) return true

        // 原生 EditText 等不支持 paste 的场景，回退 SET_TEXT（原生控件能正确刷新）。
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        return runCatching {
            node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        }.getOrDefault(false)
    }
}
