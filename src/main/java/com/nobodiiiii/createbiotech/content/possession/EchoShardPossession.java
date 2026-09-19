package com.nobodiiiii.createbiotech.content.possession;

import java.util.List;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.nobodiiiii.createbiotech.content.slimemimic.SlimeMimicHandler;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/** Serialization and body-attribute rules shared by server possession and client rendering. */
public final class EchoShardPossession {
	public static final String PLAYER_STATE_TAG = "CreateBiotechPossession";
	private static final int VERSION = 1;
	private static final String VERSION_TAG = "Version";
	private static final String BODY_TYPE_TAG = "BodyType";
	private static final String BODY_DATA_TAG = "BodyData";
	private static final String RENDER_DATA_TAG = "RenderData";
	private static final String WIDTH_TAG = "Width";
	private static final String HEIGHT_TAG = "Height";
	private static final String EYE_HEIGHT_TAG = "EyeHeight";
	private static final String ORIGINAL_ATTRIBUTES_TAG = "OriginalAttributes";
	private static final String ORIGINAL_HEALTH_TAG = "OriginalHealth";
	private static final String ORIGINAL_ABSORPTION_TAG = "OriginalAbsorption";
	private static final float MAX_DIMENSION = 64.0f;

	/** Attributes which describe the physical body and are present on vanilla players. */
	private static final List<Holder<Attribute>> BODY_ATTRIBUTES = List.of(
		Attributes.MAX_HEALTH,
		Attributes.MOVEMENT_SPEED,
		Attributes.ATTACK_DAMAGE,
		Attributes.ARMOR,
		Attributes.ARMOR_TOUGHNESS,
		Attributes.KNOCKBACK_RESISTANCE,
		Attributes.ATTACK_KNOCKBACK,
		Attributes.STEP_HEIGHT,
		Attributes.GRAVITY,
		Attributes.SAFE_FALL_DISTANCE,
		Attributes.FALL_DAMAGE_MULTIPLIER,
		Attributes.JUMP_STRENGTH,
		Attributes.OXYGEN_BONUS,
		Attributes.MAX_ABSORPTION,
		Attributes.BURNING_TIME,
		Attributes.EXPLOSION_KNOCKBACK_RESISTANCE,
		Attributes.WATER_MOVEMENT_EFFICIENCY,
		Attributes.MOVEMENT_EFFICIENCY);

	private EchoShardPossession() {}

	public static boolean isActive(CompoundTag state) {
		return state != null && state.getInt(VERSION_TAG) == VERSION
			&& state.contains(BODY_TYPE_TAG, Tag.TAG_STRING)
			&& ResourceLocation.tryParse(state.getString(BODY_TYPE_TAG)) != null;
	}

	public static CompoundTag capture(Player player, LivingEntity target, @Nullable CompoundTag previousState) {
		ResourceLocation bodyType = BuiltInRegistries.ENTITY_TYPE.getKey(target.getType());
		if (bodyType == null)
			return new CompoundTag();

		CompoundTag state = new CompoundTag();
		state.putInt(VERSION_TAG, VERSION);
		state.putString(BODY_TYPE_TAG, bodyType.toString());
		CompoundTag bodyData = target.saveWithoutId(new CompoundTag());
		sanitizeStoredBodyData(bodyData);
		state.put(BODY_DATA_TAG, bodyData);
		state.put(RENDER_DATA_TAG, sanitizeRenderData(bodyData));

		EntityDimensions dimensions = target.getDimensions(target.getPose());
		state.putFloat(WIDTH_TAG, sanitizeDimension(dimensions.width(), target.getType().getWidth()));
		state.putFloat(HEIGHT_TAG, sanitizeDimension(dimensions.height(), target.getType().getHeight()));
		state.putFloat(EYE_HEIGHT_TAG,
			Mth.clamp(dimensions.eyeHeight(), 0.0f, state.getFloat(HEIGHT_TAG)));

		if (isActive(previousState) && previousState.contains(ORIGINAL_ATTRIBUTES_TAG, Tag.TAG_COMPOUND)) {
			state.put(ORIGINAL_ATTRIBUTES_TAG, previousState.getCompound(ORIGINAL_ATTRIBUTES_TAG).copy());
			state.putFloat(ORIGINAL_HEALTH_TAG, previousState.getFloat(ORIGINAL_HEALTH_TAG));
			state.putFloat(ORIGINAL_ABSORPTION_TAG, previousState.getFloat(ORIGINAL_ABSORPTION_TAG));
		} else {
			state.put(ORIGINAL_ATTRIBUTES_TAG, captureAttributeBases(player));
			state.putFloat(ORIGINAL_HEALTH_TAG, player.getHealth());
			state.putFloat(ORIGINAL_ABSORPTION_TAG, player.getAbsorptionAmount());
		}
		return state;
	}

	/** Removes server-only body and restoration data before the state enters entity-data packets. */
	public static CompoundTag synchronizedView(CompoundTag state) {
		if (!isActive(state))
			return new CompoundTag();
		CompoundTag synchronizedState = new CompoundTag();
		synchronizedState.putInt(VERSION_TAG, VERSION);
		synchronizedState.putString(BODY_TYPE_TAG, state.getString(BODY_TYPE_TAG));
		synchronizedState.put(BODY_DATA_TAG, state.contains(RENDER_DATA_TAG, Tag.TAG_COMPOUND)
			? state.getCompound(RENDER_DATA_TAG).copy() : new CompoundTag());
		synchronizedState.putFloat(WIDTH_TAG, state.getFloat(WIDTH_TAG));
		synchronizedState.putFloat(HEIGHT_TAG, state.getFloat(HEIGHT_TAG));
		synchronizedState.putFloat(EYE_HEIGHT_TAG, state.getFloat(EYE_HEIGHT_TAG));
		return synchronizedState;
	}

	public static EntityDimensions dimensions(CompoundTag state) {
		float width = sanitizeDimension(state.getFloat(WIDTH_TAG), Player.STANDING_DIMENSIONS.width());
		float height = sanitizeDimension(state.getFloat(HEIGHT_TAG), Player.STANDING_DIMENSIONS.height());
		float eyeHeight = Mth.clamp(state.getFloat(EYE_HEIGHT_TAG), 0.0f, height);
		return EntityDimensions.fixed(width, height).withEyeHeight(eyeHeight);
	}

	@Nullable
	public static LivingEntity createBody(Level level, CompoundTag state) {
		if (!isActive(state))
			return null;
		ResourceLocation bodyTypeId = ResourceLocation.tryParse(state.getString(BODY_TYPE_TAG));
		if (bodyTypeId == null)
			return null;
		EntityType<?> bodyType = BuiltInRegistries.ENTITY_TYPE.getOptional(bodyTypeId).orElse(null);
		if (bodyType == null)
			return null;
		Entity created = bodyType.create(level);
		if (!(created instanceof LivingEntity body))
			return null;

		CompoundTag bodyData = state.contains(BODY_DATA_TAG, Tag.TAG_COMPOUND)
			? state.getCompound(BODY_DATA_TAG).copy() : new CompoundTag();
		UUID freshUuid = body.getUUID();
		body.load(bodyData);
		body.setUUID(freshUuid);
		SlimeMimicHandler.markHauntedMimic(body);
		return body;
	}

	public static void applyBodyAttributes(Player player, LivingEntity body, CompoundTag state) {
		CompoundTag originalAttributes = state.contains(ORIGINAL_ATTRIBUTES_TAG, Tag.TAG_COMPOUND)
			? state.getCompound(ORIGINAL_ATTRIBUTES_TAG) : new CompoundTag();
		for (Holder<Attribute> attribute : BODY_ATTRIBUTES) {
			AttributeInstance playerAttribute = player.getAttribute(attribute);
			AttributeInstance bodyAttribute = body.getAttribute(attribute);
			if (playerAttribute == null)
				continue;
			if (bodyAttribute != null) {
				double bodyBase = attribute == Attributes.MAX_HEALTH
					? body.getMaxHealth() : bodyAttribute.getBaseValue();
				playerAttribute.setBaseValue(bodyBase);
				continue;
			}
			ResourceLocation attributeId = BuiltInRegistries.ATTRIBUTE.getKey(attribute.value());
			if (attributeId != null && originalAttributes.contains(attributeId.toString(), Tag.TAG_ANY_NUMERIC))
				playerAttribute.setBaseValue(originalAttributes.getDouble(attributeId.toString()));
		}
		player.setHealth(Math.min(body.getHealth(), player.getMaxHealth()));
		player.setAbsorptionAmount(Math.min(body.getAbsorptionAmount(), player.getMaxAbsorption()));
	}

	public static void restorePlayerAttributes(Player player, CompoundTag state) {
		if (!state.contains(ORIGINAL_ATTRIBUTES_TAG, Tag.TAG_COMPOUND))
			return;
		CompoundTag originalAttributes = state.getCompound(ORIGINAL_ATTRIBUTES_TAG);
		for (Holder<Attribute> attribute : BODY_ATTRIBUTES) {
			AttributeInstance playerAttribute = player.getAttribute(attribute);
			ResourceLocation attributeId = BuiltInRegistries.ATTRIBUTE.getKey(attribute.value());
			if (playerAttribute == null || attributeId == null
				|| !originalAttributes.contains(attributeId.toString(), Tag.TAG_ANY_NUMERIC))
				continue;
			playerAttribute.setBaseValue(originalAttributes.getDouble(attributeId.toString()));
		}
		player.setHealth(Math.min(state.getFloat(ORIGINAL_HEALTH_TAG), player.getMaxHealth()));
		player.setAbsorptionAmount(Math.min(state.getFloat(ORIGINAL_ABSORPTION_TAG), player.getMaxAbsorption()));
	}

	private static CompoundTag captureAttributeBases(Player player) {
		CompoundTag attributes = new CompoundTag();
		for (Holder<Attribute> attribute : BODY_ATTRIBUTES) {
			AttributeInstance instance = player.getAttribute(attribute);
			ResourceLocation attributeId = BuiltInRegistries.ATTRIBUTE.getKey(attribute.value());
			if (instance != null && attributeId != null)
				attributes.putDouble(attributeId.toString(), instance.getBaseValue());
		}
		return attributes;
	}

	private static void sanitizeStoredBodyData(CompoundTag bodyData) {
		for (String field : List.of("UUID", "Pos", "Motion", "Rotation", "Passengers", "Leash",
			"Dimension", "PortalCooldown", "SleepingX", "SleepingY", "SleepingZ"))
			bodyData.remove(field);
	}

	private static CompoundTag sanitizeRenderData(CompoundTag bodyData) {
		CompoundTag renderData = bodyData.copy();
		for (String field : List.of("Health", "AbsorptionAmount", "HurtTime", "DeathTime",
			"HurtByTimestamp", "Brain", "attributes", "active_effects", "FallDistance", "Fire",
			"Air", "OnGround", "Invulnerable"))
			renderData.remove(field);
		return renderData;
	}

	private static float sanitizeDimension(float value, float fallback) {
		if (!Float.isFinite(value) || value <= 0.0f)
			value = fallback;
		return Mth.clamp(value, 1.0f / 32.0f, MAX_DIMENSION);
	}
}
