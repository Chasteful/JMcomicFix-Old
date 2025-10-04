package net.ccbluex.liquidbounce.render.context

import net.minecraft.entity.LivingEntity

class ArmorRenderContext(
    val entity: LivingEntity,
    val pieceTransparency: Float
) {
    fun getApplicablePieceTransparency(): Float = pieceTransparency
}
