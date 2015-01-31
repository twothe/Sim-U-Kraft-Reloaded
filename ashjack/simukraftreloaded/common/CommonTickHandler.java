package ashjack.simukraftreloaded.common;

import io.netty.buffer.ByteBuf;

import java.io.File;
import java.util.ArrayList;
import java.util.EnumSet;

import ashjack.simukraftreloaded.client.Gui.GuiRunMod;
import ashjack.simukraftreloaded.common.CommonProxy.V3;
import ashjack.simukraftreloaded.core.ModSimukraft;
import ashjack.simukraftreloaded.core.building.Building;
import ashjack.simukraftreloaded.folk.FolkData;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;
import cpw.mods.fml.client.FMLClientHandler;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Mod.EventHandler;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.gameevent.TickEvent.ClientTickEvent;
import cpw.mods.fml.common.gameevent.TickEvent.RenderTickEvent;
import cpw.mods.fml.common.gameevent.TickEvent.ServerTickEvent;
import cpw.mods.fml.common.gameevent.TickEvent.WorldTickEvent;
import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.server.FMLServerHandler;

public class CommonTickHandler
{
    private World serverWorld = null;
    Long lastSecondTickAt = 0l;
    Long lastMinuteTickAt = 0l;

    GuiRunMod runModui = null;
    String currentWorld = "";
    Minecraft mc = Minecraft.getMinecraft();
    long lastReset = 0;
    
    boolean haveRunStartup = false;
    
    @SubscribeEvent
    public void tick(WorldTickEvent event)
    {
    	
    }
    int ticks = 0;
    @SubscribeEvent
    public void tick(ServerTickEvent event)
    {
    	if(ticks == 200)
    	{
    		onTickInGame();
    	}
    	else
    	{
    		ticks++;
    	}
    }
    
    public static final SimpleNetworkWrapper INSTANCE = NetworkRegistry.INSTANCE.newSimpleChannel("SUKMain");
    
    public void onTickInGame()
    {
        if (mc.currentScreen != null)
        	
        {
            if (mc.currentScreen.toString().toLowerCase().contains("guimainmenu"))
            {
            	ModSimukraft.log.info("CommTH: in Gui Main menu");
            }
        }

        if (ModSimukraft.states.gameModeNumber == 10)
        {
            ModSimukraft.proxy.ranStartup = true;
            return;
        }

        Long now = System.currentTimeMillis();

        if (serverWorld != null)
        {
            //fire onUpdate() for each folkData
            FolkData.triggerAllUpdates();
            
            //handle day-night-day transitions
            ModSimukraft.dayTransitionHandler();

            //if a farm needs upgarding, incrementally upgrade it
            if (ModSimukraft.farmToUpgrade != null)
            {
                ModSimukraft.upgradeFarm();
            }

            if (ModSimukraft.demolishBlocks.size() > 0) //and demolishing buildings
            {
                ModSimukraft.demolishBlocks();
            }
        }

        // ***** ONCE A SECOND
        if (now - lastSecondTickAt > 1000)
        {
            if (ModSimukraft.proxy.ranStartup == false)
            {
               System.out.println("Haven't run startup - doing that now");
               
               serverWorld = MinecraftServer.getServer().getEntityWorld();
               currentWorld = ModSimukraft.getSavesDataFolder();
               ModSimukraft.log.info("CommTH: Startup - set serverWorld/currentWorld");
               
               System.out.println("Running Reset World Function");
               ModSimukraft.resetAndLoadNewWorld();
                            
               //Packets have had a workaround put in place - they need to be fixed.
                            
               //INSTANCE.registerMessage(PacketHandler.class, SimukraftPacket.class, 0, Side.CLIENT);
    	       //INSTANCE.sendToServer(new SimukraftPacket());
            }
            
            else      //used to detect world change - Still a bug with this, not unloading world when player switches via main menu
            {
            	//System.out.println("Have already run startup");
                if (!currentWorld.contentEquals(ModSimukraft.getSavesDataFolder()))
                {
                    if (now - lastReset >30000) {
                    	ModSimukraft.log.info("currentWorld="+currentWorld+"     getSaves="+ModSimukraft.getSavesDataFolder());
	                	currentWorld = ModSimukraft.getSavesDataFolder();
	                    ModSimukraft.proxy.ranStartup = false;
	                    
	                    ModSimukraft.resetAndLoadNewWorld();
	                    
	                    //INSTANCE.registerMessage(PacketHandler.class, SimukraftPacket.class, 0, Side.CLIENT);
	                    //INSTANCE.sendToServer(new SimukraftPacket());
                    }
                }

                //STOP THE RAIN MOD - Implemented this when I had a world where it rained ALL THE TIME!
                if (serverWorld.isRaining() && serverWorld.getWorldInfo().getRainTime() > 1 &&  ModSimukraft.configStopRain == true)
                {
                    serverWorld.getWorldInfo().setRainTime(2);  //setting to 1 or 0 doesn't work every time.
                }
            }


            // ONCE A SECOND EVERY SECOND
            lastSecondTickAt = now;
            
            
        }

        
        // ***** ONCE A MINUTE
        if (serverWorld != null && System.currentTimeMillis() - lastMinuteTickAt > 60000)
        {
            if (lastMinuteTickAt > 0)
            {
                Long start = System.currentTimeMillis();
                FolkData.generateNewFolk(serverWorld);
                ModSimukraft.states.saveStates();
                Building.checkTennants();
                Building.saveAllBuildings();
                CourierTask.saveCourierTasksAndPoints();
                MiningBox.saveMiningBoxes();
                FarmingBox.saveFarmingBoxes();
                Relationship.saveRelationships();
                //PathBox.savePathBoxes();
                ModSimukraft.log.info("CTH: Saved game data in " + (System.currentTimeMillis() - start) + " ms");
            }

            lastMinuteTickAt = now;
        }
    }

    public void resetSimUKraft()
    {
        //this resets everything first, if the player has switched worlds, gets hit several times due to weird MC GUI switching,
        // so lastReset stops it from running more than once every 30 seconds.
        if (System.currentTimeMillis() - lastReset > 30000)
        {
            
            lastReset = System.currentTimeMillis();
            Side side = cpw.mods.fml.common.FMLCommonHandler.instance().getEffectiveSide();
            
            ModSimukraft.log.info(side.toString()+"-side CommTH: resetSimUKraft()");
        }
    }

    /** runs when a world has loaded, so we can set everything up */
    private void startingWorld()
    {
        if (!ModSimukraft.proxy.ranStartup)
        {
           // TODO: no longer used
           

        }
    }

    ////////////////////////////////////////////////
    



    public String getLabel()
    {
        return "CommonTickHandler";
    }

    public CommonTickHandler()
    {
    	
    }
}


