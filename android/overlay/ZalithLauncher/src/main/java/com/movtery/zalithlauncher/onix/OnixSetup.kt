package com.movtery.zalithlauncher.onix

import com.movtery.zalithlauncher.feature.log.Logging
import com.movtery.zalithlauncher.setting.AllSettings
import com.movtery.zalithlauncher.feature.version.Version
import com.movtery.zalithlauncher.feature.version.VersionConfig
import com.movtery.zalithlauncher.feature.version.VersionInfo
import com.movtery.zalithlauncher.feature.version.VersionsManager
import net.kdt.pojavlaunch.Tools
import net.kdt.pojavlaunch.utils.DownloadUtils
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URLEncoder

/**
 * Onix on Android:
 * - the first start creates the game version "Onix" (Minecraft [MC] with the newest stable Fabric Loader, in its own folder)
 * - before a Fabric version starts, its mods folder gets the newest Onix mod for that Minecraft version from the
 *   GitHub releases, Fabric API and the FPS mods (Sodium, Lithium, FerriteCore, EntityCulling) from Modrinth
 * - the new version starts with phone-friendly video settings and a 70 % render resolution (both can be changed)
 * Nothing here stops the game from starting: without network or on errors it starts with what is there.
 */
object OnixSetup {
    const val VERSION_NAME = "Onix"
    const val MC = "1.21.11"
    const val REPO = "virtualchickenfr/onix-client"
    private const val FABRIC_META = "https://meta.fabricmc.net/v2/versions/loader"
    private const val FABRIC_API = "P7dR8mSH"

    /** Modrinth project, file name start: the mods that give phones the most frames. */
    private val FPS_MODS = listOf(
        "AANobbMI" to "sodium-",          // much faster rendering
        "gvQqBUqZ" to "lithium-",         // faster game logic
        "uXXizFIs" to "ferritecore-",     // less memory
        "NNAgCjsB" to "entityculling-",   // skips entities behind walls
    )

    /** Video settings for phones: short view distance, fast graphics, no clouds or smooth lighting. */
    private val PHONE_OPTIONS = listOf(
        "renderDistance:6", "simulationDistance:5", "graphicsMode:0", "ao:false", "renderClouds:\"false\"",
        "particles:2", "biomeBlendRadius:0", "mipmapLevels:0", "entityShadows:false", "maxFps:60", "enableVsync:false",
        "bobView:false",
    )

    /** Creates the "Onix" version in the background when it is missing. */
    @JvmStatic
    fun ensureVersion() {
        Thread {
            runCatching {
                if (createVersionIfMissing()) VersionsManager.refresh("OnixSetup")
            }.onFailure { e -> Logging.e("Onix Setup", Tools.printToString(e)) }
        }.start()
    }

    private fun createVersionIfMissing(): Boolean {
        if (File(VersionsManager.getVersionPath(VERSION_NAME), "$VERSION_NAME.json").exists()) return false
        createFabricVersion(VERSION_NAME, MC)
        return true
    }

    /** A game version [name] with Minecraft [mc] and the newest stable Fabric Loader, in its own folder. */
    @JvmStatic
    fun createFabricVersion(name: String, mc: String) {
        if (!name.matches(Regex("[A-Za-z0-9 ._-]{1,40}"))) throw IllegalArgumentException("Use letters, numbers, spaces, . _ - for the name")
        val folder = VersionsManager.getVersionPath(name)
        val json = File(folder, "$name.json")
        if (json.exists()) throw IllegalArgumentException("A profile called $name already exists")
        val loaders = JSONArray(DownloadUtils.downloadString("$FABRIC_META/${enc(mc)}"))
        if (loaders.length() == 0) throw IllegalStateException("Fabric has no loader for $mc")
        var loader = loaders.getJSONObject(0).getJSONObject("loader").getString("version")
        for (i in 0 until loaders.length()) {
            val l = loaders.getJSONObject(i).getJSONObject("loader")
            if (l.optBoolean("stable", false)) {
                loader = l.getString("version")
                break
            }
        }
        val profile = JSONObject(DownloadUtils.downloadString("$FABRIC_META/${enc(mc)}/${enc(loader)}/profile/json"))
        profile.put("id", name)
        folder.mkdirs()
        json.writeText(profile.toString(2))
        VersionInfo(mc, arrayOf(VersionInfo.LoaderInfo("Fabric", loader))).save(folder)
        // its own game folder, so the Onix mods never mix with other versions
        VersionConfig(folder, VersionConfig.IsolationType.ENABLE).save()
        // FPS first: phone video settings for the new game folder and a lower render resolution
        val options = File(folder, "options.txt")
        if (!options.exists()) options.writeText(PHONE_OPTIONS.joinToString("\n", postfix = "\n"))
        if (AllSettings.resolutionRatio.getValue() == 100) AllSettings.resolutionRatio.put(70).save()
        Logging.i("Onix Setup", "Created the version $name (Minecraft $mc, Fabric $loader)")
    }

    private const val GL4ES = "8b52d82d-8f6d-4d3a-a767-dc93f8b72fc7"
    private const val VULKAN_ZINK = "0fa435e2-46df-45c9-906c-b29606aaef00"

    /**
     * Minecraft 1.17+ needs OpenGL 3.2, which GL4ES (the app's default renderer) cannot give: such a version would not
     * start. While it still uses GL4ES it is switched to Vulkan Zink when the phone supports it (changeable in its settings).
     */
    @JvmStatic
    fun preferModernRenderer(context: android.content.Context, version: Version) {
        runCatching {
            val info = version.getVersionInfo() ?: return
            val parts = info.minecraftVersion.split(".").mapNotNull { it.toIntOrNull() }
            if (parts.size < 2 || (parts[0] == 1 && parts[1] < 17)) return
            val config = version.getVersionConfig()
            val current = config.getRenderer().ifEmpty { AllSettings.renderer.getValue() }
            if (current != GL4ES && current != "opengles2" && current.isNotEmpty()) return
            val zink = com.movtery.zalithlauncher.renderer.Renderers.getCompatibleRenderers(context).second
                .find { it.getUniqueIdentifier() == VULKAN_ZINK } ?: return
            config.setRenderer(zink.getUniqueIdentifier())
            config.save()
            Logging.i("Onix Setup", "${version.getVersionName()}: renderer set to ${zink.getRendererName()} (Minecraft ${info.minecraftVersion} needs OpenGL 3.2)")
        }.onFailure { e -> Logging.e("Onix Setup", "Renderer: " + Tools.printToString(e)) }
    }

    /** Before a launch (call it off the UI thread): newest Onix mod and Fabric API for Fabric versions. */
    @JvmStatic
    fun syncMods(version: Version) {
        val info = version.getVersionInfo() ?: return
        if (info.loaderInfo?.any { it.name == "Fabric" } != true) return
        val mc = info.minecraftVersion
        val mods = File(version.getGameDir(), "mods")
        mods.mkdirs()
        runCatching { syncOnix(mods, mc) }.onFailure { e -> Logging.e("Onix Setup", "Onix mod: " + Tools.printToString(e)) }
        runCatching { syncModrinth(mods, mc, FABRIC_API, "fabric-api-") }.onFailure { e -> Logging.e("Onix Setup", "Fabric API: " + Tools.printToString(e)) }
        for ((project, prefix) in FPS_MODS) {
            runCatching { syncModrinth(mods, mc, project, prefix) }.onFailure { e -> Logging.e("Onix Setup", "$prefix: " + Tools.printToString(e)) }
        }
    }

    private fun isOnixJar(name: String) = name.startsWith("onix-client-") && name.endsWith(".jar") && !name.contains("-sources")

    private fun syncOnix(mods: File, mc: String) {
        val release = JSONObject(DownloadUtils.downloadString("https://api.github.com/repos/$REPO/releases/latest"))
        val assets = release.getJSONArray("assets")
        for (i in 0 until assets.length()) {
            val asset = assets.getJSONObject(i)
            val name = asset.getString("name")
            // onix-client-<version>+<minecraft>.jar (GitHub may show the + as a dot)
            if (!isOnixJar(name) || !(name.endsWith("+$mc.jar") || name.endsWith(".$mc.jar"))) continue
            val target = File(mods, name)
            if (target.isFile && target.length() == asset.getLong("size")) return
            download(asset.getString("browser_download_url"), mods, name) { isOnixJar(it.name) }
            Logging.i("Onix Setup", "Installed $name")
            return
        }
        Logging.i("Onix Setup", "No Onix mod for Minecraft $mc in the latest release")
    }

    /** The newest release of a Modrinth project for this Minecraft version; a copy the player switched off stays off. */
    private fun syncModrinth(mods: File, mc: String, project: String, prefix: String) {
        if (mods.listFiles()?.any { it.name.startsWith(prefix) && it.name.endsWith(".jar.disabled") } == true) return
        val url = "https://api.modrinth.com/v2/project/$project/version?loaders=${enc("[\"fabric\"]")}&game_versions=${enc("[\"$mc\"]")}"
        val versions = JSONArray(DownloadUtils.downloadString(url))
        if (versions.length() == 0) return
        var version = versions.getJSONObject(0)
        for (i in 0 until versions.length()) {
            if (versions.getJSONObject(i).optString("version_type") == "release") {
                version = versions.getJSONObject(i)
                break
            }
        }
        val files = version.getJSONArray("files")
        var file = files.getJSONObject(0)
        for (i in 0 until files.length()) if (files.getJSONObject(i).optBoolean("primary")) file = files.getJSONObject(i)
        val name = File(file.getString("filename")).name
        if (File(mods, name).isFile) return
        download(file.getString("url"), mods, name) { it.name.startsWith(prefix) && it.name.endsWith(".jar") }
        Logging.i("Onix Setup", "Installed $name")
    }

    /** Downloads into a temporary file first, then replaces the old copies (matched by [old]) with it. */
    private fun download(url: String, mods: File, name: String, old: (File) -> Boolean) {
        val part = File(mods, "$name.part")
        DownloadUtils.downloadFile(url, part)
        mods.listFiles()?.forEach { if (it.isFile && old(it)) it.delete() }
        if (!part.renameTo(File(mods, name))) throw IllegalStateException("Could not save $name")
    }

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")
}
