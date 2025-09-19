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
 *
 */

package net.ccbluex.liquidbounce.integration.interop.protocol.rest.v1.game

import com.mojang.blaze3d.systems.RenderSystem
import net.ccbluex.liquidbounce.render.shader.DyeableItems.applyDyeToImage
import net.ccbluex.liquidbounce.render.ui.ItemImageAtlas
import net.ccbluex.liquidbounce.utils.client.mc
import net.ccbluex.liquidbounce.utils.client.world
import net.ccbluex.netty.http.model.RequestObject
import net.ccbluex.netty.http.util.httpBadRequest
import net.ccbluex.netty.http.util.httpFileStream
import net.ccbluex.netty.http.util.httpInternalServerError
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gl.SimpleFramebuffer
import net.minecraft.client.render.OverlayTexture
import net.minecraft.client.texture.NativeImage
import net.minecraft.client.texture.NativeImageBackedTexture
import net.minecraft.client.util.DefaultSkinHelper
import net.minecraft.client.util.ScreenshotRecorder
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.item.ItemStack
import net.minecraft.item.ModelTransformationMode
import net.minecraft.nbt.StringNbtReader
import net.minecraft.registry.Registries
import net.minecraft.registry.RegistryKey
import net.minecraft.registry.RegistryKeys
import net.minecraft.util.Identifier
import org.lwjgl.opengl.GL11
import java.nio.channels.Channels
import java.util.*
import javax.imageio.ImageIO
import kotlin.jvm.optionals.getOrNull
// GET /api/v1/client/itemStackTexture
//fun getItemStackTexture(requestObject: RequestObject)= run {
//    val identifier = requestObject.queryParams["id"]
//        ?: return@run httpBadRequest("Missing identifier parameter")
//    val minecraftIdentifier = runCatching { Identifier.of(identifier) }.getOrNull()
//        ?: return@run httpBadRequest("Invalid identifier")
//
//    val item = Registries.ITEM.get(minecraftIdentifier)
//    val mc = MinecraftClient.getInstance()
//    val stack = requestObject.queryParams["nbt"]?.let {
//        val nbt = StringNbtReader.parse(it)
//        mc.world?.let { world -> ItemStack.fromNbtOrEmpty(world.registryManager, nbt) }
//    } ?: ItemStack(item)
//
//    val future = java.util.concurrent.CompletableFuture<ByteArray>()
//
//    mc.execute {
//        try {
//            val framebuffer = SimpleFramebuffer(64, 64, true)
//            framebuffer.setClearColor(0f, 0f, 0f, 0f)
//            framebuffer.clear()
//            framebuffer.beginWrite(true)
//
//            RenderSystem.clearColor(0f, 0f, 0f, 0f)
//            RenderSystem.clear(GL11.GL_COLOR_BUFFER_BIT or GL11.GL_DEPTH_BUFFER_BIT)
//            val matrixStack = MatrixStack()
//            val vertexConsumers = mc.bufferBuilders.entityVertexConsumers
//            val seed = stack.hashCode()
//            mc.itemRenderer.renderItem(
//                stack,
//                ModelTransformationMode.GUI,
//                0xF000F0, OverlayTexture.DEFAULT_UV,
//                matrixStack, vertexConsumers, null, seed
//            )
//            vertexConsumers.draw()
//            framebuffer.endWrite()
//
//            val image = ScreenshotRecorder.takeScreenshot(framebuffer)
//            val buffer = okio.Buffer()
//            val channel = Channels.newChannel(buffer.outputStream())
//            image.write(channel)
//            channel.close()
//            image.close()
//            framebuffer.delete()
//            future.complete(buffer.readByteArray())
//        } catch (e: Exception) {
//            future.completeExceptionally(e)
//        }
//    }
//
//    val bytes = try {
//        future.get()
//    } catch (e: Exception) {
//        return@run httpInternalServerError("Rendering error: ${e.cause?.message ?: e.message}")
//    }
//
//    val outBuffer = okio.Buffer().write(bytes)
//    val response = httpFileStream(
//        outBuffer.inputStream(),
//        contentLength = bytes.size,
//        contentType = "image/png"
//    )
//    return response
//}

// GET /api/v1/client/resource
@Suppress("UNUSED_PARAMETER")
fun getResource(requestObject: RequestObject) = run {
    val identifier = requestObject.queryParams["id"]
        ?: return@run httpBadRequest("Missing identifier parameter")
    val minecraftIdentifier = Identifier.of(identifier)
    val resource = mc.resourceManager.getResourceOrThrow(minecraftIdentifier)

    resource.inputStream.use {
        httpFileStream(it)
    }
}
// GET /api/v1/client/dyedItemTexture
fun getDyedItemTexture(requestObject: RequestObject) = run {

    if (!ItemImageAtlas.isAtlasAvailable) {
        return@run httpInternalServerError("Item atlas not available yet")
    }

    val identifier = requestObject.queryParams["id"]
        ?: return@run httpBadRequest("Missing identifier parameter")
    val minecraftIdentifier = runCatching { Identifier.of(identifier) }.getOrNull()
        ?: return@run httpBadRequest("Invalid identifier")

    val dyeColor = requestObject.queryParams["color"]?.toIntOrNull(16) ?: -6265536


    val alternativeIdentifier = ItemImageAtlas.resolveAliasIfPresent(minecraftIdentifier)
    val of = RegistryKey.of(RegistryKeys.ITEM, alternativeIdentifier)
    val item = Registries.ITEM.get(of) ?: return@run httpBadRequest("Item not found")
    val originalImage = ItemImageAtlas.getItemImage(item) ?: return@run httpBadRequest("Item image not found")


    val dyedImage = applyDyeToImage(originalImage, dyeColor)


    val buffer = okio.Buffer()
    ImageIO.write(dyedImage, "PNG", buffer.outputStream())
    httpFileStream(buffer.inputStream())
}

// GET /api/v1/client/effectTexture
@Suppress("UNUSED_PARAMETER")
fun getEffectTexture(requestObject: RequestObject) = run {
    val identifier = requestObject.queryParams["id"]
        ?: return@run httpBadRequest("Missing identifier parameter")
    val minecraftIdentifier = runCatching { Identifier.of(identifier) }.getOrNull()
        ?: return@run httpBadRequest("Invalid identifier")


    val effectKey = RegistryKey.of(RegistryKeys.STATUS_EFFECT, minecraftIdentifier)
    Registries.STATUS_EFFECT.get(effectKey)
        ?: return@run httpBadRequest("Status effect not found")

    try {

        val texturePath = "textures/mob_effect/${minecraftIdentifier.path}.png"


        val resourceManager = MinecraftClient.getInstance().resourceManager
        val resource = resourceManager.getResource(Identifier.of(minecraftIdentifier.namespace, texturePath))
            .orElse(null)
            ?: return@run httpBadRequest("Effect texture not found")

        val image = ImageIO.read(resource.inputStream)
            ?: return@run httpInternalServerError("Failed to decode texture image")

        val buffer = okio.Buffer()
        ImageIO.write(image, "PNG", buffer.outputStream())
        httpFileStream(buffer.inputStream())
    } catch (e: Exception) {
        httpInternalServerError("Error loading effect texture: ${e.message}")
    }
}
// GET /api/v1/client/itemTexture
@Suppress("UNUSED_PARAMETER")
fun getItemTexture(requestObject: RequestObject) = run {
    if (!ItemImageAtlas.isAtlasAvailable) {
        return@run httpInternalServerError("Item atlas not available yet")
    }

    val identifier = requestObject.queryParams["id"]
        ?: return@run httpBadRequest("Missing identifier parameter")
    val minecraftIdentifier = runCatching { Identifier.of(identifier) }.getOrNull()
        ?: return@run httpBadRequest("Invalid identifier")

    val alternativeIdentifier = ItemImageAtlas.resolveAliasIfPresent(minecraftIdentifier)

    val of = RegistryKey.of(RegistryKeys.ITEM, alternativeIdentifier)

    val image = Registries.ITEM.get(of)?.let(ItemImageAtlas::getItemImage)
        ?: return@run httpBadRequest("Item image not found")

    val buffer = okio.Buffer()
    ImageIO.write(image, "PNG", buffer.outputStream())
    httpFileStream(buffer.inputStream(), contentLength = buffer.size.toInt(), contentType = "image/png")
}

// GET /api/v1/client/skin
fun getSkin(requestObject: RequestObject) = run {
    val uuid = requestObject.queryParams["uuid"]?.let { UUID.fromString(it) }
        ?: return@run httpBadRequest("Missing UUID parameter")
    val skinTextures = world.players.find { it.uuid == uuid }?.skinTextures
        ?: DefaultSkinHelper.getSkinTextures(uuid)
    val texture = mc.textureManager.getTexture(skinTextures.texture)

    if (texture is NativeImageBackedTexture) {
        val buffer = okio.Buffer()
        texture.image?.write(buffer) ?: return@run httpInternalServerError("Texture is not cached yet")
        httpFileStream(buffer.inputStream(), contentLength = buffer.size.toInt(), contentType = "image/png")
    } else {
        val resource = mc.resourceManager.getResource(skinTextures.texture)
            .getOrNull() ?: return@run httpInternalServerError("Texture not found")

        resource.inputStream.use {
            httpFileStream(it)
        }
    }
}
