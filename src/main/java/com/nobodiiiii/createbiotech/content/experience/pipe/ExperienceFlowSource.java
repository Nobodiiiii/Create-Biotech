package com.nobodiiiii.createbiotech.content.experience.pipe;

import java.lang.ref.WeakReference;

import javax.annotation.Nullable;

import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;

import net.createmod.catnip.math.BlockFace;
import net.minecraft.world.level.Level;

public abstract class ExperienceFlowSource {
	protected final BlockFace location;

	protected ExperienceFlowSource(BlockFace location) {
		this.location = location;
	}

	public boolean canProvideExperience() {
		ExperienceEndpoint endpoint = provideEndpoint();
		return endpoint != null && endpoint.extract(1, true) > 0;
	}

	public void manageSource(Level world) {}

	@Nullable
	public ExperienceEndpoint provideEndpoint() {
		return null;
	}

	public abstract boolean isEndpoint();

	public static class Endpoint extends ExperienceFlowSource {
		@Nullable
		private ExperienceEndpoint cached;

		public Endpoint(BlockFace location) {
			super(location);
		}

		@Override
		public void manageSource(Level world) {
			cached = ExperienceTransport.findEndpoint(world, location);
		}

		@Override
		@Nullable
		public ExperienceEndpoint provideEndpoint() {
			return cached;
		}

		@Override
		public boolean isEndpoint() {
			return true;
		}
	}

	public static class OpenEnded extends ExperienceFlowSource {
		private final OpenEndedExperienceEndpoint endpoint;

		public OpenEnded(BlockFace location) {
			super(location);
			this.endpoint = new OpenEndedExperienceEndpoint(location);
		}

		@Override
		public void manageSource(Level world) {
			endpoint.bind(world);
		}

		@Override
		public ExperienceEndpoint provideEndpoint() {
			return endpoint;
		}

		@Override
		public boolean isEndpoint() {
			return true;
		}
	}

	public static class OtherPipe extends ExperienceFlowSource {
		@Nullable
		private WeakReference<ExperienceTransportBehaviour> cached;

		public OtherPipe(BlockFace location) {
			super(location);
		}

		@Override
		public void manageSource(Level world) {
			ExperienceTransportBehaviour behaviour = cached == null ? null : cached.get();
			if (behaviour != null && !behaviour.blockEntity.isRemoved())
				return;
			behaviour = BlockEntityBehaviour.get(world, location.getConnectedPos(), ExperienceTransportBehaviour.TYPE);
			cached = behaviour == null ? null : new WeakReference<>(behaviour);
		}

		@Override
		public boolean canProvideExperience() {
			ExperienceTransportBehaviour behaviour = cached == null ? null : cached.get();
			return behaviour != null && behaviour.hasProvidedOutwardExperience(location.getOppositeFace());
		}

		@Override
		public boolean isEndpoint() {
			return false;
		}
	}

	public static class Blocked extends ExperienceFlowSource {
		public Blocked(BlockFace location) {
			super(location);
		}

		@Override
		public boolean isEndpoint() {
			return false;
		}
	}
}
