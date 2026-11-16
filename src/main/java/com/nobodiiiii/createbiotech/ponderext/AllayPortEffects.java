package com.nobodiiiii.createbiotech.ponderext;

import com.nododiiiii.ponderer.runtime.api.PonderActions;
import com.nododiiiii.ponderer.scene.LocalizedText;
import com.nobodiiiii.createbiotech.registry.CBParticleTypes;
import com.simibubi.create.content.logistics.box.PackageItem;
import com.simibubi.create.content.logistics.box.PackageStyles;
import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import com.simibubi.create.infrastructure.ponder.scenes.highLogistics.PonderHilo;
import com.nobodiiiii.createbiotech.content.allay.block.allayport.AllayPortBlockEntity;
import com.nobodiiiii.createbiotech.content.allay.entity.courier.AllayCourierEntity;
import com.nobodiiiii.createbiotech.registry.CBEntityTypes;
import net.createmod.ponder.api.element.ElementLink;
import net.createmod.ponder.api.element.EntityElement;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.instruction.TickingInstruction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import com.nododiiiii.ponderer.runtime.api.RuntimeEngine;
import com.nododiiiii.ponderer.runtime.api.StepBuildContext;

/** Author-owned compiled effects. The Ponderer exporter does not overwrite this class. */
final class AllayPortEffects {
    private AllayPortEffects() {}

    static void install(RuntimeEngine engine) {
        engine.steps().registerJava("create_biotech:java/allay_port/1", "向驿站配送", AllayPortEffects::deliverToPort);
        engine.steps().registerJava("create_biotech:java/allay_port/3", "向玩家配送", AllayPortEffects::deliverToPlayer);
    }

    private static void deliverToPort(StepBuildContext ctx) {
        var scene = ctx.builder();
        String targetText = LocalizedText.ref("create_biotech:allay_port", "scene_1.text_7").resolve();
        String address = LocalizedText.ref("create_biotech:allay_port", "scene_1.text_4").resolve();
        playDeliveryAnimation(scene, targetText, "blue", address,
            new BlockPos(4, 1, 4), new BlockPos(1, 1, 1), null, (byte) 1);
    }

    private static void deliverToPlayer(StepBuildContext ctx) {
        var scene = ctx.builder();
        playDeliveryAnimation(scene, "→Nobodiiiii", "output", "Nobodiiiii",
            new BlockPos(7, 2, 7), null, new Vec3(1.5, 2.2, 1.35), (byte) 0);
    }

    private static void playDeliveryAnimation(SceneBuilder scene,
                                              String targetText, String targetTextColor, String address,
                                              BlockPos senderAnimationPort, BlockPos destinationPort,
                                              Vec3 playerTarget, byte mission) {
        BlockPos stockLink = new BlockPos(6, 1, 7);
        BlockPos packager = new BlockPos(7, 1, 7);
        BlockPos senderVisualPort = new BlockPos(7, 2, 7);
        CreateSceneBuilder createScene = new CreateSceneBuilder(scene);
        PortGeometry sender =
            PortGeometry.at(senderVisualPort, senderAnimationPort, Direction.NORTH);
        PortGeometry receiver = destinationPort == null
            ? null
            : PortGeometry.at(destinationPort, destinationPort, Direction.EAST);
        FlightState flight = new FlightState(
            sender.center(), Vec3.ZERO, yawForDirection(sender.outward()), 0);
        CourierRoute outbound = calculateOutboundRoute(flight, sender, receiver, playerTarget);
        CourierRoute returning = calculateReturnRoute(flight, sender, receiver);

        PonderHilo.linkEffect(createScene, stockLink);
        scene.idle(5);

        ItemStack box = PackageStyles.getDefaultBox().copy();
        PackageItem.addAddress(box, address);
        scene.world().modifyBlockEntity(packager, PackagerBlockEntity.class, be -> {
            be.animationTicks = PackagerBlockEntity.CYCLE;
            be.animationInward = false;
            be.heldBox = box;
        });
        PonderActions.showText(scene, targetText, new Vec3(7.5, 1.0, 7.5),
            40, targetTextColor, true);
        scene.idle(30);

        scene.world().modifyBlockEntity(packager, PackagerBlockEntity.class, be -> be.heldBox = ItemStack.EMPTY);
        scene.world().modifyBlockEntity(
            senderAnimationPort, AllayPortBlockEntity.class, be -> be.flap(false));
        ElementLink<EntityElement> courierLink = scene.world().createEntity(level -> {
            AllayCourierEntity courier =
                new AllayCourierEntity(CBEntityTypes.ALLAY_COURIER.get(), level);
            courier.setPackage(box);
            courier.setPhase(AllayCourierEntity.Phase.CRUISE);
            courier.setMission(AllayCourierEntity.Mission.byId(mission));
            courier.setNoAi(true);
            courier.setNoGravity(true);
            RouteFrame firstFrame = outbound.frames().getFirst();
            courier.setPos(firstFrame.position());
            courier.setYRot(firstFrame.yaw());
            courier.yRotO = firstFrame.yaw();
            courier.yBodyRot = firstFrame.yaw();
            courier.yBodyRotO = firstFrame.yaw();
            courier.yHeadRot = firstFrame.yaw();
            courier.yHeadRotO = firstFrame.yaw();
            courier.setOldPosAndRot();
            return courier;
        });
        scene.addInstruction(new CourierAnimationClockInstruction(
            courierLink, outbound.duration() + returning.duration() + 6));
        scene.idle(1);

        scene.addInstruction(new CourierRouteInstruction(courierLink, outbound));
        scene.idle(outbound.duration());
        scene.world().modifyEntity(courierLink, entity -> {
            AllayCourierEntity courier = (AllayCourierEntity) entity;
            courier.setPackage(ItemStack.EMPTY);
            courier.setMission(AllayCourierEntity.Mission.CARRIER_RETURN);
        });

        scene.addInstruction(new CourierRouteInstruction(courierLink, returning));
        scene.idle(returning.duration());

        scene.world().modifyEntity(courierLink, Entity::discard);
        scene.idle(15);
    }

    private static final UUID PONDER_COURIER_ID =
        UUID.fromString("0e65eaec-1440-4dc0-93a7-8ddc330b6b89");
    private static final int MAX_ROUTE_TICKS = 240;
    private static final double PORT_STAGE_COMPLETION_DISTANCE = 0.055;
    private static final double PORT_CAPTURE_RADIUS = 1.15;
    private static final double PLAYER_COMPLETION_DISTANCE = 1.5;
    private static final double FLYING_SPEED_ATTRIBUTE = 0.1;
    private static final double TRAVEL_DRAG = 0.91;
    private static final double INPUT_DAMPING = 0.98;

    private enum PortAnimation {
        FLAP_INWARD,
        FLAP_OUTWARD,
        WAVE_ON,
        WAVE_OFF
    }

    private record PortEvent(int tick, BlockPos portPos, PortAnimation animation) {
    }

    private record RouteFrame(Vec3 position, float yaw, float pitch) {
    }

    private record CourierRoute(List<RouteFrame> frames, List<PortEvent> events) {
        private int duration() {
            return frames.size() - 1;
        }
    }

    private record PortGeometry(BlockPos animationPos, Vec3 center, Vec3 outward) {
        private static PortGeometry at(BlockPos visualPos, BlockPos animationPos, Direction facing) {
            return new PortGeometry(
                animationPos,
                Vec3.atCenterOf(visualPos).add(0, -0.25, 0),
                Vec3.atLowerCornerOf(facing.getNormal()));
        }

        private Vec3 mouth() {
            return center.add(outward.scale(0.62));
        }

        private Vec3 inside() {
            return center.add(outward.scale(-0.15));
        }

        private Vec3 departureClear() {
            return center.add(outward.scale(2.75)).add(0, 0.55, 0);
        }

        private Vec3 highApproach() {
            return center.add(outward.scale(3.25)).add(0, 2.25, 0);
        }

        private Vec3 lineup() {
            return center.add(outward.scale(2.5));
        }
    }

    private static final class FlightState {
        private Vec3 position;
        private Vec3 velocity;
        private float yaw;
        private float pitch;

        private FlightState(Vec3 position, Vec3 velocity, float yaw, float pitch) {
            this.position = position;
            this.velocity = velocity;
            this.yaw = yaw;
            this.pitch = pitch;
        }
    }

    private static final class RouteBuilder {
        private final FlightState state;
        private final List<RouteFrame> frames = new ArrayList<>();
        private final List<PortEvent> events = new ArrayList<>();

        private RouteBuilder(FlightState state) {
            this.state = state;
            capture();
        }

        private void capture() {
            frames.add(new RouteFrame(state.position, state.yaw, state.pitch));
        }

        private int duration() {
            return frames.size() - 1;
        }

        private void addEvent(BlockPos portPos, PortAnimation animation) {
            events.add(new PortEvent(duration(), portPos, animation));
        }

        private void addEvent(int tick, BlockPos portPos, PortAnimation animation) {
            events.add(new PortEvent(tick, portPos, animation));
        }

        private CourierRoute build() {
            return new CourierRoute(List.copyOf(frames), List.copyOf(events));
        }
    }

    private static CourierRoute calculateOutboundRoute(FlightState state, PortGeometry sender,
                                                        PortGeometry receiver, Vec3 playerTarget) {
        RouteBuilder route = new RouteBuilder(state);
        simulatePortDeparture(route, sender, false);
        if (receiver != null) {
            simulateVanillaFlight(route, receiver.highApproach(), PORT_CAPTURE_RADIUS);
            simulatePortArrival(route, receiver);
            addWaveWindow(route, receiver.animationPos());
        } else {
            simulateVanillaFlight(route, playerTarget, PLAYER_COMPLETION_DISTANCE);
        }
        return route.build();
    }

    private static CourierRoute calculateReturnRoute(FlightState state, PortGeometry sender,
                                                      PortGeometry departurePort) {
        RouteBuilder route = new RouteBuilder(state);
        if (departurePort != null) {
            simulatePortDeparture(route, departurePort, true);
        }
        simulateVanillaFlight(route, sender.highApproach(), PORT_CAPTURE_RADIUS);
        simulatePortArrival(route, sender);
        addWaveWindow(route, sender.animationPos());
        return route.build();
    }

    private static void addWaveWindow(RouteBuilder route, BlockPos targetPort) {
        route.addEvent(Math.max(1, route.duration() - 59), targetPort, PortAnimation.WAVE_ON);
        route.addEvent(route.duration(), targetPort, PortAnimation.WAVE_OFF);
    }

    private static void simulatePortDeparture(RouteBuilder route, PortGeometry port,
                                              boolean turnaroundPause) {
        if (turnaroundPause) {
            route.state.velocity = Vec3.ZERO;
            for (int tick = 0; tick < 8; tick++) {
                simulateGuidedStep(route, port.inside(), port.outward(),
                    0.16, 0.04, true);
            }
            route.addEvent(port.animationPos(), PortAnimation.FLAP_OUTWARD);
        }
        simulateGuidedFlight(route, port.mouth(), port.outward(),
            0.19, 0.04, true);
        simulateGuidedFlight(route, port.departureClear(), port.outward(),
            0.19, 0.04, true);
    }

    private static void simulatePortArrival(RouteBuilder route, PortGeometry port) {
        simulateGuidedFlight(route, port.lineup(), port.outward().scale(-1),
            0.22, 0.045, false);
        simulateGuidedFlight(route, port.mouth(), port.outward().scale(-1),
            0.16, 0.035, true);
        route.addEvent(port.animationPos(), PortAnimation.FLAP_INWARD);
        simulateGuidedFlight(route, port.inside(), port.outward().scale(-1),
            0.16, 0.035, true);
        route.state.velocity = Vec3.ZERO;
    }

    private static void simulateGuidedFlight(RouteBuilder route, Vec3 target, Vec3 lookDirection,
                                             double maxSpeed, double acceleration,
                                             boolean lockToPath) {
        for (int tick = 0; tick < MAX_ROUTE_TICKS; tick++) {
            Vec3 offset = target.subtract(route.state.position);
            double distance = offset.length();
            if (distance <= PORT_STAGE_COMPLETION_DISTANCE) {
                return;
            }
            simulateGuidedStep(route, target, lookDirection, maxSpeed, acceleration, lockToPath);
        }
        throw new IllegalStateException("Courier guided route did not reach " + target);
    }

    private static void simulateGuidedStep(RouteBuilder route, Vec3 target, Vec3 lookDirection,
                                           double maxSpeed, double acceleration,
                                           boolean lockToPath) {
        Vec3 offset = target.subtract(route.state.position);
        double distance = offset.length();
        if (distance <= 1.0E-4) {
            route.state.velocity = Vec3.ZERO;
        } else {
            Vec3 direction = offset.scale(1 / distance);
            double brakingSpeed = Math.sqrt(2 * acceleration * distance);
            double desiredSpeed = Math.min(distance, Math.min(maxSpeed, brakingSpeed));
            if (lockToPath) {
                double forwardSpeed = Math.max(0, route.state.velocity.dot(direction));
                double nextSpeed = approach(forwardSpeed, desiredSpeed, acceleration);
                route.state.velocity = direction.scale(Math.min(distance, nextSpeed));
            } else {
                Vec3 desiredVelocity = direction.scale(desiredSpeed);
                Vec3 correction = desiredVelocity.subtract(route.state.velocity);
                if (correction.lengthSqr() > acceleration * acceleration) {
                    correction = correction.normalize().scale(acceleration);
                }
                route.state.velocity = route.state.velocity.add(correction);
                if (route.state.velocity.lengthSqr() > distance * distance) {
                    route.state.velocity = route.state.velocity.normalize().scale(distance);
                }
            }
            route.state.position = route.state.position.add(route.state.velocity);
            route.state.velocity = route.state.velocity.scale(TRAVEL_DRAG);
        }
        route.state.yaw = approachDegrees(
            route.state.yaw, yawForDirection(lookDirection), 22.5f);
        route.state.pitch = Mth.approach(route.state.pitch, 0, 8);
        route.capture();
    }

    private static void simulateVanillaFlight(RouteBuilder route, Vec3 target,
                                              double completionDistance) {
        for (int tick = 0; tick < MAX_ROUTE_TICKS; tick++) {
            Vec3 previous = route.state.position;
            Vec3 offset = target.subtract(previous);
            double distance = offset.length();
            float targetYaw = yawForDirection(offset);
            route.state.yaw = approachDegrees(route.state.yaw, targetYaw, 90);
            double horizontalDistance = offset.horizontalDistance();
            float targetPitch = (float) (-(Mth.atan2(offset.y, horizontalDistance)
                * Mth.RAD_TO_DEG));
            route.state.pitch = approachDegrees(route.state.pitch, targetPitch, 20);

            double speedModifier = distance <= 4 ? 1.5 : 2.25;
            double flyingSpeed = speedModifier * FLYING_SPEED_ATTRIBUTE;
            double verticalInput = Math.abs(offset.y) <= 1.0E-5
                ? 0
                : Math.copySign(flyingSpeed, offset.y);
            Vec3 relativeInput =
                new Vec3(0, verticalInput, flyingSpeed * INPUT_DAMPING);
            Vec3 acceleration = relativeInput.scale(flyingSpeed)
                .yRot(-route.state.yaw * Mth.DEG_TO_RAD);

            route.state.velocity = route.state.velocity.add(acceleration);
            route.state.position = route.state.position.add(route.state.velocity);
            route.state.velocity = route.state.velocity.scale(TRAVEL_DRAG);
            route.capture();

            if (segmentDistanceToPointSqr(previous, route.state.position, target)
                <= completionDistance * completionDistance) {
                return;
            }
        }
        throw new IllegalStateException("Courier vanilla route did not reach " + target);
    }

    private static double segmentDistanceToPointSqr(Vec3 start, Vec3 end, Vec3 point) {
        Vec3 segment = end.subtract(start);
        double lengthSqr = segment.lengthSqr();
        if (lengthSqr <= 1.0E-8) {
            return start.distanceToSqr(point);
        }
        double progress = Mth.clamp(
            point.subtract(start).dot(segment) / lengthSqr, 0, 1);
        return start.add(segment.scale(progress)).distanceToSqr(point);
    }

    private static double approach(double value, double target, double step) {
        if (value < target) {
            return Math.min(value + step, target);
        }
        return Math.max(value - step, target);
    }

    private static float yawForDirection(Vec3 direction) {
        return (float) (Mth.atan2(direction.z, direction.x) * Mth.RAD_TO_DEG) - 90;
    }

    private static float approachDegrees(float current, float target, float maxChange) {
        return current + Mth.clamp(Mth.wrapDegrees(target - current), -maxChange, maxChange);
    }

    private static final class CourierRouteInstruction extends TickingInstruction {
        private final ElementLink<EntityElement> courierLink;
        private final CourierRoute route;

        private CourierRouteInstruction(ElementLink<EntityElement> courierLink, CourierRoute route) {
            super(false, route.duration());
            this.courierLink = courierLink;
            this.route = route;
        }

        @Override
        protected void firstTick(PonderScene scene) {
            applyPortEvents(scene, 0);
        }

        @Override
        public void tick(PonderScene scene) {
            super.tick(scene);
            int frameIndex = Mth.clamp(
                totalTicks - remainingTicks, 1, route.frames().size() - 1);
            RouteFrame previous = route.frames().get(frameIndex - 1);
            RouteFrame next = route.frames().get(frameIndex);
            Vec3 movement = next.position().subtract(previous.position());
            EntityElement element = scene.resolve(courierLink);
            if (element != null) {
                element.ifPresent(entity -> applyFrame(scene, entity, next, movement));
            }
            applyPortEvents(scene, frameIndex);
        }

        private void applyFrame(PonderScene scene, Entity entity, RouteFrame frame, Vec3 movement) {
            if (!(entity instanceof AllayCourierEntity courier) || !courier.isAlive()) {
                return;
            }
            courier.setOldPosAndRot();
            courier.setPos(frame.position());
            courier.setDeltaMovement(Vec3.ZERO);
            courier.setSpeed(0);
            courier.setXxa(0);
            courier.setYya(0);
            courier.setZza(0);
            courier.setYRot(frame.yaw());
            courier.yBodyRot = frame.yaw();
            courier.yHeadRot = frame.yaw();
            courier.setXRot(frame.pitch());
            courier.walkAnimation.update(
                Math.min((float) movement.length() * 4, 1), 0.4f);
            if (courier.tickCount % 3 == 0 && movement.lengthSqr() > 1.0E-6) {
                Vec3 trailPosition = frame.position()
                    .add(0, courier.getBbHeight() * 0.5, 0)
                    .subtract(movement.normalize().scale(courier.getBbWidth() * 0.75));
                double noteColor = (courier.tickCount % 24) / 24.0;
                scene.getWorld().addParticle(CBParticleTypes.ALLAY_COURIER_NOTE.get(),
                    trailPosition.x, trailPosition.y, trailPosition.z, noteColor, 0, 0);
            }
        }

        private void applyPortEvents(PonderScene scene, int tick) {
            for (PortEvent event : route.events()) {
                if (event.tick() != tick) {
                    continue;
                }
                if (!(scene.getWorld().getBlockEntity(event.portPos())
                    instanceof AllayPortBlockEntity port)) {
                    continue;
                }
                switch (event.animation()) {
                    case FLAP_INWARD -> port.flap(true);
                    case FLAP_OUTWARD -> port.flap(false);
                    case WAVE_ON -> port.setCourierWaving(PONDER_COURIER_ID, true);
                    case WAVE_OFF -> port.setCourierWaving(PONDER_COURIER_ID, false);
                }
            }
        }
    }

    /**
     * PonderLevel increments tickCount before calling Entity#tick, which increments it again.
     * Normalize only this courier's clock so its vanilla wing animation advances once per tick.
     */
    private static final class CourierAnimationClockInstruction extends TickingInstruction {
        private final ElementLink<EntityElement> courierLink;
        private boolean initialized;
        private int lastTickCount;

        private CourierAnimationClockInstruction(ElementLink<EntityElement> courierLink, int duration) {
            super(false, duration);
            this.courierLink = courierLink;
        }

        @Override
        public void reset(PonderScene scene) {
            super.reset(scene);
            initialized = false;
            lastTickCount = 0;
        }

        @Override
        public void tick(PonderScene scene) {
            super.tick(scene);
            EntityElement element = scene.resolve(courierLink);
            if (element == null) {
                return;
            }
            element.ifPresent(entity -> {
                if (!initialized) {
                    initialized = true;
                    lastTickCount = entity.tickCount;
                    return;
                }

                int advancedTicks = entity.tickCount - lastTickCount;
                if (advancedTicks > 1) {
                    entity.tickCount -= advancedTicks - 1;
                }
                lastTickCount = entity.tickCount;
            });
        }
    }
}
