package com.nobodiiiii.createbiotech.ponderext;

import static com.nobodiiiii.createbiotech.content.surgery.client.SurgicalTraitPonderActors.Assembly.COMPLETE;

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
        view(engine, "flight", "让仿生生物飞向盔甲架", View.FLIGHT, COMPLETE);
        view(engine, "prime", "点燃仿生生物的自爆引信", View.FUSE, COMPLETE);
        view(engine, "hide", "收起演示成品", View.HIDDEN, COMPLETE);
        view(engine, "display", "定格组装完成的成品", View.DISPLAY, COMPLETE);
        view(engine, "donors", "在手术台两侧展示完整供体", View.DONORS, COMPLETE);
        view(engine, "dissect", "将苦力怕和幻翼拆成独立体块", View.DISSECTING, COMPLETE);
        view(engine, "assemble", "按绿图将所需体块平移到台面中央", View.ASSEMBLING, COMPLETE);
        view(engine, "table", "展示安装关节后的各部位", View.TABLE, COMPLETE);
        engine.steps().registerJava(PREFIX + "return_to_table", "成品下落回台面并向上收起剩余部件", ctx -> {
            var actors = actors(ctx);
            ctx.builder().addInstruction(scene -> actors.returnToTable());
        });
        view(engine, "one_shoulder", "拆下一个肩关节", View.TABLE, Assembly.ONE_SHOULDER);
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
