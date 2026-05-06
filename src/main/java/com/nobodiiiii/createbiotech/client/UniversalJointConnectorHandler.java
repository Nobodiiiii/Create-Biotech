package com.nobodiiiii.createbiotech.client;

import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.content.universaljoint.UniversalJointItem;
import com.nobodiiiii.createbiotech.registry.CBItems;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = CreateBiotech.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class UniversalJointConnectorHandler {

	private UniversalJointConnectorHandler() {}

	@SubscribeEvent
	public static void onClientTick(TickEvent.ClientTickEvent event) {
		if (event.phase != TickEvent.Phase.END)
			return;

		Minecraft minecraft = Minecraft.getInstance();
		Player player = minecraft.player;
		Level level = minecraft.level;

		if (player == null || level == null)
			return;
		if (minecraft.screen != null)
			return;

		for (InteractionHand hand : InteractionHand.values()) {
			ItemStack heldItem = player.getItemInHand(hand);
			if (!heldItem.is(CBItems.UNIVERSAL_JOINT.get()) || !heldItem.hasTag())
				continue;

			CompoundTag tag = heldItem.getTag();
			if (tag == null || !tag.contains(UniversalJointItem.FIRST_TARGET_KEY)
				|| !tag.contains(UniversalJointItem.FIRST_FACE_KEY))
				continue;

			Direction firstFace = Direction.byName(tag.getString(UniversalJointItem.FIRST_FACE_KEY));
			if (firstFace == null)
				continue;

			BlockPos firstTarget = NbtUtils.readBlockPos(tag.getCompound(UniversalJointItem.FIRST_TARGET_KEY));
			BlockPos firstJoint = UniversalJointItem.getJointPos(firstTarget, firstFace);
			HitResult hitResult = minecraft.hitResult;

			if (!(hitResult instanceof BlockHitResult blockHitResult)) {
				SlimeBeltConnectorHandler.spawnConnectionParticle(level, Vec3.atCenterOf(firstJoint), true);
				return;
			}

			BlockPos secondTarget = blockHitResult.getBlockPos();
			Direction secondFace = blockHitResult.getDirection();
			BlockPos secondJoint = UniversalJointItem.getJointPos(secondTarget, secondFace);
			boolean canConnect = UniversalJointItem.canConnect(level, firstTarget, firstFace, secondTarget, secondFace);

			SlimeBeltConnectorHandler.spawnConnectionLine(level, Vec3.atCenterOf(firstJoint),
				Vec3.atCenterOf(secondJoint), canConnect);
			return;
		}
	}
}
