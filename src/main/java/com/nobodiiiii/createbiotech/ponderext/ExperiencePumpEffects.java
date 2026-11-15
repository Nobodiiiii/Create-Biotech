package com.nobodiiiii.createbiotech.ponderext;

import com.nododiiiii.ponderer.runtime.api.PonderPlayerElement;
import com.nododiiiii.ponderer.runtime.api.PonderPlayerSupport;
import net.createmod.ponder.api.element.ElementLink;
import net.minecraft.world.phys.Vec3;
import com.nododiiiii.ponderer.runtime.api.RuntimeEngine;
import com.nododiiiii.ponderer.runtime.api.StepBuildContext;

/** Author-owned compiled effects. The Ponderer exporter does not overwrite this class. */
final class ExperiencePumpEffects {
    private ExperiencePumpEffects() {}

    static void install(RuntimeEngine engine) {
        engine.steps().registerJava("create_biotech:java/experience_pump/1", "显示经验泵玩家", ExperiencePumpEffects::showPlayer);
        engine.steps().registerJava("create_biotech:java/experience_pump/2", "移除经验泵玩家", ExperiencePumpEffects::removePlayer);
    }

    private static void showPlayer(StepBuildContext ctx) {
        var scene = ctx.builder();
        ctx.state().put("experience.player", PonderPlayerSupport.addPlayer(scene, new Vec3(2.5, 1.0, 3.5)));
    }

    @SuppressWarnings("unchecked")
    private static void removePlayer(StepBuildContext ctx) {
        var scene = ctx.builder();
        var playerLink = (ElementLink<PonderPlayerElement>) ctx.state().get("experience.player");
        PonderPlayerSupport.removePlayer(scene, playerLink, 10);
    }


}
