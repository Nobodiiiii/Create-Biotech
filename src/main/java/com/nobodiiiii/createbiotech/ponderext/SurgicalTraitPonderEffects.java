package com.nobodiiiii.createbiotech.ponderext;

import static com.nobodiiiii.createbiotech.content.surgery.client.SurgicalTraitPonderActors.Assembly.COMPLETE;

import com.nobodiiiii.createbiotech.content.surgery.client.SurgicalPonderInterfaces;
import com.nobodiiiii.createbiotech.content.surgery.client.SurgicalTraitPonderActors;
import com.nobodiiiii.createbiotech.content.surgery.client.SurgicalTraitPonderActors.Assembly;
import com.nobodiiiii.createbiotech.content.surgery.client.SurgicalTraitPonderActors.View;
import com.nododiiiii.ponderer.runtime.api.RuntimeEngine;
import com.nododiiiii.ponderer.runtime.api.StepBuildContext;

/** Playback instructions for the fourth surgical table chapter. */
final class SurgicalTraitPonderEffects {
    private static final String PREFIX = "create_biotech:java/surgical_table/traits_";
    private static final String ACTORS = "create_biotech:surgical_table.trait_actors";

    private SurgicalTraitPonderEffects() {}

    static void install(RuntimeEngine engine) {
        view(engine, "flight", "展示仿生生物扑翼飞行", View.FLIGHT, COMPLETE);
        view(engine, "dive", "展示仿生生物接敌自爆", View.DIVE, COMPLETE);
        view(engine, "hide", "收起演示成品", View.HIDDEN, COMPLETE);
        view(engine, "display", "定格组装完成的成品", View.DISPLAY, COMPLETE);
        view(engine, "donors", "在手术台两侧展示完整供体", View.DONORS, COMPLETE);
        view(engine, "dissect", "将苦力怕和幻翼拆成独立体块", View.DISSECTING, COMPLETE);
        view(engine, "assemble", "按绿图将所需体块平移到台面中央", View.ASSEMBLING, COMPLETE);
        view(engine, "table", "展示安装关节后的各部位", View.TABLE, COMPLETE);
        view(engine, "extra_torso", "增加其他供体的躯干组织", View.TABLE, Assembly.EXTRA_TORSO);
        engine.steps().registerJava(PREFIX + "detach_torso", "剪下多余躯干并留在台面", ctx -> {
            var actors = actors(ctx);
            ctx.builder().addInstruction(scene -> actors.detachExtraTorso());
        });
        view(engine, "one_shoulder", "拆下一个肩关节", View.TABLE, Assembly.ONE_SHOULDER);
        engine.steps().registerJava(PREFIX + "preview_box", "将台面成品的特性填入纸箱悬停页", ctx -> {
            var actors = actors(ctx);
            ctx.builder().addInstruction(scene -> SurgicalPonderInterfaces.setBoxHoverTraits(scene, actors.traits()));
        });
        view(engine, "pack_missing", "装箱检查未生效的成品", View.PACKED, Assembly.ONE_SHOULDER);
        view(engine, "pack_complete", "再次装箱查看已生效的特性", View.PACKED, COMPLETE);
    }

    private static void view(RuntimeEngine engine, String id, String label, View view, Assembly assembly) {
        engine.steps().registerJava(PREFIX + id, label, ctx -> {
            var actors = actors(ctx);
            ctx.builder().addInstruction(scene -> actors.show(view, assembly));
        });
    }

    private static SurgicalTraitPonderActors actors(StepBuildContext ctx) {
        return (SurgicalTraitPonderActors) ctx.state().computeIfAbsent(ACTORS, key -> {
            var actors = new SurgicalTraitPonderActors(ctx.builder().getScene().getWorld());
            ctx.builder().addInstruction(scene -> {
                actors.reset(scene);
                scene.addElement(actors);
                var rotation = scene.getTransform().yRotation;
                rotation.startWithValue(rotation.getValue() + 180);
            });
            return actors;
        });
    }
}
