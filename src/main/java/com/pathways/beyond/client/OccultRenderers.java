package com.pathways.beyond.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.pathways.beyond.PathwaysMod;
import com.pathways.beyond.entity.OccultEntities;
import com.pathways.beyond.registry.ModEntities;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/**
 * Entity renderers.
 *
 * <p>Textures are the mod's own: each creature has a hand-painted 64x64 or 128x128 skin with the
 * asymmetry built into the artwork (one shoulder higher, one sleeve longer, one eye smaller), so
 * the uncanny read survives at any distance and in any light.
 *
 * <p>The Unblinking gets a dedicated model because it is not a person: a single enormous eye in
 * a bone cradle, with an iris that tracks the player and a lid that never closes.
 */
public final class OccultRenderers {
    private OccultRenderers() {}

    public static final ModelLayerLocation UNBLINKING_LAYER =
            new ModelLayerLocation(PathwaysMod.id("unblinking"), "main");

    private static ResourceLocation tex(String name) {
        return PathwaysMod.id("textures/entity/" + name + ".png");
    }

    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.Watcher.get(), context ->
                new HumanoidMobRenderer<OccultEntities.WatcherEntity,
                        HumanoidModel<OccultEntities.WatcherEntity>>(context,
                        new HumanoidModel<>(context.bakeLayer(net.minecraft.client.model.geom.ModelLayers.ZOMBIE)),
                        0.6f) {
                    @Override
                    public ResourceLocation getTextureLocation(OccultEntities.WatcherEntity entity) {
                        return tex("watcher");
                    }
                });
        event.registerEntityRenderer(ModEntities.Hollow.get(), context ->
                new HumanoidMobRenderer<OccultEntities.HollowEntity,
                        HumanoidModel<OccultEntities.HollowEntity>>(context,
                        new HumanoidModel<>(context.bakeLayer(net.minecraft.client.model.geom.ModelLayers.ZOMBIE)),
                        0.5f) {
                    @Override
                    public ResourceLocation getTextureLocation(OccultEntities.HollowEntity entity) {
                        return tex("hollow");
                    }
                });
        event.registerEntityRenderer(ModEntities.WhisperingHusk.get(), context ->
                new HumanoidMobRenderer<OccultEntities.WhisperingHuskEntity,
                        HumanoidModel<OccultEntities.WhisperingHuskEntity>>(context,
                        new HumanoidModel<>(context.bakeLayer(net.minecraft.client.model.geom.ModelLayers.ZOMBIE)),
                        0.5f) {
                    @Override
                    public ResourceLocation getTextureLocation(OccultEntities.WhisperingHuskEntity entity) {
                        return tex("whispering_husk");
                    }
                });
        event.registerEntityRenderer(ModEntities.VeilTenant.get(), context ->
                new HumanoidMobRenderer<OccultEntities.VeilTenantEntity,
                        HumanoidModel<OccultEntities.VeilTenantEntity>>(context,
                        new HumanoidModel<>(context.bakeLayer(net.minecraft.client.model.geom.ModelLayers.ZOMBIE)),
                        0.6f) {
                    @Override
                    public ResourceLocation getTextureLocation(OccultEntities.VeilTenantEntity entity) {
                        return tex("veil_tenant");
                    }

                    @Override
                    protected void scale(OccultEntities.VeilTenantEntity entity, PoseStack pose, float partial) {
                        pose.scale(1.06f, 1.14f, 1.06f);   // it is a little too tall to be a person
                    }
                });
        event.registerEntityRenderer(ModEntities.SpiritWisp.get(), context ->
                new MobRenderer<OccultEntities.SpiritWispEntity,
                        HumanoidModel<OccultEntities.SpiritWispEntity>>(context,
                        new HumanoidModel<>(context.bakeLayer(net.minecraft.client.model.geom.ModelLayers.ZOMBIE)),
                        0.2f) {
                    @Override
                    public ResourceLocation getTextureLocation(OccultEntities.SpiritWispEntity entity) {
                        return tex("spirit_wisp");
                    }

                    @Override
                    protected void scale(OccultEntities.SpiritWispEntity entity, PoseStack pose, float partial) {
                        pose.scale(0.6f, 0.75f, 0.6f);
                    }
                });
        event.registerEntityRenderer(ModEntities.Marionette.get(), context ->
                new HumanoidMobRenderer<OccultEntities.MarionetteEntity,
                        HumanoidModel<OccultEntities.MarionetteEntity>>(context,
                        new HumanoidModel<>(context.bakeLayer(net.minecraft.client.model.geom.ModelLayers.ZOMBIE)),
                        0.5f) {
                    @Override
                    public ResourceLocation getTextureLocation(OccultEntities.MarionetteEntity entity) {
                        return tex("marionette");
                    }
                });
        event.registerEntityRenderer(ModEntities.BizarroClone.get(), context ->
                new HumanoidMobRenderer<OccultEntities.BizarroCloneEntity,
                        PlayerModel<OccultEntities.BizarroCloneEntity>>(context,
                        new PlayerModel<>(context.bakeLayer(net.minecraft.client.model.geom.ModelLayers.PLAYER), false),
                        0.5f) {
                    @Override
                    public ResourceLocation getTextureLocation(OccultEntities.BizarroCloneEntity entity) {
                        return tex("player_spirit_form");
                    }
                });
        event.registerEntityRenderer(ModEntities.HistoricalEcho.get(), context ->
                new HumanoidMobRenderer<OccultEntities.HistoricalEchoEntity,
                        HumanoidModel<OccultEntities.HistoricalEchoEntity>>(context,
                        new HumanoidModel<>(context.bakeLayer(net.minecraft.client.model.geom.ModelLayers.ZOMBIE)),
                        0.5f) {
                    @Override
                    public ResourceLocation getTextureLocation(OccultEntities.HistoricalEchoEntity entity) {
                        return tex("player_spirit_form");
                    }

                    @Override
                    protected void scale(OccultEntities.HistoricalEchoEntity entity, PoseStack pose, float partial) {
                        pose.scale(0.95f, 0.95f, 0.95f);
                    }
                });
        event.registerEntityRenderer(ModEntities.PlayerBodyShell.get(), context ->
                new HumanoidMobRenderer<OccultEntities.PlayerBodyShellEntity,
                        PlayerModel<OccultEntities.PlayerBodyShellEntity>>(context,
                        new PlayerModel<>(context.bakeLayer(net.minecraft.client.model.geom.ModelLayers.PLAYER), false),
                        0.5f) {
                    @Override
                    public ResourceLocation getTextureLocation(OccultEntities.PlayerBodyShellEntity entity) {
                        return tex("player_spirit_form");
                    }
                });
        // ---- the bosses ---------------------------------------------------------------
        event.registerEntityRenderer(ModEntities.ChoirmasterOfTheVeil.get(), context ->
                new HumanoidMobRenderer<com.pathways.beyond.entity.boss.ChoirmasterEntity,
                        HumanoidModel<com.pathways.beyond.entity.boss.ChoirmasterEntity>>(context,
                        new HumanoidModel<>(context.bakeLayer(net.minecraft.client.model.geom.ModelLayers.ZOMBIE)),
                        0.9f) {
                    @Override
                    public ResourceLocation getTextureLocation(
                            com.pathways.beyond.entity.boss.ChoirmasterEntity entity) {
                        return tex("choirmaster_of_the_veil");
                    }

                    @Override
                    protected void scale(com.pathways.beyond.entity.boss.ChoirmasterEntity entity,
                                         PoseStack pose, float partial) {
                        pose.scale(1.15f, 1.2f, 1.15f);
                    }
                });
        event.registerEntityRenderer(ModEntities.TheUnblinking.get(), context ->
                new MobRenderer<com.pathways.beyond.entity.boss.UnblinkingEntity, UnblinkingModel>(context,
                        new UnblinkingModel(context.bakeLayer(UNBLINKING_LAYER)), 2.2f) {
                    @Override
                    public ResourceLocation getTextureLocation(
                            com.pathways.beyond.entity.boss.UnblinkingEntity entity) {
                        return tex("the_unblinking");
                    }
                });
    }

    public static void registerLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(UNBLINKING_LAYER, UnblinkingModel::createLayer);
    }

    /**
     * The Unblinking: one eye the size of a cart, set in a cradle of bone ribs, with a lid that
     * is always a fraction away from closing.
     */
    public static class UnblinkingModel extends EntityModel<com.pathways.beyond.entity.boss.UnblinkingEntity> {
        private final ModelPart eyeball;
        private final ModelPart iris;
        private final ModelPart lid;
        private final ModelPart ribs;

        public UnblinkingModel(ModelPart root) {
            this.eyeball = root.getChild("eyeball");
            this.iris = root.getChild("iris");
            this.lid = root.getChild("lid");
            this.ribs = root.getChild("ribs");
        }

        public static LayerDefinition createLayer() {
            MeshDefinition mesh = new MeshDefinition();
            PartDefinition root = mesh.getRoot();
            root.addOrReplaceChild("eyeball", CubeListBuilder.create()
                            .texOffs(0, 0)
                            .addBox(-32.0f, -24.0f, -32.0f, 64.0f, 48.0f, 64.0f, new CubeDeformation(0.0f)),
                    PartPose.offset(0.0f, 8.0f, 0.0f));
            root.addOrReplaceChild("iris", CubeListBuilder.create()
                            .texOffs(0, 52)
                            .addBox(-16.0f, -16.0f, -1.0f, 32.0f, 32.0f, 2.0f, new CubeDeformation(0.0f)),
                    PartPose.offset(0.0f, 8.0f, -33.0f));
            root.addOrReplaceChild("lid", CubeListBuilder.create()
                            .texOffs(96, 0)
                            .addBox(-32.0f, -8.0f, -32.0f, 64.0f, 16.0f, 64.0f, new CubeDeformation(0.05f)),
                    PartPose.offsetAndRotation(0.0f, -8.0f, 0.0f, -0.35f, 0.0f, 0.0f));
            root.addOrReplaceChild("ribs", CubeListBuilder.create()
                            .texOffs(64, 64)
                            .addBox(-40.0f, -4.0f, -40.0f, 80.0f, 8.0f, 80.0f, new CubeDeformation(0.0f)),
                    PartPose.offset(0.0f, 34.0f, 0.0f));
            return LayerDefinition.create(mesh, 256, 128);
        }

        @Override
        public void setupAnim(com.pathways.beyond.entity.boss.UnblinkingEntity entity, float limbSwing,
                              float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
            // the iris tracks the player: the whole horror is that it is looking back
            this.iris.xRot = headPitch * ((float) Math.PI / 180.0f) * 0.35f;
            this.iris.yRot = netHeadYaw * ((float) Math.PI / 180.0f) * 0.35f;
            this.eyeball.yRot = ageInTicks * 0.01f;
            // the lid breathes, and never quite shuts
            this.lid.xRot = -0.35f + (float) Math.sin(ageInTicks * 0.04f) * 0.06f;
            this.ribs.yRot = (float) Math.sin(ageInTicks * 0.02f) * 0.05f;
        }

        @Override
        public void renderToBuffer(PoseStack pose, com.mojang.blaze3d.vertex.VertexConsumer consumer,
                                   int packedLight, int packedOverlay, int colour) {
            this.eyeball.render(pose, consumer, packedLight, packedOverlay, colour);
            this.iris.render(pose, consumer, packedLight, packedOverlay, colour);
            this.lid.render(pose, consumer, packedLight, packedOverlay, colour);
            this.ribs.render(pose, consumer, packedLight, packedOverlay, colour);
        }
    }

    /** Unused import guard: keeps RenderType referenced for the emissive layer added later. */
    static RenderType unusedRenderType() {
        return net.minecraft.client.renderer.RenderType.eyes(tex("the_unblinking"));
    }
}
