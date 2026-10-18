package com.largebatata.nettoggle

import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import moe.shizuku.server.IShizukuService
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.text.TextUtils
import android.util.LruCache
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ImageButton
import android.widget.Switch
import android.widget.PopupMenu
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import java.util.concurrent.Executors
import rikka.shizuku.Shizuku

/** One screen; cached observations are display hints, never permission to execute a toggle. */
class MainActivity : Activity() {
    private data class AppEntry(val label: String, val pkg: String, val info: ApplicationInfo,
        val inventoryKey: String)
    private data class Row(val icon: ImageView, val name: TextView, val pkg: TextView,
        val state: TextView, val networkSwitch: Switch, val details: LinearLayout,
        val container: LinearLayout, var boundPackage: String? = null)

    private object RuntimeRules {
        var model: RuleListModel? = null
        var inventory: Map<String, String> = emptyMap()
        val session = RuleSession()
        var pendingPackage: String? = null
        var onWriteFinished: (() -> Unit)? = null
    }

    private var actualPermission: Boolean? = null
    private val io = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val verification = VerificationQueue()
    private val icons = LruCache<String, Drawable>(48)
    private val apps = mutableMapOf<String, AppEntry>()
    private lateinit var ui: UiTheme
    private lateinit var connectionTag: TextView
    private var renderedConnection: ConnectionPresentation? = null
    private var resetListPosition = false
    private var deferredAnchor: String? = null
    private var deferredTop = 0
    private var listScrolling = false
    private lateinit var model: RuleListModel
    private lateinit var cacheStore: RuleCacheStore
    private var cacheDirty = false
    private var cacheSaveQueued = false
    private var uiQueued = false
    private var selectedGroup = RuleGroup.ALL
    private var filter = ""
    private var pendingPackage: String?
        get() = RuntimeRules.pendingPackage
        set(value) { RuntimeRules.pendingPackage = value }
    private var firewall: IFirewallService? = null
    private var activeConnection: ServiceConnection? = null
    private var bindTimeout: Runnable? = null
    @Volatile private var readToken = 0L
    @Volatile private var destroyed = false
    private var foreground = false
    private var failedReads = 0
    private var checkedApps = 0
    private lateinit var header: TextView
    private lateinit var summary: TextView
    private lateinit var refreshButton: ImageButton
    private lateinit var listView: ListView
    private val tabs = mutableMapOf<RuleGroup, Button>()
    private val adapter = AppAdapter()

    private val updateUi = Runnable { uiQueued = false; if (!destroyed) renderList() }
    private val saveCache = Runnable { cacheSaveQueued = false; flushCache() }
    private val serviceArgs by lazy {
        Shizuku.UserServiceArgs(ComponentName(this, FirewallUserService::class.java))
            .daemon(false).processNameSuffix("firewall").tag("nettoggle-firewall-v1").version(4)
    }
    private val received = Shizuku.OnBinderReceivedListener { runOnUiThread { maybeBind() } }
    private val died = Shizuku.OnBinderDeadListener {
        runOnUiThread { disconnected(getString(R.string.shizuku_stopped)) }
    }
    private val permission = Shizuku.OnRequestPermissionResultListener { code, result ->
        if (!destroyed && code == 100) {
            if (result == PackageManager.PERMISSION_GRANTED) maybeBind()
            else { actualPermission = false; disconnected(getString(R.string.permission_denied)) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ui = UiTheme(this)
        selectedGroup = RuleGroup.entries.firstOrNull { it.name == savedInstanceState?.getString("selected_group") } ?: RuleGroup.ALL
        filter = savedInstanceState?.getString("filter").orEmpty()
        deferredAnchor = savedInstanceState?.getString("scroll_pkg")
        deferredTop = savedInstanceState?.getInt("scroll_top", 0) ?: 0
        cacheStore = RuleCacheStore(this)
        loadApps()
        val inventory = apps.mapValues { (_, entry) -> entry.inventoryKey }
        if (RuntimeRules.inventory != inventory || RuntimeRules.model == null) {
            RuntimeRules.model = RuleListModel(apps.values.map { RuleApp(it.pkg, it.label) }, cacheStore.load())
            RuntimeRules.inventory = inventory
            RuntimeRules.session.invalidate()
        }
        model = requireNotNull(RuntimeRules.model)
        if (!Shizuku.pingBinder() || !RuntimeRules.session.sameServer(Shizuku.getBinder())) {
            RuntimeRules.session.invalidate()
            model.invalidate()
        }
        createUi()
        renderList() // Local inventory + explicit cache labels, before any Shizuku call.
        Shizuku.addBinderReceivedListenerSticky(received)
        Shizuku.addBinderDeadListener(died)
        Shizuku.addRequestPermissionResultListener(permission)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        val anchor = adapter.anchor()
        outState.putString("selected_group", selectedGroup.name)
        outState.putString("filter", filter)
        outState.putString("scroll_pkg", anchor.first ?: deferredAnchor)
        outState.putInt("scroll_top", if (anchor.first != null) anchor.second else deferredTop)
        super.onSaveInstanceState(outState)
    }

    override fun onResume() {
        super.onResume()
        foreground = true
        RuntimeRules.session.enter(SystemClock.elapsedRealtime())
        RuntimeRules.onWriteFinished = { if (!destroyed && foreground) resumeVerification() }
        checkPermission()
        if (!Shizuku.pingBinder() || actualPermission != true) {
            disconnected(getString(if (!Shizuku.pingBinder()) R.string.start_shizuku else R.string.request_permission))
        } else if (firewall == null) maybeBind()
        else if (!serviceAvailable()) disconnected(getString(R.string.service_disconnected))
        else resumeVerification()
    }

    override fun onStop() {
        foreground = false
        RuntimeRules.onWriteFinished = null
        RuntimeRules.session.leave(SystemClock.elapsedRealtime())
        cancelVerification()
        flushCache()
        scheduleUiUpdate(immediate = true)
        super.onStop()
    }

    override fun onDestroy() {
        destroyed = true
        cancelVerification()
        mainHandler.removeCallbacks(updateUi)
        mainHandler.removeCallbacks(saveCache)
        flushCache()
        Shizuku.removeBinderReceivedListener(received)
        Shizuku.removeBinderDeadListener(died)
        Shizuku.removeRequestPermissionResultListener(permission)
        detachConnection()
        // Let an accepted write finish. Stale read tasks check readToken before invoking Binder.
        io.shutdown()
        super.onDestroy()
    }

    private fun createUi() {
        val stacked = useStackedChrome(resources.configuration.screenWidthDp, resources.configuration.fontScale)
        val page = FrameLayout(this).apply { setBackgroundColor(ui.background) }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(4), dp(16), dp(8))
        }
        page.addView(root, FrameLayout.LayoutParams(
            if (resources.configuration.screenWidthDp > 680) dp(680) else -1, -1, Gravity.CENTER_HORIZONTAL))
        setContentView(page)
        page.setOnApplyWindowInsetsListener { view, insets ->
            val bars = insets.getInsets(android.view.WindowInsets.Type.systemBars() or
                android.view.WindowInsets.Type.displayCutout() or android.view.WindowInsets.Type.ime())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        page.requestApplyInsets()
        val toolbar = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        toolbar.addView(TextView(this).apply {
            setText(R.string.app_name); textSize = 22f; gravity = Gravity.CENTER_VERTICAL; setTextColor(ui.text)
            setTypeface(null, Typeface.BOLD); maxLines = 1
        }, LinearLayout.LayoutParams(0, dp(52), 1f))
        val connectionTouch = FrameLayout(this).apply {
            minimumWidth = dp(88)
            isClickable = true; isFocusable = true
            setOnClickListener { if (!serviceAvailable()) maybeBind(requestPermission = true) else showInformation() }
        }
        connectionTag = TextView(this).apply {
            gravity = Gravity.CENTER; textSize = 12f
            setPadding(dp(10), 0, dp(10), 0)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        connectionTouch.addView(connectionTag, FrameLayout.LayoutParams(-2, dp(32), Gravity.CENTER))
        if (!stacked) toolbar.addView(connectionTouch, LinearLayout.LayoutParams(-2, dp(48)))
        refreshButton = iconButton(R.drawable.ic_refresh, R.string.refresh_accessibility).apply {
            setOnClickListener {
                checkPermission()
                if (!serviceAvailable()) {
                    disconnected(getString(R.string.service_disconnected))
                    maybeBind(requestPermission = true)
                }
                else if (!verification.running && pendingPackage == null) {
                    RuntimeRules.session.invalidate(); resumeVerification()
                }
            }
        }
        toolbar.addView(refreshButton, LinearLayout.LayoutParams(dp(48), dp(48)))
        toolbar.addView(iconButton(R.drawable.ic_more, R.string.more_accessibility).apply {
            setOnClickListener { anchor ->
                PopupMenu(this@MainActivity, anchor).apply {
                    menu.add(0, 1, 0, R.string.system_usage)
                    menu.add(0, 2, 1, R.string.information_title)
                    setOnMenuItemClickListener {
                        if (it.itemId == 1) openSystemDataUsage() else showInformation()
                        true
                    }
                    show()
                }
            }
        }, LinearLayout.LayoutParams(dp(48), dp(48)))
        root.addView(toolbar)
        if (stacked) root.addView(LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(TextView(this@MainActivity).apply {
                setText(R.string.shizuku_label); textSize = 14f; setTextColor(ui.secondary)
            }, LinearLayout.LayoutParams(0, dp(48), 1f))
            addView(connectionTouch, LinearLayout.LayoutParams(-2, dp(48)))
        })
        val search = EditText(this).apply {
            setHint(R.string.search); setSingleLine(true); textSize = 15f
            setTextColor(ui.text); setHintTextColor(ui.secondary)
            setPadding(dp(16), 0, dp(16), 0)
            background = ui.shape(ui.surfaceHigh, 28)
            val icon = getDrawable(R.drawable.ic_search)?.apply { setTint(ui.secondary); setBounds(0, 0, dp(20), dp(20)) }
            setCompoundDrawablesRelative(icon, null, null, null)
            compoundDrawablePadding = dp(10)
            setText(filter)
            addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) {
                    filter = s?.toString().orEmpty()
                    resetListPosition = true
                    scheduleUiUpdate(immediate = true)
                }
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            })
        }
        root.addView(search, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(4); bottomMargin = dp(8) })
        root.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            RuleGroup.entries.forEach { group ->
                val tab = Button(this@MainActivity).apply {
                    textSize = 13f; isAllCaps = false; minWidth = 0; maxLines = if (stacked) 2 else 1
                    minimumHeight = dp(48); stateListAnimator = null; elevation = 0f
                    setPadding(dp(4), 0, dp(4), 0)
                    setOnClickListener {
                        if (selectedGroup != group) {
                            selectedGroup = group; resetListPosition = true
                            scheduleUiUpdate(immediate = true)
                        }
                    }
                }
                tabs[group] = tab
                addView(tab, LinearLayout.LayoutParams(0, if (stacked) -2 else dp(48), 1f).apply {
                    if (group != RuleGroup.UNBLOCKED) marginEnd = dp(4)
                })
            }
        })
        summary = TextView(this).apply {
            textSize = 12f; setTextColor(ui.secondary)
            setPadding(dp(4), dp(6), dp(4), 0)
        }
        root.addView(summary)
        header = TextView(this).apply {
            setText(R.string.waiting_shizuku); textSize = 12f; setTextColor(ui.secondary)
            maxLines = 2; ellipsize = TextUtils.TruncateAt.END
            setPadding(dp(4), dp(2), dp(4), dp(8))
            setOnClickListener { showInformation() }
        }
        root.addView(header)
        val frame = FrameLayout(this).apply {
            background = ui.shape(ui.surface, 24)
            clipToOutline = true
        }
        listView = ListView(this).apply {
            divider = ColorDrawable(ui.outline); dividerHeight = 1
            setCacheColorHint(Color.TRANSPARENT)
            adapter = this@MainActivity.adapter
            setOnScrollListener(object : android.widget.AbsListView.OnScrollListener {
                override fun onScrollStateChanged(view: android.widget.AbsListView?, state: Int) {
                    listScrolling = state != android.widget.AbsListView.OnScrollListener.SCROLL_STATE_IDLE
                }
                override fun onScroll(view: android.widget.AbsListView?, firstVisibleItem: Int, visibleItemCount: Int, totalItemCount: Int) {}
            })
        }
        frame.addView(listView, FrameLayout.LayoutParams(-1, -1))
        val empty = TextView(this).apply {
            setText(R.string.empty_group); gravity = Gravity.CENTER; textSize = 14f
            setTextColor(ui.secondary); setPadding(dp(24), dp(16), dp(24), dp(16))
        }
        frame.addView(empty, FrameLayout.LayoutParams(-1, -1))
        listView.emptyView = empty
        root.addView(frame, LinearLayout.LayoutParams(-1, 0, 1f))
    }

    private fun iconButton(icon: Int, label: Int) = ImageButton(this).apply {
        setImageResource(icon); imageTintList = ColorStateList.valueOf(ui.secondary)
        contentDescription = getString(label); tooltipText = getString(label)
        setPadding(dp(12), dp(12), dp(12), dp(12))
        background = ui.ripple(Color.TRANSPARENT, 24)
        minimumWidth = dp(48); minimumHeight = dp(48)
    }

    private fun showInformation() {
        AlertDialog.Builder(this).setTitle(R.string.information_title)
            .setMessage(getString(R.string.information_body) + "\n\n" +
                getString(R.string.reboot_notice) + "\n\n" + header.text)
            .setPositiveButton(R.string.close_information, null).show()
    }

    private fun openSystemDataUsage() {
        try { startActivity(Intent(Settings.ACTION_DATA_USAGE_SETTINGS)) }
        catch (_: ActivityNotFoundException) { openSettingsFallback() }
        catch (_: SecurityException) { openSettingsFallback() }
    }

    private fun openSettingsFallback() {
        try {
            startActivity(Intent(Settings.ACTION_SETTINGS))
            Toast.makeText(this, R.string.settings_fallback, Toast.LENGTH_LONG).show()
        } catch (_: Exception) {
            Toast.makeText(this, R.string.settings_failed, Toast.LENGTH_LONG).show()
        }
    }

    @Suppress("DEPRECATION")
    private fun loadApps() {
        if (Process.myUid() / 100000 != 0) return
        for (app in packageManager.getInstalledApplications(0)) {
            if (app.packageName == packageName || app.flags and ApplicationInfo.FLAG_SYSTEM != 0 || app.uid < 10000) continue
            // An uninstall/reinstall can race the earlier inventory read. Skip that row safely.
            val pkg = try { packageManager.getPackageInfo(app.packageName, 0) }
                catch (_: PackageManager.NameNotFoundException) { continue }
            if (pkg.applicationInfo?.uid != app.uid) continue
            val label = runCatching { packageManager.getApplicationLabel(app).toString() }.getOrDefault(app.packageName)
            apps[app.packageName] = AppEntry(label, app.packageName, app,
                "${app.uid}:${pkg.firstInstallTime}:${pkg.lastUpdateTime}:$label")
        }
    }

    private fun scheduleUiUpdate(immediate: Boolean = false) {
        if (destroyed) return
        if (immediate) {
            mainHandler.removeCallbacks(updateUi)
            uiQueued = false
            renderList()
        } else if (!uiQueued) {
            uiQueued = true
            mainHandler.postDelayed(updateUi, 100)
        }
    }

    private fun renderList() {
        val counts = model.counts()
        val stacked = useStackedChrome(resources.configuration.screenWidthDp, resources.configuration.fontScale)
        tabs[RuleGroup.ALL]?.text = getString(if (stacked) R.string.all_tab_stacked else R.string.all_tab, counts.all)
        tabs[RuleGroup.BLOCKED]?.text = getString(if (stacked) R.string.blocked_tab_stacked else R.string.blocked_tab, counts.blocked)
        tabs[RuleGroup.UNBLOCKED]?.text = getString(if (stacked) R.string.unblocked_tab_stacked else R.string.unblocked_tab, counts.unblocked)
        tabs.forEach { (group, button) ->
            val selected = group == selectedGroup
            button.isSelected = selected
            button.setTypeface(null, if (selected) Typeface.BOLD else Typeface.NORMAL)
            button.setTextColor(if (selected) ui.onPrimaryContainer else ui.secondary)
            // Stable background objects: result batches only change text/counts.
            if (button.tag != selected) {
                button.background = ui.ripple(if (selected) ui.primaryContainer else Color.TRANSPARENT, 24)
                button.tag = selected
            }
        }
        summary.text = getString(R.string.summary, counts.verified, counts.all, counts.unknown)
        renderConnection()
        adapter.submit(model.visible(selectedGroup, filter).mapNotNull { apps[it.pkg] })
        refreshButton.isEnabled = pendingPackage == null && !verification.running &&
            !(activeConnection != null && firewall == null)
        refreshButton.alpha = if (refreshButton.isEnabled) 1f else 0.38f
    }

    private fun renderConnection() {
        val state = presentConnection(Shizuku.getBinder()?.isBinderAlive == true, actualPermission,
            firewall?.asBinder()?.isBinderAlive == true, activeConnection != null && firewall == null)
        if (renderedConnection == state) return
        renderedConnection = state
        val (text, background, foreground) = when (state) {
            ConnectionPresentation.AUTHORIZED -> Triple(R.string.connection_ok, ui.connectedBackground, ui.connectedText)
            ConnectionPresentation.STOPPED -> Triple(R.string.connection_stopped, ui.surfaceHigh, ui.secondary)
            ConnectionPresentation.UNAUTHORIZED -> Triple(R.string.connection_unauthorized, ui.surfaceHigh, ui.secondary)
            ConnectionPresentation.DISCONNECTED -> Triple(R.string.connection_off, ui.warningBackground, ui.warningText)
            ConnectionPresentation.CONNECTING -> Triple(R.string.connection_busy, ui.warningBackground, ui.warningText)
        }
        connectionTag.setText(text)
        connectionTag.setTextColor(foreground)
        connectionTag.background = ui.shape(background, 16)
        (connectionTag.parent as View).contentDescription = getString(R.string.connection_accessibility, getString(text))
    }

    private data class RowSignature(val pkg: String, val live: RuleState?, val cached: RuleState?,
        val error: String?, val busy: Boolean, val enabled: Boolean)

    private inner class AppAdapter : BaseAdapter() {
        private var entries = emptyList<AppEntry>()
        private var signatures = emptyList<RowSignature>()
        fun anchor(): Pair<String?, Int> = entries.getOrNull(listView.firstVisiblePosition)?.pkg to
            (listView.getChildAt(0)?.top ?: 0)
        fun submit(next: List<AppEntry>) {
            val nextSignatures = next.map { entry ->
                val live = model.state(entry.pkg)
                RowSignature(entry.pkg, live, model.cached(entry.pkg)?.state, model.error(entry.pkg),
                    pendingPackage == entry.pkg, foreground && serviceAvailable() && live != null && pendingPackage == null)
            }
            if (!resetListPosition && signatures == nextSignatures) return
            val orderChanged = entries.map { it.pkg } != next.map { it.pkg }
            val stayAtTop = keepAbsoluteListTop(listView.firstVisiblePosition,
                listView.getChildAt(0)?.top ?: 0, deferredAnchor != null)
            if (resetListPosition) { deferredAnchor = null; deferredTop = 0 }
            else if (orderChanged && !stayAtTop && !listScrolling && deferredAnchor == null) {
                val anchor = anchor()
                deferredAnchor = anchor.first; deferredTop = anchor.second
            }
            entries = next; signatures = nextSignatures
            notifyDataSetChanged()
            if (resetListPosition || (stayAtTop && !listScrolling)) listView.setSelectionFromTop(0, 0)
            else if (!listScrolling) {
                val index = entries.indexOfFirst { it.pkg == deferredAnchor }
                if (index >= 0) {
                    listView.setSelectionFromTop(index, deferredTop)
                    deferredAnchor = null
                }
            }
            resetListPosition = false
        }
        override fun hasStableIds() = true
        override fun getCount() = entries.size
        override fun getItem(position: Int) = entries[position]
        override fun getItemId(position: Int) = stablePackageId(entries[position].pkg)
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val line = convertView ?: createRow()
            val row = line.tag as Row
            val entry = entries[position]
            val recycled = row.boundPackage != entry.pkg
            row.boundPackage = entry.pkg
            row.name.text = entry.label; row.pkg.text = entry.pkg
            var icon = icons.get(entry.pkg)
            if (icon == null) {
                icon = runCatching { packageManager.getApplicationIcon(entry.info) }.getOrNull()
                if (icon != null) icons.put(entry.pkg, icon)
            }
            row.icon.setImageDrawable(icon)
            bindState(row, entry.pkg)
            if (recycled) row.networkSwitch.jumpDrawablesToCurrentState()
            return line
        }
    }

    private fun createRow(): LinearLayout {
        val line = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(76); setPadding(dp(12), dp(8), dp(8), dp(8))
        }
        val icon = ImageView(this).apply { importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO }
        line.addView(icon, LinearLayout.LayoutParams(dp(40), dp(40)).apply { marginEnd = dp(12) })
        val middle = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(48)
        }
        val name = TextView(this).apply {
            textSize = 15f; setTextColor(ui.text); setTypeface(null, Typeface.BOLD)
            maxLines = 1; ellipsize = TextUtils.TruncateAt.END
        }
        val pkg = TextView(this).apply {
            textSize = 11f; setTextColor(ui.secondary); maxLines = 1; ellipsize = TextUtils.TruncateAt.END
        }
        val state = TextView(this).apply { textSize = 12f; maxLines = 2; ellipsize = TextUtils.TruncateAt.END }
        middle.addView(name); middle.addView(pkg); middle.addView(state)
        line.addView(middle, LinearLayout.LayoutParams(0, -2, 1f))
        val control = Switch(this).apply {
            showText = false; splitTrack = false; switchMinWidth = dp(52)
            thumbTintList = ui.switchThumb(); trackTintList = ui.switchTrack()
            minimumHeight = dp(48); minimumWidth = dp(64)
            setPadding(dp(4), 0, dp(4), 0)
        }
        line.addView(control, LinearLayout.LayoutParams(dp(64), dp(48)))
        line.tag = Row(icon, name, pkg, state, control, middle, line)
        return line
    }

    private fun bindState(row: Row, pkg: String) {
        val live = model.state(pkg)
        val cached = model.cached(pkg)
        val busy = pendingPackage == pkg
        val presentation = presentSwitch(live, cached?.state, serviceAvailable(), foreground, pendingPackage != null)
        row.networkSwitch.setOnCheckedChangeListener(null)
        row.networkSwitch.isChecked = presentation.allowed
        row.networkSwitch.isEnabled = presentation.enabled
        val text = when {
            busy -> getString(R.string.pending_state)
            live == RuleState.BLOCKED -> getString(R.string.rule_off)
            live == RuleState.ALLOWED -> getString(R.string.rule_on)
            live == RuleState.INACTIVE_DENY -> getString(R.string.rule_retained)
            cached != null -> getString(R.string.rule_history, getString(when (cached.state) {
                RuleState.BLOCKED -> R.string.cache_blocked
                RuleState.ALLOWED -> R.string.cache_allowed
                RuleState.INACTIVE_DENY -> R.string.cache_inactive
            }))
            else -> getString(R.string.rule_unknown)
        }
        row.state.text = model.error(pkg)?.let {
            getString(R.string.state_with_error, text, getString(R.string.rule_error, it))
        } ?: text
        row.state.setTextColor(when {
            live == RuleState.BLOCKED -> ui.blockedText
            live == RuleState.INACTIVE_DENY -> ui.primary
            model.error(pkg) != null -> ui.warningText
            else -> ui.secondary
        })
        row.container.setBackgroundColor(if (live == RuleState.BLOCKED) ui.blockedBackground else Color.TRANSPARENT)
        val label = apps[pkg]?.label ?: pkg
        row.networkSwitch.contentDescription = getString(R.string.switch_accessibility, label, row.state.text)
        row.networkSwitch.stateDescription = if (live == null) getString(R.string.unknown_accessibility) else text
        row.details.setOnClickListener(null)
        row.details.isClickable = false; row.details.isFocusable = false
        row.details.contentDescription = null
        if (presentation.canClearRetained) {
            row.details.contentDescription = getString(R.string.retained_accessibility, label)
            row.details.isFocusable = true
            row.details.setOnClickListener {
                val svc = firewall ?: return@setOnClickListener
                AlertDialog.Builder(this).setTitle(R.string.clear_retained_title)
                    .setMessage(getString(R.string.clear_retained_body, pkg))
                    .setPositiveButton(R.string.restore) { _, _ -> applyToggle(svc, pkg, false) }
                    .setNegativeButton(R.string.cancel_action, null).show()
            }
        } else if (model.error(pkg) != null) {
            row.details.isFocusable = true
            row.details.setOnClickListener {
                AlertDialog.Builder(this).setTitle(R.string.read_failed_button)
                    .setMessage(model.error(pkg)).setPositiveButton(R.string.close_information, null).show()
            }
        }
        row.networkSwitch.setOnCheckedChangeListener { _, allow ->
            // No optimistic rule state. Restore the last confirmed visual until write + readback.
            bindState(row, pkg)
            requestSwitch(pkg, allow)
        }
    }

    /** Query the server, bypassing Shizuku API's positive permission cache. No rule command. */
    private fun checkPermission() {
        actualPermission = if (!Shizuku.pingBinder()) null else runCatching {
            IShizukuService.Stub.asInterface(Shizuku.getBinder()).checkSelfPermission()
        }.getOrNull()
    }

    private fun serviceAvailable() = actualPermission == true && Shizuku.getBinder()?.isBinderAlive == true &&
        firewall?.asBinder()?.isBinderAlive == true

    private fun maybeBind(requestPermission: Boolean = false) {
        if (destroyed || !foreground) return
        if (firewall != null && !serviceAvailable()) disconnected(getString(R.string.service_disconnected))
        if (activeConnection != null || firewall != null) return
        if (Process.myUid() / 100000 != 0) { header.setText(R.string.owner_only); return }
        try {
            checkPermission()
            if (!Shizuku.pingBinder()) { header.setText(R.string.start_shizuku); scheduleUiUpdate(immediate = true); return }
            if (actualPermission != true) {
                header.setText(R.string.request_permission)
                scheduleUiUpdate(immediate = true)
                if (requestPermission) Shizuku.requestPermission(100)
                return
            }
            header.setText(R.string.connecting)
            val connection = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                    if (destroyed || activeConnection !== this) return
                    if (binder == null || !binder.pingBinder()) {
                        disconnected(getString(R.string.binder_invalid)); return
                    }
                    bindTimeout?.let { mainHandler.removeCallbacks(it) }
                    checkPermission()
                    // Binder death can clear Shizuku's global reference on another thread.
                    val server = Shizuku.getBinder()
                    if (actualPermission != true || server == null || !server.isBinderAlive) {
                        disconnected(getString(R.string.permission_denied)); return
                    }
                    firewall = IFirewallService.Stub.asInterface(binder)
                    RuntimeRules.session.connected(server, binder)
                    header.setText(R.string.connected)
                    resumeVerification()
                }
                override fun onServiceDisconnected(name: ComponentName?) {
                    if (!destroyed && activeConnection === this) disconnected(getString(R.string.service_disconnected))
                }
            }
            activeConnection = connection
            scheduleUiUpdate(immediate = true)
            bindTimeout = Runnable {
                if (!destroyed && activeConnection === connection && firewall == null)
                    disconnected(getString(R.string.connection_timeout))
            }.also { mainHandler.postDelayed(it, 15000) }
            Shizuku.bindUserService(serviceArgs, connection)
        } catch (e: Exception) {
            disconnected(getString(R.string.connection_failed, e.message ?: getString(R.string.unknown_error)))
        }
    }

    private fun detachConnection() {
        bindTimeout?.let { mainHandler.removeCallbacks(it) }; bindTimeout = null
        val connection = activeConnection; activeConnection = null
        if (connection != null) runCatching { Shizuku.unbindUserService(serviceArgs, connection, false) }
    }

    private fun disconnected(reason: String) {
        if (destroyed) return
        cancelVerification()
        val abandoned = firewall
        firewall = null
        // A server killed externally cannot perform its usual non-daemon service cleanup.
        // Queue finite cleanup AFTER any accepted read/write, targeting this exact old Binder.
        if (abandoned != null) io.execute {
            runCatching { if (abandoned.asBinder().isBinderAlive) abandoned.destroy() }
        }
        RuntimeRules.session.invalidate()
        failedReads = 0
        detachConnection()
        // A write already accepted on the old Binder still owns the process-wide gate.
        model.invalidate()
        flushCache()
        header.text = reason
        scheduleUiUpdate(immediate = true)
    }

    private fun cancelVerification() {
        verification.cancel()
        readToken = verification.token
    }

    private fun resumeVerification() {
        if (destroyed || !foreground || pendingPackage != null || verification.running || !serviceAvailable()) return
        val full = RuntimeRules.session.takeFullRefresh()
        if (full) model.invalidate()
        val packages = model.unverified()
        if (packages.isEmpty()) {
            header.text = getString(if (model.counts().unknown == 0) R.string.snapshot_reused else R.string.snapshot_errors)
            scheduleUiUpdate(immediate = true)
            return
        }
        startVerification(packages)
    }

    private fun startVerification(packages: List<String>) {
        if (destroyed || !foreground || pendingPackage != null || verification.running || !serviceAvailable()) return
        val svc = firewall ?: return
        val token = verification.restart(packages)
        readToken = token
        failedReads = apps.keys.count { model.error(it) != null }
        checkedApps = apps.size - packages.size
        header.text = getString(R.string.checking, checkedApps, apps.size)
        scheduleUiUpdate(immediate = true)
        verifyNext(svc, token)
    }

    private fun verifyNext(svc: IFirewallService, token: Long) {
        if (destroyed || !foreground || firewall !== svc || token != readToken || pendingPackage != null) return
        val batch = verification.next(token)
        if (batch.isEmpty()) {
            header.text = if (failedReads == 0) getString(R.string.checked) else getString(R.string.check_failed, failedReads)
            flushCache()
            scheduleUiUpdate(immediate = true)
            return
        }
        io.execute {
            if (destroyed || token != readToken) return@execute
            val results = try {
                val response: Array<out String?>? = svc.getStates(batch.toTypedArray())
                if (response == null || response.size != batch.size) batch.map { getString(R.string.batch_invalid) }
                else response.map { it ?: getString(R.string.batch_invalid) }
            } catch (e: Exception) { batch.map { "ERROR:${e.message ?: getString(R.string.unknown_error)}" } }
            runOnUiThread {
                if (destroyed || !foreground || firewall !== svc || !verification.complete(token)) return@runOnUiThread
                val now = System.currentTimeMillis()
                batch.forEachIndexed { index, pkg ->
                    if (model.record(pkg, results[index], now)) markCacheDirty() else failedReads++
                }
                checkedApps += batch.size
                header.text = getString(R.string.checking, checkedApps, apps.size)
                scheduleUiUpdate()
                verifyNext(svc, token)
            }
        }
    }

    private fun markCacheDirty() {
        cacheDirty = true
        if (!cacheSaveQueued) {
            cacheSaveQueued = true
            mainHandler.postDelayed(saveCache, 300)
        }
    }

    private fun flushCache() {
        mainHandler.removeCallbacks(saveCache); cacheSaveQueued = false
        if (!cacheDirty || !::model.isInitialized) return
        runCatching { cacheStore.save(model.cacheSnapshot()) }.onSuccess { cacheDirty = false }.onFailure {
            if (!destroyed && foreground) Toast.makeText(this, R.string.cache_save_failed, Toast.LENGTH_LONG).show()
        }
    }

    private fun requestSwitch(pkg: String, allow: Boolean) {
        checkPermission()
        if (!serviceAvailable()) { disconnected(getString(R.string.service_disconnected)); return }
        val svc = firewall ?: return
        if (!foreground || pendingPackage != null || model.state(pkg) == null) return
        val blocked = !allow
        if (blocked || pkg.contains("clash", true) || pkg.contains("shizuku", true)) {
            AlertDialog.Builder(this).setTitle(R.string.confirm_title)
                .setMessage(getString(R.string.confirm_rule, pkg))
                .setPositiveButton(R.string.continue_action) { _, _ -> applyToggle(svc, pkg, blocked) }
                .setNegativeButton(R.string.cancel_action, null).show()
        } else applyToggle(svc, pkg, blocked)
    }

    private fun applyToggle(svc: IFirewallService, pkg: String, blocked: Boolean) {
        checkPermission()
        if (!serviceAvailable()) { disconnected(getString(R.string.service_disconnected)); return }
        if (destroyed || !foreground || svc !== firewall || pendingPackage != null || model.state(pkg) == null) return
        val affected = model.affectedByWrite(pkg, blocked, apps.values.filter {
            it.info.uid == apps[pkg]?.info?.uid
        }.map { it.pkg })
        val generation = RuntimeRules.session.generation
        pendingPackage = pkg
        cancelVerification() // An in-flight read finishes first on the same I/O executor.
        model.invalidate(affected) // Keep every unrelated verified UID in the running snapshot.
        header.setText(R.string.operation_pending)
        scheduleUiUpdate(immediate = true)
        io.execute {
            val result = try { svc.setBlocked(pkg, blocked) } catch (e: Exception) { "ERROR:${e.message}" }
            runOnUiThread {
                pendingPackage = null
                // Finish even after Activity recreation. Never publish across a disconnected session.
                if (RuntimeRules.model === model && generation == RuntimeRules.session.generation) {
                    val success = model.record(pkg, result, System.currentTimeMillis())
                    if (success) {
                        cacheDirty = true
                        flushCache()
                    } else {
                        // A failed write may have partially changed the target; explicitly read it back.
                        model.invalidate(listOf(pkg))
                        if (!destroyed && foreground) Toast.makeText(this, result, Toast.LENGTH_LONG).show()
                    }
                }
                RuntimeRules.onWriteFinished?.invoke()

            }
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()
}
