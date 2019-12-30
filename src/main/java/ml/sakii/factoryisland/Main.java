package ml.sakii.factoryisland;

import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.WritableRaster;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Random;
import java.util.Scanner;

import javax.imageio.ImageIO;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.LineUnavailableException;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;

import ml.sakii.factoryisland.blocks.ModBlock;
import ml.sakii.factoryisland.items.ItemType;
import ml.sakii.factoryisland.items.PlayerInventory;

public class Main
{

	public final static byte MAJOR = 0;
	public final static byte MINOR = 10;
	public final static byte REVISION = -8;
	
	public static boolean devmode = true, nopause = false, headless=false, small=false;
	public static Color4 drillGradientBeginColor = new Color4(100, 40, 40, 200);
	public static BufferedImage drillSide;
	public static Color4 drillSideColor, drillFrontColor, chestModule, tankModule;
	public static Surface fire;

	public static JFrame Frame;
	public static Game GAME;

	public static final HashMap<String, ItemType> Items = new HashMap<>(20);


	public static Surface lamp;
	public static final ArrayList<String> ModRegistry = new ArrayList<>();

	public final static int MP_PACKET_EACH = 300;
	public static Surface stone, grass, dirt, sand, playerSide, playerFront, wood, leaf, sapling, saplingTop,
			alienFront, alienSide;
	public final static int TICKSPEED = 20; // 20 tick every second
	public final static int ENTITYSYNCRATE = 3; //every 3 ticks (=0.15s)
	public final static int PHYSICS_FPS = 30;
	public static final Color TRANSPARENT = new Color(0, 0, 0, 0);

	public static Surface[] waters, oils;
	public static Color4 wmSideColor, wmGradientBeginColor, wmPoweredColor;
	static final JPanel Base = new JPanel(new CardLayout());
	
	static Clip BGMusic;

	static BufferedImage GUIBG;
	static BufferedImage Logo;
	static BufferedImage MainMenuBG, PausedBG, SettingsBG, originalPausedBG;
	static BufferedImage MenuButtonTexture;
	static ArrayList<String> Mods = new ArrayList<>();

	static String PreviousCLCard = "";
	static long seed;


	static Color skyColor = Color.BLACK;
	static boolean sound = true;
	private static AudioInputStream BGAudioStream;

	private static String CurrentCLCard = "";
	public static GraphicsConfiguration graphicsconfig;
	
	
	private static MultiplayerGUI MPGui;
	private static PauseGUI pauseGui;

	

	static int screen;
	public static int Width;
	public static int Height;
	public static GameEngine Engine; //csak a headless server hasznalja

	
	@SuppressWarnings("resource")
	public static void main(String[] args) throws Exception
	{

		System.setOut(new ProxyPrintStream(System.out, "log.txt"));
        System.setErr(new ProxyPrintStream(System.err, "log.txt"));
		

        List<String> params = Arrays.asList(args);
        String name=null,map=null;
        for(int i=0;i<params.size();i++) {
        	switch(params.get(i)) {
        	case "-name":name=params.get(i+1);
        		break;
        	case "-map":map=params.get(i+1);
        		break;
        	case "-server":headless=true;
        		break;
        	case "-small":small=true;
        		break;
        	}
        }
        

        
        if(headless) {
        	Config.username="SERVER";
        	LoadResources();
        	if(map==null) {
        		map="server";
        	}
        	File mapFile = new File("saves/"+map+"/map.xml");
        	if(mapFile.exists()) {
        		Engine = new GameEngine(map,null,0,LoadMethod.EXISTING,null);
        	}else {
        		Engine = new GameEngine(map,null,new Random().nextLong(),LoadMethod.GENERATE,null);
        	}
        	
        	Engine.afterGen();
        	Engine.startPhysics();
        	Engine.ticker.start();
        	Main.log("Timers started");
        	String error = Engine.startServer();
        	if(error != null) {
        		Engine.disconnect(error);
        	}else {
	        	Main.log("Setup done");
	        	Scanner s = new Scanner(System.in);
	        	while(s.hasNextLine()) {
	        		String line = s.nextLine();
	        		if(line.trim().equalsIgnoreCase("stop")) {
	        			Main.log("Stopping server...");
	        			Engine.disconnect(null);
	        			break;
	        		}
					Main.err("Unknown command: "+line);
	        	}
	        	s.close();
        	}
        }else {
        
	        setupWindow(name);
	   
	        if(map !=null) {
				SwitchWindow("generate");
				SingleplayerGUI sp = ((SingleplayerGUI)Base.getComponents()[0]);
				sp.worldsList.setSelectedValue(map, true);
				sp.join(false);
				
			}
        
        }
					

		
	}



	public static void setupWindow(String username)
	{
		if(username == null) {


		String username2 = JOptionPane.showInputDialog("Enter username", Config.username);
		Config.username = username2 == null ? Config.username : username2;

		Config.save();
		}else {
			Config.username = username;
		}

		GraphicsDevice[] gs = GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices();
		String[] resolutions = new String[gs.length];

		for (int i = 0; i < gs.length; i++)
		{
			resolutions[i] = "<html><body><div align='center'>Screen " + i + "<br>"
					+ gs[i].getDefaultConfiguration().getBounds().width + "x"
					+ gs[i].getDefaultConfiguration().getBounds().height + "</div></body></html>";
		}
		if (gs.length > 1)
			screen = JOptionPane.showOptionDialog(null, "Please select a display", "Please select a display",
					JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, resolutions, null);

		if (screen < 0)
			screen = 0;

		GraphicsDevice d = GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()[screen];
		Frame= new JFrame();
		Frame.setUndecorated(true);

		//Frame.setExtendedState(java.awt.Frame.NORMAL);
		graphicsconfig = d.getDefaultConfiguration();
		Rectangle bounds = graphicsconfig.getBounds();
		if (System.getProperty("os.name").toLowerCase().contains("win"))
		{
			Frame.setBounds(bounds);

			
		} else // linuxon a talcat es stb nem lehet eltakarni
		{
			d.setFullScreenWindow(Frame);
		}

		
    	LoadResources();

		//Frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
		Frame.setTitle("FactoryIsland " + MAJOR + "." + MINOR + "." + REVISION);


		MPGui = new MultiplayerGUI();
		Base.add(new SingleplayerGUI(), "generate");
		Base.add(MPGui, "connect");
		Base.add(new MainMenuGUI(), "mainmenu");
		Base.add(new SettingsGUI(), "settings");
		pauseGui= new PauseGUI();
		Base.add(pauseGui, "pause");
		Base.add(new DeadGUI(),"died");

		Frame.add(Base);
		
		Frame.addWindowListener(new WindowListener()
		{

			@Override
			public void windowActivated(WindowEvent e)
			{
				if (GAME != null)
				{
					GAME.centered = false;
				}
			}

			@Override
			public void windowClosed(WindowEvent e)
			{
				
			}

			@Override
			public void windowClosing(WindowEvent e)
			{
				if (GAME != null)
				{
					GAME.disconnect(null);
				}
				Frame.dispose();

			}

			@Override
			public void windowDeactivated(WindowEvent e)
			{
			}

			@Override
			public void windowDeiconified(WindowEvent e)
			{
			}

			@Override
			public void windowIconified(WindowEvent e)
			{
	
			}

			@Override
			public void windowOpened(WindowEvent e)
			{
			}

		});



		SwitchWindow("mainmenu");
		
		Frame.setVisible(true);
		Main.log(Config.username + " @ "+ Frame.getBounds().toString());

		Width = Frame.getWidth();
		Height = Frame.getHeight();
	}

	public static boolean joinServer(String IP, JLabel statusLabel)
	{
		if (sound)
			BGMusic.stop();
		GAME = new Game(IP, 0, LoadMethod.MULTIPLAYER, statusLabel);

		if (GAME.error == null)
		{
			statusLabel.setText("Opening game screen...");			

			openGame();
			return true;
		}
		
		Main.err(GAME.error);
		statusLabel.setText("<html>Error: "+GAME.error+"</html>");
		JOptionPane.showMessageDialog(Frame, GAME.error);
		return false;
		
	}

	public static boolean launchWorld(String mapName, boolean generate, JLabel statusLabel)
	{
		if (sound)
			BGMusic.stop();
		if (generate)
		{
			GAME = new Game(mapName, seed, LoadMethod.GENERATE, statusLabel);
			if(GAME.error==null) {
				statusLabel.setText("Executing post-worldgen instructions...");
				GAME.Engine.afterGen();
			}else {
				Main.err(GAME.error);
				statusLabel.setText("<html>Error: "+GAME.error+"</html>");
				GAME=null;
				return false;
			}
		} else
		{
			GAME = new Game(mapName, 0, LoadMethod.EXISTING, statusLabel);
			if(GAME.error != null) {
				Main.err(GAME.error);
				statusLabel.setText("<html>Error: "+GAME.error+"</html>");
				GAME=null;
				return false;
			}
		}
		statusLabel.setText("Opening game screen...");

		openGame();
		return true;
	}

	private static void openGame() {
		Main.log("Game setup done.");
		GAME.Engine.ticker.start();
		if(GAME.Engine.isSingleplayer())
			GAME.Engine.startPhysics();
		
		
		
		Main.log("Switching to game window...");
		
		
		Base.add(GAME, "game");

		SwitchWindow("game");
		GAME.renderThread.start();
		
		
		if(GAME.PE.getHealth()==0) {
			GAME.Engine.world.hurtEntity(GAME.PE.ID, 0, false);
		}
	}
	
	public static void SwitchWindow(String To)
	{

		if (CurrentCLCard.equals("pause") && To.equals("mainmenu") && sound)
		{
			try
			{
				if (!BGMusic.isOpen())
				{
					BGMusic.open(BGAudioStream);
					BGMusic.start();
				}
			} catch (LineUnavailableException | IOException e)
			{
				Main.log("Could not load main menu music: " + e.getMessage());
			}

		}
		((CardLayout) (Main.Base.getLayout())).show(Base, To);
		PreviousCLCard = CurrentCLCard;
		CurrentCLCard = To;

	}

	private static void LoadResources()
	{
		GUIBG = loadTexture("textures/stone.png");
		Logo = loadTexture("textures/logo.png");
		MainMenuBG = loadTexture("textures/mainmenu.png");
		PausedBG = loadTexture("textures/paused.png");
		originalPausedBG = loadTexture("textures/paused.png");
		SettingsBG = loadTexture("textures/settings.png");

		MenuButtonTexture = loadTexture("textures/button.png");

		stone = new Surface(loadTexture("textures/blocks/stone.png"));
		waters = new Surface[]
		{ null, new Surface(loadTexture("textures/blocks/water_1.png")),
				new Surface(loadTexture("textures/blocks/water_2.png")),
				new Surface(loadTexture("textures/blocks/water_3.png")),
				new Surface(loadTexture("textures/blocks/water_4.png")) };
		oils = new Surface[]
		{ null, new Surface(new Color(80, 80, 80)), new Surface(new Color(60, 60, 60)),
				new Surface(new Color(35, 35, 35)) };
		grass = new Surface(loadTexture("textures/blocks/grass.png"));
		dirt = new Surface(loadTexture("textures/blocks/dirt.png"));
		sand = new Surface(loadTexture("textures/blocks/sand.png"));
		wood = new Surface(loadTexture("textures/blocks/wood2.png"));
		leaf = new Surface(loadTexture("textures/blocks/leaf3.png"));
		sapling = new Surface(new Color(150, 100, 50).darker());
		saplingTop = new Surface(Color.GREEN);
		chestModule = new Color4(85, 85, 85);
		tankModule = new Color4(255, 153, 0);
		playerSide = new Surface(Color.BLUE);
		playerFront = new Surface(Color.RED);
		alienSide = new Surface(Color.GREEN.brighter());
		alienFront = new Surface(Color.GREEN.darker().darker());
		drillSide = loadTexture("textures/blocks/drill_side5.png");
		drillSideColor = Surface.averageColor(drillSide);
		//Color4 tmp = new Color4(drillGradientBeginColor);
		drillFrontColor = new Color4().set(drillSideColor).darker().darker();//tmp.blend(drillSideColor);
		lamp = new Surface(new Color(240, 220, 170));

		wmSideColor = new Color4(200, 200, 255);
		wmGradientBeginColor = new Color4(20, 20, 70);
		fire = new Surface(new Color(255, 153, 0));
		wmPoweredColor = new Color4().set(fire.c).brighter();

		BufferedReader reader = new BufferedReader(
				new InputStreamReader(Main.class.getResourceAsStream("blocks/BlockRegistry.txt")));

		String line = "";
		try
		{
			while ((line = reader.readLine()) != null)
			{

				GameEngine.nullBlock("ml.sakii.factoryisland.blocks." + line + "Block"); //TODO ez kell még?
				PlayerInventory.Creative.add(Items.get(line), 1, false);
			}
		} catch (IOException e1)
		{
			e1.printStackTrace();
		}

		File mods = new File("mods");
		if (!mods.exists())
		{
			mods.mkdir();
		} else
		{

			File[] directories = mods.listFiles(new FilenameFilter()
			{
				@Override
				public boolean accept(File current, String name)
				{
					return new File(current, name).isDirectory();
				}
			});
			for (File mod : directories)
			{
				if (new File(mod, "mod.js").exists())
				{
					Main.log("Found mod: " + mod.getName());
					@SuppressWarnings("unused")
					ModBlock modBlock = new ModBlock(mod.getName(), 0, 0, 0, null);
					Mods.add(mod.getName());
				}

			}
		}

		File saves = new File("saves");
		if (!saves.exists())
		{
			saves.mkdir();
		}

		
		if(!headless) {
			try
			{
		
				BufferedInputStream inputStream = new BufferedInputStream(
						Main.class.getResourceAsStream("sounds/Zongora.wav"));
				BGAudioStream = AudioSystem.getAudioInputStream(inputStream);
		
				BGMusic = AudioSystem.getClip();
				BGMusic.open(BGAudioStream);
				sound = true;
			} catch (Exception e)
			{
				Main.log("Could not load sounds: " + e.getMessage());
				sound = false;
			}
		}else {
			sound=false;
		}
	}

	private static BufferedImage loadTexture(String path)
	{
		if(headless) {
			return newEmptyImage();
		}
		BufferedImage image;
		try
		{
			image = ImageIO.read(Main.class.getResourceAsStream(path));

		} catch (Exception e)
		{
			image = newEmptyImage();
			Main.log("Could not load texture from '" + path + "': " + e.getMessage());

		}
		return toCompatibleImage(image);
	}
	
	private static BufferedImage newEmptyImage() {
		BufferedImage i = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
		Graphics ig = i.createGraphics();
		ig.setColor(Color.WHITE);
		ig.fillRect(0, 0, 1, 1);
		ig.dispose();
		return i;
	}
	
	static BufferedImage toCompatibleImage(BufferedImage image)
	{
		if(headless) {
			return image;
		}
	    // obtain the current system graphical settings
	    /*GraphicsConfiguration gfxConfig = GraphicsEnvironment.
	        getLocalGraphicsEnvironment().getDefaultScreenDevice().
	        getDefaultConfiguration();*/
		GraphicsConfiguration gfxConfig = GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()[screen].getDefaultConfiguration();

	    /*
	     * if image is already compatible and optimized for current system 
	     * settings, simply return it
	     */
	    if (image.getColorModel().equals(gfxConfig.getColorModel()))
	        return image;

	    // image is not optimized, so create a new image that is
	    BufferedImage newImage = gfxConfig.createCompatibleImage(
	            image.getWidth(), image.getHeight(), image.getTransparency());

	    // get the graphics context of the new image to draw the old image on
	    Graphics2D g2d = newImage.createGraphics();

	    // actually draw the image and dispose of context no longer needed
	    g2d.drawImage(image, 0, 0, null);
	    g2d.dispose();

	    // return the new optimized image
	    return newImage; 
	}
	
	static BufferedImage deepCopy(BufferedImage bi) {
		 ColorModel cm = bi.getColorModel();
		 boolean isAlphaPremultiplied = cm.isAlphaPremultiplied();
		 WritableRaster raster = bi.copyData(null);
		 return new BufferedImage(cm, raster, isAlphaPremultiplied, null);
		}
	
	public static void log(Object message) {
		String timeStamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
		String msg = "[" + timeStamp + "] INFO: "+message;
		System.out.println(msg);
	}
	
	public static void err(Object message) {
		String timeStamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
		String msg = "[" + timeStamp + "] Error: "+message;
			System.err.println(msg);
	}

}
