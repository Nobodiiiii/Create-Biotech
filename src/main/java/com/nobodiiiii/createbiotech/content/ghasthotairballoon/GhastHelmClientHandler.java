package com.nobodiiiii.createbiotech.content.ghasthotairballoon;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.nobodiiiii.createbiotech.CreateBiotech;
import com.simibubi.create.AllPackets;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.actors.trainControls.ControlsHandler;
import com.simibubi.create.content.contraptions.actors.trainControls.ControlsInputPacket;

import net.createmod.catnip.math.AngleHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = CreateBiotech.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class GhastHelmClientHandler {

	private static final double DETECT_RADIUS = 16d;
	private static final double ENGAGED_RADIUS = 24d;
	private static final int SCAN_PERIOD_TICKS = 10;
	private static final double VERTICAL_DEADZONE = 0.5d;
	private static final double HORIZONTAL_DEADZONE = 0.5d;
	private static final float YAW_DEADZONE = 5f;
	private static final float ALIGN_FOR_FORWARD_DEGREES = 60f;
	private static final Set<Integer> ALL_BUTTONS = Set.of(0, 1, 2, 3, 4, 5);

	private static boolean previousSprintDown;
	private static boolean controllingGhastBalloon;

	private static BlockPos nearestStation;
	private static int scanCooldown;
	private static boolean magnetEngaged;

	private GhastHelmClientHandler() {}

	public static void startControlling(GhastHotAirBalloonEntity entity) {
		controllingGhastBalloon = entity != null;
		previousSprintDown = false;
		nearestStation = null;
		scanCooldown = 0;
		magnetEngaged = false;
	}

	public static boolean shouldShowMagnetPrompt() {
		return controllingGhastBalloon && nearestStation != null;
	}

	@SubscribeEvent
	public static void onClientTick(TickEvent.ClientTickEvent event) {
		if (event.phase != TickEvent.Phase.END)
			return;

		if (!controllingGhastBalloon)
			return;

		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null || minecraft.level == null) {
			reset();
			return;
		}

		AbstractContraptionEntity contraption = ControlsHandler.getContraption();
		BlockPos controlsPos = ControlsHandler.getControlsPos();
		if (contraption == null || controlsPos == null) {
			reset();
			return;
		}

		boolean sprintDown = minecraft.options.keySprint.isDown();
		if (sprintDown && !previousSprintDown) {
			AllPackets.getChannel().sendToServer(
				new ControlsInputPacket(ControlsHandler.currentlyPressed, false, contraption.getId(), controlsPos, true));
			ControlsHandler.stopControlling();
			reset();
			return;
		}
		previousSprintDown = sprintDown;

		tickMagnetSnap(minecraft, contraption, controlsPos);
	}

	private static void tickMagnetSnap(Minecraft minecraft, AbstractContraptionEntity contraption, BlockPos controlsPos) {
		if (!(contraption instanceof GhastHotAirBalloonEntity balloon)) {
			nearestStation = null;
			magnetEngaged = false;
			return;
		}
		if (!(balloon.getVehicle() instanceof Ghast ghast) || !ghast.isAlive()) {
			nearestStation = null;
			magnetEngaged = false;
			return;
		}

		if (--scanCooldown <= 0) {
			double radius = magnetEngaged ? ENGAGED_RADIUS : DETECT_RADIUS;
			nearestStation = findClosestValidStation(minecraft.level, ghast.position(), radius);
			scanCooldown = SCAN_PERIOD_TICKS;
		}

		boolean altDown = minecraft.screen == null && Screen.hasAltDown();
		if (altDown && nearestStation != null) {
			Set<Integer> buttons = computeSyntheticButtons(ghast, nearestStation);
			if (!buttons.isEmpty()) {
				AllPackets.getChannel().sendToServer(new ControlsInputPacket(
					buttons, true, balloon.getId(), controlsPos, false));
			}
			magnetEngaged = true;
		} else if (magnetEngaged) {
			AllPackets.getChannel().sendToServer(new ControlsInputPacket(
				new HashSet<>(ALL_BUTTONS), false, balloon.getId(), controlsPos, false));
			magnetEngaged = false;
		}
	}

	private static BlockPos findClosestValidStation(ClientLevel level, Vec3 center, double radius) {
		double r2 = radius * radius;
		int minCX = SectionPos.blockToSectionCoord((int) Math.floor(center.x - radius));
		int maxCX = SectionPos.blockToSectionCoord((int) Math.ceil(center.x + radius));
		int minCZ = SectionPos.blockToSectionCoord((int) Math.floor(center.z - radius));
		int maxCZ = SectionPos.blockToSectionCoord((int) Math.ceil(center.z + radius));

		BlockPos closest = null;
		double closestDistSqr = Double.MAX_VALUE;
		for (int cx = minCX; cx <= maxCX; cx++) {
			for (int cz = minCZ; cz <= maxCZ; cz++) {
				if (!level.hasChunk(cx, cz))
					continue;
				LevelChunk chunk = level.getChunk(cx, cz);
				for (Map.Entry<BlockPos, BlockEntity> entry : chunk.getBlockEntities().entrySet()) {
					if (!(entry.getValue() instanceof GhastHotAirBalloonAssemblyStationBlockEntity station))
						continue;
					if (!station.isReadyToAccept())
						continue;
					BlockPos pos = entry.getKey();
					Vec3 stationCenter = new Vec3(pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5);
					double dsq = stationCenter.distanceToSqr(center);
					if (dsq > r2)
						continue;
					if (dsq < closestDistSqr) {
						closestDistSqr = dsq;
						closest = pos;
					}
				}
			}
		}
		return closest;
	}

	private static Set<Integer> computeSyntheticButtons(Ghast ghast, BlockPos stationPos) {
		Set<Integer> btns = new HashSet<>();
		double targetY = stationPos.getY() + 1 + GhastHotAirBalloonSeatEntity.GHAST_PASSENGER_Y_OFFSET;
		Vec3 target = new Vec3(stationPos.getX() + 0.5, targetY, stationPos.getZ() + 0.5);
		Vec3 delta = target.subtract(ghast.position());

		if (delta.y > VERTICAL_DEADZONE)
			btns.add(4);
		else if (delta.y < -VERTICAL_DEADZONE)
			btns.add(5);

		double horizDist = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
		if (horizDist > HORIZONTAL_DEADZONE) {
			float desiredYaw = (float) Math.toDegrees(Math.atan2(-delta.x, delta.z));
			float yawDiff = AngleHelper.getShortestAngleDiff(ghast.getYRot(), desiredYaw);

			if (yawDiff > YAW_DEADZONE)
				btns.add(3);
			else if (yawDiff < -YAW_DEADZONE)
				btns.add(2);

			if (Math.abs(yawDiff) < ALIGN_FOR_FORWARD_DEGREES)
				btns.add(0);
		}

		return btns;
	}

	private static void reset() {
		controllingGhastBalloon = false;
		previousSprintDown = false;
		nearestStation = null;
		scanCooldown = 0;
		magnetEngaged = false;
	}
}
