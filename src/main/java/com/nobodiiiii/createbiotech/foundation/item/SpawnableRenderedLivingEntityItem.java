package com.nobodiiiii.createbiotech.foundation.item;

import java.util.Objects;
import java.util.function.Consumer;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;

/**
 * An entity-rendered item that can place its represented mob like a spawn egg.
 */
public class SpawnableRenderedLivingEntityItem<T extends Mob> extends RenderedLivingEntityItem<T> {

	public SpawnableRenderedLivingEntityItem(Properties properties, EntityType<T> entityType) {
		super(properties, entityType);
	}

	public SpawnableRenderedLivingEntityItem(Properties properties, EntityType<T> entityType,
		Consumer<T> renderedEntityConfigurer) {
		super(properties, entityType, renderedEntityConfigurer);
	}

	public SpawnableRenderedLivingEntityItem(Properties properties, EntityType<T> entityType,
		Consumer<T> renderedEntityConfigurer, float scaleMultiplier) {
		super(properties, entityType, renderedEntityConfigurer, scaleMultiplier);
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		Level level = context.getLevel();
		if (!(level instanceof ServerLevel serverLevel))
			return InteractionResult.SUCCESS;

		ItemStack stack = context.getItemInHand();
		BlockPos clickedPos = context.getClickedPos();
		Direction clickedFace = context.getClickedFace();
		BlockState clickedState = level.getBlockState(clickedPos);
		BlockPos spawnPos = clickedState.getCollisionShape(level, clickedPos).isEmpty()
			? clickedPos
			: clickedPos.relative(clickedFace);

		Consumer<T> stackConfigurer = EntityType.appendDefaultStackConfig(entity -> {
		}, serverLevel, stack, context.getPlayer());
		T spawned = getRenderedEntityType().spawn(serverLevel, stack.getTag(),
			stackConfigurer.andThen(this::configureSpawnedEntity), spawnPos, MobSpawnType.SPAWN_EGG, true,
			!Objects.equals(clickedPos, spawnPos) && clickedFace == Direction.UP);
		if (spawned != null) {
			stack.shrink(1);
			level.gameEvent(context.getPlayer(), GameEvent.ENTITY_PLACE, clickedPos);
		}

		return InteractionResult.CONSUME;
	}

	protected void configureSpawnedEntity(T entity) {
		configureRenderedEntity(entity);
	}
}
