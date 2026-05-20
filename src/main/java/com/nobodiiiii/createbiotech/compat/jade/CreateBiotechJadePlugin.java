package com.nobodiiiii.createbiotech.compat.jade;

import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.content.evokerenchantingchamber.EvokerEnchantingChamberBlock;
import com.nobodiiiii.createbiotech.content.evokerenchantingchamber.EvokerEnchantingChamberBlockEntity;
import com.nobodiiiii.createbiotech.content.experience.ExperienceConstants;
import com.nobodiiiii.createbiotech.content.experience.ExperienceCrystallizerBlock;
import com.nobodiiiii.createbiotech.content.experience.ExperienceCrystallizerBlockEntity;
import com.nobodiiiii.createbiotech.content.experience.ExperienceTankBlock;
import com.nobodiiiii.createbiotech.content.experience.ExperienceTankBlockEntity;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;
import snownee.jade.api.config.IPluginConfig;

@WailaPlugin
public class CreateBiotechJadePlugin implements IWailaPlugin {

	public static final ResourceLocation EXPERIENCE_TANK_INFO = CreateBiotech.asResource("experience_tank_info");
	public static final ResourceLocation EXPERIENCE_CRYSTALLIZER_INFO =
		CreateBiotech.asResource("experience_crystallizer_info");
	public static final ResourceLocation EVOKER_ENCHANTING_CHAMBER_INFO =
		CreateBiotech.asResource("evoker_enchanting_chamber_info");

	@Override
	public void register(IWailaCommonRegistration registration) {
		registration.registerBlockDataProvider(ExperienceTankServerData.INSTANCE, ExperienceTankBlockEntity.class);
		registration.registerBlockDataProvider(ExperienceCrystallizerServerData.INSTANCE,
			ExperienceCrystallizerBlockEntity.class);
		registration.registerBlockDataProvider(EvokerEnchantingChamberServerData.INSTANCE,
			EvokerEnchantingChamberBlockEntity.class);
	}

	@Override
	public void registerClient(IWailaClientRegistration registration) {
		registration.registerBlockComponent(ExperienceTankComponent.INSTANCE, ExperienceTankBlock.class);
		registration.registerBlockComponent(ExperienceCrystallizerComponent.INSTANCE,
			ExperienceCrystallizerBlock.class);
		registration.registerBlockComponent(EvokerEnchantingChamberComponent.INSTANCE,
			EvokerEnchantingChamberBlock.class);
	}

	public enum ExperienceTankComponent implements IBlockComponentProvider {
		INSTANCE;

		@Override
		public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
			CompoundTag data = accessor.getServerData();
			if (!data.contains("Stored") || !data.contains("Capacity"))
				return;
			int stored = data.getInt("Stored");
			int capacity = data.getInt("Capacity");
			tooltip.add(Component.translatable("create_biotech.gui.goggles.experience_container")
				.withStyle(ChatFormatting.GRAY));
			tooltip.add(Component.literal(stored + " XP")
				.withStyle(ChatFormatting.GOLD)
				.append(Component.literal(" / ").withStyle(ChatFormatting.GRAY))
				.append(Component.literal(capacity + " XP").withStyle(ChatFormatting.DARK_GRAY)));
		}

		@Override
		public ResourceLocation getUid() {
			return EXPERIENCE_TANK_INFO;
		}
	}

	public enum ExperienceCrystallizerComponent implements IBlockComponentProvider {
		INSTANCE;

		@Override
		public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
			CompoundTag data = accessor.getServerData();
			if (data.contains("Buffered")) {
				int buffered = data.getInt("Buffered");
				int perNugget = data.getInt("PerNugget");
				tooltip.add(Component.translatable("create_biotech.gui.goggles.experience_buffer")
					.withStyle(ChatFormatting.GRAY));
				tooltip.add(Component.literal(buffered + " XP")
					.withStyle(ChatFormatting.GOLD)
					.append(Component.literal(" / ").withStyle(ChatFormatting.GRAY))
					.append(Component.literal(perNugget + " XP").withStyle(ChatFormatting.DARK_GRAY)));
			}
			if (data.contains("OutputCount") && data.getInt("OutputCount") > 0) {
				int count = data.getInt("OutputCount");
				int max = data.getInt("OutputMax");
				tooltip.add(Component.translatable("create_biotech.gui.goggles.crystallized_nuggets")
					.withStyle(ChatFormatting.GRAY));
				tooltip.add(Component.literal(count + "")
					.withStyle(ChatFormatting.GOLD)
					.append(Component.literal(" / ").withStyle(ChatFormatting.GRAY))
					.append(Component.literal(max + "").withStyle(ChatFormatting.DARK_GRAY)));
			}
		}

		@Override
		public ResourceLocation getUid() {
			return EXPERIENCE_CRYSTALLIZER_INFO;
		}
	}

	public enum ExperienceTankServerData implements IServerDataProvider<BlockAccessor> {
		INSTANCE;

		@Override
		public void appendServerData(CompoundTag tag, BlockAccessor accessor) {
			if (!(accessor.getBlockEntity() instanceof ExperienceTankBlockEntity tank))
				return;
			tag.putInt("Stored", tank.getStoredExperience());
			tag.putInt("Capacity", tank.getCapacity());
		}

		@Override
		public ResourceLocation getUid() {
			return EXPERIENCE_TANK_INFO;
		}
	}

	public enum ExperienceCrystallizerServerData implements IServerDataProvider<BlockAccessor> {
		INSTANCE;

		@Override
		public void appendServerData(CompoundTag tag, BlockAccessor accessor) {
			if (!(accessor.getBlockEntity() instanceof ExperienceCrystallizerBlockEntity crystallizer))
				return;
			tag.putInt("Buffered", crystallizer.getBufferedXp());
			tag.putInt("PerNugget", ExperienceConstants.XP_PER_NUGGET);
			tag.putInt("OutputCount", crystallizer.getOutputCount());
			tag.putInt("OutputMax", ExperienceCrystallizerBlockEntity.MAX_STACK);
		}

		@Override
		public ResourceLocation getUid() {
			return EXPERIENCE_CRYSTALLIZER_INFO;
		}
	}

	public enum EvokerEnchantingChamberComponent implements IBlockComponentProvider {
		INSTANCE;

		@Override
		public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
			CompoundTag data = accessor.getServerData();
			if (!data.contains("Stored") || !data.contains("Capacity"))
				return;
			int stored = data.getInt("Stored");
			int capacity = data.getInt("Capacity");
			tooltip.add(Component.translatable("create_biotech.gui.goggles.enchanting_chamber.cache")
				.withStyle(ChatFormatting.GRAY));
			tooltip.add(Component.literal(stored + " XP")
				.withStyle(ChatFormatting.GOLD)
				.append(Component.literal(" / ").withStyle(ChatFormatting.GRAY))
				.append(Component.literal(capacity + " XP").withStyle(ChatFormatting.DARK_GRAY)));
			if (data.contains("XpTotal") && data.getInt("XpTotal") > 0) {
				int consumed = data.getInt("XpConsumed");
				int total = data.getInt("XpTotal");
				tooltip.add(Component.translatable("create_biotech.gui.goggles.enchanting_chamber.progress")
					.withStyle(ChatFormatting.GRAY));
				tooltip.add(Component.literal(consumed + " XP")
					.withStyle(ChatFormatting.AQUA)
					.append(Component.literal(" / ").withStyle(ChatFormatting.GRAY))
					.append(Component.literal(total + " XP").withStyle(ChatFormatting.DARK_GRAY)));
				if (data.getBoolean("Waiting"))
					tooltip.add(Component.translatable("create_biotech.gui.goggles.enchanting_chamber.waiting")
						.withStyle(ChatFormatting.RED));
			} else if (data.getBoolean("Ready")) {
				tooltip.add(Component.translatable("create_biotech.gui.goggles.enchanting_chamber.ready")
					.withStyle(ChatFormatting.GREEN));
			}
		}

		@Override
		public ResourceLocation getUid() {
			return EVOKER_ENCHANTING_CHAMBER_INFO;
		}
	}

	public enum EvokerEnchantingChamberServerData implements IServerDataProvider<BlockAccessor> {
		INSTANCE;

		@Override
		public void appendServerData(CompoundTag tag, BlockAccessor accessor) {
			EvokerEnchantingChamberBlockEntity chamber = resolveChamber(accessor);
			if (chamber == null)
				return;
			tag.putInt("Stored", chamber.getStoredExperience());
			tag.putInt("Capacity", ExperienceConstants.CHAMBER_CACHE_CAPACITY);
			tag.putInt("XpTotal", chamber.getXpTotal());
			tag.putInt("XpConsumed", chamber.getXpConsumed());
			tag.putBoolean("Waiting", chamber.isWaitingForExperience());
			tag.putBoolean("Ready", !chamber.getPendingOutput().isEmpty());
		}

		private static EvokerEnchantingChamberBlockEntity resolveChamber(BlockAccessor accessor) {
			BlockEntity be = accessor.getBlockEntity();
			if (be instanceof EvokerEnchantingChamberBlockEntity chamber)
				return chamber.getController();
			BlockState state = accessor.getBlockState();
			if (state.getBlock() instanceof EvokerEnchantingChamberBlock
				&& state.getValue(EvokerEnchantingChamberBlock.HALF) == DoubleBlockHalf.UPPER) {
				BlockPos lowerPos = accessor.getPosition().below();
				BlockEntity lower = accessor.getLevel().getBlockEntity(lowerPos);
				if (lower instanceof EvokerEnchantingChamberBlockEntity chamber)
					return chamber;
			}
			return null;
		}

		@Override
		public ResourceLocation getUid() {
			return EVOKER_ENCHANTING_CHAMBER_INFO;
		}
	}
}
