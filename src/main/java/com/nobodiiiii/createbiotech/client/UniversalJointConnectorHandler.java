package com.nobodiiiii.createbiotech.client;

import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.content.cardboardbox.CapturedEntityBoxItem;
import com.nobodiiiii.createbiotech.content.universaljoint.HalfShaftBlockEntity;
import com.nobodiiiii.createbiotech.content.universaljoint.UniversalJointItem;
import com.nobodiiiii.createbiotech.content.universaljoint.UniversalJointItem.Endpoint;
import com.nobodiiiii.createbiotech.content.universaljoint.UniversalJointRepair;
import com.nobodiiiii.createbiotech.content.universaljoint.UniversalJointRepair.Selection;
import com.nobodiiiii.createbiotech.foundation.item.CBItemData;
import com.nobodiiiii.createbiotech.foundation.utility.SubLevelCompat;
import com.nobodiiiii.createbiotech.registry.CBItems;

import net.createmod.catnip.animation.AnimationTickHolder;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
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
		if (player == null || level == null || minecraft.screen != null)
			return;

		for (InteractionHand hand : InteractionHand.values()) {
			ItemStack held = player.getItemInHand(hand);
			if (held.is(CBItems.UNIVERSAL_JOINT.get()) && CBItemData.has(held)) {
				renderPlacementPreview(minecraft, level, held);
				return;
			}
			if (CapturedEntityBoxItem.isBox(held) && UniversalJointRepair.canRepairWith(held)
				&& UniversalJointRepair.readSelection(held) != null) {
				renderRepairPreview(minecraft, level, held);
				return;
			}
		}
	}

	private static void renderPlacementPreview(Minecraft minecraft, Level level, ItemStack held) {
		CompoundTag tag = CBItemData.getReadOnly(held);
		if (tag == null)
			return;
		Endpoint first = UniversalJointItem.readFirstEndpoint(level, tag);
		if (first == null)
			return;

		float partialTick = AnimationTickHolder.getPartialTicks();
		Vec3 firstWorld = renderCenter(level, first.targetPos(), first.jointPos(), partialTick);
		if (!(minecraft.hitResult instanceof BlockHitResult hit)
			|| hit.getType() == HitResult.Type.MISS) {
			SlimeBeltConnectorHandler.spawnConnectionParticle(level, firstWorld, true);
			return;
		}

		BlockPos secondTarget = hit.getBlockPos();
		Direction secondFace = hit.getDirection();
		BlockPos secondJoint = UniversalJointItem.getJointPos(secondTarget, secondFace);
		if (!UniversalJointItem.isWithinPreviewRange(level, first.jointPos(), secondJoint))
			return;

		boolean valid = UniversalJointItem.canConnect(level, first.targetPos(), first.clickedFace(),
			secondTarget, secondFace);
		Vec3 secondWorld = renderCenter(level, secondTarget, secondJoint, partialTick);
		SlimeBeltConnectorHandler.spawnConnectionLine(level, firstWorld, secondWorld, valid);
	}

	private static void renderRepairPreview(Minecraft minecraft, Level level, ItemStack box) {
		Selection selection = UniversalJointRepair.readSelection(box);
		if (selection == null)
			return;
		BlockEntity selectedRaw = SubLevelCompat.resolveBlockEntityFast(level,
			selection.cachedAddress().rawPos(), selection.cachedAddress().subLevelId());
		if (!(selectedRaw instanceof HalfShaftBlockEntity selected)
			|| !selected.getEndpointId().equals(selection.endpointId()))
			return;

		float partialTick = AnimationTickHolder.getPartialTicks();
		Vec3 firstWorld = renderCenter(level, selected.getBlockPos(), selected.getBlockPos(), partialTick);
		if (!(minecraft.hitResult instanceof BlockHitResult hit)
			|| hit.getType() == HitResult.Type.MISS) {
			SlimeBeltConnectorHandler.spawnConnectionParticle(level, firstWorld, true);
			return;
		}

		BlockEntity secondRaw = level.getBlockEntity(hit.getBlockPos());
		if (!(secondRaw instanceof HalfShaftBlockEntity second))
			return;
		Vec3 secondWorld = renderCenter(level, second.getBlockPos(), second.getBlockPos(), partialTick);
		boolean valid = !selected.getEndpointId().equals(second.getEndpointId())
			&& SubLevelCompat.distanceSquared(level, Vec3.atCenterOf(selected.getBlockPos()),
				Vec3.atCenterOf(second.getBlockPos()))
				<= UniversalJointItem.getConnectionRange() * UniversalJointItem.getConnectionRange();
		SlimeBeltConnectorHandler.spawnConnectionLine(level, firstWorld, secondWorld, valid);
	}

	private static Vec3 renderCenter(Level level, BlockPos spaceAnchor, BlockPos pos,
		float partialTick) {
		return SubLevelCompat.toRenderWorld(SubLevelCompat.getContaining(level, spaceAnchor),
			Vec3.atCenterOf(pos), partialTick);
	}
}
