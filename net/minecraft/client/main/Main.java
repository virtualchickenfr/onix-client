package net.minecraft.client.main;

import com.google.common.base.Stopwatch;
import com.google.common.base.Ticker;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.authlib.properties.PropertyMap;
import com.mojang.authlib.properties.PropertyMap.Serializer;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.jtracy.TracyClient;
import com.mojang.logging.LogUtils;
import com.mojang.util.UndashedUuid;
import java.io.File;
import java.net.Authenticator;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.Proxy;
import java.net.Proxy.Type;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import joptsimple.ArgumentAcceptingOptionSpec;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.client.ClientBootstrap;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.RunArgs;
import net.minecraft.client.WindowSettings;
import net.minecraft.client.session.Session;
import net.minecraft.client.session.telemetry.GameLoadTimeEvent;
import net.minecraft.client.session.telemetry.TelemetryEventProperty;
import net.minecraft.client.util.GlException;
import net.minecraft.client.util.tracy.TracyLoader;
import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.datafixer.Schemas;
import net.minecraft.obfuscate.DontObfuscate;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.util.JsonHelper;
import net.minecraft.util.Util;
import net.minecraft.util.Uuids;
import net.minecraft.util.WinNativeModuleUtil;
import net.minecraft.util.crash.CrashReport;
import net.minecraft.util.crash.CrashReportSection;
import net.minecraft.util.logging.UncaughtExceptionLogger;
import net.minecraft.util.profiling.jfr.FlightProfiler;
import net.minecraft.util.profiling.jfr.InstanceType;
import org.apache.commons.lang3.StringEscapeUtils;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

@Environment(EnvType.CLIENT)
public class Main {
	@DontObfuscate
	public static void main(String[] args) {
		OptionParser optionParser = new OptionParser();
		optionParser.allowsUnrecognizedOptions();
		optionParser.accepts("demo");
		optionParser.accepts("disableMultiplayer");
		optionParser.accepts("disableChat");
		optionParser.accepts("fullscreen");
		optionParser.accepts("checkGlErrors");
		OptionSpec<Void> optionSpec = optionParser.accepts("renderDebugLabels");
		OptionSpec<Void> optionSpec2 = optionParser.accepts("jfrProfile");
		OptionSpec<Void> optionSpec3 = optionParser.accepts("tracy");
		OptionSpec<Void> optionSpec4 = optionParser.accepts("tracyNoImages");
		OptionSpec<String> optionSpec5 = optionParser.accepts("quickPlayPath").withRequiredArg();
		OptionSpec<String> optionSpec6 = optionParser.accepts("quickPlaySingleplayer").withRequiredArg();
		OptionSpec<String> optionSpec7 = optionParser.accepts("quickPlayMultiplayer").withRequiredArg();
		OptionSpec<String> optionSpec8 = optionParser.accepts("quickPlayRealms").withRequiredArg();
		OptionSpec<File> optionSpec9 = optionParser.accepts("gameDir").withRequiredArg().<File>ofType(File.class).defaultsTo(new File("."));
		OptionSpec<File> optionSpec10 = optionParser.accepts("assetsDir").withRequiredArg().ofType(File.class);
		OptionSpec<File> optionSpec11 = optionParser.accepts("resourcePackDir").withRequiredArg().ofType(File.class);
		OptionSpec<String> optionSpec12 = optionParser.accepts("proxyHost").withRequiredArg();
		OptionSpec<Integer> optionSpec13 = optionParser.accepts("proxyPort").withRequiredArg().defaultsTo("8080").ofType(Integer.class);
		OptionSpec<String> optionSpec14 = optionParser.accepts("proxyUser").withRequiredArg();
		OptionSpec<String> optionSpec15 = optionParser.accepts("proxyPass").withRequiredArg();
		OptionSpec<String> optionSpec16 = optionParser.accepts("username").withRequiredArg().defaultsTo("Player" + System.currentTimeMillis() % 1000L);
		OptionSpec<String> optionSpec17 = optionParser.accepts("uuid").withRequiredArg();
		OptionSpec<String> optionSpec18 = optionParser.accepts("xuid").withOptionalArg().defaultsTo("");
		OptionSpec<String> optionSpec19 = optionParser.accepts("clientId").withOptionalArg().defaultsTo("");
		OptionSpec<String> optionSpec20 = optionParser.accepts("accessToken").withRequiredArg().required();
		OptionSpec<String> optionSpec21 = optionParser.accepts("version").withRequiredArg().required();
		OptionSpec<Integer> optionSpec22 = optionParser.accepts("width").withRequiredArg().<Integer>ofType(Integer.class).defaultsTo(854);
		OptionSpec<Integer> optionSpec23 = optionParser.accepts("height").withRequiredArg().<Integer>ofType(Integer.class).defaultsTo(480);
		OptionSpec<Integer> optionSpec24 = optionParser.accepts("fullscreenWidth").withRequiredArg().ofType(Integer.class);
		OptionSpec<Integer> optionSpec25 = optionParser.accepts("fullscreenHeight").withRequiredArg().ofType(Integer.class);
		OptionSpec<String> optionSpec26 = optionParser.accepts("userProperties").withRequiredArg().defaultsTo("{}");
		OptionSpec<String> optionSpec27 = optionParser.accepts("profileProperties").withRequiredArg().defaultsTo("{}");
		OptionSpec<String> optionSpec28 = optionParser.accepts("assetIndex").withRequiredArg();
		OptionSpec<String> optionSpec29 = optionParser.accepts("userType").withRequiredArg().defaultsTo("legacy");
		OptionSpec<String> optionSpec30 = optionParser.accepts("versionType").withRequiredArg().defaultsTo("release");
		OptionSpec<String> optionSpec31 = optionParser.nonOptions();
		OptionSet optionSet = optionParser.parse(args);
		File file = getOption(optionSet, optionSpec9);
		String string = getOption(optionSet, optionSpec21);
		String string2 = "Pre-bootstrap";

		Logger logger;
		RunArgs runArgs;
		try {
			if (optionSet.has(optionSpec2)) {
				FlightProfiler.INSTANCE.start(InstanceType.CLIENT);
			}

			if (optionSet.has(optionSpec3)) {
				TracyLoader.load();
			}

			Stopwatch stopwatch = Stopwatch.createStarted(Ticker.systemTicker());
			Stopwatch stopwatch2 = Stopwatch.createStarted(Ticker.systemTicker());
			GameLoadTimeEvent.INSTANCE.addTimer(TelemetryEventProperty.LOAD_TIME_TOTAL_TIME_MS, stopwatch);
			GameLoadTimeEvent.INSTANCE.addTimer(TelemetryEventProperty.LOAD_TIME_PRE_WINDOW_MS, stopwatch2);
			SharedConstants.createGameVersion();
			TracyClient.reportAppInfo("Minecraft Java Edition " + SharedConstants.getGameVersion().getName());
			CompletableFuture<?> completableFuture = Schemas.optimize(DataFixTypes.REQUIRED_TYPES);
			CrashReport.initCrashReport();
			logger = LogUtils.getLogger();
			string2 = "Bootstrap";
			Bootstrap.initialize();
			ClientBootstrap.initialize();
			GameLoadTimeEvent.INSTANCE.setBootstrapTime(Bootstrap.LOAD_TIME.get());
			Bootstrap.logMissing();
			string2 = "Argument parsing";
			List<String> list = optionSet.valuesOf(optionSpec31);
			if (!list.isEmpty()) {
				logger.info("Completely ignored arguments: {}", list);
			}

			String string3 = optionSpec29.value(optionSet);
			Session.AccountType accountType = Session.AccountType.byName(string3);
			if (accountType == null) {
				logger.warn("Unrecognized user type: {}", string3);
			}

			String string4 = getOption(optionSet, optionSpec12);
			Proxy proxy = Proxy.NO_PROXY;
			if (string4 != null) {
				try {
					proxy = new Proxy(Type.SOCKS, new InetSocketAddress(string4, getOption(optionSet, optionSpec13)));
				} catch (Exception var83) {
				}
			}

			final String string5 = getOption(optionSet, optionSpec14);
			final String string6 = getOption(optionSet, optionSpec15);
			if (!proxy.equals(Proxy.NO_PROXY) && isNotNullOrEmpty(string5) && isNotNullOrEmpty(string6)) {
				Authenticator.setDefault(new Authenticator() {
					protected PasswordAuthentication getPasswordAuthentication() {
						return new PasswordAuthentication(string5, string6.toCharArray());
					}
				});
			}

			int i = getOption(optionSet, optionSpec22);
			int j = getOption(optionSet, optionSpec23);
			OptionalInt optionalInt = toOptional(getOption(optionSet, optionSpec24));
			OptionalInt optionalInt2 = toOptional(getOption(optionSet, optionSpec25));
			boolean bl = optionSet.has("fullscreen");
			boolean bl2 = optionSet.has("demo");
			boolean bl3 = optionSet.has("disableMultiplayer");
			boolean bl4 = optionSet.has("disableChat");
			boolean bl5 = !optionSet.has(optionSpec4);
			boolean bl6 = optionSet.has(optionSpec);
			Gson gson = new GsonBuilder().registerTypeAdapter(PropertyMap.class, new Serializer()).create();
			PropertyMap propertyMap = JsonHelper.deserialize(gson, getOption(optionSet, optionSpec26), PropertyMap.class);
			PropertyMap propertyMap2 = JsonHelper.deserialize(gson, getOption(optionSet, optionSpec27), PropertyMap.class);
			String string7 = getOption(optionSet, optionSpec30);
			File file2 = optionSet.has(optionSpec10) ? getOption(optionSet, optionSpec10) : new File(file, "assets/");
			File file3 = optionSet.has(optionSpec11) ? getOption(optionSet, optionSpec11) : new File(file, "resourcepacks/");
			UUID uUID = isUuidSetAndValid(optionSpec17, optionSet, logger)
				? UndashedUuid.fromStringLenient(optionSpec17.value(optionSet))
				: Uuids.getOfflinePlayerUuid(optionSpec16.value(optionSet));
			String string8 = optionSet.has(optionSpec28) ? optionSpec28.value(optionSet) : null;
			String string9 = optionSet.valueOf(optionSpec18);
			String string10 = optionSet.valueOf(optionSpec19);
			String string11 = getOption(optionSet, optionSpec5);
			String string12 = unescape(getOption(optionSet, optionSpec6));
			String string13 = unescape(getOption(optionSet, optionSpec7));
			String string14 = unescape(getOption(optionSet, optionSpec8));
			Session session = new Session(optionSpec16.value(optionSet), uUID, optionSpec20.value(optionSet), toOptional(string9), toOptional(string10), accountType);
			runArgs = new RunArgs(
				new RunArgs.Network(session, propertyMap, propertyMap2, proxy),
				new WindowSettings(i, j, optionalInt, optionalInt2, bl),
				new RunArgs.Directories(file, file3, file2, string8),
				new RunArgs.Game(bl2, string, string7, bl3, bl4, bl5, bl6),
				new RunArgs.QuickPlay(string11, string12, string13, string14)
			);
			Util.startTimerHack();
			completableFuture.join();
		} catch (Throwable var84) {
			CrashReport crashReport = CrashReport.create(var84, string2);
			CrashReportSection crashReportSection = crashReport.addElement("Initialization");
			WinNativeModuleUtil.addDetailTo(crashReportSection);
			MinecraftClient.addSystemDetailsToCrashReport(null, null, string, null, crashReport);
			MinecraftClient.printCrashReport(null, file, crashReport);
			return;
		}

		Thread thread = new Thread("Client Shutdown Thread") {
			public void run() {
				MinecraftClient minecraftClient = MinecraftClient.getInstance();
				if (minecraftClient != null) {
					IntegratedServer integratedServer = minecraftClient.getServer();
					if (integratedServer != null) {
						integratedServer.stop(true);
					}
				}
			}
		};
		thread.setUncaughtExceptionHandler(new UncaughtExceptionLogger(logger));
		Runtime.getRuntime().addShutdownHook(thread);
		MinecraftClient minecraftClient = null;

		try {
			Thread.currentThread().setName("Render thread");
			RenderSystem.initRenderThread();
			minecraftClient = new MinecraftClient(runArgs);
		} catch (GlException var81) {
			Util.shutdownExecutors();
			logger.warn("Failed to create window: ", (Throwable)var81);
			return;
		} catch (Throwable var82) {
			CrashReport crashReport2 = CrashReport.create(var82, "Initializing game");
			CrashReportSection crashReportSection2 = crashReport2.addElement("Initialization");
			WinNativeModuleUtil.addDetailTo(crashReportSection2);
			MinecraftClient.addSystemDetailsToCrashReport(minecraftClient, null, runArgs.game.version, null, crashReport2);
			MinecraftClient.printCrashReport(minecraftClient, runArgs.directories.runDir, crashReport2);
			return;
		}

		MinecraftClient minecraftClient2 = minecraftClient;
		minecraftClient.run();

		try {
			minecraftClient2.scheduleStop();
		} finally {
			minecraftClient.stop();
		}
	}

	@Nullable
	private static String unescape(@Nullable String string) {
		return string == null ? null : StringEscapeUtils.unescapeJava(string);
	}

	private static Optional<String> toOptional(String string) {
		return string.isEmpty() ? Optional.empty() : Optional.of(string);
	}

	private static OptionalInt toOptional(@Nullable Integer i) {
		return i != null ? OptionalInt.of(i) : OptionalInt.empty();
	}

	@Nullable
	private static <T> T getOption(OptionSet optionSet, OptionSpec<T> optionSpec) {
		try {
			return optionSet.valueOf(optionSpec);
		} catch (Throwable var5) {
			if (optionSpec instanceof ArgumentAcceptingOptionSpec<T> argumentAcceptingOptionSpec) {
				List<T> list = argumentAcceptingOptionSpec.defaultValues();
				if (!list.isEmpty()) {
					return (T)list.get(0);
				}
			}

			throw var5;
		}
	}

	private static boolean isNotNullOrEmpty(@Nullable String s) {
		return s != null && !s.isEmpty();
	}

	private static boolean isUuidSetAndValid(OptionSpec<String> uuidOption, OptionSet optionSet, Logger logger) {
		return optionSet.has(uuidOption) && isUuidValid(uuidOption, optionSet, logger);
	}

	private static boolean isUuidValid(OptionSpec<String> uuidOption, OptionSet optionSet, Logger logger) {
		try {
			UndashedUuid.fromStringLenient(uuidOption.value(optionSet));
			return true;
		} catch (IllegalArgumentException var4) {
			logger.warn("Invalid UUID: '{}", uuidOption.value(optionSet));
			return false;
		}
	}

	static {
		System.setProperty("java.awt.headless", "true");
	}
}
