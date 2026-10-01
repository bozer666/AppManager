package com.bozer666.appmanager

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.tabs.TabLayout

data class AppEntry(
    val label: String,
    val packageName: String,
    val isSystem: Boolean,
    val icon: Drawable?
)

class MainActivity : AppCompatActivity() {

    private val allApps = mutableListOf<AppEntry>()
    private val shownApps = mutableListOf<AppEntry>()
    private lateinit var adapter: AppAdapter

    // 0 全部, 1 第三方, 2 系统
    private var tabMode = 0
    private var query = ""

    // 各枚举来源返回数量（诊断用），-1 表示未完成
    private var countA = -1
    private var countB = -1
    private var countC = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val recycler = findViewById<RecyclerView>(R.id.recycler)
        recycler.layoutManager = LinearLayoutManager(this)
        adapter = AppAdapter(shownApps) { entry -> showActions(entry) }
        recycler.adapter = adapter

        findViewById<TabLayout>(R.id.tabs).addOnTabSelectedListener(
            object : TabLayout.OnTabSelectedListener {
                override fun onTabSelected(tab: TabLayout.Tab) {
                    tabMode = tab.position
                    applyFilter()
                }
                override fun onTabUnselected(tab: TabLayout.Tab) {}
                override fun onTabReselected(tab: TabLayout.Tab) {}
            }
        )

        findViewById<SearchView>(R.id.search).setOnQueryTextListener(
            object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(q: String?) = false
                override fun onQueryTextChange(newText: String?): Boolean {
                    query = newText.orEmpty().trim()
                    applyFilter()
                    return true
                }
            }
        )

        findViewById<View>(R.id.btn_unknown_sources).setOnClickListener {
            safeStart(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES), "安装未知应用")
        }
        findViewById<View>(R.id.btn_all_apps).setOnClickListener {
            safeStart(Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS), "全部应用")
        }
        findViewById<View>(R.id.btn_system_settings).setOnClickListener {
            safeStart(Intent(Settings.ACTION_SETTINGS), "系统设置")
        }

        // 手动输入包名直达（枚举被限制时的兜底）
        val pkgInput = findViewById<EditText>(R.id.pkg_input)
        findViewById<Button>(R.id.btn_go_pkg).setOnClickListener {
            val pkg = pkgInput.text.toString().trim()
            if (pkg.isEmpty()) {
                toast("先输入包名")
                return@setOnClickListener
            }
            showActions(AppEntry(pkg, pkg, false, null))
        }
        findViewById<Button>(R.id.btn_diag).setOnClickListener { showDiag() }

        loadApps()
    }

    private fun toEntry(pm: PackageManager, info: ApplicationInfo): AppEntry {
        return AppEntry(
            label = try { info.loadLabel(pm).toString() } catch (_: Exception) { info.packageName },
            packageName = info.packageName,
            isSystem = (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
            icon = try { info.loadIcon(pm) } catch (_: Exception) { null }
        )
    }

    private fun loadApps() {
        val loading = findViewById<ProgressBar>(R.id.loading)
        val countView = findViewById<TextView>(R.id.count)
        Thread {
            val pm = packageManager
            val map = LinkedHashMap<String, AppEntry>()

            // 来源 A：getInstalledApplications
            try {
                val listA = pm.getInstalledApplications(PackageManager.GET_META_DATA)
                countA = listA.size
                listA.forEach { map[it.packageName] = toEntry(pm, it) }
            } catch (_: Exception) { countA = -2 }

            // 来源 B：getInstalledPackages
            try {
                val listB = pm.getInstalledPackages(PackageManager.GET_META_DATA)
                countB = listB.size
                listB.forEach { pkg ->
                    val ai = pkg.applicationInfo ?: return@forEach
                    if (!map.containsKey(pkg.packageName)) map[pkg.packageName] = toEntry(pm, ai)
                }
            } catch (_: Exception) { countB = -2 }

            // 来源 C：launcher 可见应用
            try {
                val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                val listC = pm.queryIntentActivities(launcher, 0)
                countC = listC.size
                listC.forEach { ri ->
                    val pkg = ri.activityInfo.packageName
                    if (!map.containsKey(pkg)) map[pkg] = toEntry(pm, ri.activityInfo.applicationInfo)
                }
            } catch (_: Exception) { countC = -2 }

            val list = map.values.sortedWith(compareBy({ it.isSystem }, { it.label.lowercase() }))
            val ver = try {
                packageManager.getPackageInfo(packageName, 0).versionName
            } catch (_: Exception) { "" }
            runOnUiThread {
                allApps.clear()
                allApps.addAll(list)
                loading.visibility = View.GONE
                countView.text = "v$ver · 共 ${list.size} 个应用"
                applyFilter()
            }
        }.start()
    }

    private fun showDiag() {
        val msg = "getInstalledApplications: ${fmtCount(countA)}\n" +
            "getInstalledPackages: ${fmtCount(countB)}\n" +
            "launcher 查询: ${fmtCount(countC)}\n" +
            "去重合并后: ${allApps.size}"
        AlertDialog.Builder(this)
            .setTitle("枚举诊断")
            .setMessage(msg)
            .setPositiveButton("确定", null)
            .show()
    }

    private fun fmtCount(n: Int) = when (n) {
        -1 -> "未完成"
        -2 -> "异常"
        else -> n.toString()
    }

    private fun applyFilter() {
        val q = query.lowercase()
        shownApps.clear()
        shownApps.addAll(allApps.filter { e ->
            (tabMode == 0 || (tabMode == 1) != e.isSystem) &&
                (q.isEmpty() || e.label.lowercase().contains(q) || e.packageName.lowercase().contains(q))
        })
        adapter.notifyDataSetChanged()
    }

    private fun showActions(entry: AppEntry) {
        val items = arrayOf(
            "允许安装未知应用",
            "应用信息",
            "悬浮窗权限",
            "通知管理",
            "忽略电池优化",
            "打开应用",
            "卸载",
            "清除数据",
            "强行停止"
        )
        AlertDialog.Builder(this)
            .setTitle(entry.label)
            .setItems(items) { _, which ->
                when (which) {
                    0 -> safeStart(
                        Intent(
                            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            Uri.parse("package:${entry.packageName}")
                        ),
                        "安装未知应用"
                    )
                    1 -> openAppInfo(entry)
                    2 -> safeStart(
                        Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${entry.packageName}")
                        ),
                        "悬浮窗权限"
                    )
                    3 -> safeStart(
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, entry.packageName),
                        "通知管理"
                    )
                    4 -> safeStart(
                        Intent(
                            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                            Uri.parse("package:${entry.packageName}")
                        ),
                        "忽略电池优化"
                    )
                    5 -> {
                        val launch = packageManager.getLaunchIntentForPackage(entry.packageName)
                        if (launch != null) safeStart(launch, "打开应用")
                        else toast("这个应用没有可启动的界面")
                    }
                    6 -> safeStart(
                        Intent(Intent.ACTION_DELETE, Uri.parse("package:${entry.packageName}")),
                        "卸载"
                    )
                    7 -> {
                        toast("请在「存储」中清除数据")
                        openAppInfo(entry)
                    }
                    8 -> {
                        toast("请在应用信息页点「强行停止」")
                        openAppInfo(entry)
                    }
                }
            }
            .show()
    }

    private fun openAppInfo(entry: AppEntry) {
        safeStart(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:${entry.packageName}")
            ),
            "应用信息"
        )
    }

    private fun safeStart(intent: Intent, label: String) {
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            toast("打不开「${label}」，可能被车机屏蔽了")
        } catch (_: SecurityException) {
            toast("打不开「${label}」，可能被车机屏蔽了")
        }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    private class AppAdapter(
        private val items: List<AppEntry>,
        private val onClick: (AppEntry) -> Unit
    ) : RecyclerView.Adapter<AppAdapter.Holder>() {

        class Holder(v: View) : RecyclerView.ViewHolder(v) {
            val icon: ImageView = v.findViewById(R.id.app_icon)
            val name: TextView = v.findViewById(R.id.app_name)
            val pkg: TextView = v.findViewById(R.id.app_pkg)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_app, parent, false)
            return Holder(v)
        }

        override fun getItemCount() = items.size

        override fun onBindViewHolder(h: Holder, pos: Int) {
            val e = items[pos]
            h.name.text = e.label
            h.pkg.text = if (e.isSystem) "${e.packageName} · 系统" else e.packageName
            if (e.icon != null) h.icon.setImageDrawable(e.icon) else h.icon.setImageDrawable(null)
            h.itemView.setOnClickListener { onClick(e) }
        }
    }
}
