/*
 * Changlelog:
 * 
 *  + Kreat�v m�d, multiplayerben is
 *  * Lehet csin�lni relogint
 *  + Multiplayer v�gtelen ciklusba delay -> teljes�tm�ny n�v.
 *  * poz�ci�, aim mindenhol j�l bet�lt�dik
 *  * mar nem kell escapelni mp csatlakoz�skor (ertelmetlen kod bennmaradt!)
 *  + p�lya t�rl�s
 *  + elozo palya alapbol kivalasztva
 *  * fix t�vols�gtart�s
 * 	+ log file
 * 	+ f�nyek
 * 	+ screenshot ment�s
 *  + latszik a log a  status labelben, vmiert gyorsabban valt ablakot (?)
 *  + egyszer�s�tett gomb k�d
 *  + inf�k a f�men�ben
 *  + PE
 *  + entity rendszer
 *  + �rl�ny AI
 *  + fizika k�l�n sz�lon
 *  + seterr, setout ha nem devmode
 *  + �rl�nyek,entity rendszer mpben
 */

package ml.sakii.factoryisland;

import java.awt.AWTException;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.Transparency;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.awt.image.BufferedImageOp;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map.Entry;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.imageio.ImageIO;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;

import ml.sakii.factoryisland.blocks.Block;
import ml.sakii.factoryisland.blocks.BlockFace;
import ml.sakii.factoryisland.blocks.BlockInventoryInterface;
import ml.sakii.factoryisland.blocks.BreakListener;
import ml.sakii.factoryisland.blocks.InteractListener;
import ml.sakii.factoryisland.blocks.PlaceListener;
import ml.sakii.factoryisland.blocks.TextureListener;
import ml.sakii.factoryisland.blocks.WaterBlock;
import ml.sakii.factoryisland.entities.Entity;
import ml.sakii.factoryisland.entities.PlayerEntity;
import ml.sakii.factoryisland.entities.PlayerMP;
import ml.sakii.factoryisland.items.Item;
import ml.sakii.factoryisland.items.ItemStack;
import ml.sakii.factoryisland.items.PlayerInventory;
import ml.sakii.factoryisland.net.GameClient;
import ml.sakii.factoryisland.net.GameServer;

public class Game2 extends JPanel implements KeyListener, MouseListener, MouseWheelListener
{

	private static final long serialVersionUID = 2515747642091598425L;

	public final GameEngine Engine;
	//public final EAngle PE.ViewAngle = new EAngle(-135, 0);
	//public final Vector PE.getPos() = new Vector(19.5f, 19.5f, 15.0f);
	public final PlayerEntity PE;
	public final HashMap<String, PlayerMP> playerList = new HashMap<>();
	public final CopyOnWriteArrayList<Object3D> Objects = new CopyOnWriteArrayList<>();

	public boolean moved;
	public BlockInventoryInterface activeInventory;

	final Vector BottomViewVector = new Vector(), TopViewVector = new Vector(), RightViewVector = new Vector(),
			LeftViewVector = new Vector();
	private final Vector ViewTo = new Vector();
	private final Vector FrontViewVector = new Vector();
	private final Vector BackViewVector = new Vector();
	String connected;

	float hratio, vratio;
	final boolean[] key = new boolean[20];
	boolean locked = false;
	float ratio;
	int margin;
	CopyOnWriteArrayList<TextureListener> TextureBlocks = new CopyOnWriteArrayList<>();
	Frustum ViewFrustum;
	Vector ViewVector = new Vector();

	boolean centered;
	private float centerX, centerY, McenterX, McenterY;

	private float difX, difY;

	private float dx, dy;
	private boolean F3 = false;
	private float FPS = 30f;
	private LinkedList<String> debugInfo = new LinkedList<>();

	BufferedImage FrameBuffer;


	private final Cursor invisibleCursor = Toolkit.getDefaultToolkit()
			.createCustomCursor(new BufferedImage(1, 1, Transparency.TRANSLUCENT), new Point(0, 0), "InvisibleCursor");

	private boolean localInvActive = true;
	private final int MAXSAMPLES = 30;
	private Kernel kernel = new Kernel(3, 3, new float[]
	{ 1f / 40f, 1f / 40f, 1f / 40f, 1f / 40f, 1f / 40f, 1f / 40f, 1f / 40f, 1f / 40f, 1f / 40f });
	private BufferedImageOp op = new ConvolveOp(kernel);

	private float measurement;
	private Vector previousPos;
	private EAngle previousAim;
	private long previousTime, currentTime;
	private Robot rob;

	private boolean running = true;

	boolean firstframe = true;
	

	private BlockFace SelectedFace;
	private Polygon3D SelectedPolygon;
	private Block SelectedBlock = Block.NOTHING;
	private Entity SelectedEntity;
	private float speed = 4.8f;
	private Star[] Stars;
	private int tickindex = 0;
	private final float[] ticklist = new float[MAXSAMPLES];

	private float ticksum = 0;
	private int VisibleCount;
	boolean creative;

	
	public Game2(String location, long seed, LoadMethod loadmethod, JLabel statusLabel) {

		
		Engine = new GameEngine(location, this, seed, loadmethod, statusLabel);
		PE = new PlayerEntity(Engine);
		
		init();
		previousPos = new Vector().set(PE.getPos());
		previousAim = new EAngle(PE.ViewAngle.yaw, PE.ViewAngle.pitch);
		switch(loadmethod) {

		case MULTIPLAYER:

			String[] addr = location.split(":");
			int port = 1420;
			if (addr.length != 1)
			{
				port = Integer.parseInt(addr[1]);
			}
			connected = connect(addr[0], port, false);
			break;
		case EXISTING:
			File playerFile = new File("saves/" + location + "/" + Config.username + ".xml");
			if (playerFile.exists())
			{
					PE.move(Engine.world.loadVector(Config.username, "x", "y", "z"));
					PE.ViewAngle.set(Engine.world.loadVector(Config.username, "yaw", "pitch", "yaw"));
					break;
			}

			//$FALL-THROUGH$
		case GENERATE:
			teleportToSpawn();
		}
		
		Engine.world.addEntity(PE);
		
		SwitchInventory(true);

		
		
	}
	

	private void init()
	{
		addKeyListener(this);
		addMouseListener(this);
		addMouseWheelListener(this);
		addComponentListener(new ComponentAdapter()
		{
			@Override
			public void componentShown(ComponentEvent e)
			{
				Game2.this.requestFocusInWindow();
			}
		});

		setCursor(invisibleCursor);

		McenterX = Main.Frame.getX() + Main.Frame.getWidth() / 2;
		McenterY = Main.Frame.getY() + Main.Frame.getHeight() / 2;

		ViewVector.set(PE.ViewAngle.toVector());

		RightViewVector.set(ViewVector).CrossProduct(PE.VerticalVector);
		TopViewVector.set(RightViewVector).CrossProduct(ViewVector);
		LeftViewVector.set(RightViewVector).multiply(-1);
		BottomViewVector.set(TopViewVector).multiply(-1);

		ViewFrustum = new Frustum(this);

		resizeScreen();

		ViewTo.set(PE.getPos()).add(ViewVector);
		Point2D.Float P = P(ViewTo);
		dx = -Config.zoom * P.x + centerX;
		dy = -Config.zoom * P.y + centerY;

		try
		{
			rob = new Robot();
		} catch (AWTException e)
		{
			e.printStackTrace();
		}

		Stars = new Star[200];
		for (int i = 0; i < Stars.length; i++)
		{
			Stars[i] = new Star(this);
		}

	}

	@Override
	public void paintComponent(Graphics g)
	{
		super.paintComponent(g);
		Thread.currentThread().setPriority(Thread.MAX_PRIORITY);
		previousTime = currentTime;
		currentTime = System.nanoTime();

		if (previousTime == 0L)
		{
			FPS = 60;
			measurement = (float) CalcAverageTick(FPS);
		} else
		{
			FPS = 1000000000f / (currentTime - previousTime);
			measurement = (float) CalcAverageTick(FPS);
		}

		if (Main.focused && centered)
		{
			Point arg0 = MouseInfo.getPointerInfo().getLocation();
			difX = McenterX - (float) (arg0.getX());
			difY = McenterY - (float) (arg0.getY());
		}

		if (Main.focused)
		{
			try
			{
				rob.mouseMove((int) McenterX, (int) McenterY);
				centered = true;
			} catch (Exception e)
			{
				Main.log("Could not center mouse:" + e.getMessage());
			}
		}else{
			Main.log("game not in focus");
			pause();
		}

		BackViewVector.set(ViewVector).CrossProduct(PE.VerticalVector).CrossProduct(PE.VerticalVector);
		FrontViewVector.set(BackViewVector).multiply(-1);
		Controls();
		moved = !previousPos.equals(PE.getPos()) || !previousAim.equals(PE.ViewAngle);
		previousPos.set(PE.getPos());
		previousAim.pitch = PE.ViewAngle.pitch;
		previousAim.yaw = PE.ViewAngle.yaw;

		if (moved || firstframe)
		{

			PE.ViewAngle.normalize();

			ViewVector.set(PE.ViewAngle.toVector());

			RightViewVector.set(ViewVector).CrossProduct(PE.VerticalVector);
			TopViewVector.set(RightViewVector).CrossProduct(ViewVector);
			LeftViewVector.set(RightViewVector).multiply(-1);
			BottomViewVector.set(TopViewVector).multiply(-1);

			if (!locked) ViewFrustum.update();

			ViewTo.set(PE.getPos()).add(ViewVector);
			Point2D.Float P = P(ViewTo);

			dx = -Config.zoom * P.x + centerX;
			dy = -Config.zoom * P.y + centerY;

			firstframe = false;
		}

		Graphics fb = FrameBuffer.createGraphics();
		fb.setColor(Main.skyColor);
		fb.fillRect(0, 0, FrameBuffer.getWidth(), FrameBuffer.getHeight());

		if (!locked)
		{
			for (TextureListener b : TextureBlocks)
			{
				b.updateTexture();
			}

		}

		fb.setColor(Color.WHITE);
		for (int i = 0; i < Stars.length; i++)
		{
			Stars[i].draw(fb);
		}

		Block ViewBlock = Engine.world.getBlockAtF(PE.getPos().x, PE.getPos().y, PE.getPos().z);
		if (ViewBlock != Block.NOTHING && !ViewBlock.transparent)
		{
			for (Polygon3D p : ViewBlock.Polygons)
			{
				p.faceFilter = true;
				p.adjecentFilter = true;
			}
		}

		SelectedPolygon = null;
		VisibleCount = 0;
		SelectedEntity=null;
		
		Objects.parallelStream().filter(o -> o.update()).sorted().forEachOrdered(o ->
		{
			
			o.draw(FrameBuffer, fb);
			
			if(o instanceof Polygon3D) {
				Polygon3D poly = (Polygon3D)o;
				if (poly.AvgDist < 5 && poly.polygon.contains(centerX, centerY))
				{
					SelectedPolygon = poly;
				}
				VisibleCount++;
			}
		});

		fb.dispose();
		
		
		if (SelectedPolygon == null)
		{
			SelectedBlock.select(BlockFace.NONE);
			SelectedBlock = Block.NOTHING;
			SelectedFace = BlockFace.NONE;
		} else
		{
			if (SelectedBlock.HitboxPolygons.containsKey(SelectedPolygon))
			{
				BlockFace newFace = SelectedBlock.HitboxPolygons.get(SelectedPolygon);
				if (newFace != SelectedFace)
				{
					SelectedFace = newFace;
					SelectedBlock.select(SelectedFace);
				}
			} else
			{
				Vector centroid = SelectedPolygon.centroid;
				Block block1 = Engine.world.getBlockAtF(centroid.x, centroid.y, centroid.z);
				if (block1.HitboxPolygons.containsKey(SelectedPolygon))
				{
					SelectedFace = block1.HitboxPolygons.get(SelectedPolygon);
					SelectedBlock.select(BlockFace.NONE);
					SelectedBlock = block1;
					SelectedBlock.select(SelectedFace);
				} else
				{
					HashMap<BlockFace, Block> BlocksNearby;
					if (block1 == Block.NOTHING)
					{
						BlocksNearby = Engine.world.get6Blocks(centroid.x, centroid.y, centroid.z, false);
					} else
					{
						BlocksNearby = Engine.world.get6Blocks(block1, false);
					}

					for (Block block2 : BlocksNearby.values())
					{
						if (block2.HitboxPolygons.containsKey(SelectedPolygon))
						{
							SelectedFace = block2.HitboxPolygons.get(SelectedPolygon);
							SelectedBlock.select(BlockFace.NONE);
							SelectedBlock = block2;
							SelectedBlock.select(SelectedFace);
							break;
						}

					}
					
					if(SelectedBlock == Block.NOTHING) {
						for(Entity e : Engine.world.getAllEntities()) {
							if(e.Objects.contains(SelectedPolygon)) {
								SelectedEntity=e;
								break;
							}
						}
						
					}

				}

			}

		}

		
		if (activeInventory != null)
		{
			g.drawImage(op.filter(FrameBuffer, null), 0, 0, Main.Frame.getWidth(), Main.Frame.getHeight(), null);
		} else
		{
			g.drawImage(FrameBuffer, 0, 0, Main.Frame.getWidth(), Main.Frame.getHeight(), null);
		}

		if (ViewBlock instanceof WaterBlock)
		{
			g.setColor(ViewBlock.Polygons.get(0).s.c);
			g.fillRect(0, 0, Main.Frame.getWidth(), Main.Frame.getHeight());
		}

		((Graphics2D) g).setPaint(new Color(1.0f, 1.0f, 1.0f, 0.2f));

		int cX = Main.Frame.getWidth() / 2;
		int cY = Main.Frame.getHeight() / 2;
		g.drawLine(cX, cY - 20, cX, cY + 20);
		g.drawLine(cX - 20, cY, cX + 20, cY);

		g.setFont(new Font("SANS", Font.BOLD, 15));
		g.setColor(Color.GRAY);

		if (F3)
		{

			debugInfo.clear();
			debugInfo.add("Eye:" + PE.getPos());
			debugInfo.add("yaw: " + Math.round(PE.ViewAngle.yaw) + ", pitch: " + Math.round(PE.ViewAngle.pitch));
			debugInfo.add("FPS (smooth): " + (int) measurement + " - " + FPS);
			debugInfo.add("Selected Block:" + SelectedBlock.getSelectedFace() + ", "+SelectedBlock+","+SelectedBlock.BlockMeta);
			debugInfo.add("SelectedPolygon: "+SelectedPolygon);
			debugInfo.add("SelectedEntity: "+SelectedEntity);
			debugInfo.add("Polygon count: " + VisibleCount + "/" + Objects.size());
			debugInfo.add("Filter locked: " + locked + ", moved: " + moved + ", nopause:" + Main.nopause);
			debugInfo.add("Tick: " + Engine.Tick + "(" + Engine.TickableBlocks.size() + ")");
			debugInfo.add("needUpdate:" + Engine.TickableBlocks.contains(SelectedBlock));
			debugInfo.add("Blocks: " + Engine.world.getSize());
			if (Engine.client != null)
			{
				debugInfo.add("PacketCount: " + Engine.client.packetCount);
				debugInfo.add("BlockCount: " + Engine.client.blockcount);
				Iterator<PlayerMP> iter = playerList.values().iterator();
				for (int i = 0; i < playerList.values().size() * 25; i += 25)
				{
					PlayerMP player = iter.next();
					debugInfo.add("Player"+i+": "+player);
				}
			}
			debugInfo.add("Seed: " + Engine.world.seed);
			debugInfo.add("PE.GravityVelocity: " + PE.GravityVelocity + ", PE.JumpVelocity: " + PE.JumpVelocity + ", result: "
					+ (PE.JumpVelocity - PE.GravityVelocity));
			debugInfo.add("Entities ("+Engine.world.getAllEntities().size()+"): "+Engine.world.getAllEntities());
			debugInfo.add("FirstBlockUnder: " + Engine.world.getBlockUnderEntity(false, true, PE));
			debugInfo.add("feetBlock2:" + Engine.world.getBlockAtF(PE.getPos().x, PE.getPos().y,
					PE.getPos().z - ((1.7f + World.GravityAcceleration / FPS) * PE.VerticalVector.z)));
			debugInfo.add("flying: " + PE.flying);
			//debugInfo.add("Physics FPS: " + (Engine.physics==null ? "" : ""+Engine.physicsFPS) );
				
			
			g.setColor(Color.BLACK);
			g.setFont(new Font(g.getFont().getName(), g.getFont().getStyle(), 15));
			for (int j = 1; j > -1; j--)
			{

				Iterator<String> iter = debugInfo.iterator();
				int i = 0;
				while (iter.hasNext())
				{
					g.drawString(iter.next(), 10, 100 + i + j);
					i += 25;
				}
				g.setColor(Color.WHITE);
				g.setFont(new Font(g.getFont().getName(), g.getFont().getStyle(), 15));
			}
		} else
		{
			g.drawString("FPS: " + (int) measurement, 20, 20);

		}
		// Inventory Inv = Engine.Inv;
		if (Engine.Inv.items.size() > 0)
		{
			Font defaultFont = g.getFont();

			g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 28));

			for (int i = 0; i < Engine.Inv.items.size(); i++)
			{
				ItemStack stack = Engine.Inv.items.get(i);
				BufferedImage icon = Main.Items.get(stack.kind.name).ItemTexture;// stack.kind.getTexture();
				int w = icon.getWidth();
				int h = icon.getHeight();
				g.drawImage(icon, i * w, Main.Frame.getHeight() - h, null);
				int offset = w / 3;
				int textX = i * w + offset;
				int textY = Main.Frame.getHeight() - h;

				if (i != Engine.Inv.hotbarIndex)
				{
					g.setColor(Color.WHITE);
					g.drawString(stack.amount + "", textX, textY);

					g.setColor(Color.BLACK);
					g.drawString(stack.amount + "", textX - 1, textY - 1);

				} else
				{
					g.setColor(Color.BLACK);
					g.drawString(Engine.Inv.SelectedStack.amount + "", textX, textY);

					g.setColor(Color.WHITE);
					g.drawString(Engine.Inv.SelectedStack.amount + "", textX - 1, textY - 1);

				}
			}

			g.setFont(defaultFont);
			if (Engine.Inv.SelectedStack != null)
			{
				g.setColor(Color.BLACK);
				g.drawString(Engine.Inv.SelectedStack.kind.name, 25, Main.Frame.getHeight() - 100);
				g.setColor(Color.WHITE);
				g.drawString(Engine.Inv.SelectedStack.kind.name, 25 - 1, Main.Frame.getHeight() - 100 - 1);
			}

			if (Engine.Inv.hotbarIndex > -1)
			{
				BufferedImage viewmodel = Main.Items.get(Engine.Inv.SelectedStack.kind.name).ViewmodelTexture;
				int w = viewmodel.getWidth();
				int h = viewmodel.getHeight();

				g.drawImage(viewmodel, Main.Frame.getWidth() / 3 * 2, Main.Frame.getHeight() - h, 2 * w, 2 * h, null);
			}

		}

		int mainOffset = 150;
		if (activeInventory != null)
		{
			if (activeInventory.getInv().items.size() > 0)
			{
				Font defaultFont = g.getFont();

				g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 28));

				for (int i = 0; i < activeInventory.getInv().items.size(); i++)
				{
					ItemStack stack = activeInventory.getInv().items.get(i);
					BufferedImage icon = Main.Items.get(stack.kind.name).ItemTexture;// stack.kind.getTexture();
					int w = icon.getWidth();
					int h = icon.getHeight();
					g.drawImage(icon, i * w, Main.Frame.getHeight() - h - mainOffset, null);
					int offset = w / 3;
					int textX = i * w + offset;
					int textY = Main.Frame.getHeight() - h - mainOffset;

					if (i != activeInventory.getInv().hotbarIndex)
					{
						g.setColor(Color.WHITE);
						g.drawString(stack.amount + "", textX, textY);

						g.setColor(Color.BLACK);
						g.drawString(stack.amount + "", textX - 1, textY - 1);

					} else
					{
						g.setColor(Color.BLACK);
						g.drawString(activeInventory.getInv().SelectedStack.amount + "", textX, textY);

						g.setColor(Color.WHITE);
						g.drawString(activeInventory.getInv().SelectedStack.amount + "", textX - 1, textY - 1);

					}
				}

				g.setFont(defaultFont);
				if (activeInventory.getInv().SelectedStack != null)
				{
					g.setColor(Color.BLACK);
					g.drawString(activeInventory.getInv().SelectedStack.kind.name, 25, Main.Frame.getHeight() - 100);
					g.setColor(Color.WHITE);
					g.drawString(activeInventory.getInv().SelectedStack.kind.name, 25 - 1,
							Main.Frame.getHeight() - 100 - 1);
				}

				

			}
			g.setColor(Color.BLACK);
			g.drawString(activeInventory.getBlock().toString(), 25, Main.Frame.getHeight() - 100 - mainOffset);
			g.setColor(Color.WHITE);
			g.drawString(activeInventory.getBlock().toString(), 25 - 1, Main.Frame.getHeight() - 100 - 1 - mainOffset);
		}

		if (running)
		{
			repaint();
		}
	}

	private void Controls()
	{

		if (key[0]) // W
		{
			move(FrontViewVector, speed, PE, FPS, Engine.world);

		}

		if (key[1]) // S
		{
			move(BackViewVector, speed, PE, FPS, Engine.world);

		}
		if (key[2]) // A
		{
			move(LeftViewVector, speed, PE, FPS, Engine.world);

		}

		if (key[3]) // D
		{
			move(RightViewVector, speed, PE, FPS, Engine.world);

		}

		if (difX != 0)
		{
			PE.ViewAngle.yaw -= difX * (Config.sensitivity) / FPS * PE.VerticalVector.z;

		}
		if (difY != 0)
		{
			PE.ViewAngle.pitch += difY * (Config.sensitivity) / FPS * PE.VerticalVector.z;

		}

		if (PE.flying)
		{
			if (key[4])
			{
				PE.getPos().z += speed / FPS * PE.VerticalVector.z;

			}
			if (key[5])
			{
				PE.getPos().z -= speed / FPS * PE.VerticalVector.z;

			}
		}

		if (PE.VerticalVector.z == 1 && PE.getPos().z < 0// Engine.world.CHUNK_HEIGHT / 2
				&& Engine.world.getBlockUnderEntity(true, true, PE) != Block.NOTHING)
		{
			PE.VerticalVector.z = -1;
			Block above = Engine.world.getBlockUnderEntity(false, true, PE);
			if (PE.getPos().z >= above.z - 1.7f)
			{
				PE.getPos().z = above.z - 1.7f;
			}
		} else if (PE.VerticalVector.z == -1 && PE.getPos().z >= 0// Engine.world.CHUNK_HEIGHT / 2
				&& Engine.world.getBlockUnderEntity(true, true, PE) != Block.NOTHING)
		{
			PE.VerticalVector.z = 1;

			Block under = Engine.world.getBlockUnderEntity(false, true, PE);
			if (PE.getPos().z <= under.z + 2.7f)
			{
				PE.getPos().z = under.z + 2.7f;
			}
		}

		
		/*if (key[7])
		{
			PE.fly(true);
		} else
		{
			PE.fly(false);
		}*/
		
		if (!PE.flying)
			if(key[4]) {
				PE.jump();
			}
		
		Vector VerticalVector = PE.VerticalVector;
		if (!Engine.world.getBlockAtF(PE.getPos().x, PE.getPos().y,
				PE.getPos().z - ((1.7f + World.GravityAcceleration / FPS) * VerticalVector.z)).solid)
		{
			PE.GravityVelocity -= World.GravityAcceleration / FPS;
		}

		float resultant = (PE.JumpVelocity + PE.GravityVelocity);
		if (Math.abs(PE.JumpVelocity) + Math.abs(PE.GravityVelocity) != 0f)
		{
			float JumpDistance = resultant / FPS * VerticalVector.z;
			if (resultant < 0)
			{// lefel� esik �nmag�hoz k�pest
				Block under = Engine.world.getBlockUnderEntity(false, true, PE);// world.getBlockAtF(PE.getPos().x,
																			// PE.getPos().y, PE.getPos().z+JumpDistance);
				if (VerticalVector.z == 1)
				{

					if (under != Block.NOTHING && under.z + 1 >= PE.getPos().z - 1.7f + JumpDistance)
					{ // beleesne egy blokkba fel�lr�l
						PE.getPos().z = under.z + 2.7f;
						PE.JumpVelocity = 0;
						PE.GravityVelocity = 0;
					} else
					{
						PE.getPos().z += JumpDistance;
					}
				} else
				{
					if (under != Block.NOTHING && under.z <= PE.getPos().z + 1.7f + JumpDistance)
					{ // beleesne egy blokkba alulr�l
						PE.getPos().z = under.z - 1.7f;
						PE.JumpVelocity = 0;
						PE.GravityVelocity = 0;
					} else
					{
						PE.getPos().z += JumpDistance;
					}
				}
			} else if (resultant > 0)
			{ // felfel� ugrik �nmag�hoz k�pest
				Block above = Engine.world.getBlockUnderEntity(false, false, PE);// world.getBlockAtF(PE.getPos().x,
																				// PE.getPos().y,
																				// PE.getPos().z+JumpDistance);

				if (VerticalVector.z == 1)
				{
					if (above != Block.NOTHING && above.z <= PE.getPos().z + JumpDistance)
					{ // belefejelne egy blokkba alulr�l
						PE.getPos().z = above.z - 0.01f;
						PE.JumpVelocity = 0;
						PE.GravityVelocity = 0;
					} else
					{
						PE.getPos().z += JumpDistance;
					}
				} else
				{
					if (above != Block.NOTHING && above.z + 1 >= PE.getPos().z + JumpDistance)
					{ // belefejelne egy blokkba fel�lr�l
						PE.getPos().z = above.z + 1.01f;
						PE.JumpVelocity = 0;
						PE.GravityVelocity = 0;
					} else
					{
						PE.getPos().z += JumpDistance;
					}

				}
			}

		}

		if (PE.JumpVelocity > 0)
		{
			PE.JumpVelocity = Math.max(0, PE.JumpVelocity - World.GravityAcceleration / FPS);
		}
			

	}

	@Override
	public void keyPressed(KeyEvent arg0)
	{
		if (arg0.getKeyCode() == KeyEvent.VK_W)
		{
			key[0] = true;
			if (activeInventory != null)
				openInventory(null);
		}
		if (arg0.getKeyCode() == KeyEvent.VK_S)
		{
			key[1] = true;
			if (activeInventory != null)
				openInventory(null);
		}
		if (arg0.getKeyCode() == KeyEvent.VK_A)
		{
			key[2] = true;
			if (activeInventory != null)
				openInventory(null);
		}
		if (arg0.getKeyCode() == KeyEvent.VK_D)
		{
			key[3] = true;
			if (activeInventory != null)
				openInventory(null);
		}
		if (arg0.getKeyCode() == KeyEvent.VK_SPACE)
		{
			key[4] = true;
			if (activeInventory != null)
				openInventory(null);
		}
		if (arg0.getKeyCode() == KeyEvent.VK_SHIFT)
		{
			key[5] = true;
		}
		if (arg0.getKeyCode() == KeyEvent.VK_F)
		{
			locked = !locked;
			if (locked)
			{
				Engine.timer.stop();
			} else
			{
				Engine.timer.start();
			}
		}
		if (arg0.getKeyCode() == KeyEvent.VK_F3)
		{
			F3 = !F3;
		}
		if (arg0.getKeyCode() == KeyEvent.VK_F6)
		{
			if (Engine.client == null && Engine.server == null)
			{
				Engine.server = new GameServer(Engine);
				Engine.server.start();
				Main.log("Server started");

				String error = connect("localhost", Engine.server.Listener.acceptThread.port, true);
				if (error != null)
				{
					JOptionPane.showInternalMessageDialog(Main.Frame.getContentPane(), error,
							"Could not connect to server", JOptionPane.ERROR_MESSAGE);
					Main.err(error);
					disconnect();
				}
			}
		}

		if (arg0.getKeyCode() == KeyEvent.VK_ESCAPE)
		{
			pause();
		}

		/*if (arg0.getKeyCode() == KeyEvent.VK_E)
		{
			PE.GravityVelocity = -200;
		}*/

		if (arg0.getKeyCode() == KeyEvent.VK_T)
		{
			key[6] = true;
		}
		if (arg0.getKeyCode() == KeyEvent.VK_CONTROL)
		{
			key[7] = !key[7];
			if (key[7])
			{
				speed = 8f;
			} else
			{
				speed = 4.7f;
			}
		}

		if (arg0.getKeyCode() == KeyEvent.VK_G)
		{
			String itemName = JOptionPane.showInputDialog(Main.Frame.getContentPane(), "Item name: (case-sensitive)",
					"Give", JOptionPane.QUESTION_MESSAGE);
			if (itemName != null && Main.Items.get(itemName) != null)
			{
				Engine.Inv.add(Main.Items.get(itemName), 1, true);
			} else
			{
				Main.err("Give: no such item");
			}
		}
		if (arg0.getKeyCode() == KeyEvent.VK_Q)
		{
			if (localInvActive && activeInventory != null && activeInventory.getInv().items.size() > 0)
			{
				SwitchInventory(false);
			} else if (!localInvActive && Engine.Inv.items.size() > 0)
			{
				SwitchInventory(true);
			}
		}
		if (arg0.getKeyCode() == KeyEvent.VK_P)
		{
			Main.nopause=!Main.nopause;
		}
		if (arg0.getKeyCode() == KeyEvent.VK_F2)
		{
			File outputfile = new File(Config.username+"-"+new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss").format(new Date())+".png");
			try
			{
				ImageIO.write(FrameBuffer, "png", outputfile);
			} catch (IOException e)
			{
				e.printStackTrace();
			}
		}
	}

	@Override
	public void keyReleased(KeyEvent arg0)
	{
		if (arg0.getKeyCode() == KeyEvent.VK_W)
		{
			key[0] = false;
		}
		if (arg0.getKeyCode() == KeyEvent.VK_S)
		{
			key[1] = false;
		}
		if (arg0.getKeyCode() == KeyEvent.VK_A)
		{
			key[2] = false;
		}
		if (arg0.getKeyCode() == KeyEvent.VK_D)
		{
			key[3] = false;
		}
		if (arg0.getKeyCode() == KeyEvent.VK_SPACE)
		{
			key[4] = false;
		}
		if (arg0.getKeyCode() == KeyEvent.VK_SHIFT)
		{
			key[5] = false;
		}
		if (arg0.getKeyCode() == KeyEvent.VK_T)
		{
			key[6] = false;
		}

	}

	@Override
	public void mouseExited(MouseEvent arg0)
	{

	}

	@Override
	public void mousePressed(MouseEvent arg0)
	{
		if (activeInventory == null)
		{
			if (arg0.getButton() == MouseEvent.BUTTON1)
			{
				if (SelectedBlock != Block.NOTHING && SelectedBlock != null)
				{
					if (SelectedBlock instanceof BlockInventoryInterface
							&& activeInventory == (((BlockInventoryInterface) SelectedBlock)))
					{
						SwitchInventory(true);
					}
					if (Engine.client == null)
					{
						if (SelectedBlock instanceof BreakListener)
						{
							((BreakListener) SelectedBlock).breaked(null);
						}
						Engine.world.destroyBlock(SelectedBlock);
					} else
					{
						Engine.client.sendData(("06," + Config.username + "," + SelectedBlock.x + "," + SelectedBlock.y
								+ "," + SelectedBlock.z));
					}
					if (SelectedBlock.returnOnBreak
							&& ((SelectedBlock instanceof WaterBlock && (((WaterBlock) SelectedBlock).getHeight() == 4))
									|| !(SelectedBlock instanceof WaterBlock)))
					{
						Item item = Main.Items.get(SelectedBlock.name);
						if (Engine.Inv.items.size() == 0)
						{
							Engine.Inv.add(item, 1, true);
							SwitchInventory(false);
						} else
						{
							Engine.Inv.add(item, 1, true);
						}

					}
				}else if(SelectedEntity != null) {
					Engine.world.killEntity(SelectedEntity.ID);
					
				}
				
			}

			if (arg0.getButton() == MouseEvent.BUTTON3)
			{
				if (SelectedBlock != Block.NOTHING)
				{
					if (!key[5])
					{
						if (Engine.Inv.hotbarIndex > -1)
						{
							if (Engine.Inv.SelectedStack.kind.className.contains("ml.sakii.factoryisland.blocks") && placeBlock(Engine.Inv.SelectedStack.kind.className))
							{
								Engine.Inv.add(Engine.Inv.SelectedStack.kind, -1, true);
							}
						}
					} else if (SelectedBlock instanceof InteractListener)
					{
						((InteractListener) SelectedBlock).interact(SelectedFace);
						if (SelectedBlock instanceof BlockInventoryInterface)
						{
							PlayerInventory otherInv = ((BlockInventoryInterface) SelectedBlock).getInv();
							if (Engine.Inv.items.size() == 0 && otherInv.items.size() > 0)
							{
								SwitchInventory(false);
							}
						}
					}
				}

			}
		}
		if (arg0.getButton() == MouseEvent.BUTTON2)
		{
			if (localInvActive)
			{
				SwapItems(false, Engine.Inv.SelectedStack.kind.name);
			} else
			{
				SwapItems(true, activeInventory.getInv().SelectedStack.kind.name);
			}

		}

	}

	public Point2D convert3Dto2D(Vector v)
	{
		Point2D.Float P = P(v);
		float x2d = dx + Config.zoom * P.x;
		float y2d = dy + Config.zoom * P.y;

		P.setLocation(x2d, y2d);
		return P;

	}

	@Override
	public void mouseReleased(MouseEvent arg0)
	{

	}

	@Override
	public void mouseWheelMoved(MouseWheelEvent arg0)
	{
		if (arg0.getWheelRotation() > 0)
		{ // UP/AWAY
			if (localInvActive)
			{
				Engine.Inv.wheelUp();
			} else
			{
				activeInventory.getInv().wheelUp();
			}

		}

		if (arg0.getWheelRotation() < 0)
		{ // DOWN/TOWARDS
			if (localInvActive)
			{
				Engine.Inv.wheelDown();
			} else
			{
				activeInventory.getInv().wheelDown();
			}
		}

	}

	public void openInventory(BlockInventoryInterface inv)
	{
		activeInventory = inv;
		if (activeInventory != null)
		{
			activeInventory.getInv().hotbarIndex = -1;
			activeInventory.getInv().SelectedStack = null;
			// activeInventory.setIndex(-1);
		} else
		{
			SwitchInventory(true);
		}
	}

	private String connect(String IP, int port, boolean sendPos)
	{

		Engine.client = new GameClient(this);
		String error = Engine.client.connect(IP, port, sendPos);
		if (error == null)
		{
			Engine.client.start();
			Main.log("Client started");
			return null;
		}
		Main.log("Unable to start client (" + error + ")");

		return error;

	}

	public void disconnect()
	{
		running = false;
		Engine.timer.stop();
		Engine.stopPhysics();
		if (Engine.client != null)
		{
			Main.log("disconnecting from multiplayer");
			Engine.client.kill();

			if (Engine.server != null)
			{
				Engine.server.kill();
			}

			playerList.clear();

		} else
		{
			Engine.world.saveByShutdown();
		}
		Objects.clear();
		//Entities.clear();
		Main.SwitchWindow("mainmenu");
		Main.Base.remove(this);
		Main.GAME = null;
		System.gc();
	}

	void pause()
	{
		running = false;
		if (Engine.server == null) {
			Engine.timer.stop();
			Engine.stopPhysics();
		}
			
		Main.focused = false;
		centered = false;
		setCursor(Cursor.getDefaultCursor());
		removeKeyListener(this);
		removeMouseListener(this);
		removeMouseWheelListener(this);
		for (int i = 0; i < key.length; i++)
		{
			key[i] = false;
		}

		Main.PausedBG = op.filter(FrameBuffer, null);
		Main.SwitchWindow("pause");
	}

	void resume()
	{
		Main.focused = true;
		addKeyListener(this);
		addMouseListener(this);
		addMouseWheelListener(this);
		setCursor(invisibleCursor);
		if (Engine.server == null) {
			Engine.timer.start();
			Engine.startPhysics();
		}
		firstframe = true;
		running = true;
		Main.SwitchWindow("game");
		currentTime=System.nanoTime();

	}

	void resizeScreen()
	{

		FrameBuffer = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice()
				.getDefaultConfiguration().createCompatibleImage(Config.width, Config.height);

		centerX = FrameBuffer.getWidth() / 2;
		centerY = FrameBuffer.getHeight() / 2;

		ViewFrustum.hratio = centerX / Config.zoom;
		ViewFrustum.vratio = centerY / Config.zoom;

		ratio = (FrameBuffer.getWidth() * 1f / Main.Frame.getWidth()
				+ FrameBuffer.getHeight() * 1f / Main.Frame.getHeight()) / 2;
		margin = (int) (5 * ratio);

	}

	private double CalcAverageTick(float newtick)
	{
		ticksum -= ticklist[tickindex]; /* subtract value falling off */
		ticksum += newtick; /* add new value */
		ticklist[tickindex] = newtick; /* save new value so it can be subtracted later */
		if (++tickindex == MAXSAMPLES)
		{
			tickindex = 0;
		}

		/* return average */
		return ticksum / MAXSAMPLES;
	}

	public void SwitchInventory(boolean local)
	{
		if (!local && activeInventory != null)
		{
			/*
			 * if(localInvActive) { Engine.Inv.hotbarIndex=-1;
			 * Engine.Inv.SelectedStack=null;
			 * 
			 * if(activeInventory.getInv().items.size()>0) {
			 * activeInventory.getInv().hotbarIndex=0;
			 * activeInventory.getInv().SelectedStack=activeInventory.getInv().items.get(0);
			 * } }else {
			 */
			Engine.Inv.hotbarIndex = -1;
			Engine.Inv.SelectedStack = null;

			if (activeInventory.getInv().items.size() > 0)
			{
				activeInventory.getInv().hotbarIndex = 0;
				activeInventory.getInv().SelectedStack = activeInventory.getInv().items.get(0);
			}

			// }
			localInvActive = false;
		} else
		{
			if (activeInventory != null)
			{
				activeInventory.getInv().hotbarIndex = -1;
				activeInventory.getInv().SelectedStack = null;
			}

			if (Engine.Inv.items.size() > 0)
			{
				Engine.Inv.hotbarIndex = 0;
				Engine.Inv.SelectedStack = Engine.Inv.items.get(0);
			}

			localInvActive = true;
		}
	}

	

	private static HashMap<BlockFace, Block> getBlocksCollidingWithPlayer(float x, float y, float z, World world, Entity entity)
	{
		HashMap<BlockFace, Block> result = new HashMap<>();
		int dx = (int) Math.floor(x);
		int dy = (int) Math.floor(y);
		int dz1 = (int) Math.floor(z);
		int dz2 = (int) Math.floor(z - 1f * entity.VerticalVector.z);
		int dz3 = (int) Math.floor(z - 1.699f * entity.VerticalVector.z);

		result.put(BlockFace.TOP, world.getBlockAt(dx, dy, dz1));
		result.put(BlockFace.NONE, world.getBlockAt(dx, dy, dz2));
		result.put(BlockFace.BOTTOM, world.getBlockAt(dx, dy, dz3));

		return result;

	}

	static boolean move(Vector direction, float coefficient, Entity entity, float FPS, World world)
	{
		boolean success=true;
		float targetX, targetY;
		float nextX = entity.getPos().x + direction.x * coefficient / FPS;
		float nextY = entity.getPos().y + direction.y * coefficient / FPS;

		HashMap<BlockFace, Block> blocks6X = getBlocksCollidingWithPlayer(nextX+Math.copySign(World.BLOCK_RANGE, direction.x), entity.getPos().y, entity.getPos().z, world, entity);
		HashMap<BlockFace, Block> blocks6Y = getBlocksCollidingWithPlayer(entity.getPos().x, nextY+Math.copySign(World.BLOCK_RANGE, direction.y), entity.getPos().z, world, entity);
		Block nextBlockX1 = blocks6X.get(BlockFace.TOP);
		Block nextBlockX2 = blocks6X.get(BlockFace.NONE);
		Block nextBlockX3 = blocks6X.get(BlockFace.BOTTOM);

		Block nextBlockY1 = blocks6Y.get(BlockFace.TOP);
		Block nextBlockY2 = blocks6Y.get(BlockFace.NONE);
		Block nextBlockY3 = blocks6Y.get(BlockFace.BOTTOM);

		if (!nextBlockX1.solid && !nextBlockX2.solid && !nextBlockX3.solid)
		{
			targetX = nextX;
		}else {
			int bx;
			if(nextBlockX1.solid) {
				bx=nextBlockX1.x;
			//	by=nextBlockX1.y;
			}else if(nextBlockX2.solid) {
				bx=nextBlockX2.x;
			//	by=nextBlockX2.y;
			}else{
				bx=nextBlockX3.x;
			//	by=nextBlockX3.y;
			}
			
			
			if(direction.x<0) {
				targetX=bx+1+World.BLOCK_RANGE;
			}else {
				targetX=bx-World.BLOCK_RANGE;


				
			}
			success=false;
		}
		if (!nextBlockY1.solid && !nextBlockY2.solid && !nextBlockY3.solid)
		{
			targetY = nextY;
		}else {
			
			int by;
			if(nextBlockY1.solid) {
			//	bx=nextBlockX1.x;
				by=nextBlockY1.y;
			}else if(nextBlockY2.solid) {
			//	bx=nextBlockX2.x;
				by=nextBlockY2.y;
			}else{
			//	bx=nextBlockX3.x;
				by=nextBlockY3.y;
			}
			
			if(direction.y<0) {
				targetY=by+1+World.BLOCK_RANGE;
			}else {
				targetY=by-World.BLOCK_RANGE;


				
			}
			
			success=false;
		}
		
		entity.move(targetX, targetY, entity.getPos().z);
		
		return success;


	}

	private Point2D.Float P(Vector v)
	{

		float t = v.substract(PE.getPos()).DotProduct(ViewVector);
		v.multiply(1 / t).add(PE.getPos());

		// Vector ViewToPoint = v.cpy().substract(PE.getPos());
		// ViewToPoint.set(v);
		// ViewToPoint.substract(PE.getPos());

		// float t = ViewVector.DotProduct(ViewToPoint);

		// ViewToPoint.multiply(1/t).add(PE.getPos());

		return new Point2D.Float(RightViewVector.DotProduct(v), BottomViewVector.DotProduct(v));
	}

	@Override
	public void keyTyped(KeyEvent arg0)
	{

	}

	@Override
	public void mouseClicked(MouseEvent arg0)
	{

	}

	@Override
	public void mouseEntered(MouseEvent arg0)
	{

	}

	private boolean placeBlock(String className)
	{
		int nextX = SelectedBlock.x + SelectedFace.direction[0];
		int nextY = SelectedBlock.y + SelectedFace.direction[1];
		int nextZ = SelectedBlock.z + SelectedFace.direction[2];

		Block placeable = Engine.createBlockByClass(className, nextX, nextY, nextZ);
		if (!placeable.canBePlacedOn.isEmpty() && !placeable.canBePlacedOn
				.contains(Engine.world.getBlockAt(placeable.x, placeable.y, placeable.z - 1).name))
		{
			return false;
		}
		boolean success = Engine.world.addBlock(placeable, false);
		if (success)
		{
			if (Engine.client != null)
			{
				for (Entry<String, Item> entry : Main.Items.entrySet())
				{
					String BlockName = entry.getKey();
					String ClassName = entry.getValue().className;
					if (ClassName.equals(className))
					{
						Engine.client.sendData(
								("05," + Config.username + "," + nextX + "," + nextY + "," + nextZ + "," + BlockName));
					}
				}
			}
			if (placeable instanceof PlaceListener)
			{
				((PlaceListener) placeable).placed(SelectedFace);
			}
			return true;

		}
		return false;

	}

	private void SwapItems(boolean addToLocal, String itemName)
	{
		if (Engine.client == null)
		{
			if (!addToLocal)
			{
				if (Engine.Inv.items.size() > 0 && activeInventory != null)
				{
					Item removedFromLocal = Engine.Inv.SelectedStack.kind;
					activeInventory.getInv().add(removedFromLocal, 1, true);
					Engine.Inv.add(removedFromLocal, -1, true);
				}
				if (Engine.client == null && Engine.Inv.items.size() == 0)
					SwitchInventory(false);

			} else
			{
				Item removedFromActiveInv = activeInventory.getInv().SelectedStack.kind;
				Engine.Inv.add(removedFromActiveInv, 1, true);
				activeInventory.getInv().add(removedFromActiveInv, -1, true);

				// if(Engine.client == null &&
				if (activeInventory.getInv().items.size() == 0)
					SwitchInventory(true);

			}

		} else
		{
			// 14,Sakii,1,2,3,Stone,true (add to local)
			Engine.client.sendData(
					"14," + Config.username + "," + activeInventory.getBlock().x + "," + activeInventory.getBlock().y
							+ "," + activeInventory.getBlock().z + "," + itemName + "," + addToLocal);
		}
	}

	private void teleportToSpawn()
	{
		Block SpawnBlock = Engine.world.getSpawnBlock();
		PE.move(SpawnBlock.x + 0.5f, SpawnBlock.y + 0.5f, SpawnBlock.z + 2.7f);
	}

}
