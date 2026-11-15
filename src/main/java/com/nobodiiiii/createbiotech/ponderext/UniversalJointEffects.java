package com.nobodiiiii.createbiotech.ponderext;

import com.mojang.blaze3d.vertex.PoseStack;
import com.nobodiiiii.createbiotech.content.universaljoint.HalfShaftBlock;
import com.nobodiiiii.createbiotech.content.universaljoint.UniversalJointBlock;
import com.nobodiiiii.createbiotech.content.universaljoint.UniversalJointBlockEntity;
import com.nobodiiiii.createbiotech.content.universaljoint.UniversalJointRenderer;
import com.nobodiiiii.createbiotech.registry.CBBlocks;
import com.nododiiiii.ponderer.runtime.api.RuntimeEngine;
import com.nododiiiii.ponderer.runtime.api.StepBuildContext;
import com.nododiiiii.ponderer.scene.LocalizedText;
import com.simibubi.create.AllBlocks;
import net.createmod.ponder.api.element.ElementLink;
import net.createmod.ponder.api.element.WorldSectionElement;
import net.createmod.ponder.api.level.PonderLevel;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.element.AnimatedSceneElementBase;
import net.createmod.ponder.foundation.element.WorldSectionElementImpl;
import net.createmod.ponder.foundation.instruction.TickingInstruction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import org.joml.Vector3f;

/** Author-owned effects; the optional physics demonstration is built only with Simulated. */
final class UniversalJointEffects {
    private static final BlockPos LEFT = new BlockPos(1, 2, 2);
    private static final BlockPos RIGHT = new BlockPos(3, 2, 2);

    private UniversalJointEffects() {}

    static void install(RuntimeEngine engine) {
        engine.steps().registerJava("create_biotech:java/universal_joint/prepare_link",
            "初始化虚拟万向节端点", UniversalJointEffects::prepareLink);
        engine.steps().registerJava("create_biotech:java/universal_joint/stretch_and_break",
            "物理结构拉伸万向节直到断裂", UniversalJointEffects::stretchAndBreak);
    }

    private static void prepareLink(StepBuildContext ctx) {
        // The portable scene builder does not apply CreateSceneBuilder's virtual flag
        // to newly placed block entities. Both ends must use the virtual-world lookup.
        ctx.builder().world().modifyBlockEntity(new BlockPos(1, 1, 3),
            UniversalJointBlockEntity.class, UniversalJointBlockEntity::markVirtual);
        ctx.builder().world().modifyBlockEntity(new BlockPos(2, 2, 2),
            UniversalJointBlockEntity.class, UniversalJointBlockEntity::markVirtual);
    }

    private static void stretchAndBreak(StepBuildContext ctx) {
        if (!ModList.get().isLoaded("simulated"))
            return;

        var builder = ctx.builder();
        var select = ctx.util().select();
        var previous = select.fromTo(0, 1, 0, 4, 5, 5);
        builder.addKeyframe();
        builder.world().setBlocks(previous, Blocks.AIR.defaultBlockState(), false);
        builder.world().setBlock(LEFT.west(), AllBlocks.ANDESITE_CASING.getDefaultState(), false);
        builder.world().setBlock(RIGHT.east(), AllBlocks.ANDESITE_CASING.getDefaultState(), false);
        builder.world().setBlock(LEFT, CBBlocks.UNIVERSAL_JOINT.get().defaultBlockState()
            .setValue(UniversalJointBlock.FACING, Direction.WEST), false);
        builder.world().setBlock(RIGHT, CBBlocks.UNIVERSAL_JOINT.get().defaultBlockState()
            .setValue(UniversalJointBlock.FACING, Direction.EAST), false);

        // Follow the configured distances, including the same overload colour as the real shaft.
        double strainDistance = UniversalJointBlockEntity.getStrainStartDistance();
        double breakDistance = UniversalJointBlockEntity.getElasticDisconnectRange();
        double initialDistance = Math.min(2, strainDistance);
        int originalSize = builder.getScene().getBasePlateSize();
        int originalX = builder.getScene().getBasePlateOffsetX();
        int originalZ = builder.getScene().getBasePlateOffsetZ();
        builder.addInstruction(new TickingInstruction(false, 1) {
            @Override
            public void onScheduled(PonderScene scene) {
                super.onScheduled(scene);
                // Restore the opening view on replay and when seeking back to a keyframe.
                builder.configureBasePlate(originalX, originalZ, originalSize);
            }

            @Override
            protected void firstTick(PonderScene scene) {
                builder.configureBasePlate(0, 0, 5);
            }
        });
        builder.world().showSection(select.fromTo(LEFT.west(), LEFT), Direction.DOWN);
        var moving = builder.world().showIndependentSection(select.fromTo(RIGHT, RIGHT.east()), Direction.DOWN);
        builder.world().moveSection(moving, new Vec3(initialDistance - 2, 0, 0), 0);
        builder.idle(20);
        // These endpoints deliberately have no persisted link: the section's render transform
        // moves independently of block storage. Draw exactly one shaft using that same transform.
        builder.addInstruction(scene -> scene.addElement(new SlimeShaftElement(scene, moving)));
        builder.overlay().showText(180)
            .text(LocalizedText.ref("create_biotech:universal_joint", "scene_1.text_3").resolve())
            .pointAt(Vec3.atCenterOf(RIGHT).add(initialDistance - 2, 0, 0))
            .placeNearTarget();
        builder.idle(20);
        builder.world().moveSection(moving, new Vec3(strainDistance - initialDistance, 0, 0), 40);
        builder.idle(40);
        builder.world().moveSection(moving, new Vec3(breakDistance - strainDistance - 0.01, 0, 0), 60);
        builder.idle(70);
        builder.world().moveSection(moving, new Vec3(0.01, 0, 0), 5);
        builder.idle(5);
        builder.addInstruction(scene -> scene.forEach(SlimeShaftElement.class, shaft -> shaft.setVisible(false)));
        builder.world().setBlock(LEFT, CBBlocks.HALF_SHAFT.get().defaultBlockState()
            .setValue(HalfShaftBlock.FACING, Direction.WEST), false);
        builder.world().setBlock(RIGHT, CBBlocks.HALF_SHAFT.get().defaultBlockState()
            .setValue(HalfShaftBlock.FACING, Direction.EAST), false);
        Vec3 fractureCenter = Vec3.atCenterOf(LEFT).add(breakDistance / 2, 0, 0);
        builder.world().createItemEntity(fractureCenter, new Vec3(0, 0.08, 0), new ItemStack(Items.SLIME_BALL));
        builder.effects().emitParticles(fractureCenter,
            builder.effects().particleEmitterWithinBlockSpace(
                new BlockParticleOption(ParticleTypes.BLOCK, Blocks.SLIME_BLOCK.defaultBlockState()),
                new Vec3(0, 0.08, 0)), 20, 1);
        builder.world().moveSection(moving, new Vec3(0.5, 0, 0), 15);
        builder.idle(45);
    }

    private static final class SlimeShaftElement extends AnimatedSceneElementBase {
        private final PonderScene scene;
        private final ElementLink<WorldSectionElement> moving;

        private SlimeShaftElement(PonderScene scene, ElementLink<WorldSectionElement> moving) {
            this.scene = scene;
            this.moving = moving;
            forceApplyFade(1);
        }

        @Override
        protected void renderFirst(PonderLevel world, MultiBufferSource buffer, GuiGraphics graphics,
            float fade, float partialTicks) {
            if (!(scene.resolve(moving) instanceof WorldSectionElementImpl section)
                || !(world.getBlockEntity(LEFT) instanceof UniversalJointBlockEntity left)
                || !(Minecraft.getInstance().getBlockEntityRenderDispatcher().getRenderer(left)
                    instanceof UniversalJointRenderer renderer))
                return;

            // Use Ponder's interpolated transform so the slime stays attached on every frame.
            PoseStack transform = new PoseStack();
            section.transformMS(transform, partialTicks);
            Vec3 start = UniversalJointBlockEntity.getInnerEndpoint(LEFT, world.getBlockState(LEFT));
            Vec3 end = transform(UniversalJointBlockEntity.getInnerEndpoint(RIGHT, world.getBlockState(RIGHT)),
                transform);
            Vec3 rightCenter = transform(Vec3.atCenterOf(RIGHT), transform);
            renderer.renderShaftBetween(start, end, 0, Vec3.atCenterOf(LEFT).distanceTo(rightCenter),
                graphics.pose(), buffer, lightCoordsFromFade(fade), OverlayTexture.NO_OVERLAY);
        }

        private static Vec3 transform(Vec3 position, PoseStack transform) {
            Vector3f result = position.toVector3f().mulPosition(transform.last().pose());
            return new Vec3(result.x(), result.y(), result.z());
        }
    }
}
