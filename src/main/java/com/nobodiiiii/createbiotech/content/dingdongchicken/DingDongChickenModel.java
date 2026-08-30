package com.nobodiiiii.createbiotech.content.dingdongchicken;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.model.ChickenModel;
import net.minecraft.client.model.geom.ModelPart;

public class DingDongChickenModel extends ChickenModel<DingDongChickenEntity> {
	private final ModelPart body;

	public DingDongChickenModel(ModelPart root) {
		super(root);
		body = root.getChild("body");
	}

	public void translateToBody(PoseStack poseStack) {
		body.translateAndRotate(poseStack);
	}
}
