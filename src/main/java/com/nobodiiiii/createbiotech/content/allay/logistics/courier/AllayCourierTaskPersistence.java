package com.nobodiiiii.createbiotech.content.allay.logistics.courier;

import com.nobodiiiii.createbiotech.content.allay.entity.courier.AllayCourierEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import java.util.UUID;

/** AllayCourierTaskPersistence algorithms over the canonical AllayCourierTask state. */
final class AllayCourierTaskPersistence {
	private AllayCourierTaskPersistence() {}


	static CompoundTag save(AllayCourierTask operationOwner, HolderLookup.Provider registries, CompoundTag tag) {
		tag.putInt("CoordinateModelVersion", 1);
		tag.putUUID("Id", operationOwner.id);
		tag.put("Box", operationOwner.box.saveOptional(registries));
		tag.putString("CurrentDimension", operationOwner.currentDimension.location().toString());
		tag.putString("TargetDimension", operationOwner.targetDimension.location().toString());
		if (operationOwner.sourceDimension != null) tag.putString("SourceDimension", operationOwner.sourceDimension.location().toString());
		if (operationOwner.sourceAllayPortPos != null) tag.put("SourceAllayPortPos", NbtUtils.writeBlockPos(operationOwner.sourceAllayPortPos));
		if (operationOwner.targetAllayPortPos != null) tag.put("TargetAllayPortPos", NbtUtils.writeBlockPos(operationOwner.targetAllayPortPos));
		if (operationOwner.sourceAllayPortSubLevelId != null) tag.putUUID("SourceAllayPortSubLevelId", operationOwner.sourceAllayPortSubLevelId);
		if (operationOwner.targetAllayPortSubLevelId != null) tag.putUUID("TargetAllayPortSubLevelId", operationOwner.targetAllayPortSubLevelId);
		if (operationOwner.targetPlayerId != null) tag.putUUID("TargetPlayer", operationOwner.targetPlayerId);
		if (operationOwner.sourcePlayerId != null) tag.putUUID("SourcePlayer", operationOwner.sourcePlayerId);
		tag.putString("SourceAddress", operationOwner.sourceAddress);
		tag.putString("ReturnMode", operationOwner.returnMode.serializedName());
		tag.putByte("Mission", (byte) operationOwner.mission.ordinal());
		tag.putByte("Phase", (byte) operationOwner.phase.ordinal());
		tag.put("Position", AllayCourierTaskPersistence.vecToTag(operationOwner.position));
		tag.put("LaunchDirection", AllayCourierTaskPersistence.vecToTag(operationOwner.launchDirection));
		tag.putString("PortMotion", operationOwner.portMotion.name());
		if (operationOwner.portDepartureOrigin != null) tag.put("PortDepartureOrigin", AllayCourierTaskPersistence.vecToTag(operationOwner.portDepartureOrigin));
		if (operationOwner.portDepartureClearTarget != null) {
			tag.put("PortDepartureClearTarget", AllayCourierTaskPersistence.vecToTag(operationOwner.portDepartureClearTarget));
		}
		if (operationOwner.portDepartureAllayPortPos != null) {
			tag.put("PortDepartureAllayPortPos", NbtUtils.writeBlockPos(operationOwner.portDepartureAllayPortPos));
		}
		if (operationOwner.portDepartureAllayPortSubLevelId != null) {
			tag.putUUID("PortDepartureAllayPortSubLevelId", operationOwner.portDepartureAllayPortSubLevelId);
		}
		tag.putInt("PhaseTicks", operationOwner.phaseTicks);
		tag.putInt("PortMotionTicks", operationOwner.portMotionTicks);
		tag.putInt("DeliveryElapsedTicks", operationOwner.deliveryElapsedTicks);
		tag.putBoolean("TeleportedNearTarget", operationOwner.teleportedNearTarget);
		tag.putBoolean("ForceArrivalPending", operationOwner.forceArrivalPending);
		if (operationOwner.lastKnownTargetDimension != null) {
			tag.putString("LastKnownTargetDimension", operationOwner.lastKnownTargetDimension.location().toString());
		}
		if (operationOwner.lastKnownTargetPosition != null) {
			tag.put("LastKnownTargetPosition", AllayCourierTaskPersistence.vecToTag(operationOwner.lastKnownTargetPosition));
		}
		tag.putInt("LostTargetTicks", operationOwner.lostTargetTicks);
		return tag;
	}

	static AllayCourierTask load(HolderLookup.Provider registries, CompoundTag tag) {
		UUID id = tag.getUUID("Id");
		ItemStack box = ItemStack.parseOptional(registries, tag.getCompound("Box"));
		ResourceKey<Level> currentDimension = AllayCourierTaskPersistence.dimensionKey(tag.getString("CurrentDimension"));
		ResourceKey<Level> targetDimension = AllayCourierTaskPersistence.dimensionKey(tag.getString("TargetDimension"));
		ResourceKey<Level> sourceDimension = tag.contains("SourceDimension")
			? AllayCourierTaskPersistence.dimensionKey(tag.getString("SourceDimension")) : null;
		BlockPos sourceAllayPort = NbtUtils.readBlockPos(tag, "SourceAllayPortPos")
			.orElse(null);
		BlockPos targetAllayPort = NbtUtils.readBlockPos(tag, "TargetAllayPortPos")
			.orElse(null);
		UUID sourceAllayPortSubLevelId = tag.hasUUID("SourceAllayPortSubLevelId")
			? tag.getUUID("SourceAllayPortSubLevelId") : null;
		UUID targetAllayPortSubLevelId = tag.hasUUID("TargetAllayPortSubLevelId")
			? tag.getUUID("TargetAllayPortSubLevelId") : null;
		UUID targetPlayer = tag.hasUUID("TargetPlayer") ? tag.getUUID("TargetPlayer") : null;
		UUID sourcePlayer = tag.hasUUID("SourcePlayer") ? tag.getUUID("SourcePlayer") : null;
		AllayCourierReturnMode returnMode = tag.contains("ReturnMode")
			? AllayCourierReturnMode.byName(tag.getString("ReturnMode"))
			: AllayCourierTask.defaultReturnMode(sourceAllayPort, sourcePlayer);
		AllayCourierEntity.Mission mission = AllayCourierEntity.Mission.values()[tag.getByte("Mission")];
		AllayCourierEntity.Phase phase = AllayCourierEntity.Phase.values()[tag.getByte("Phase")];

		AllayCourierTask task = new AllayCourierTask(id, box, currentDimension, targetDimension,
			sourceAllayPort, targetAllayPort, sourceAllayPortSubLevelId,
			targetAllayPortSubLevelId, targetPlayer,
			sourcePlayer, sourceDimension, tag.getString("SourceAddress"), returnMode, mission,
			AllayCourierTaskPersistence.vecFromTag(tag, "Position"), AllayCourierTaskPersistence.vecFromTag(tag, "LaunchDirection"));
		task.worldCoordinates = tag.getInt("CoordinateModelVersion") >= 1;
		task.phase = phase;
		task.phaseTicks = tag.getInt("PhaseTicks");
		task.portMotionTicks = tag.getInt("PortMotionTicks");
		task.deliveryElapsedTicks = tag.getInt("DeliveryElapsedTicks");
		task.teleportedNearTarget = tag.getBoolean("TeleportedNearTarget");
		task.forceArrivalPending = tag.getBoolean("ForceArrivalPending");
		if (tag.contains("LastKnownTargetDimension")) {
			task.lastKnownTargetDimension = AllayCourierTaskPersistence.dimensionKey(tag.getString("LastKnownTargetDimension"));
		}
		if (tag.contains("LastKnownTargetPosition")) {
			task.lastKnownTargetPosition = AllayCourierTaskPersistence.vecFromTag(tag, "LastKnownTargetPosition");
		}
		task.lostTargetTicks = tag.getInt("LostTargetTicks");
		if (tag.contains("PortMotion")) {
			task.portMotion = AllayCourierTask.PortMotion.byName(tag.getString("PortMotion"));
			task.portDepartureOrigin = tag.contains("PortDepartureOrigin")
				? AllayCourierTaskPersistence.vecFromTag(tag, "PortDepartureOrigin") : null;
			task.portDepartureClearTarget = tag.contains("PortDepartureClearTarget")
				? AllayCourierTaskPersistence.vecFromTag(tag, "PortDepartureClearTarget") : null;
			task.portDepartureAllayPortPos = NbtUtils.readBlockPos(tag, "PortDepartureAllayPortPos")
				.orElse(null);
			task.portDepartureAllayPortSubLevelId = tag.hasUUID("PortDepartureAllayPortSubLevelId")
				? tag.getUUID("PortDepartureAllayPortSubLevelId") : null;
		} else if (tag.getInt("AllayPortEntryTicks") >= 0 && tag.contains("AllayPortEntryTicks")) {
			task.portMotion = AllayCourierTask.PortMotion.ARRIVAL_INSIDE;
		} else if (tag.contains("InitialWaypoint")) {
			Vec3 legacyWaypoint = AllayCourierTaskPersistence.vecFromTag(tag, "InitialWaypoint");
			task.portDepartureOrigin = legacyWaypoint.subtract(task.launchDirection.scale(0.5));
			task.portMotion = AllayCourierTask.PortMotion.DEPARTURE_MOUTH;
		}
		return task;
	}

	static ResourceKey<Level> dimensionKey(String id) {
		return ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(id));
	}

	static CompoundTag vecToTag(Vec3 vector) {
		CompoundTag tag = new CompoundTag();
		tag.putDouble("X", vector.x);
		tag.putDouble("Y", vector.y);
		tag.putDouble("Z", vector.z);
		return tag;
	}

	static Vec3 vecFromTag(CompoundTag tag, String key) {
		CompoundTag vector = tag.getCompound(key);
		return new Vec3(vector.getDouble("X"), vector.getDouble("Y"), vector.getDouble("Z"));
	}
}
