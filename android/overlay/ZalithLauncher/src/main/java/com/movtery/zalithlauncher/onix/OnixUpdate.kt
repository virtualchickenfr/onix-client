package com.movtery.zalithlauncher.onix

import android.content.Context
import android.widget.Toast
import com.movtery.zalithlauncher.feature.log.Logging
import com.movtery.zalithlauncher.feature.update.UpdateUtils
import com.movtery.zalithlauncher.task.TaskExecutors
import com.movtery.zalithlauncher.ui.dialog.TipDialog
import com.movtery.zalithlauncher.utils.ZHTools
import net.kdt.pojavlaunch.Tools
import net.kdt.pojavlaunch.utils.DownloadUtils
import org.json.JSONObject

/**
 * App updates from the Onix GitHub releases: every release carries onix-android.json
 * ({ versionCode, versionName, apk: { all, arm64-v8a, … } }) next to the APKs.
 */
object OnixUpdate {
    private const val INFO_URL = "https://github.com/${OnixSetup.REPO}/releases/latest/download/onix-android.json"
    @Volatile
    private var running = false

    /** Looks for a newer app in the background; [verbose] also tells when there is none (settings button). */
    @JvmStatic
    fun check(context: Context, verbose: Boolean) {
        if (running) return
        running = true
        Thread {
            try {
                val json = JSONObject(DownloadUtils.downloadString(INFO_URL))
                val code = json.getInt("versionCode")
                val name = json.getString("versionName")
                if (code <= ZHTools.getVersionCode()) {
                    if (verbose) toast(context, "Onix is up to date ($name)")
                    return@Thread
                }
                val apks = json.getJSONObject("apk")
                val arch = UpdateUtils.getArchModel()
                val url = if (arch != null && apks.has(arch)) apks.getString(arch) else apks.getString("all")
                TaskExecutors.runInUIThread {
                    TipDialog.Builder(context)
                        .setTitle("Onix $name")
                        .setMessage("A new version of Onix is ready. Download and install it now?")
                        .setConfirmClickListener { download(context, url) }
                        .showDialog()
                }
            } catch (e: Exception) {
                Logging.e("Onix Update", Tools.printToString(e))
                if (verbose) toast(context, "Could not check for updates")
            } finally {
                running = false
            }
        }.start()
    }

    private fun download(context: Context, url: String) {
        toast(context, "Downloading the update…")
        Thread {
            try {
                val apk = UpdateUtils.sApkFile
                apk.parentFile?.mkdirs()
                DownloadUtils.downloadFile(url, apk)
                UpdateUtils.installApk(context, apk)
            } catch (e: Exception) {
                Logging.e("Onix Update", Tools.printToString(e))
                toast(context, "The update could not be downloaded")
            }
        }.start()
    }

    private fun toast(context: Context, text: String) {
        TaskExecutors.runInUIThread { Toast.makeText(context, text, Toast.LENGTH_SHORT).show() }
    }
}
