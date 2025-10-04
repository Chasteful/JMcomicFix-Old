/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 *
 * Copyright (c) 2015 - 2025 CCBlueX
 *
 * LiquidBounce is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * LiquidBounce is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with LiquidBounce. If not, see <https://www.gnu.org/licenses/>.
 *
 */
package net.ccbluex.liquidbounce.integration.theme

import net.ccbluex.liquidbounce.features.module.modules.client.ModuleHudEditor
import net.ccbluex.liquidbounce.render.shader.CanvasShader
import net.ccbluex.liquidbounce.utils.client.mc
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.render.RenderLayer
import net.minecraft.client.texture.NativeImageBackedTexture
import net.minecraft.util.Identifier
import java.io.Closeable
import kotlin.math.exp

sealed interface ThemeBackground : Closeable {

    companion object {
        @JvmStatic
        fun shader(shader: CanvasShader) = ShaderThemeBackground(shader)
        @JvmStatic
        fun image(imageId: Identifier) = ImageThemeBackground(imageId)
        @JvmStatic
        fun none() = MinecraftThemeBackground
    }

    /**
     * Returns false to let Minecraft render its default wallpaper.
     */
    object MinecraftThemeBackground : ThemeBackground {
        override fun draw(
            context: DrawContext,
            width: Int,
            height: Int,
            mouseX: Int,
            mouseY: Int,
            delta: Float
        ): Boolean = false // Show default Minecraft wallpaper

        @Suppress("EmptyFunctionBlock")
        override fun close() { }
    }

    /**
     * Background implementation that renders a static image texture.
     * @param imageId The Minecraft resource identifier for the image
     */
    class ImageThemeBackground(private val imageId: Identifier) : ThemeBackground {

        private var currentOffsetX = 0f
        private var currentOffsetY = 0f

//        private fun resizeImage(original: NativeImage, newW: Int, newH: Int): NativeImage {
//            val resized = NativeImage(NativeImage.Format.RGBA, newW, newH, false)
//            original.resizeSubRectTo(0, 0, original.width, original.height, resized)
//            return resized
//        }

        private fun smooth(current: Float, target: Float, delta: Float, speed: Float): Float {
            val factor = 1f - exp((-speed * delta).toDouble()).toFloat()
            return current + (target - current) * factor
        }

        override fun draw(
            context: DrawContext,
            width: Int,
            height: Int,
            mouseX: Int,
            mouseY: Int,
            delta: Float
        ): Boolean {
            val texture = mc.textureManager.getTexture(imageId)
            val (texW, texH) = if (texture is NativeImageBackedTexture) {
                texture.image?.let { it.width to it.height } ?: (width to height)
            } else {
                width to height
            }

            val baseScale = (width.toFloat() / texW).coerceAtLeast(height.toFloat() / texH)
            val scale = baseScale * ModuleHudEditor.background

            val maxOffsetX = ((texW * scale - width) / 2f).coerceAtLeast(0f)
            val maxOffsetY = ((texH * scale - height) / 2f).coerceAtLeast(0f)

            val targetOffsetX = (((mouseX / width.toFloat()) - 0.5f) * maxOffsetX * 2f)
                .coerceIn(-maxOffsetX, maxOffsetX)
            val targetOffsetY = (((mouseY / height.toFloat()) - 0.5f) * maxOffsetY * 2f)
                .coerceIn(-maxOffsetY, maxOffsetY)


            currentOffsetX = smooth(currentOffsetX, targetOffsetX, delta, 0.25f)
            currentOffsetY = smooth(currentOffsetY, targetOffsetY, delta, 0.25f)

            val scaledWidth = (texW * scale).toInt()
            val scaledHeight = (texH * scale).toInt()

            context.drawTexture(
                RenderLayer::getGuiTextured,
                imageId,
                (-currentOffsetX - (scaledWidth - width) / 2).toInt(),
                (-currentOffsetY - (scaledHeight - height) / 2).toInt(),
                0f, 0f,
                scaledWidth, scaledHeight,
                scaledWidth, scaledHeight,
            )
            return true
        }

        override fun close() {
            mc.textureManager.destroyTexture(imageId)
        }
    }

    /**
     * Background implementation that renders using a custom shader.
     * @param shader The canvas shader to use for rendering
     */
    class ShaderThemeBackground(private val shader: CanvasShader) : ThemeBackground {

        override fun draw(
            context: DrawContext,
            width: Int,
            height: Int,
            mouseX: Int,
            mouseY: Int,
            delta: Float
        ): Boolean {
            shader.draw(mouseX, mouseY, delta)
            return true
        }

        override fun close() {
            shader.close()
        }
    }

    /**
     * Draws the background on the screen.
     * @param context The drawing context
     * @param width Screen width
     * @param height Screen height
     * @param mouseX Mouse X coordinate
     * @param mouseY Mouse Y coordinate
     * @param delta Time delta for animations
     * @return true if background was drawn, false to use default Minecraft background
     */
    @Suppress("LongParameterList")
    fun draw(
        context: DrawContext,
        width: Int,
        height: Int,
        mouseX: Int,
        mouseY: Int,
        delta: Float
    ): Boolean
}
