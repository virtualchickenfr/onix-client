package dev.onix.client.module.impl.utility;

import com.google.gson.JsonObject;
import dev.onix.client.OnixClient;
import dev.onix.client.module.Category;
import dev.onix.client.module.Module;
import dev.onix.client.setting.BoolSetting;
import dev.onix.client.setting.TextSetting;
import java.io.Closeable;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import net.minecraft.client.multiplayer.ServerData;

/**
 * Discord Rich Presence: your Discord profile shows that you play Onix, on which server or in singleplayer, and for how
 * long. Talks to the Discord app on this computer through its local IPC socket (no extra library, nothing online).
 * Needs a Discord application: create one at discord.com/developers and paste its Application ID.
 */
public class DiscordPresenceModule extends Module {
    private final TextSetting appId = add(new TextSetting("appId", "Application ID", "", 24)
            .describe("From discord.com/developers → New Application → Application ID. Upload an image called 'onix' there for the big picture."));
    private final BoolSetting showServer = add(new BoolSetting("server", "Show Server", true).describe("Shows the server address; off shows only 'Multiplayer'."));
    private final BoolSetting showName = add(new BoolSetting("name", "Show Player Name", false));

    private volatile Thread worker;
    private final long started = System.currentTimeMillis() / 1000;

    public DiscordPresenceModule() {
        super("discord", "Discord Status", "Shows on your Discord profile that you play Onix and where.", Category.UTILITY, false);
        tags("discord", "rich presence", "status", "rpc");
    }

    @Override
    protected void onEnable() {
        if (worker != null) return;
        worker = new Thread(this::run, "Onix Discord");
        worker.setDaemon(true);
        worker.start();
    }

    @Override
    protected void onDisable() {
        Thread w = worker;
        worker = null;
        if (w != null) w.interrupt();
    }

    private void run() {
        while (worker == Thread.currentThread()) {
            String id = appId.get().trim();
            if (!id.matches("\\d{15,22}")) {
                sleep(5000);
                continue;
            }
            try (Ipc ipc = Ipc.open()) {
                if (ipc == null) {
                    sleep(15000); // Discord is not running
                    continue;
                }
                JsonObject hello = new JsonObject();
                hello.addProperty("v", 1);
                hello.addProperty("client_id", id);
                ipc.send(0, hello.toString());
                ipc.read();
                String last = "";
                while (worker == Thread.currentThread()) {
                    String activity = activity();
                    if (!activity.equals(last)) {
                        JsonObject args = new JsonObject();
                        args.addProperty("pid", ProcessHandle.current().pid());
                        args.add("activity", com.google.gson.JsonParser.parseString(activity));
                        JsonObject cmd = new JsonObject();
                        cmd.addProperty("cmd", "SET_ACTIVITY");
                        cmd.add("args", args);
                        cmd.addProperty("nonce", UUID.randomUUID().toString());
                        ipc.send(1, cmd.toString());
                        ipc.read();
                        last = activity;
                    }
                    sleep(10000);
                }
            } catch (IOException | RuntimeException e) {
                OnixClient.LOGGER.debug("Discord presence: {}", e.toString());
                sleep(15000);
            }
        }
    }

    /** What the profile shows right now. */
    private String activity() {
        var mc = mc();
        String state;
        ServerData server = mc.getCurrentServer();
        if (mc.level == null) state = "In the menu";
        else if (mc.hasSingleplayerServer()) state = "Singleplayer";
        else if (server != null && showServer.isOn()) state = "On " + server.ip;
        else state = "Multiplayer";
        JsonObject a = new JsonObject();
        a.addProperty("details", showName.isOn() && mc.getUser() != null ? "Playing as " + mc.getUser().getName() : "Playing Minecraft with Onix");
        a.addProperty("state", state);
        JsonObject ts = new JsonObject();
        ts.addProperty("start", started);
        a.add("timestamps", ts);
        JsonObject assets = new JsonObject();
        assets.addProperty("large_image", "onix");
        assets.addProperty("large_text", "Onix Client " + OnixClient.VERSION);
        a.add("assets", assets);
        return a.toString();
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Discord's local IPC: a named pipe on Windows, a Unix socket elsewhere; frames are [opcode][length][json] little endian. */
    private abstract static class Ipc implements Closeable {
        abstract void write(byte[] data) throws IOException;

        abstract int readFully(byte[] into) throws IOException;

        void send(int op, String json) throws IOException {
            byte[] body = json.getBytes(StandardCharsets.UTF_8);
            ByteBuffer b = ByteBuffer.allocate(8 + body.length).order(ByteOrder.LITTLE_ENDIAN);
            b.putInt(op).putInt(body.length).put(body);
            write(b.array());
        }

        String read() throws IOException {
            byte[] head = new byte[8];
            readFully(head);
            int len = ByteBuffer.wrap(head).order(ByteOrder.LITTLE_ENDIAN).getInt(4);
            if (len < 0 || len > 1 << 20) throw new IOException("Bad Discord frame");
            byte[] body = new byte[len];
            readFully(body);
            return new String(body, StandardCharsets.UTF_8);
        }

        static Ipc open() {
            for (int i = 0; i < 10; i++) {
                try {
                    if (System.getProperty("os.name", "").toLowerCase().contains("win")) {
                        RandomAccessFile pipe = new RandomAccessFile("\\\\.\\pipe\\discord-ipc-" + i, "rw");
                        return new Ipc() {
                            void write(byte[] d) throws IOException { pipe.write(d); }
                            int readFully(byte[] d) throws IOException { pipe.readFully(d); return d.length; }
                            public void close() throws IOException { pipe.close(); }
                        };
                    }
                    for (String dir : new String[]{System.getenv("XDG_RUNTIME_DIR"), System.getenv("TMPDIR"), "/tmp"}) {
                        if (dir == null) continue;
                        Path p = Path.of(dir, "discord-ipc-" + i);
                        if (!Files.exists(p)) continue;
                        SocketChannel ch = SocketChannel.open(StandardProtocolFamily.UNIX);
                        ch.connect(UnixDomainSocketAddress.of(p));
                        return new Ipc() {
                            void write(byte[] d) throws IOException { ch.write(ByteBuffer.wrap(d)); }
                            int readFully(byte[] d) throws IOException {
                                ByteBuffer b = ByteBuffer.wrap(d);
                                while (b.hasRemaining()) if (ch.read(b) < 0) throw new IOException("Discord closed the connection");
                                return d.length;
                            }
                            public void close() throws IOException { ch.close(); }
                        };
                    }
                } catch (IOException ignored) {
                    // try the next socket
                }
            }
            return null;
        }
    }
}
