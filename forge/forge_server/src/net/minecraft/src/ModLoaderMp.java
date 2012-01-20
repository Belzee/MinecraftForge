package net.minecraft.src;

import java.io.*;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URISyntaxException;
import java.util.*;
import java.util.logging.Logger;
import net.minecraft.server.MinecraftServer;
import net.minecraft.src.forge.ObjectPair;

public class ModLoaderMp
{
    public static final String NAME = "ModLoaderMP";
    public static final String VERSION = "1.1";
    private static boolean hasInit = false;
    private static Map<Class, ObjectPair<Integer, Integer>> entityTrackerMap = new HashMap<Class, ObjectPair<Integer, Integer>>();
    private static Map<Class, EntityTrackerEntry2> entityTrackerEntryMap = new HashMap<Class, EntityTrackerEntry2>();
    private static List<String> bannedMods = new ArrayList<String>();
    private static MinecraftServer instance = null;
	private static Method method_getNextWindowId;
	private static Field field_currentWindowId;
	private static long[] clock;

    public ModLoaderMp()
    {
    }

    public static void InitModLoaderMp()
    {
        init();
    }

    public static void RegisterEntityTracker(Class entityClass, int trackDistance, int updateTicks)
    {
        init();
        if (entityTrackerMap.containsKey(entityClass))
        {
            System.out.println("RegisterEntityTracker error: entityClass already registered.");
        }
        else
        {
            entityTrackerMap.put(entityClass, new ObjectPair(trackDistance, updateTicks));
        }
    }

    public static void RegisterEntityTrackerEntry(Class entityClass, int entityID)
    {
        RegisterEntityTrackerEntry(entityClass, false, entityID);
    }

    public static void RegisterEntityTrackerEntry(Class entityClass, boolean hasOwner, int entityID)
    {
        init();
        if (entityID > 255)
        {
            System.out.println("RegisterEntityTrackerEntry error: entityId cannot be greater than 255.");
        }
        if (entityTrackerEntryMap.containsKey(entityClass))
        {
            System.out.println("RegisterEntityTrackerEntry error: entityClass already registered.");
        }
        else
        {
            entityTrackerEntryMap.put(entityClass, new EntityTrackerEntry2(entityID, hasOwner));
        }
    }

    public static void HandleAllLogins(EntityPlayerMP player)
    {
        init();        
        sendModCheck(player);
        for (BaseMod mod : ModLoader.getLoadedMods())
        {
            if (mod instanceof BaseModMp)
            {
                ((BaseModMp)mod).HandleLogin(player);
            }
        }
    }

    public static void HandleAllPackets(Packet230ModLoader packet, EntityPlayerMP player)
    {
        init();
        if (packet.modId == "ModLoaderMP".hashCode())
        {
            switch (packet.packetType)
            {
                case 0:
                    handleModCheckResponse(packet, player);
                    break;

                case 1:
                    handleSendKey(packet, player);
                    break;
            }
        }
        else
        {
            for (BaseMod mod : ModLoader.getLoadedMods())
            {
                if (!(mod instanceof BaseModMp))
                {
                    continue;
                }
                BaseModMp modmp = (BaseModMp)mod;
                if (modmp.getId() != packet.modId)
                {
                    continue;
                }
                modmp.HandlePacket(packet, player);
                break;
            }
        }
    }

    public static void HandleEntityTrackers(EntityTracker entitytracker, Entity entity)
    {
        init();
        for (Map.Entry<Class, ObjectPair<Integer, Integer>> entry : entityTrackerMap.entrySet())
        {
            if (entry.getKey().isInstance(entity))
            {
                entitytracker.trackEntity(entity, entry.getValue().getValue1(), entry.getValue().getValue2(), true);
                return;
            }
        }
    }

    public static EntityTrackerEntry2 HandleEntityTrackerEntries(Entity entity)
    {
        init();
        if (entityTrackerEntryMap.containsKey(entity.getClass()))
        {
            return entityTrackerEntryMap.get(entity.getClass());
        }
        else
        {
            return null;
        }
    }

    public static void SendPacketToAll(BaseModMp mod, Packet230ModLoader packet)
    {
        init();
        if (mod == null)
        {
            IllegalArgumentException ex = new IllegalArgumentException("baseModMp cannot be null.");
            ModLoader.getLogger().throwing("ModLoaderMP", "SendPacketToAll", ex);
            ModLoader.ThrowException("baseModMp cannot be null.", ex);
            return;
        }
        else
        {
            packet.modId = mod.getId();
            sendPacketToAll(packet);
            return;
        }
    }

    private static void sendPacketToAll(Packet packet)
    {
        if (packet != null)
        {
        	MinecraftServer inst = (MinecraftServer)getMinecraftServerInstance();
            for (EntityPlayerMP player : (List<EntityPlayerMP>)inst.configManager.playerEntities)
            {
                player.playerNetServerHandler.sendPacket(packet);
            }
        }
    }

    public static void SendPacketTo(BaseModMp mod, EntityPlayerMP player, Packet230ModLoader packet)
    {
        init();
        if (mod == null)
        {
            IllegalArgumentException ex = new IllegalArgumentException("baseModMp cannot be null.");
            ModLoader.getLogger().throwing("ModLoaderMP", "SendPacketTo", ex);
            ModLoader.ThrowException("baseModMp cannot be null.", ex);
            return;
        }
        else
        {
            packet.modId = mod.getId();
            sendPacketTo(player, packet);
            return;
        }
    }

    public static void Log(String s)
    {
        MinecraftServer.logger.info(s);
        ModLoader.getLogger().fine(s);
        System.out.println(s);
    }

    public static World GetPlayerWorld(EntityPlayer player)
    {
        for (World world : getMinecraftServerInstance().worldMngr)
        {
            if (world.playerEntities.contains(player))
            {
                return world;
            }
        }

        return null;
    }

    private static void init()
    {
        if (hasInit)
        {
        	return;
        }
        hasInit = true;
        try
        {
            File file = getMinecraftServerInstance().getFile("banned-mods.txt");
            if (!file.exists())
            {
                file.createNewFile();
            }
            BufferedReader bufferedreader = new BufferedReader(new InputStreamReader(new FileInputStream(file)));
            String s;
            while ((s = bufferedreader.readLine()) != null)
            {
                bannedMods.add(s);
            }
        }
        catch (FileNotFoundException filenotfoundexception)
        {
            ModLoader.getLogger().throwing("ModLoader", "init", filenotfoundexception);
            ModLoader.ThrowException("ModLoaderMultiplayer", filenotfoundexception);
            return;
        }
        catch (IOException ioexception)
        {
            ModLoader.getLogger().throwing("ModLoader", "init", ioexception);
            ModLoader.ThrowException("ModLoaderMultiplayer", ioexception);
            return;
        }
        Log("ModLoaderMP 1.1 Initialized");
    }

    private static void sendPacketTo(EntityPlayerMP player, Packet230ModLoader packet)
    {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(bytes);
        try 
        {
            packet.writePacketData(dos);
            dos.flush();
            byte[] data = bytes.toByteArray();
            if (data.length >= 32750)
            {
                for (int x = 0; x < data.length; x += 32750)
                {
                    Packet250CustomPayload pkt = new Packet250CustomPayload();
                    bytes.reset();
                    dos.writeInt(0);           //ModID: Forge
                    dos.writeByte(1);          //PktID: MLMP Packet
                    dos.writeInt(data.length); //Total Data Len
                    dos.writeInt(x);           //Current Data offset
                    dos.write(data, x, Math.min(data.length - x, 32750));
                    pkt.field_44005_a = "Forge";
                    pkt.field_44004_c = bytes.toByteArray();
                    pkt.field_44003_b = pkt.field_44004_c.length;
                    player.playerNetServerHandler.sendPacket(pkt);
                }
            }
            else
            {
                Packet250CustomPayload pkt = new Packet250CustomPayload();
                bytes.reset();
                dos.writeInt(0);           //ModID: Forge
                dos.writeByte(1);          //PktID: MLMP Packet
                dos.writeInt(data.length); //Total Data Len
                dos.writeInt(0);           //Current Data offset
                dos.write(data);
                pkt.field_44005_a = "Forge";
                pkt.field_44004_c = bytes.toByteArray();
                pkt.field_44003_b = pkt.field_44004_c.length;
                player.playerNetServerHandler.sendPacket(pkt);   
            }
        } 
        catch (IOException e) 
        {
            e.printStackTrace();
        }
    }

    private static void sendModCheck(EntityPlayerMP player)
    {
        Packet230ModLoader packet = new Packet230ModLoader();
        packet.modId = "ModLoaderMP".hashCode();
        packet.packetType = 0;
        sendPacketTo(player, packet);
    }

    private static void handleModCheckResponse(Packet230ModLoader packet, EntityPlayerMP player)
    {
        StringBuilder clientMods = new StringBuilder();
        if (packet.dataString.length != 0)
        {
            for (int i = 0; i < packet.dataString.length; i++)
            {
                if (packet.dataString[i].lastIndexOf("mod_") != -1)
                {
                    if (clientMods.length() != 0)
                    {
                        clientMods.append(", ");
                    }
                    clientMods.append(packet.dataString[i].substring(packet.dataString[i].lastIndexOf("mod_")));
                }
            }
        }
        else
        {
            clientMods.append("no mods");
        }
        Log(player.username + " joined with " + clientMods);
        
        ArrayList<String> clientBannedMods = new ArrayList<String>();
        for (int j = 0; j < bannedMods.size(); j++)
        {
            for (int k = 0; k < packet.dataString.length; k++)
            {
            	String mod = packet.dataString[k];
                if (mod.lastIndexOf("mod_") != -1 && mod.substring(mod.lastIndexOf("mod_")).startsWith(bannedMods.get(j)))
                {
                    clientBannedMods.add(mod.substring(mod.lastIndexOf("mod_")));
                }
            }
        }

        ArrayList<String> missingMods = new ArrayList<String>();
        for (BaseMod mod : ModLoader.getLoadedMods())
        {
        	if (mod instanceof BaseModMp)
        	{
	            BaseModMp modmp = (BaseModMp)mod;
	            if (!modmp.hasClientSide() || modmp.toString().lastIndexOf("mod_") == -1)
	            {
	                continue;
	            }
	            String name = modmp.toString().substring(modmp.toString().lastIndexOf("mod_"));
	            boolean clientHasMod = false;
	            for (int l1 = 0; l1 < packet.dataString.length; l1++)
	            {
	            	String tmp = packet.dataString[l1]; 
	                if (tmp.lastIndexOf("mod_") == -1)
	                {
	                    continue;
	                }
	                String tmpName = tmp.substring(tmp.lastIndexOf("mod_"));
	                if (!name.equals(tmpName))
	                {
	                    continue;
	                }
	                clientHasMod = true;
	                break;
	            }
	
	            if (!clientHasMod)
	            {
	                missingMods.add(name);
	            }
        	}
        }

        if (clientBannedMods.size() != 0)
        {
            StringBuilder msg = new StringBuilder();
            for (String mod : clientBannedMods) 
            {
            	msg.append((msg.length() != 0 ? ", " : "")).append(mod);
            }

            Log(player.username + " kicked for having " + msg);
            
            msg = new StringBuilder();
            for (String mod : clientBannedMods) 
            {
            	msg.append("\n").append(mod);
            }

            player.playerNetServerHandler.kickPlayer("The following mods are banned on this server:" + msg);
        }
        else if (missingMods.size() != 0)
        {
            StringBuilder msg = new StringBuilder();
            for (String mod : missingMods) 
            {
            	msg.append("\n").append(mod);
            }

            player.playerNetServerHandler.kickPlayer("You are missing the following mods:" + msg);
        }
    }

    private static void handleSendKey(Packet230ModLoader packet, EntityPlayerMP player)
    {
        if (packet.dataInt.length != 2)
        {
            System.out.println("SendKey packet received with missing data.");
        }
        else
        {
            int modID = packet.dataInt[0];
            int key = packet.dataInt[1];
            for (BaseMod mod : ModLoader.getLoadedMods())
            {
                if (!(mod instanceof BaseModMp))
                {
                    continue;
                }
                BaseModMp modmp = (BaseModMp)mod;
                if (modmp.getId() != modID)
                {
                    continue;
                }
                modmp.HandleSendKey(player, key);
                break;
            }
        }
    }

    public static void getCommandInfo(ICommandListener listener)
    {
        for (BaseMod mod : ModLoader.getLoadedMods())
        {
            if (mod instanceof BaseModMp)
            {
                ((BaseModMp)mod).GetCommandInfo(listener);
            }
        }
    }

    public static boolean HandleCommand(String command, String username, Logger logger, boolean isOp)
    {
        boolean handled = false;
        for (BaseMod mod : ModLoader.getLoadedMods())
        {
            if (!(mod instanceof BaseModMp))
            {
                continue;
            }
            if (((BaseModMp)mod).HandleCommand(command, username, logger, isOp))
            {
                handled = true;
            }
        }

        return handled;
    }

    public static void sendChatToAll(String prefix, String message)
    {
        sendChatToAll(prefix + ": " + message);
    }

    public static void sendChatToAll(String message)
    {
    	MinecraftServer inst = (MinecraftServer)getMinecraftServerInstance();
        for (EntityPlayerMP player : (List<EntityPlayerMP>)inst.configManager.playerEntities)
        {
            player.playerNetServerHandler.sendPacket(new Packet3Chat(message));
        }

        MinecraftServer.logger.info(message);
    }

    public static void sendChatToOps(String prefix, String message)
    {
        sendChatToOps("\2477(" + prefix + ": " + message + ")");
    }

    public static void sendChatToOps(String message)
    {
    	MinecraftServer inst = (MinecraftServer)getMinecraftServerInstance();
        for (EntityPlayerMP player : (List<EntityPlayerMP>)inst.configManager.playerEntities)
        {
            if (inst.configManager.isOp(player.username))
            {
                player.playerNetServerHandler.sendPacket(new Packet3Chat(message));
            }
        }

        MinecraftServer.logger.info(message);
    }

    public static Packet GetTileEntityPacket(BaseModMp basemodmp, int x, int y, int z, int l, int ai[], float af[], String as[])
    {
        Packet230ModLoader packet230modloader = new Packet230ModLoader();
        packet230modloader.modId = "ModLoaderMP".hashCode();
        packet230modloader.packetType = 1;
        packet230modloader.isChunkDataPacket = true;
        int i1 = ai != null ? ai.length : 0;
        int ai1[] = new int[i1 + 5];
        ai1[0] = basemodmp.getId();
        ai1[1] = x;
        ai1[2] = y;
        ai1[3] = z;
        ai1[4] = l;
        if (i1 != 0)
        {
            System.arraycopy(ai, 0, ai1, 5, ai.length);
        }
        packet230modloader.dataInt = ai1;
        packet230modloader.dataFloat = af;
        packet230modloader.dataString = as;
        return packet230modloader;
    }

    public static void SendTileEntityPacket(TileEntity tileentity)
    {
        sendPacketToAll(tileentity.getDescriptionPacket());
    }

    public static BaseModMp GetModInstance(Class modCls)
    {
    	for (BaseMod mod : ModLoader.getLoadedMods())
    	{
            if (!(mod instanceof BaseModMp))
            {
                continue;
            }
            
            BaseModMp modmp = (BaseModMp)mod;
            if (modCls.isInstance(modmp))
            {
                return modmp;
            }
        }

        return null;
    }
    

    public static void OnTick(MinecraftServer server)
    {
        init();
        
        long l = 0L;
        if (server.worldMngr != null)
        {
        	if (clock == null || clock.length != server.worldMngr.length)
        	{
        		clock = new long[server.worldMngr.length];
        	}
            
    		for (int x = 0; x < server.worldMngr.length; x++)
            {
    			World world = server.worldMngr[x];
                for (Map.Entry<BaseMod, Boolean> hook : ModLoader.getInGameHooks().entrySet())
            	{
        			if (clock[x] != world.getWorldTime() || !hook.getValue())
            		{
            			if (world != null)
            			{
            				hook.getKey().OnTickInGame(world);
            			}
            		}
            	}
                clock[x] = server.worldMngr[x].getWorldTime();
            }
        }
    }

	public static void Init(MinecraftServer server) 
	{
        instance = server;

        try
        {
            String workingDir = ModLoader.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath();
            workingDir = workingDir.substring(0, workingDir.lastIndexOf(47));
            ModLoader.setDirectories(
            		new File(workingDir, "/config/"),
            		new File(workingDir, "/config/ModLoader.cfg"),
            		new File(workingDir, "ModLoader.txt"),
            		new File(workingDir, "/mods/")
    		);
        }
        catch (URISyntaxException ex)
        {
            ModLoader.getLogger().throwing("ModLoaderMP", "Init", ex);
            ModLoader.ThrowException("ModLoaderMp", ex);
            return;
        }

        try
        {
            try
            {
                method_getNextWindowId = EntityPlayerMP.class.getDeclaredMethod("aS");
            }
            catch (NoSuchMethodException var3)
            {
                method_getNextWindowId = EntityPlayerMP.class.getDeclaredMethod("getNextWidowId");
            }

            method_getNextWindowId.setAccessible(true);

            try
            {
                field_currentWindowId = EntityPlayerMP.class.getDeclaredField("cl");
            }
            catch (NoSuchFieldException var2)
            {
                field_currentWindowId = EntityPlayerMP.class.getDeclaredField("currentWindowId");
            }

            field_currentWindowId.setAccessible(true);
        }
        catch (NoSuchFieldException ex)
        {
        	ModLoader.getLogger().throwing("ModLoaderMp", "Init", ex);
        	ModLoader.ThrowException("ModLoaderMp", ex);
            return;
        }
        catch (NoSuchMethodException ex)
        {
        	ModLoader.getLogger().throwing("ModLoaderMp", "Init", ex);
        	ModLoader.ThrowException("ModLoaderMp", ex);
            return;
        }
 
        init();		
	}

	public static MinecraftServer getMinecraftServerInstance() 
	{
		return instance;
	}
}
