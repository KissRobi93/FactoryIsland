package ml.sakii.factoryisland.net;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import javax.swing.JOptionPane;

import ml.sakii.factoryisland.Config;
import ml.sakii.factoryisland.EAngle;
import ml.sakii.factoryisland.Game;
import ml.sakii.factoryisland.Main;
import ml.sakii.factoryisland.Vector;
import ml.sakii.factoryisland.blocks.Block;
import ml.sakii.factoryisland.blocks.BlockInventoryInterface;
import ml.sakii.factoryisland.entities.Entity;
import ml.sakii.factoryisland.entities.PlayerMP;


public class GameClient extends Thread{

	/*public static final Set<String> CODES = new HashSet<>(Arrays.asList(
			"00", "02", "05", "06", "07", "66", "67" ,"98", "01", "08", "03","10","11","12", "loaded", "ping", "pong"));*/
	
	static final Set<String> ALLCODES = new HashSet<>(Arrays.asList(
			"00", "01", "02", "03","04", "05", "06", "07", "08","10","11", "14","15","16","17","97", "13", "66", "67" ,"98", "loaded", "ping", "pong"));
	
	static final String DELIMETER = "\r\n";
	static final long CMDTIME = 20; 
	
	private boolean connected = true;

	public int packetCount, blockcount;
	
	
	private Game game;
	private Socket socket;
	private BufferedWriter outputStream;
	private BufferedReader inputStream;

	long pingTime;
	private boolean terrainLoaded=false;
	
	
	
	public GameClient(Game game){
		this.game = game;
		this.setName("GameClient");

		
	}
	
	public String connect(String ipAddress, int port, boolean sendPos) {
		try {
			Main.log("Connecting... "+ipAddress+":"+port);
			socket = new Socket(InetAddress.getByName(ipAddress), port);
			outputStream = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
			inputStream = new BufferedReader(new InputStreamReader(socket.getInputStream()));
			
			sendData("ping");
			if(sendPos) {
				sendData(("00,"+Config.username +","+ game.PE.getPos() +","+ Math.toRadians(game.PE.ViewAngle.yaw)+","+ Math.toRadians(game.PE.ViewAngle.pitch)));
			}else {
				sendData("00,"+Config.username);	
			}
			
			while(!terrainLoaded) {
			
			
				String message;
				try {
					message = inputStream.readLine().trim();
				} catch (IOException e) {
					e.printStackTrace();
					continue;
				}
	
				if(message == null || message.isEmpty())
					continue;
				
	
				
				if(Main.devmode) {
						Main.log("(CLIENT:"+Config.username+") RECEIVED:  "+message);
				}
				
				handleMessage(message);
			}
			
			return null;
		} catch (Exception e) {
			return e.getMessage();
		}
		
	}

	
	@Override
	public void run(){

		while(connected){
			

			
			String message=null;
			try {
				if(inputStream.ready()) {
					message = inputStream.readLine().trim();
				}else {
					try
					{
						Thread.sleep(CMDTIME);
					} catch (InterruptedException e)
					{
						e.printStackTrace();
					}
					continue;
				}
			} catch (IOException e) {
				e.printStackTrace();
				continue;
			}

			if(message == null || message.isEmpty())
				continue;
			

			
			if(Main.devmode) {
					Main.log("(CLIENT:"+Config.username+") RECEIVED:  "+message);
			}
			

			
			handleMessage(message);
			
			
			
		}
		Main.log("sending 66");

		sendData(("66," + Config.username));
		try {
			inputStream.close();
			outputStream.close();
			socket.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		Main.log("connecting closed");

	}
	
	private void handleMessage(String message) {
		String[] part = message.split(",");
		String otherName = (part.length>1) ? part[1] : "";

		switch(part[0]){
		
		case "01": // DOWNLOAD BLOCKS
			
			
			for(int i = 0;i<part.length;i=i+5){
				Block b = game.Engine.createBlockByName(part[i+1], cInt(part[i+2]), cInt(part[i+3]), cInt(part[i+4]));
				if(b != Block.NOTHING && b != null){
					game.Engine.world.addBlock(b, true);
					//toBeDeleted.add(entry);
					blockcount++;
				}else{
					Main.log("Could not parse received values as a block: " + part[i+1]+","+cInt(part[i+2])+","+cInt(part[i+3])+","+cInt(part[i+4]));
				}
			}
			packetCount++;
			break;
			
		case "08": // DOWNLOAD METADATA
			
			for(int i = 0;i<part.length;i=i+6){
				
				game.Engine.world.getBlockAt(cInt(part[i+1]), cInt(part[i+2]), cInt(part[i+3])).BlockMeta.put(part[i+4], part[i+5]);
			}
			
			
			break;
		
		case "loaded":
			terrainLoaded = true;
			Main.log("Terrain download from server completed ("+blockcount+" blocks / "+packetCount+" packets)");
			
			
			break;
		case "03": // ADD A PLAYER
			
				Vector ViewFrom = new Vector(Float.parseFloat(part[2]), Float.parseFloat(part[3]), Float.parseFloat(part[4]));
				PlayerMP newPlayer = new PlayerMP(ViewFrom, new EAngle(Float.parseFloat(part[5]), 0), part[1],new Random().nextLong(), game.Engine);
				game.playerList.put(part[1], newPlayer);
				game.Objects.addAll(newPlayer.Objects);
				//game.Objects.add(newPlayer.title);
				//game.Entities.add(newPlayer);
				//game.Texts.add(new Text3D(part[1], ViewFrom[0], ViewFrom[1], ViewFrom[2]));
				Main.log("(CLIENT:" + Config.username + ") Player "+part[1]+" added at "+ViewFrom);
			
			break;
		
		case "04": // MOVE A PLAYER
			
			PlayerMP otherPlayer = game.playerList.get(otherName);
			if(otherPlayer!=null) {
				otherPlayer.ViewFrom[0] = Float.parseFloat(part[2]);
				otherPlayer.ViewFrom[1] = Float.parseFloat(part[3]);
				otherPlayer.ViewFrom[2] = Float.parseFloat(part[4]);
				otherPlayer.yaw = Float.parseFloat(part[5]);
				otherPlayer.update();
			}
			
			break;
		
		case "67": // DELETE PLAYER
			
			game.Objects.removeAll(game.playerList.get(otherName).Objects);
			//game.Entities.remove(game.playerList.get(otherName));
			game.playerList.remove(otherName);
			
			break;
		
		case "05": // PLACE BLOCK
			
			Block b = game.Engine.createBlockByName(part[4], cInt(part[1]), cInt(part[2]), cInt(part[3]));
			game.Engine.world.addBlock(b,true);

			break;
		case "06": // DELETE BLOCK
			game.Engine.world.destroyBlock(game.Engine.world.getBlockAt(cInt(part[1]),cInt(part[2]), cInt(part[3])));
			break;
		case "07": // EDIT METADATA
			
			game.Engine.world.getBlockAt(cInt(part[1]), cInt(part[2]), cInt(part[3])).setMetadata(part[4], part[5]);

			break;
		case "10": // ADD TO INVENTORY
			int size0 = game.Engine.Inv.items.size();
			game.Engine.Inv.add(Main.Items.get(part[2]), cInt(part[3]), false);
			if(game.Engine.Inv.items.size()==0)
				game.SwitchInventory(false);
			if(game.Engine.Inv.items.size()==1 && size0==0)
				game.SwitchInventory(true);
			break;
		case "13": // ADD TO BLOCK INVENTORY
			((BlockInventoryInterface)game.Engine.world.getBlockAt(cInt(part[1]), cInt(part[2]), cInt(part[3]))).getInv().add(Main.Items.get(part[4]), cInt(part[5]), false);
			if(game.activeInventory.getInv().items.size()==0)
				game.SwitchInventory(true);
			break;
		case "14": // SWAP BLOCKS 14,Sakii,1,2,3,Stone,true (add to local)
			boolean addToLocal = Boolean.parseBoolean(part[6]);
			game.Engine.Inv.add(Main.Items.get(part[5]), addToLocal ? 1 : -1, false);
			((BlockInventoryInterface)game.Engine.world.getBlockAt(cInt(part[2]), cInt(part[3]), cInt(part[4]))).getInv().add(Main.Items.get(part[5]), addToLocal ? -1 : 1, false);
			
			if(game.activeInventory.getInv().items.size()==0) {
				game.SwitchInventory(true);
			}else if(game.Engine.Inv.items.size()==0) {
				game.SwitchInventory(false);
			}
			break;

		case "98": // SERVER CLOSED
			if(game.Engine.server == null) {
				JOptionPane.showMessageDialog(Main.Frame.getContentPane(), "Server closed", "Disconnected", JOptionPane.ERROR_MESSAGE);
				game.disconnect();
			}
			
			break;
		case "97": // SAME USERNAME
				JOptionPane.showInternalMessageDialog(Main.Frame.getContentPane(), "Someone with the same username already logged in", "Disconnected", JOptionPane.ERROR_MESSAGE);
				game.disconnect();
			
			break;
		case "11": //FORCE MOVE
			game.PE.move(Float.parseFloat(part[1]), Float.parseFloat(part[2]), Float.parseFloat(part[3]));
			game.PE.ViewAngle.yaw = Float.parseFloat(part[4]);
			game.PE.ViewAngle.pitch = Float.parseFloat(part[5]);
			game.moved=true;
			break;
		case "15": // SPAWN ENTITY 15,className,x,y,z,yaw,pitch,name,ID
			String className = part[1];
			Vector pos=new Vector(Float.parseFloat(part[2]), Float.parseFloat(part[3]), Float.parseFloat(part[4]));
			EAngle aim=new EAngle(Float.parseFloat(part[5]), Float.parseFloat(part[6]));
			String name=part[7];
			long ID=Long.parseLong(part[8]);
			Entity e = Entity.createEntity(className, pos, aim, name, ID, game.Engine); 
			game.Engine.world.addEntity(e);
			break;
		case "16": // MOVE ENTITY
			game.Engine.world.getEntity(Long.parseLong(part[1])).move(Float.parseFloat(part[2]), Float.parseFloat(part[3]), Float.parseFloat(part[4]));
			break;
		case "17": // KILL ENTITIY
			game.Engine.world.killEntity(Long.parseLong(part[1]));
			break;
		case "pong":
			Main.log("ping time: " + (System.currentTimeMillis()-pingTime) + " ms");
			break;
		default:
			Main.err("(CLIENT) Unknown message received: "+message);
			break;
		}
		
	}
	
	public void sendData(String data){
		if(data.isEmpty()) {
			Main.log("empty message from client");
			return;
		}
		try {
			if(data.equals("ping")) {
				pingTime = System.currentTimeMillis();
			}
			outputStream.write(data+DELIMETER);
			outputStream.flush();
			if(Main.devmode) {
				if(ALLCODES.contains(data.split(",")[0])){
					Main.log("(CLIENT:"+Config.username+") SENT:  "+data);
				}else if(!data.isEmpty()){
					Main.log("(CLIENT:"+Config.username+") I DUNNO WAT I SENT LOL:  "+data);
				}
			}
		} catch (IOException e) {
			Main.err(e.getMessage());
			game.disconnect();
			JOptionPane.showMessageDialog(Main.Frame.getContentPane(), "Error: " + e.getMessage(), "Disconnected", JOptionPane.ERROR_MESSAGE);
			this.kill();
		}
		
		
		
	}
	
	public void kill(){
		terrainLoaded=true;
		connected=false;
				
	}
	
	private static int cInt(String data){
		try{
			return Integer.parseInt(data); 
		}catch(Exception e){
			return 0;
		}
	}
	
	
}
