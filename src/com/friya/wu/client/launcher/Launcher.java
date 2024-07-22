package com.friya.wu.client.launcher;

import com.wurmonline.client.LwjglClient;
import com.wurmonline.client.WurmClientBase;
import com.wurmonline.client.launcherfx.WurmMain;
import com.wurmonline.client.options.Options;
import com.wurmonline.client.options.screenres.DisplayDevice;
import com.wurmonline.client.options.screenres.DisplayResolution;
import com.wurmonline.client.resources.Resources;
import com.wurmonline.client.settings.GlobalData;
import com.wurmonline.client.settings.Profile;
import com.wurmonline.client.steam.SteamHandler;
import com.wurmonline.client.steam.SteamServerFX;
import javafx.geometry.Rectangle2D;
import javafx.stage.Screen;
import javassist.*;
import org.gotti.wurmunlimited.modloader.classhooks.HookManager;
import org.gotti.wurmunlimited.modloader.interfaces.Configurable;
import org.gotti.wurmunlimited.modloader.interfaces.Initable;
import org.gotti.wurmunlimited.modloader.interfaces.WurmClientMod;

import javax.swing.*;
import java.awt.*;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.List;
import java.util.logging.Logger;

// TODO: Send an initial command when character is logged in so we don't get kicked out on timeout
// TODO: I suppose I could add environment variables to allow quick-login to come via normal WurmLauncher too.
public class Launcher implements WurmClientMod, Initable, Configurable
{
	private static final Logger logger = Logger.getLogger(Launcher.class.getName());
	private final HookManager hookMan = HookManager.getInstance();

	private String character;
	private String server;
	private int port;
	private String serverPassword;

	private boolean patchQuit = false;
	private boolean patchDisplay = false;

	@Override
	public void configure(Properties p)
	{
		patchQuit = Boolean.valueOf(p.getProperty("patchQuit", String.valueOf(patchQuit))).booleanValue();
		patchDisplay = Boolean.valueOf(p.getProperty("patchDisplay", String.valueOf(patchDisplay))).booleanValue();

		try {
			// This can only be triggered from command-line.
			if (System.getProperty("patchStart") != null && System.getProperty("patchStart").equals("yes")) {
				character = System.getProperty("character");
				server =  System.getProperty("server");
				if(!System.getProperty("port").isEmpty()) {
					port = Integer.parseInt(System.getProperty("port"));
				} else {
					port = 3724;
				}
				serverPassword = System.getProperty("serverPassword");
				if(serverPassword == null) {
					serverPassword = "";
				}

				patchStart();
			}

			if (patchDisplay || (System.getProperty("patchDisplay") != null && System.getProperty("patchDisplay").equals("yes"))) {
				patchDisplayDevice();
				displayDeviceCtor(DisplayDevice.getInstance());
			}

			if (patchQuit || (System.getProperty("patchQuit") != null && System.getProperty("patchQuit").equals("yes"))) {
				patchQuitDialog();
			}

			patchServerBrowser();

		} catch(Throwable ex) {
			throw new RuntimeException("Error starting FastLauncher: " + ex + "\n" + Arrays.toString(ex.getStackTrace()));
		}
	}

	private void patchStart()
	{
		logger.info("Patching start...");
		hookMan.registerHook("com.wurmonline.client.launcherfx.WurmMain", "start", null,
			() -> (proxy, method, args) -> {
				logger.info("Taking control over WurmMain.start()");
				start((WurmMain)proxy);
				return null;
			}
		);
	}

	private void patchDisplayDevice()
	{
		logger.info("Patching display modes...");
		try {
			ClassPool pool = ClassPool.getDefault();
			CtClass cc = pool.get("com.wurmonline.client.options.screenres.DisplayDevice");

			// Give DisplayDevice an empty ctor (displayDeviceCtor will set this one up)
			CtConstructor constructor = cc.getDeclaredConstructor(new CtClass[] { });
			constructor.setBody("{ }");
		} catch (Exception e) {
			logger.severe("Error patching displayDevice: " + e);
		}
	}

	private void patchQuitDialog()
	{
		logger.info("Patching quit dialog...");
		try {
			hookMan.registerHook("com.wurmonline.client.renderer.gui.HeadsUpDisplay", "showQuitConfirmationWindow", null,
				() -> (proxy, method, args) -> {
					logger.info("Quitting!");
					WurmClientBase.performShutdown();
					return null;
				}
			);
		} catch (Exception e) {
			logger.severe("Error patching quit dialog: " + e);
		}
	}

	private void patchServerBrowser()
	{
		logger.info("Patching server browser...");

		try {
			hookMan.registerHook("com.wurmonline.client.steam.SteamServerFX", "getServerName", null,
				() -> (proxy, method, args) -> {
					logger.info("IP for server: " + Arrays.toString(args));
					String res = (String) method.invoke(proxy, args);
					return ((SteamServerFX)proxy).getIpAdress() + " - " + res;
				}
			);
		} catch (Exception e) {
			logger.severe("Error patching server browser: " + e);
		}

	}

	@Override
	public void init() {
		WurmClientMod.super.init();
		patchQuitDialog();
	}

	private void start(WurmMain wurmMain)
	{
		try {
			try {
				if (System.getProperty("os.name").contains("Windows")) {
					UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
				} else {
					UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
				}
			} catch (Exception e) {
				logger.warning("Could not setup system look and feel. Using default.");
			}

			if (WurmClientBase.steamHandler == null) {
				WurmClientBase.steamHandler = new SteamHandler();
			}

			SteamHandler.SteamInitializeResults steamInitializeResults = WurmClientBase.steamHandler.initializeSteam();
			logger.info("Initialized steam");
			if (steamInitializeResults == SteamHandler.SteamInitializeResults.SteamisNotRunningNeedToRestart) {
				logger.severe("Shutting down client");
				WurmClientBase.performShutdown();
			} else if (steamInitializeResults == SteamHandler.SteamInitializeResults.SteamApiFailed) {
				logger.severe("Could not start SteamApi Shutting down (Steam is not Running)");
				throw new Throwable("Could not start SteamApi Shutting down (Steam is not Running)");
			}

			connectToIp(this.server, this.port, this.serverPassword, this.character, !this.serverPassword.isEmpty());

		} catch (Throwable var11) {
			logger.severe("Error in start(): " + var11);
		} finally {
			System.out.println(">>> Main thread exiting.");
		}
	}

	private void connectToIp(String ip, int port, String serverPassword, String userNameOverride, boolean isPasswordProtected)
	{
		try {
			Field field = null;
			field = WurmMain.class.getDeclaredField("serverIp");
			field.set(null, this.server);

			field = WurmMain.class.getDeclaredField("serverPort");
			field.set(null, this.port);

		} catch (NoSuchFieldException | IllegalAccessException e) {
			throw new RuntimeException(e);
		}

		logger.info("Using fast launcher for character '" + this.character + "' @ " + this.server + ":" + this.port + "...");

		this.launchGame(serverPassword, userNameOverride);
	}

	private void launchGame(String serverPassword, String userNameOverride)
	{
		Profile profile = com.wurmonline.client.settings.Profile.getProfile();

		profile.associateConfig();
		WurmClientBase.setServerPassword(serverPassword);
		WurmClientBase.setPassword(WurmClientBase.steamHandler.getSteamIdAsString());
		WurmClientBase.setUsername(userNameOverride);
		WurmClientBase.setExtraTileData(Options.isExtraTileData.value());
		List<String> checkedPackNames = new ArrayList<>();
		checkedPackNames.add("sound.jar");
		checkedPackNames.add("pmk.jar");
		checkedPackNames.add("graphics.jar");
		Resources resources = new Resources(GlobalData.getPackDirectory(), checkedPackNames);

		Screen mainScreen = (Screen)Screen.getScreensForRectangle(0, 0, 640, 480).get(0);

		Rectangle2D mainBounds = mainScreen.getBounds();
		GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
		GraphicsDevice[] gs = ge.getScreenDevices();

		for (GraphicsDevice device : gs) {
			Rectangle rect = device.getDefaultConfiguration().getBounds();
			Rectangle2D bounds = new Rectangle2D(rect.getX(), rect.getY(), rect.getWidth(), rect.getHeight());
			if (bounds.equals(mainBounds)) {
				LwjglClient.setStartupDevice(device);
			}
		}

		System.gc();
		WurmClientBase.launch(profile.launchProfile(), resources, false);
	}

	private class MyDisplayMode
	{
		public final int width;
		public final int height;
		public final int frequency;
		public final int bitsPerPixel;

		private MyDisplayMode(int width, int height, int frequency, int bitsPerPixel) {
			this.width = width;
			this.height = height;
			this.frequency = frequency;
			this.bitsPerPixel = bitsPerPixel;
		}

		public int getBitsPerPixel() { return bitsPerPixel; }
		public int getFrequency() { return frequency; }
		public int getHeight() { return height; }
		public int getWidth() { return width; }
	}

	private MyDisplayMode[] getDisplayModes()
	{
		GraphicsDevice gd = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
		int w = gd.getDisplayMode().getWidth();
		int h = gd.getDisplayMode().getHeight();
		int f = com.sun.javafx.tk.Toolkit.getToolkit().getRefreshRate();
		int bpp = 24;

		return new MyDisplayMode[]{ new MyDisplayMode(w, h, f, bpp) };
	}

	private void displayDeviceCtor(DisplayDevice dd)
	{
		try {
			Map<String, DisplayResolution> resolutionMap = new HashMap<>();
			// org.lwjgl.opengl.DisplayMode[] displayModes = Display.getAvailableDisplayModes();
			MyDisplayMode[] displayModes = getDisplayModes();;
			int dmLen = displayModes.length;

			int i;
			for(i = 0; i < dmLen; ++i) {
				// DisplayMode mode = displayModes[i];
				MyDisplayMode mode = displayModes[i];
				int height = mode.getHeight();
				int width = mode.getWidth();
				int bpp = mode.getBitsPerPixel();
				if (bpp >= 24 && height >= 480 && width >= 640) {
					String key = height + " " + width;
					DisplayResolution resolution = (DisplayResolution)resolutionMap.get(key);
					if (resolution == null) {
						resolution = new DisplayResolution(width, height);
						resolutionMap.put(key, resolution);
					}

					resolution.addRate(mode.getFrequency());
				}
			}

			Collection<DisplayResolution> resCollection = resolutionMap.values();
			dd.resolutions = (DisplayResolution[])resCollection.toArray(new DisplayResolution[0]);
			Arrays.sort(dd.resolutions);
			DisplayResolution[] var14 = dd.resolutions;
			i = var14.length;

			for(int var15 = 0; var15 < i; ++var15) {
				DisplayResolution res = var14[var15];

				// This is res.compile();
				Class<?> cls = Class.forName("com.wurmonline.client.options.screenres.DisplayResolution");
				Method compileMethod = cls.getDeclaredMethod("compile");
				compileMethod.setAccessible(true);
				compileMethod.invoke(res);
			}
		} catch (ClassNotFoundException | InvocationTargetException | NoSuchMethodException | IllegalAccessException e) {
			throw new RuntimeException(e);
		}
	}
}
