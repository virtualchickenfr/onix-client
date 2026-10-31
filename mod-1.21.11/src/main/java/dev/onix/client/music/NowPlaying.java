package dev.onix.client.music;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.onix.client.OnixClient;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;

/**
 * What the PC is playing right now (Spotify first, otherwise any media player), read from the operating system:
 * Windows media controls (through a small PowerShell loop), playerctl on Linux, AppleScript on macOS.
 * No Spotify account or login is needed. Polling runs on a daemon thread; the game reads the last answer.
 */
public final class NowPlaying {
    /** A song with its position at the moment it was read. */
    public record Track(String app, String title, String artist, String album, long durationMs, long positionMs, long readAt, boolean playing) {
        public String key() {
            return (artist + " – " + title).toLowerCase(Locale.ROOT);
        }

        /** Playback position now, counting on from the last reading while playing. */
        public long positionNow() {
            long p = positionMs + (playing ? System.currentTimeMillis() - readAt : 0);
            return durationMs > 0 ? Math.min(p, durationMs) : p;
        }

        public boolean isSpotify() {
            return app != null && app.toLowerCase(Locale.ROOT).contains("spotify");
        }
    }

    private static volatile Track current;
    private static volatile boolean onlySpotify = true;
    private static Thread worker;
    private static volatile boolean running;
    private static volatile Process process;

    private NowPlaying() {}

    public static Track current() {
        Track t = current;
        if (t == null) return null;
        if (System.currentTimeMillis() - t.readAt() > 15_000 && !t.playing()) return null;
        return t;
    }

    /** Starts reading (idempotent). */
    public static synchronized void start(boolean spotifyOnly) {
        onlySpotify = spotifyOnly;
        if (running) return;
        running = true;
        worker = new Thread(NowPlaying::loop, "Onix Now Playing");
        worker.setDaemon(true);
        worker.start();
    }

    public static synchronized void stop() {
        running = false;
        Process p = process;
        if (p != null) p.destroy();
        if (worker != null) worker.interrupt();
        worker = null;
        current = null;
    }

    private static void loop() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        int failures = 0;
        while (running) {
            try {
                if (os.contains("win")) windows();
                else if (os.contains("mac")) pollMac();
                else pollLinux();
                failures = 0;
            } catch (InterruptedException e) {
                return;
            } catch (Exception e) {
                if (++failures == 1) OnixClient.LOGGER.warn("Now playing: {}", e.toString());
            }
            try {
                Thread.sleep(Math.min(30_000, 1000L * (1 + failures * 2)));
            } catch (InterruptedException e) {
                return;
            }
        }
    }

    // ---------------------------------------------------------------- Windows: media controls through PowerShell

    private static final String WINDOWS_SCRIPT = """
            $ErrorActionPreference = 'Stop'
            Add-Type -AssemblyName System.Runtime.WindowsRuntime
            $asTask = ([System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object { $_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1' })[0]
            function Await($op, $type) { $t = $asTask.MakeGenericMethod($type).Invoke($null, @($op)); $t.Wait(-1) | Out-Null; $t.Result }
            [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager,Windows.Media.Control,ContentType=WindowsRuntime] | Out-Null
            $mgr = Await ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]::RequestAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager])
            while ($true) {
              try {
                $s = $null
                foreach ($x in $mgr.GetSessions()) { if ($x.SourceAppUserModelId -like '*Spotify*') { $s = $x } }
                if ($s -eq $null) { $s = $mgr.GetCurrentSession() }
                if ($s -ne $null) {
                  $p = Await ($s.TryGetMediaPropertiesAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties])
                  $t = $s.GetTimelineProperties()
                  $playing = $s.GetPlaybackInfo().PlaybackStatus -eq 'Playing'
                  $o = [ordered]@{ app = $s.SourceAppUserModelId; title = $p.Title; artist = $p.Artist; album = $p.AlbumTitle;
                    dur = [long]$t.EndTime.TotalMilliseconds; pos = [long]$t.Position.TotalMilliseconds;
                    at = $t.LastUpdatedTime.ToUnixTimeMilliseconds(); now = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds(); playing = $playing }
                  [Console]::Out.WriteLine(($o | ConvertTo-Json -Compress))
                } else { [Console]::Out.WriteLine('{}') }
                [Console]::Out.Flush()
              } catch { [Console]::Out.WriteLine('{}'); [Console]::Out.Flush() }
              Start-Sleep -Milliseconds 700
            }
            """;

    private static void windows() throws Exception {
        String encoded = Base64.getEncoder().encodeToString(WINDOWS_SCRIPT.getBytes(StandardCharsets.UTF_16LE));
        ProcessBuilder pb = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-EncodedCommand", encoded);
        pb.redirectErrorStream(true);
        Process p = pb.start();
        process = p;
        try (BufferedReader in = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while (running && (line = in.readLine()) != null) {
                line = line.trim();
                if (!line.startsWith("{")) continue;
                JsonObject o = JsonParser.parseString(line).getAsJsonObject();
                if (!o.has("title")) {
                    current = null;
                    continue;
                }
                long now = System.currentTimeMillis();
                boolean playing = o.has("playing") && o.get("playing").getAsBoolean();
                long pos = o.get("pos").getAsLong();
                // the media controls report the position at "at"; Spotify only updates it on play, pause and seek
                long at = o.has("at") ? o.get("at").getAsLong() : now;
                long psNow = o.has("now") ? o.get("now").getAsLong() : now;
                if (playing && at > 0 && psNow >= at) pos += psNow - at;
                set(new Track(str(o, "app"), str(o, "title"), str(o, "artist"), str(o, "album"), o.get("dur").getAsLong(), pos, now, playing));
            }
        } finally {
            p.destroy();
            process = null;
        }
        throw new IllegalStateException("PowerShell media reader stopped");
    }

    // ---------------------------------------------------------------- Linux: playerctl

    private static void pollLinux() throws Exception {
        while (running) {
            String player = onlySpotify ? "spotify" : "%any";
            String out = run("playerctl", "-p", player, "metadata", "--format",
                    "{{status}}\t{{title}}\t{{artist}}\t{{album}}\t{{mpris:length}}\t{{position}}\t{{playerName}}");
            if (out == null || out.isBlank()) {
                current = null;
            } else {
                String[] f = out.trim().split("\t", -1);
                if (f.length >= 7) {
                    set(new Track(f[6], f[1], f[2], f[3], parse(f[4]) / 1000, parse(f[5]) / 1000, System.currentTimeMillis(), f[0].equalsIgnoreCase("Playing")));
                }
            }
            Thread.sleep(1000);
        }
    }

    // ---------------------------------------------------------------- macOS: Spotify through AppleScript

    private static void pollMac() throws Exception {
        String script = "if application \"Spotify\" is running then tell application \"Spotify\" to return (player state as string) & \"\\t\" & "
                + "(name of current track) & \"\\t\" & (artist of current track) & \"\\t\" & (album of current track) & \"\\t\" & "
                + "(duration of current track) & \"\\t\" & ((player position * 1000) as integer)";
        while (running) {
            String out = run("osascript", "-e", script);
            if (out == null || out.isBlank()) {
                current = null;
            } else {
                String[] f = out.trim().split("\t", -1);
                if (f.length >= 6) set(new Track("Spotify", f[1], f[2], f[3], parse(f[4]), parse(f[5]), System.currentTimeMillis(), f[0].equalsIgnoreCase("playing")));
            }
            Thread.sleep(1000);
        }
    }

    // ---------------------------------------------------------------- helpers

    private static void set(Track t) {
        if (onlySpotify && !t.isSpotify() && !"Spotify".equals(t.app())) {
            current = null;
            return;
        }
        if (t.title() == null || t.title().isBlank()) {
            current = null;
            return;
        }
        current = t;
    }

    private static String str(JsonObject o, String k) {
        return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : "";
    }

    private static long parse(String s) {
        try {
            return (long) Double.parseDouble(s.trim().replace(',', '.'));
        } catch (Exception e) {
            return 0;
        }
    }

    private static String run(String... cmd) throws Exception {
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        return p.waitFor() == 0 ? out : null;
    }
}
