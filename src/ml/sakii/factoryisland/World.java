package ml.sakii.factoryisland;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import javax.swing.JLabel;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import ml.sakii.factoryisland.blocks.Block;
import ml.sakii.factoryisland.blocks.BlockFace;
import ml.sakii.factoryisland.blocks.BlockInventoryInterface;
import ml.sakii.factoryisland.blocks.TextureListener;
import ml.sakii.factoryisland.blocks.TickListener;
import ml.sakii.factoryisland.entities.Alien;
import ml.sakii.factoryisland.entities.Entity;
import ml.sakii.factoryisland.entities.PlayerEntity;
import ml.sakii.factoryisland.items.PlayerInventory;
import ml.sakii.factoryisland.items.ItemType;
import ml.sakii.factoryisland.net.PlayerMPData;

public class World {

	final int CHUNK_WIDTH = 10;
	//int CHUNK_HEIGHT = 40;
	int MAP_RADIUS = 5;
	//int index = -1; // -1=REMOTE MAP
	public String worldName=""; //""=REMOTE MAP
	long seed;
	static final float BLOCK_RANGE = 0.2f;
	static final float GravityAcceleration=9.81f;
	
	private GameEngine Engine;
	private ConcurrentHashMap<Point3D, Block> Blocks = new ConcurrentHashMap<>(10000);
	private Game game;
	private PlayerInventory tmpInventory;
	
	private ConcurrentHashMap<Long, Entity> Entities = new ConcurrentHashMap<>();
	
	ArrayList<Vector> SpawnableSurface = new ArrayList <>();
	private int worldTop,worldBottom;

	
	public World(String worldName, GameEngine engine, Game game, boolean existing, JLabel statusLabel) {
		Engine = engine;
		this.game = game;
		this.worldName = worldName;
		
		
		
		if(existing) {
			loadWorld(engine, statusLabel);
		}else if(Config.creative){
			tmpInventory = new PlayerInventory(engine);
			game.creative=true;
			engine.Inv=PlayerInventory.Creative;
		}
		
		
	}

	private void loadWorld(GameEngine engine, JLabel statusLabel) {
		File file = new File("saves/" + worldName + "/map.xml");
		/*if (!file.exists()) {

			return;
		}*/

		DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
		DocumentBuilder db;
		Document document;
		try {
			db = dbf.newDocumentBuilder();
			document = db.parse(file);
		} catch (Exception e) {
			e.printStackTrace();
			return;
		}

		Node World = document.getElementsByTagName("blocks").item(0);
		//Node root = document.getElementsByTagName("world").item(0);
		//CHUNK_HEIGHT = Integer.parseInt(((Element) root).getAttribute("height"));

		NodeList Blocks = World.getChildNodes();

		for (int i = 0; i < Blocks.getLength(); i++) {
			
			statusLabel.setText("Loading blocks... "+i+"/"+Blocks.getLength()+" - Location");
			Node Block = Blocks.item(i);
			Element BE = (Element) Block;
			Block b = engine.createBlockByName(BE.getAttribute("name"), Integer.parseInt(BE.getAttribute("x")),
					Integer.parseInt(BE.getAttribute("y")), Integer.parseInt(BE.getAttribute("z")));

			NodeList nodes = Block.getChildNodes();
			
			
			statusLabel.setText("Loading blocks... "+i+"/"+Blocks.getLength()+" - Metadata");
			b.BlockMeta.clear();
			NodeList Metadata = nodes.item(0).getChildNodes();
			for (int j = 0; j < Metadata.getLength(); j++) {
				Node item = Metadata.item(j);
				b.BlockMeta.put(item.getNodeName(), item.getTextContent());
			}
			
			statusLabel.setText("Loading blocks... "+i+"/"+Blocks.getLength()+" - Powers");

			b.powers.clear();
			NodeList Powers = nodes.item(1).getChildNodes();
			for (int j = 0; j < Powers.getLength(); j++) {
				Node item = Powers.item(j);
				b.powers.put(BlockFace.valueOf(item.getNodeName()), Integer.parseInt(item.getTextContent()));
			}
			
			statusLabel.setText("Loading blocks... "+i+"/"+Blocks.getLength()+" - Block Inventory");

			if(b instanceof BlockInventoryInterface) {
				((BlockInventoryInterface) b).getInv().clear();
				NodeList Stacks = nodes.item(2).getChildNodes();
				for (int j = 0; j < Stacks.getLength(); j++) {
					Node item = Stacks.item(j);
					((BlockInventoryInterface) b).getInv().add(Main.Items.get(item.getNodeName()), Integer.parseInt(item.getTextContent()), false);
				}
				
			}
			
			statusLabel.setText("Loading blocks... "+i+"/"+Blocks.getLength()+" - Adding Block");
			addBlockNoReplace(b, true);
			//ReplaceBlock(b, true);
			//b.onLoad();

		}

		
		
		statusLabel.setText("Loading entities...");
		Node entitiesGroup = document.getElementsByTagName("entities").item(0);
		if(entitiesGroup != null) { // kompatibilit�s
			NodeList entities = entitiesGroup.getChildNodes();
			for (int i = 0; i < entities.getLength(); i++) {
				Main.log("loading entity "+i);
				Node entity = entities.item(i);
				Element entityElement = (Element)entity;
				Entity e = Entity.createEntity(entity.getNodeName(),
						Vector.parseVector(entityElement.getAttribute("pos")),
						EAngle.parseEAngle(entityElement.getAttribute("aim")),
						entityElement.getAttribute("name"),
						Long.parseLong(entityElement.getAttribute("id")),
						engine);
				if(e != null) addEntity(e);
			
			}
		}
		
		statusLabel.setText("Loading misc data...");
		int tick = Integer.parseInt(document.getElementsByTagName("tick").item(0).getTextContent());
		engine.Tick = tick;
		
		long seed = Long.parseLong(document.getElementsByTagName("seed").item(0).getTextContent());
		this.seed = seed;

		
		statusLabel.setText("Loading player inventory...");
		PlayerInventory loadedInv = loadInv(Config.username, null);
		if(Config.creative) {
			tmpInventory = loadedInv;
			game.creative=true;
			engine.Inv=PlayerInventory.Creative;
		}else {
			game.creative=false;
			if(!loadedInv.items.isEmpty())
				for(Entry<ItemType, Integer> stack : loadedInv.items.entrySet()) {
					engine.Inv.add(stack.getKey(), stack.getValue(), false);
			}
		}
		
	}
	
	public PlayerInventory loadInv(String username, GameEngine engine) {
		

		PlayerInventory output = new PlayerInventory(engine);

		File file = new File("saves/" + worldName + "/"+username+".xml");
		if (!file.exists()) {
			return output;
		}

		DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
		DocumentBuilder db;
		Document document;
		try {
			db = dbf.newDocumentBuilder();
			document = db.parse(file);
		} catch (Exception e) {
			e.printStackTrace();
			return output;
		}
		
		Node players = document.getElementsByTagName(username).item(0);
		//Node localPlayer = players.getFirstChild();
		if(players == null) {
			Main.err("No data of "+username+" in player file!");
			return output;
		}
		NodeList stacks = players.getChildNodes();
		for (int i = 0; i < stacks.getLength(); i++) {
			Node stack = stacks.item(i);
			output.add(Main.Items.get(stack.getNodeName()),
					Integer.parseInt(stack.getTextContent()), false);
		}

		
		return output;
		
		
	}

	public Vector loadVector(String username, String param1, String param2, String param3) {
		Vector output=new Vector(0,0,0);
		File file = new File("saves/" + worldName + "/"+username+".xml");
		if (!file.exists()) {
			return null;
		}

		DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
		DocumentBuilder db;
		Document document;
		try {
			db = dbf.newDocumentBuilder();
			document = db.parse(file);
		} catch (Exception e) {
			e.printStackTrace();
			return output;
		}
		
		Node players = document.getElementsByTagName(username).item(0);
		//Node localPlayer = players.getFirstChild();
		if(players == null) {
			Main.err("No data of "+username+" in player file!");
			return output;
		}
		/*NodeList stacks = players.getChildNodes();
		for (int i = 0; i < stacks.getLength(); i++) {
			Node stack = stacks.item(i);
			output.addMore(Main.Items.get(stack.getNodeName()),
					Integer.parseInt(stack.getTextContent()));
		}*/
		NamedNodeMap nnm = players.getAttributes();
		
		output.x = Float.parseFloat(nnm.getNamedItem(param1).getNodeValue());
		output.y = Float.parseFloat(nnm.getNamedItem(param2).getNodeValue());
		output.z = Float.parseFloat(nnm.getNamedItem(param3).getNodeValue());
		
		
		return output;
	}
	
	public Entity getEntity(long ID){
		return Entities.get(ID);
	}
	
	public Collection<Entity> getAllEntities(){
		return Entities.values();
	}
	
	
 	public Block getBlockAt(int x, int y, int z) {
		
		Point3D p = new Point3D(x, y, z);
		return getBlockAtP(p);

	}
 	
 	public Block getBlockAtP(Point3D p) {
		Block b = Blocks.get(p);

		return (b == null) ? Block.NOTHING : b ;
 	}

	public Block getBlockAtF(float x, float y, float z) {

		return getBlockAt((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));
	}
	
	public boolean addBlockNoReplace(Block b, boolean resend) {
		if(getBlockAt(b.x, b.y, b.z) == Block.NOTHING) {
			if(resend && Engine.client != null) {
				StringBuilder sb = new StringBuilder();
				sb.append("05,"+b.name+","+b.x+","+b.y+","+b.z);
				for(Entry<String,String> entry : b.BlockMeta.entrySet()) {
					sb.append(",");
					sb.append(entry.getKey()+","+entry.getValue());
				}
				Engine.client.sendData(sb.toString());
			}else {
				ReplaceBlock(b);
			}
			return true;
		}
		return false;
	}
	
	public void addBlockReplace(Block b, boolean resend) {
		Block existing =getBlockAt(b.x, b.y, b.z); 
		if(existing != Block.NOTHING) {
			destroyBlock(existing, resend);
		}
			if(resend && Engine.client != null) {
				//Engine.client.sendData("05,"+b.name+","+b.x+","+b.y+","+b.z);
				StringBuilder sb = new StringBuilder();
				sb.append("05,"+b.name+","+b.x+","+b.y+","+b.z);
				for(Entry<String,String> entry : b.BlockMeta.entrySet()) {
					sb.append(",");
					sb.append(entry.getKey()+","+entry.getValue());
				}
				Engine.client.sendData(sb.toString());
			}else {
				ReplaceBlock(b);
			}
			//return false;
		//}else {
			/*if(resend && Engine.client != null) {
				Engine.client.sendData("05,"+b.name+","+b.x+","+b.y+","+b.z);
			}else {
				ReplaceBlock(b);
			}*/
			//return true;
		//}
	}
	
	private void ReplaceBlock(Block b) {

		
		ArrayList<Block> sources=new ArrayList<>(); // kikapcsolja az osszes fenyforrast es elmenti oket
		for(Block nearby : get6Blocks(b, false).values()) {
			for(Polygon3D poly : nearby.Polygons) {
				Set<Block> sources2 = poly.getSources();
				
				for(Block source : new ArrayList<>(sources2)) {
					sources.add(source);
					removeLight(source.pos, source, source.lightLevel, new HashMap<Point3D,Integer>());
				}
			}
		}
		
		
		Blocks.put(b.pos, b);
		for (Block entry : get6Blocks(b, false).values()) {
			if (entry instanceof TickListener) {
				Engine.TickableBlocks.add((TickListener) entry);
			}
		}
		if (b instanceof TickListener) {
			Engine.TickableBlocks.add((TickListener) b);
		}
		
		if (b instanceof TextureListener) {
			game.TextureBlocks.add((TextureListener)b);
		}
		
		filterAdjecentBlocks(b);

		if(game != null) {
			game.Objects.addAll(b.Polygons);
		}
		

		
		
		for(Block source : sources) { //elterjeszti az elmentett a fenyforrasokat
			addLight(source.pos, source, source.lightLevel, new HashMap<Point3D,Integer>());
		}
		
		if(b.lightLevel>0) { //ha ad ki fenyt akkor elterjeszti
			addLight(b.pos, b, b.lightLevel, new HashMap<Point3D,Integer>());
		}
		
		if(b.z>worldTop) {
			worldTop=b.z;
		}
		
		if(b.z<worldBottom) {
			worldBottom=b.z;
		}
		
		//return true;
	}


	public void destroyBlock(Block b, boolean resend) {
		if(resend && Engine.client != null) {
			Engine.client.sendData(("06," + b.x + "," + b.y	+ "," + b.z));

		}else {
			Block local = getBlockAtP(b.pos);
			if (local == Block.NOTHING) {
				return;
			}
	
			
			for (Block bu : get6Blocks(b, false).values()) {
				if (bu instanceof TickListener) {
					Engine.TickableBlocks.add((TickListener) bu);
				}
			}
			
			Blocks.remove(b.pos);
			
			if (b instanceof TickListener) {
				Engine.TickableBlocks.remove(b);
			}
			
			if (b instanceof TextureListener) {
				game.TextureBlocks.remove(b);
			}
	
			filterAdjecentBlocks(b);
	
			if(game != null) {
				game.Objects.removeAll(b.Polygons);
			}
			

			if(b.lightLevel>0)
				removeLight(b.pos, b, b.lightLevel, new HashMap<Point3D,Integer>());
			
			for(Polygon3D poly : b.Polygons) { // kiuteskor eleg ujraszamolni a kozeli forrasokat
				//for(Block source : poly.lightSources.keySet()) {
				for(Block source : poly.getSources()) {
					if(source!=b)
						addLight(source.pos, source, source.lightLevel, new HashMap<Point3D,Integer>()); //valojaban csak az uj blokkokhoz adodik hozza 
				}
			}
		}
	}
	
	public void addEntity(Entity e) {
		Entities.put(e.ID, e);
		
		if(Engine.server != null && Engine.client != null) {
			Engine.client.sendData("15,"+e.className+","+e.getPos()+","+e.ViewAngle.yaw+","+e.ViewAngle.pitch+","+e.name+","+e.ID);
			
		}
		if(game != null) {
			game.Objects.addAll(e.Objects);
		}
	
	}
	
	
	public void killEntity(long ID, boolean resend) {
		
		if(resend && Engine.client != null) { 
			Engine.client.sendData("17,"+ID);
			
		}else {
			Entity e = Entities.get(ID);
			if(game != null && e != null) {
					game.Objects.removeAll(e.Objects);
			}
			
			 Entities.remove(ID);
		}
		
	}

	boolean walk(Vector direction, float coefficient, Entity entity, float FPS, boolean resend)
	{
		boolean success=true;
		float targetX, targetY;
		float nextX = entity.getPos().x + direction.x * coefficient / FPS;
		float nextY = entity.getPos().y + direction.y * coefficient / FPS;
		Point3D coords = entity.tmpPoint;//new Point3D();
		Block[] blocks6X = getCollidingBlocks(nextX+Math.copySign(World.BLOCK_RANGE, direction.x), entity.getPos().y, entity.getPos().z, entity, coords);
		Block[] blocks6Y = getCollidingBlocks(entity.getPos().x, nextY+Math.copySign(World.BLOCK_RANGE, direction.y), entity.getPos().z, entity, coords);
		Block nextBlockX1 = blocks6X[0];//.get(BlockFace.TOP);
		Block nextBlockX2 = blocks6X[1];//.get(BlockFace.NONE);
		Block nextBlockX3 = blocks6X[2];//.get(BlockFace.BOTTOM);

		Block nextBlockY1 = blocks6Y[0];//.get(BlockFace.TOP);
		Block nextBlockY2 = blocks6Y[1];//.get(BlockFace.NONE);
		Block nextBlockY3 = blocks6Y[2];//.get(BlockFace.BOTTOM);

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

		entity.move(targetX, targetY, entity.getPos().z, resend);
		return success;


	}
	
	 Block[] getCollidingBlocks(float x, float y, float z, Entity entity, Point3D p)
	{
		//HashMap<BlockFace, Block> result = new HashMap<>();
		 Block[] result = new Block[3];
		int dx = (int) Math.floor(x);
		int dy = (int) Math.floor(y);
		int dz1 = (int) Math.floor(z);
		int dz2 = (int) Math.floor(z - 1f * entity.VerticalVector.z);
		int dz3 = (int) Math.floor(z - 1.699f * entity.VerticalVector.z);

		/*result.put(BlockFace.TOP, world.getBlockAt(dx, dy, dz1));
		result.put(BlockFace.NONE, world.getBlockAt(dx, dy, dz2));
		result.put(BlockFace.BOTTOM, world.getBlockAt(dx, dy, dz3));*/
		p.set(dx, dy, dz1);
		result[0]=getBlockAtP(p);
		
		p.set(dx, dy, dz2);
		result[1]=getBlockAtP(p);
		
		p.set(dx, dy, dz3);
		result[2]=getBlockAtP(p);
		
		return result;

	}
	
	public HashMap<BlockFace, Block> get6Blocks(Block center, boolean includeNothing) {
		return get6Blocks(new Point3D().set(center.x, center.y, center.z), includeNothing); //masolni kell point3d-t mert felulirja

	}

	public HashMap<BlockFace, Block> get6Blocks(float xf, float yf, float zf, boolean includeNothing) {
		return get6Blocks(new Point3D().set(xf, yf, zf), includeNothing);
		
	}
	
	public HashMap<BlockFace, Block> get6Blocks(Point3D p, boolean includeNothing){
		int x = p.x;
		int y = p.y;
		int z = p.z;
		HashMap<BlockFace, Block> result = new HashMap<>();

		p.set(x, y, z + 1);
		Block top = getBlockAtP(p);
		if (top != Block.NOTHING || includeNothing) {
			result.put(BlockFace.TOP, top);
		}

		p.set(x, y, z - 1);
		Block bottom = getBlockAtP(p);
		if (bottom != Block.NOTHING || includeNothing) {
			result.put(BlockFace.BOTTOM, bottom);
		}

		p.set(x - 1, y, z);
		Block west = getBlockAtP(p);
		if (west != Block.NOTHING || includeNothing) {
			result.put(BlockFace.WEST, west);
		}

		p.set(x + 1, y, z);
		Block east = getBlockAtP(p);
		if (east != Block.NOTHING || includeNothing) {
			result.put(BlockFace.EAST, east);
		}

		p.set(x, y - 1, z);
		Block south = getBlockAtP(p);
		if (south != Block.NOTHING || includeNothing) {
			result.put(BlockFace.SOUTH, south);
		}

		p.set(x, y + 1, z);
		Block north = getBlockAtP(p);
		if (north != Block.NOTHING || includeNothing) {
			result.put(BlockFace.NORTH, north);
		}

		return result;

		
		
	}
	
	
	private void modifyLight(Point3D pos, Block source, int intensity, HashMap<Point3D, Integer> alreadyMapped, boolean add)
	{
		Point3D p0s = new Point3D().set(pos); //pos at lesz irva ugyanebben a ciklusban, ezert masolni kell ha kulcskent hasznaljuk
		if(alreadyMapped==null) {
			alreadyMapped=new HashMap<>();
			alreadyMapped.put(p0s, intensity);
		}else if(!alreadyMapped.containsKey(p0s) || alreadyMapped.get(p0s)<intensity) {
				alreadyMapped.put(p0s, intensity);
		}else{
				return;
		}
		
		if(intensity<=0) {
			return;
		}
		
		Point3D coord = new Point3D().set(pos);// get6blocks atirja a parametert ezert le kell masolni

		HashMap<BlockFace, Block> nearby = get6Blocks(coord, false);
		for(Entry<BlockFace, Block> entry : nearby.entrySet()) {
			Block b = entry.getValue();
			BlockFace face = entry.getKey();
			
			
			
			for(Entry<Polygon3D, BlockFace> polys :  b.HitboxPolygons.entrySet()) {
				BlockFace polyface = polys.getValue(); 
				Polygon3D poly = polys.getKey();
				if(polyface == face.getOpposite()) {
					
					Integer current = poly.checkSource(source);
					
					
						
					if(add) {
						if(current==null || current<intensity) {
							poly.addSource(source, intensity);
						}
					}else {
						poly.removeSource(source);
					}

					if(polyface == BlockFace.TOP && poly.adjecentFilter && poly.getLight()<3 && !SpawnableSurface.contains(poly.spawnpoint)) {
						SpawnableSurface.add(poly.spawnpoint);
					}else if(SpawnableSurface.contains(poly.spawnpoint)) {
						SpawnableSurface.remove(poly.spawnpoint);
					}
					
					
				}
				
						
				
			}
			
		}
		
		for(Entry<BlockFace, Block> entry : get6Blocks(coord.set(pos), true).entrySet()) {
			Block b = entry.getValue();
			BlockFace face = entry.getKey();
			if(b == Block.NOTHING) { // b koordinatai 0,0,0 ezert b-t nem lehet hasznalni
				if(add) {
					addLight(coord.set(pos).add(face), source, intensity-1, alreadyMapped);
				}else {
					removeLight(coord.set(pos).add(face), source, intensity-1, alreadyMapped);
				}
			}
		}
		

		
	}
	
	
	public void addLight(Point3D pos, Block source, int intensity, HashMap<Point3D, Integer> alreadyMapped) {
		modifyLight(pos, source, intensity, alreadyMapped, true);
	}
	
	public void removeLight(Point3D pos, Block source, int intensity, HashMap<Point3D, Integer> alreadyMapped) {
		modifyLight(pos, source, intensity, alreadyMapped, false);
	}
	
	
	/*public void removeLight(int x, int y, int z, Block source, int level, HashMap<Point3D, Integer> alreadyMapped)
	{
		Point3D coord = new Point3D(x, y, z);
		if(alreadyMapped==null) {
			alreadyMapped=new HashMap<>();
			alreadyMapped.put(coord, level);
		}else if(!alreadyMapped.containsKey(coord) || alreadyMapped.get(coord)<level) {
				alreadyMapped.put(coord, level);
		}else{
				return;
		}
		
		
		if(level <=0) {
			return;
		}
		
		HashMap<BlockFace, Block> nearby = get6Blocks(x, y, z, false);
		for(Entry<BlockFace, Block> entry : nearby.entrySet()) {
			Block b = entry.getValue();
			BlockFace face = entry.getKey();
			
			
			for(Entry<Polygon3D, BlockFace> polys :  b.HitboxPolygons.entrySet()) {
				BlockFace polyface = polys.getValue(); 
				Polygon3D poly = polys.getKey();
				if(polyface == face.getOpposite()) {

					poly.removeSource(source);

					if(polyface == BlockFace.TOP && poly.adjecentFilter && poly.getLight()<7 && !SpawnableSurface.contains(poly.spawnpoint)) {
						SpawnableSurface.add(poly.spawnpoint);
					
					}else if(SpawnableSurface.contains(poly.spawnpoint)) {
						SpawnableSurface.remove(poly.spawnpoint);
					}
				}
				
						
				
			}
			
		}
		

		
		Block top = getBlockAt(x, y, z + 1);
		if (top == Block.NOTHING) {
			removeLight(x, y, z+1, source, level-1, alreadyMapped);
		}

		Block bottom = getBlockAt(x, y, z - 1);
		if (bottom == Block.NOTHING) {
			removeLight(x, y, z - 1, source, level-1, alreadyMapped);
		}

		Block west = getBlockAt(x - 1, y, z);
		if (west == Block.NOTHING) {
			removeLight(x - 1, y, z, source, level-1, alreadyMapped);
		}

		Block east = getBlockAt(x + 1, y, z);
		if (east == Block.NOTHING) {
			removeLight(x + 1, y, z, source, level-1, alreadyMapped);
		}

		Block south = getBlockAt(x, y - 1, z);
		if (south == Block.NOTHING) {
			removeLight(x, y - 1, z, source, level-1, alreadyMapped);
		}

		Block north = getBlockAt(x, y + 1, z);
		if (north == Block.NOTHING) {
			removeLight(x, y + 1, z, source, level-1, alreadyMapped);
		}

		
	}*/
	

	public int getTop(int x, int y) {
		TreeSet<Block> blockColumn = new TreeSet<>((arg0, arg1) -> Integer.compare(arg0.z, arg1.z));

		for (Entry<Point3D, Block> entry : Blocks.entrySet()) {
			Point3D pos = entry.getKey();
			Block b = entry.getValue();

			if (pos.x == x && pos.y == y) {
				blockColumn.add(b);
			}
		}
		if(blockColumn.isEmpty()) {
			return 0;//Block.NOTHING;
		}
		return blockColumn.last().z;
	}

	public ArrayList<Block> getWhole(boolean visibleOnly) {
		ArrayList<Block> Blocks = new ArrayList<>();

		if (!visibleOnly) {
			Blocks.addAll(this.Blocks.values());
		} else {
			for (Block b : this.Blocks.values()) {
				for (Polygon3D poly : b.Polygons) {
					if (poly.adjecentFilter) {
						Blocks.add(b);
						break;
					}
				}
			}
		}

		return Blocks;

	}

	public Block getSpawnBlock() {

		for(Block b : Blocks.values()) {
			if(b.name.equals("ChestModule") && getBlockAt(b.x, b.y, b.z+1)==Block.NOTHING) {
				return b;
			}

		}

		for(Block b : Blocks.values()) {
			if(getBlockAt(b.x, b.y, b.z + 1) == Block.NOTHING &&
					getBlockAt(b.x, b.y, b.z + 2) == Block.NOTHING) {
				return b;
			}

		}
		Main.err("No spawnblock!");
		return Block.NOTHING;

	}

	public int getSize() {
		return Blocks.size();
	}

	public Block[] getColumn(int x, int y) {
		TreeSet<Block> blockColumn = new TreeSet<>((arg0, arg1) -> Integer.compare(arg0.z, arg1.z));

		for (Entry<Point3D, Block> entry : Blocks.entrySet()) {
			Point3D pos = entry.getKey();
			Block b = entry.getValue();

			if (pos.x == x && pos.y == y) {
				blockColumn.add(b);
			}
		}
		
		return blockColumn.toArray(new Block[0]);
	}

	public Block getBlockUnderEntity(boolean inverse, boolean under, Entity entity, Point3D feetPoint, Point3D tmpPoint, TreeSet<Point3D> playerColumn) {
		//TreeSet<Integer> playerColumn = new TreeSet<>();
		//ArrayList<Block> playerColumn0 = new ArrayList<>();
		playerColumn.clear();
		Vector entityPos = entity.getPos();

		int x=(int) Math.floor(entityPos.x) ;
		int y= (int) Math.floor(entityPos.y);
		feetPoint.set(entityPos.x, entityPos.y, entityPos.z);
		//Point3D feetPoint=new Point3D(x, y , feetZ);
		
		//Block nothing = new Nothing();
		//nothing.z=feetZ;

		//for (Entry<Point3D, Block> entry : Blocks.entrySet()) {
		
		for(int i=x-1;i<=x+1;i++) {
			for(int j=y-1;j<=y+1;j++) {
				for(int k=worldBottom;k<=worldTop;k++) {
					
					//Point3D pos = new Point3D(i,j,k);
					
					//Block b = Blocks.get(pos);
					tmpPoint.set(i,j,k);
					Block b = getBlockAtP(tmpPoint);
					if(b == Block.NOTHING) {
						continue;
					}
					
					if (b.x == x && b.y == y && b.solid) {
						playerColumn.add(b.pos);
					}else if((b.x-BLOCK_RANGE < entityPos.x && entityPos.x < b.x+1+BLOCK_RANGE)
							&&  b.y == y && b.solid) {
							
						playerColumn.add(b.pos);
					}else if((b.y-BLOCK_RANGE < entityPos.y && entityPos.y < b.y+1+BLOCK_RANGE)
							&&  b.x == x && b.solid) {
							
						playerColumn.add(b.pos);
					}
					
					
				}
			}
			

			

		}
		
		
		if (playerColumn.isEmpty()) {
			return Block.NOTHING;
		}
		
		Point3D result;
		//Integer result;
		//Comparator<Block> comp = ((arg0, arg1) -> Integer.compare(arg0.z, arg1.z));
		//playerColumn0.sort(comp);
		//TreeSet<Block> playerColumn = new TreeSet<>(comp);
		//playerColumn.addAll(playerColumn0);
		
		if(under) {
			if ((entity.VerticalVector.z == 1 && !inverse) || (entity.VerticalVector.z == -1 && inverse)) {
				result = playerColumn.floor(feetPoint);
	
			} else {
				result = playerColumn.ceiling(feetPoint);
	
			}
		}else {
			if ((entity.VerticalVector.z == 1 && !inverse) || (entity.VerticalVector.z == -1 && inverse)) {
				result = playerColumn.ceiling(feetPoint);
	
			} else {
				result = playerColumn.floor(feetPoint);
	
			}			
		}

		return result == null ? Block.NOTHING : getBlockAtP(result);


	}

	public void saveByShutdown() {

		saveWorld(worldName, getWhole(false), Engine.Tick, seed, getAllEntities());
		
		
		if(Engine.server == null) { // singleplayer, game-b�l szedi az adatokat
			//HashMap<PlayerMPData, Inventory> map = new HashMap<>();
			//map.put(new PlayerMPData(0, null, new float[] {game.PE.ViewFrom.x, game.PE.ViewFrom.y, game.PE.ViewFrom.z}, game.ViewAngle.yaw, Config.username), Engine.Inv);
			//saveWorld(worldName, new ArrayList<>(Blocks.values()), Engine.Tick, CHUNK_HEIGHT); 
			
			savePlayer(worldName, Config.username, game.PE.getPos(), game.PE.ViewAngle, game.creative ? tmpInventory : Engine.Inv);
		}else { //multiplayer, engine.server-b�l szedi az adatokat
			//saveWorld(worldName, new ArrayList<>(Blocks.values()), Engine.Tick, CHUNK_HEIGHT); 
			//saveWorld(worldName, getWhole(false), Engine.Tick, seed, getAllEntities());
			
			//HashMap<PlayerMPData, Inventory> map = new HashMap<>();
			for(PlayerMPData data : Engine.server.clients.values()) {
				//map.put(key, value)
				savePlayer(worldName, data.username, data.position, data.aim, data.inventory);
			}
			
		}
	}

	public static void saveWorld(String worldName, List<Block> Blocks, long tickCount, long seed, Collection<Entity> entities) {
		File saves = new File("saves");
		File mods = new File("mods");
		File wname = new File("saves/" + worldName);

		File file = new File("saves/" + worldName + "/map.xml");
		try {
			saves.mkdir();
			mods.mkdir();
			wname.mkdir();

			file.createNewFile();
		} catch (IOException e1) {
			e1.printStackTrace();
			return;
		}
		DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
		DocumentBuilder db;
		Document document;
		try {
			db = dbf.newDocumentBuilder();
			document = db.newDocument();
		} catch (Exception e) {
			e.printStackTrace();
			return;
		}

		Element root = document.createElement("world");

		Element seedE = document.createElement("seed");
		seedE.setTextContent(seed+"");
		root.appendChild(seedE);
		
		//root.setAttribute("height", "" + height);

		Element blocks = document.createElement("blocks");
		for (Block b : Blocks) {
			Element BlockElement = document.createElement("block");
			BlockElement.setAttribute("x", "" + b.x);
			BlockElement.setAttribute("y", "" + b.y);
			BlockElement.setAttribute("z", "" + b.z);
			BlockElement.setAttribute("name", "" + b.name);

			Element Metadata = document.createElement("metadata");
			for (Entry<String, String> entry : b.BlockMeta.entrySet()) {
				Element data = document.createElement(entry.getKey());
				data.setTextContent(entry.getValue());
				Metadata.appendChild(data);
			}
			BlockElement.appendChild(Metadata);

			Element Powers = document.createElement("power");
			for (Entry<BlockFace, Integer> entry : b.powers.entrySet()) {
				Element power = document.createElement(entry.getKey().name());
				power.setTextContent(entry.getValue().toString());
				Powers.appendChild(power);
			}
			BlockElement.appendChild(Powers);
			
			if(b instanceof BlockInventoryInterface) {
				Element Inv = document.createElement("inventory");
				for (Entry<ItemType, Integer> entry : ((BlockInventoryInterface)b).getInv().items.entrySet()) {
					Element stack = document.createElement(entry.getKey().name);
					stack.setTextContent(entry.getValue()+"");
					Inv.appendChild(stack);
				}
				BlockElement.appendChild(Inv);
			}
			
			
			
			
			blocks.appendChild(BlockElement);

		}

		root.appendChild(blocks);

		Element tick = document.createElement("tick");
		tick.setTextContent(tickCount + "");
		root.appendChild(tick);
		
		Element entitiesNode = document.createElement("entities");
		for(Entity e : entities) {
			if(!(e instanceof PlayerEntity)) {
				Element entity = document.createElement(e.className);
				//entity.setAttribute("className", );
				entity.setAttribute("name", e.name);
				entity.setAttribute("pos", e.getPos().toString());
				entity.setAttribute("id", e.ID+"");
				entity.setAttribute("aim", e.ViewAngle.toString());
				entitiesNode.appendChild(entity);
			}
		}
		
		root.appendChild(entitiesNode);

		
		document.appendChild(root);

		Main.log("Saved: " + file.getPath());

		// write the content into xml file
		TransformerFactory transformerFactory = TransformerFactory.newInstance();
		Transformer transformer;
		try {
			transformer = transformerFactory.newTransformer();
			DOMSource source = new DOMSource(document);
			StreamResult result = new StreamResult(file);
			transformer.transform(source, result);
		} catch (TransformerException e) {
			e.printStackTrace();
			return;
		}

	}
	
	public static void savePlayer(String worldName, String username, Vector position, EAngle direction, PlayerInventory inventory) {
		File saves = new File("saves");
		File mods = new File("mods");
		File wname = new File("saves/" + worldName);
		File file = new File("saves/" + worldName + "/"+username+".xml");
		try {
			saves.mkdir();
			mods.mkdir();
			wname.mkdir();
			file.createNewFile();
		} catch (IOException e1) {
			e1.printStackTrace();
			return;
		}
		
		DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
		DocumentBuilder db;
		Document document;
		try {
			db = dbf.newDocumentBuilder();
			document = db.newDocument();
		} catch (Exception e) {
			e.printStackTrace();
			return;
		}

		Element root = document.createElement(username);
		
		//Element players = document.createElement("players");
		//Element localPlayer = document.createElement(Config.username);
		for (Entry<ItemType, Integer> is : inventory.items.entrySet()) {
			Element stack = document.createElement(is.getKey().name);
			stack.setTextContent(is.getValue() + "");
			root.appendChild(stack);
		}
		root.setAttribute("x", ""+position.x);
		root.setAttribute("y", ""+position.y);
		root.setAttribute("z", ""+position.z);
		root.setAttribute("yaw", ""+direction.yaw);
		root.setAttribute("pitch", ""+direction.pitch);
		//players.appendChild(localPlayer);

		//root.appendChild(players);

		
		document.appendChild(root);

		
		Main.log("Saved player: " + file.getPath());

		// write the content into xml file
		TransformerFactory transformerFactory = TransformerFactory.newInstance();
		Transformer transformer;
		try {
			transformer = transformerFactory.newTransformer();
			DOMSource source = new DOMSource(document);
			StreamResult result = new StreamResult(file);
			transformer.transform(source, result);
		} catch (TransformerException e) {
			e.printStackTrace();
			return;
		}

	}

	int getAlienCount() {
		int count=0;
		for(Entity e : Entities.values()) {
			if(e instanceof Alien) {
				count++;
			}
		}
		return count;
	}
	
	void filterAdjecentBlocks(Block bl) {
		filterBlock(bl);
		for (Block b : get6Blocks(bl, false).values()) {
			filterBlock(b);
		}
	}

	private void filterBlock(Block bl) {
		HashMap<BlockFace, Block> blocks6 = get6Blocks(bl, true);
		for (Entry<BlockFace, Block> entry : blocks6.entrySet()) {
			BlockFace key = entry.getKey();
			Block other = entry.getValue();
			Polygon3D side = null;
			
			for (Entry<Polygon3D, BlockFace> entry2 : bl.HitboxPolygons.entrySet()) {
				if(entry2.getValue() == key) side=entry2.getKey();
					
			}
			
			if(side==null) {
				Main.err("side=null in filterBlock()");
				return;
			}
			
			if(bl.fullblock) {

				if(!other.fullblock) {
					side.adjecentFilter=true;
				}else {
					
					if(!bl.transparent && other.transparent) {
						side.adjecentFilter = true;
					}else {
						side.adjecentFilter = false;
					}
					/*if(bl.transparent) {
						if(other.transparent) {
							side.adjecentFilter = false;
						}else {
							side.adjecentFilter = false;
						}
					}else {
						if(other.transparent) {
							side.adjecentFilter = true;
						}else {
							side.adjecentFilter = false;
						}
					}*/
				}
				
			}else {
				side.adjecentFilter = true;
			}
					
				
				/*		
						
					if(other.fullblock) {
						side.adjecentFilter=false;			
								
					}else {
						side.adjecentFilter	
								
					}
						
						
						
					}
					
					
					
				}
			}
			
			
			
			
			if (bl.transparent) {
				if (value.fullblock) {
					for (Polygon3D poly : bl.Polygons) {
						if (poly.normal.equals(key.directionVector)) {
							poly.adjecentFilter = false;
						}
					}
				} else {
					for (Polygon3D poly : bl.Polygons) {
						if (poly.normal.equals(key.directionVector)) {
							poly.adjecentFilter = true;
						}
					}
				}
			} else {
				if (value.transparent || !value.fullblock) {
					for (Polygon3D poly : bl.Polygons) {
						if (poly.normal.equals(key.directionVector)) {
							poly.adjecentFilter = true;
						}
					}
				} else {
					for (Polygon3D poly : bl.Polygons) {
						if (poly.normal.equals(key.directionVector)) {
							poly.adjecentFilter = false;
						}
					}
				}
			}*/
		}

	}

	/*public void addBlocks(Block[] blockArray, boolean overwrite) {
		for(Block b : blockArray) {
			addBlock(b, overwrite);
		}
		
	}*/

	/*public void remapLight()
	{
		for(Block b : getWhole(false)) {
			for(Polygon3D p : b.Polygons) {
				p.recalcLightedColor();
			}
		}
		
	}*/

}
