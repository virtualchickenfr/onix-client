package com.movtery.zalithlauncher.onix

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.fragment.app.Fragment
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.event.single.LaunchGameEvent
import com.movtery.zalithlauncher.feature.accounts.AccountsManager
import com.movtery.zalithlauncher.feature.log.Logging
import com.movtery.zalithlauncher.feature.version.VersionsManager
import com.movtery.zalithlauncher.task.TaskExecutors
import com.movtery.zalithlauncher.ui.fragment.AccountFragment
import com.movtery.zalithlauncher.ui.fragment.ControlButtonFragment
import com.movtery.zalithlauncher.ui.fragment.SettingsFragment
import com.movtery.zalithlauncher.utils.ZHTools
import com.movtery.zalithlauncher.utils.path.PathManager
import net.kdt.pojavlaunch.Tools
import net.kdt.pojavlaunch.utils.DownloadUtils
import org.greenrobot.eventbus.EventBus
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors

/**
 * The home screen of Onix for Android: the same interface as the Onix PC launcher (launcher/src, copied into
 * assets/onix-ui by android/apply.py) in a WebView. assets/onix-ui/android-api.js implements the launcher's `api`
 * on top of [Bridge]: accounts, game versions (= profiles), launching, files of a version and downloads.
 */
class OnixWebFragment : Fragment() {
    private var web: WebView? = null
    private val worker = Executors.newFixedThreadPool(3)

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = WebView(requireContext())
        view.setBackgroundColor(0)
        view.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            @Suppress("DEPRECATION")
            allowFileAccessFromFileURLs = true
            @Suppress("DEPRECATION")
            allowUniversalAccessFromFileURLs = true
            useWideViewPort = true
            loadWithOverviewMode = true
            mediaPlaybackRequiresUserGesture = false
            textZoom = 100
        }
        view.webChromeClient = WebChromeClient()
        view.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(v: WebView, request: WebResourceRequest): Boolean {
                val url = request.url
                if (url.scheme == "file") return false
                startActivity(Intent(Intent.ACTION_VIEW, url))
                return true
            }
        }
        view.addJavascriptInterface(Bridge(), "OnixAndroid")
        view.loadUrl("file:///android_asset/onix-ui/index.html")
        web = view
        return view
    }

    /** The PC interface has its own title bar and navigation: the app's bar only shows on its other pages. */
    private fun appBar(show: Boolean) {
        val v = if (show) View.VISIBLE else View.GONE
        activity?.findViewById<View>(R.id.top_layout)?.visibility = v
        activity?.findViewById<View>(R.id.shadowView)?.visibility = v
    }

    override fun onResume() {
        super.onResume()
        if (!isHidden) appBar(false)
        emit("android:resume", JSONObject())
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        appBar(hidden)
    }

    override fun onDestroyView() {
        appBar(true)
        web?.destroy()
        web = null
        super.onDestroyView()
    }

    private fun emit(channel: String, value: Any?) {
        val json = JSONObject.quote(JSONObject().put("v", value ?: JSONObject.NULL).toString())
        TaskExecutors.runInUIThread {
            web?.evaluateJavascript("window.__onixEmit && window.__onixEmit(${JSONObject.quote(channel)}, JSON.parse($json).v)", null)
        }
    }

    private fun versionDir(name: String): File {
        val v = VersionsManager.getVersions().find { it.getVersionName() == name }
            ?: throw IllegalArgumentException("Unknown profile $name")
        return v.getGameDir()
    }

    /** A path inside the game folder of a version; nothing outside it can be reached. */
    private fun resolve(profile: String, rel: String): File {
        val root = versionDir(profile).canonicalFile
        val f = File(root, rel).canonicalFile
        if (f != root && !f.path.startsWith(root.path + File.separator)) throw SecurityException("Outside the profile: $rel")
        return f
    }

    private fun ui(block: () -> Unit) = TaskExecutors.runInUIThread { if (isAdded) block() }

    inner class Bridge {
        /** Every call is asynchronous: the result goes back through window.__onixDone(id, ok, json). */
        @JavascriptInterface
        fun call(id: String, method: String, argsJson: String) {
            worker.execute {
                var ok = true
                val result: Any? = try {
                    handle(method, JSONArray(argsJson))
                } catch (e: Throwable) {
                    ok = false
                    Logging.e("Onix UI", "$method: " + Tools.printToString(e))
                    e.message ?: e.toString()
                }
                val json = JSONObject.quote(JSONObject().put("v", result ?: JSONObject.NULL).toString())
                TaskExecutors.runInUIThread {
                    web?.evaluateJavascript("window.__onixDone(${JSONObject.quote(id)}, $ok, JSON.parse($json).v)", null)
                }
            }
        }

        private fun handle(method: String, a: JSONArray): Any? = when (method) {
            "info" -> JSONObject()
                .put("totalMemMb", Runtime.getRuntime().maxMemory() / 1048576)
                .put("deviceMemMb", deviceMemoryMb())
                .put("versionName", requireContext().packageManager.getPackageInfo(requireContext().packageName, 0).versionName)

            // ---------------------------------------------------------------- accounts
            "accounts" -> {
                val current = AccountsManager.currentAccount?.uniqueUUID
                JSONArray().apply {
                    AccountsManager.allAccounts.forEach {
                        put(JSONObject().put("uuid", it.uniqueUUID).put("name", it.username)
                            .put("active", it.uniqueUUID == current).put("type", it.accountType ?: "")
                            .put("profileId", it.profileId ?: ""))
                    }
                }
            }
            "switchAccount" -> {
                val acc = AccountsManager.allAccounts.find { it.uniqueUUID == a.getString(0) } ?: throw IllegalArgumentException("Unknown account")
                AccountsManager.currentAccount = acc
                true
            }
            "removeAccount" -> {
                File(PathManager.DIR_ACCOUNT_NEW, a.getString(0)).delete()
                AccountsManager.reload()
                true
            }

            // ---------------------------------------------------------------- versions (profiles)
            "versions" -> JSONArray().apply {
                val current = VersionsManager.getCurrentVersion()?.getVersionName()
                VersionsManager.getVersions().filter { it.isValid() }.forEach { v ->
                    val info = v.getVersionInfo()
                    put(JSONObject().put("name", v.getVersionName())
                        .put("mc", info?.minecraftVersion ?: v.getVersionName())
                        .put("loader", info?.loaderInfo?.firstOrNull()?.name?.lowercase() ?: "vanilla")
                        .put("loaderVersion", info?.loaderInfo?.firstOrNull()?.version ?: "")
                        .put("current", v.getVersionName() == current))
                }
            }
            "createVersion" -> {
                OnixSetup.createFabricVersion(a.getString(0), a.getString(1))
                VersionsManager.refresh("OnixUI")
                Thread.sleep(600)
                true
            }
            "deleteVersion" -> {
                val folder = VersionsManager.getVersionPath(a.getString(0))
                folder.deleteRecursively()
                VersionsManager.refresh("OnixUI")
                Thread.sleep(400)
                true
            }
            "selectVersion" -> {
                VersionsManager.saveCurrentVersion(a.getString(0))
                true
            }
            "launch" -> {
                VersionsManager.saveCurrentVersion(a.getString(0))
                ui { EventBus.getDefault().post(LaunchGameEvent()) }
                true
            }

            // ---------------------------------------------------------------- files of a version
            "list" -> JSONArray().apply {
                val dir = resolve(a.getString(0), a.getString(1))
                dir.listFiles()?.forEach {
                    put(JSONObject().put("name", it.name).put("dir", it.isDirectory)
                        .put("size", if (it.isFile) it.length() else 0).put("mtime", it.lastModified()))
                }
            }
            "read" -> resolve(a.getString(0), a.getString(1)).let { if (it.isFile) it.readText() else null }
            "readTail" -> resolve(a.getString(0), a.getString(1)).let { f ->
                val max = 512 * 1024L
                f.inputStream().use { s -> s.skip(maxOf(0, f.length() - max)); s.readBytes().toString(Charsets.UTF_8) }
            }
            "dataUrl" -> resolve(a.getString(0), a.getString(1)).let { f ->
                if (!f.isFile || f.length() > 8 * 1024 * 1024) null
                else "data:image/${if (f.name.endsWith(".png")) "png" else "jpeg"};base64," + Base64.encodeToString(f.readBytes(), Base64.NO_WRAP)
            }
            "write" -> resolve(a.getString(0), a.getString(1)).let { it.parentFile?.mkdirs(); it.writeText(a.getString(2)); true }
            "delete" -> resolve(a.getString(0), a.getString(1)).deleteRecursively()
            "rename" -> resolve(a.getString(0), a.getString(1)).renameTo(resolve(a.getString(0), a.getString(2)))
            "exists" -> resolve(a.getString(0), a.getString(1)).exists()
            "download" -> {
                val target = resolve(a.getString(0), a.getString(2))
                val label = a.optString(3, target.name)
                target.parentFile?.mkdirs()
                val part = File(target.path + ".part")
                emit("mods:progress", JSONObject().put("label", "Downloading $label").put("progress", 0))
                DownloadUtils.downloadFile(a.getString(1), part)
                if (target.exists()) target.delete()
                part.renameTo(target)
                emit("mods:progress", JSONObject().put("label", "Downloaded $label").put("progress", 1))
                target.length()
            }

            // ---------------------------------------------------------------- the rest of the app
            "open" -> {
                ui {
                    when (a.getString(0)) {
                        "accounts" -> ZHTools.swapFragmentWithAnim(this@OnixWebFragment, AccountFragment::class.java, AccountFragment.TAG, null)
                        "settings" -> ZHTools.swapFragmentWithAnim(this@OnixWebFragment, SettingsFragment::class.java, SettingsFragment.TAG, null)
                        "controls" -> ZHTools.swapFragmentWithAnim(this@OnixWebFragment, ControlButtonFragment::class.java, ControlButtonFragment.TAG, null)
                        else -> {}
                    }
                }
                true
            }
            "openUrl" -> { ui { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(a.getString(0)))) }; true }
            "prefsGet" -> requireContext().getSharedPreferences("onix_ui", 0).getString(a.getString(0), null)
            "prefsSet" -> { requireContext().getSharedPreferences("onix_ui", 0).edit().putString(a.getString(0), a.getString(1)).apply(); true }
            "checkUpdate" -> { ui { OnixUpdate.check(requireContext(), true) }; true }
            else -> throw IllegalArgumentException("Unknown call $method")
        }
    }

    private fun deviceMemoryMb(): Long {
        val am = requireContext().getSystemService(android.content.Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val info = android.app.ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        return info.totalMem / 1048576
    }
}
