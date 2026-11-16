package com.nobodiiiii.createbiotech.ponderext;

import com.nododiiiii.ponderer.runtime.api.RuntimeEngine;
import com.nododiiiii.ponderer.runtime.api.StepBuildContext;
import net.createmod.ponder.api.element.ElementLink;
import net.createmod.ponder.api.element.EntityElement;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.instruction.TickingInstruction;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** Author-owned orb animation; entity placement remains editable in the scene. */
final class ExperiencePumpOrbEffects {
    private static final double ORBIT_RADIUS = 0.2;
    private static final int ORBIT_PERIOD_TICKS = 60;

    private ExperiencePumpOrbEffects() {}

    static void install(RuntimeEngine engine) {
        engine.steps().registerJava("create_biotech:java/experience_pump/orbit_input",
            "入口经验球原地小幅转圈", ExperiencePumpOrbEffects::orbitInput);
    }

    private static void orbitInput(StepBuildContext ctx) {
        for (var link : ctx.entities().getOrDefault("experience.input_orb", List.of()))
            ctx.builder().addInstruction(new OrbOrbitInstruction(link));
    }

    private static final class OrbOrbitInstruction extends TickingInstruction {
        private final ElementLink<EntityElement> link;
        private Vec3 center;

        private OrbOrbitInstruction(ElementLink<EntityElement> link) {
            super(false, 60);
            this.link = link;
        }

        @Override
        public void reset(PonderScene scene) {
            super.reset(scene);
            center = null;
        }

        @Override
        protected void firstTick(PonderScene scene) {
            EntityElement element = scene.resolve(link);
            if (element != null)
                element.ifPresent(entity -> {
                    center = entity.position();
                    entity.setNoGravity(true);
                });
        }

        @Override
        public void tick(PonderScene scene) {
            super.tick(scene);
            EntityElement element = scene.resolve(link);
            if (element == null || center == null)
                return;

            double angle = (totalTicks - remainingTicks) * (2 * Math.PI / ORBIT_PERIOD_TICKS);
            Vec3 position = center.add(Math.cos(angle) * ORBIT_RADIUS, 0,
                Math.sin(angle) * ORBIT_RADIUS);
            element.ifPresent(entity -> {
                if (!entity.isAlive())
                    return;
                entity.setOldPosAndRot();
                entity.setPos(position);
                entity.setDeltaMovement(Vec3.ZERO);
            });
        }
    }
}
