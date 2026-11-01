package dev.onix.client.social;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.onix.client.OnixClient;
import dev.onix.client.network.OnixPresence;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.util.HttpUtil;

/**
 * Lets party members join the leader's singleplayer world without port forwarding: the leader's game opens the world
 * to LAN and keeps a "host" WebSocket to the Onix server; each member's game listens on a local port, Minecraft
 * connects there, and the bytes travel over WebSockets through the server's relay to the leader's LAN port.
 * Minecraft still checks every player with Mojang – the relay only carries bytes.
 */
public final class PartyRelay {
    private PartyRelay() {}

    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    /** Bytes are collected this long before they are sent, so fewer, larger messages cross the relay. */
    private static final int BATCH_MS = 12;

    private static volatile WebSocket hostSocket;
    private static volatile boolean hostStarting;
    private static volatile long hostRetryAt;
    private static volatile int lanPort;
    private static volatile ServerSocket guestServer;
    private static volatile int guestPort;

    private static String relayUrl(String token, String role, String conn) {
        String base = OnixPresence.server().replaceFirst("^http", "ws");
        return base + "/v1/relay?role=" + role + "&token=" + URLEncoder.encode(token, StandardCharsets.UTF_8)
                + (conn == null ? "" : "&conn=" + conn);
    }

    private static void thread(String name, Runnable r) {
        Thread t = new Thread(r, "Onix Party " + name);
        t.setDaemon(true);
        t.start();
    }

    // ================================================================== leader

    /** Opens the world to LAN (once) and keeps the host connection to the relay. Called every tick while in a world. */
    public static void startHost() {
        if (hostSocket != null || hostStarting || System.currentTimeMillis() < hostRetryAt) return;
        Minecraft mc = Minecraft.getInstance();
        IntegratedServer server = mc.getSingleplayerServer();
        if (server == null) return;
        if (!server.isPublished()) {
            int port = HttpUtil.getAvailablePort();
            if (!server.publishServer(null, false, port)) {
                hostRetryAt = System.currentTimeMillis() + 30_000;
                return;
            }
        }
        lanPort = server.getPort();
        hostStarting = true;
        thread("host", () -> {
            try {
                String token = OnixPresence.relayToken();
                if (token == null) throw new IOException("not signed in to the Onix server");
                StringBuilder text = new StringBuilder();
                hostSocket = HTTP.newWebSocketBuilder().buildAsync(URI.create(relayUrl(token, "host", null)), new WebSocket.Listener() {
                    @Override
                    public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
                        text.append(data);
                        if (last) {
                            String msg = text.toString();
                            text.setLength(0);
                            try {
                                JsonObject o = JsonParser.parseString(msg).getAsJsonObject();
                                if (o.has("open")) openData(token, o.get("open").getAsString());
                            } catch (Exception ignored) {
                                // not for us
                            }
                        }
                        ws.request(1);
                        return null;
                    }

                    @Override
                    public CompletionStage<?> onClose(WebSocket ws, int code, String reason) {
                        if (hostSocket == ws) hostSocket = null;
                        hostRetryAt = System.currentTimeMillis() + 5_000;
                        return null;
                    }

                    @Override
                    public void onError(WebSocket ws, Throwable error) {
                        if (hostSocket == ws) hostSocket = null;
                        hostRetryAt = System.currentTimeMillis() + 10_000;
                    }
                }).get();
                OnixClient.LOGGER.info("[party] hosting the world for the party (LAN port {})", lanPort);
                // keep the connection alive
                thread("ping", () -> {
                    WebSocket ws = hostSocket;
                    while (ws != null && ws == hostSocket) {
                        try {
                            Thread.sleep(25_000);
                            ws.sendText("ping", true).join();
                        } catch (Exception e) {
                            break;
                        }
                    }
                });
            } catch (Exception e) {
                OnixClient.LOGGER.warn("[party] relay host failed: {}", e.toString());
                hostRetryAt = System.currentTimeMillis() + 15_000;
            } finally {
                hostStarting = false;
            }
        });
    }

    /** The relay knows the leader's world, so members can be sent there. */
    public static boolean hostReady() {
        return hostSocket != null;
    }

    public static void stopHost() {
        WebSocket ws = hostSocket;
        hostSocket = null;
        if (ws != null) ws.sendClose(WebSocket.NORMAL_CLOSURE, "bye");
    }

    /** A member connects: a data WebSocket for them, piped to the local LAN port. */
    private static void openData(String token, String conn) {
        thread("data", () -> {
            try {
                Socket socket = new Socket(InetAddress.getLoopbackAddress(), lanPort);
                socket.setTcpNoDelay(true);
                pipe(socket, relayUrl(token, "data", conn));
            } catch (Exception e) {
                OnixClient.LOGGER.warn("[party] could not open a connection for a member: {}", e.toString());
            }
        });
    }

    // ================================================================== member

    /** True for the local address a member uses to reach the leader's world. */
    public static boolean isGuestAddress(String address) {
        return guestPort != 0 && address.equals("127.0.0.1:" + guestPort);
    }

    /** Listens on a local port that leads to the leader's world, then hands the port over to connect to. */
    public static void joinWorld(IntConsumer connect, Consumer<String> failed) {
        thread("guest", () -> {
            try {
                String token = OnixPresence.relayToken();
                if (token == null) throw new IOException("not signed in to the Onix server");
                ServerSocket old = guestServer;
                if (old != null) old.close();
                ServerSocket listen = new ServerSocket();
                listen.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
                guestServer = listen;
                guestPort = listen.getLocalPort();
                thread("guest-accept", () -> {
                    while (!listen.isClosed()) {
                        try {
                            Socket socket = listen.accept();
                            socket.setTcpNoDelay(true);
                            thread("guest-pipe", () -> {
                                try {
                                    pipe(socket, relayUrl(token, "guest", null));
                                } catch (Exception e) {
                                    OnixClient.LOGGER.warn("[party] relay connection failed: {}", e.toString());
                                    try {
                                        socket.close();
                                    } catch (IOException ignored) {
                                        // closed
                                    }
                                }
                            });
                        } catch (IOException e) {
                            break;
                        }
                    }
                });
                int port = guestPort;
                Minecraft.getInstance().execute(() -> connect.accept(port));
            } catch (Exception e) {
                Minecraft.getInstance().execute(() -> failed.accept(e.getMessage() == null ? e.toString() : e.getMessage()));
            }
        });
    }

    // ================================================================== byte pipe: TCP socket <-> WebSocket

    private static void pipe(Socket socket, String url) throws Exception {
        OutputStream out = socket.getOutputStream();
        WebSocket ws = HTTP.newWebSocketBuilder().buildAsync(URI.create(url), new WebSocket.Listener() {
            @Override
            public CompletionStage<?> onBinary(WebSocket w, ByteBuffer data, boolean last) {
                try {
                    byte[] b = new byte[data.remaining()];
                    data.get(b);
                    synchronized (out) {
                        out.write(b);
                        out.flush();
                    }
                } catch (IOException e) {
                    w.abort();
                }
                w.request(1);
                return null;
            }

            @Override
            public CompletionStage<?> onClose(WebSocket w, int code, String reason) {
                close(socket);
                return null;
            }

            @Override
            public void onError(WebSocket w, Throwable error) {
                close(socket);
            }
        }).get();
        // socket -> relay, a few milliseconds of bytes per message
        thread("pipe-up", () -> {
            byte[] buf = new byte[64 * 1024];
            try (InputStream in = socket.getInputStream()) {
                while (true) {
                    int n = in.read(buf);
                    if (n < 0) break;
                    long until = System.currentTimeMillis() + BATCH_MS;
                    while (n < buf.length - 4096 && System.currentTimeMillis() < until) {
                        if (in.available() > 0) {
                            int m = in.read(buf, n, Math.min(in.available(), buf.length - n));
                            if (m < 0) break;
                            n += m;
                        } else {
                            Thread.sleep(1);
                        }
                    }
                    ws.sendBinary(ByteBuffer.wrap(buf, 0, n).duplicate(), true).join();
                }
            } catch (Exception ignored) {
                // closed
            }
            ws.sendClose(WebSocket.NORMAL_CLOSURE, "closed");
            close(socket);
        });
    }

    private static void close(Socket socket) {
        try {
            socket.close();
        } catch (IOException ignored) {
            // already closed
        }
    }
}
