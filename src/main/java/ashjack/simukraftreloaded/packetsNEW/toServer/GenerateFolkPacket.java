package ashjack.simukraftreloaded.packetsNEW.toServer;

import net.minecraft.world.World;
import ashjack.simukraftreloaded.folk.FolkData;
import io.netty.buffer.ByteBuf;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;

public class GenerateFolkPacket implements IMessage
{

	static World world;
	static boolean isForced;
	
	public GenerateFolkPacket(){}
	
	public GenerateFolkPacket(World whirld, boolean forced)
	{
		this.isForced = forced;
		this.world = whirld;
	}
	
	@Override
	public void fromBytes(ByteBuf buf) 
	{
		isForced = buf.readBoolean();
	}

	@Override
	public void toBytes(ByteBuf buf) 
	{
		buf.writeBoolean(isForced);
	}

	public static class Handler implements IMessageHandler<GenerateFolkPacket, IMessage> 
	{
		
		 @Override
		 public IMessage onMessage(GenerateFolkPacket message, MessageContext ctx) 
		 {
			 if(!isForced)
			 {
				 FolkData.generateNewFolk(world);
			 }
			 else
			 {
				 FolkData.forceGenerateNewFolk(world);
			 }
			 return null;
		 }
		 
	}
	
}
