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
 */
@file:OptIn(FlowPreview::class)

package net.ccbluex.liquidbounce.features.module.modules.render

import com.mojang.authlib.GameProfile
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import net.ccbluex.liquidbounce.LiquidBounce
import net.ccbluex.liquidbounce.api.core.withScope
import net.ccbluex.liquidbounce.authlib.utils.generateOfflinePlayerUuid
import net.ccbluex.liquidbounce.authlib.yggdrasil.GameProfileRepository
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.minecraft.client.network.PlayerListEntry
import net.minecraft.client.texture.NativeImage
import net.minecraft.client.texture.NativeImageBackedTexture
import net.minecraft.client.util.SkinTextures
import java.util.function.Supplier
import java.io.FileInputStream
import net.minecraft.util.Identifier
import kotlin.time.Duration.Companion.seconds

object ModuleSkinChanger : ClientModule("SkinChanger", Category.RENDER) {
    private val slim by boolean("Slim", true)
    private val username = text("Username", "")
        .apply(::tagBy)
    private val nativeSkin by file("Native")

    init {
        withScope {
            username.asStateFlow().debounce { 2.seconds }.collectLatest { username ->
                while (mc.player == null) { delay(1.seconds) }
                skinTextures = textureSupplier(username)
            }
        }
    }

    @Volatile
    var skinTextures: Supplier<SkinTextures>? = null
        private set

    private fun textureSupplier(username: String): Supplier<SkinTextures> {
        if (username.isBlank()) return fileSkinSupplier()

        val uuid = GameProfileRepository().fetchUuidByUsername(username)
            ?: generateOfflinePlayerUuid(username)
        val profile = mc.sessionService.fetchProfile(uuid, false)?.profile
            ?: GameProfile(uuid, username)

        val mojangSupplier = PlayerListEntry.texturesSupplier(profile)
        return try {
            mojangSupplier.get()
            mojangSupplier
        } catch (_: Exception) {
            fileSkinSupplier()
        }
    }

    private var cachedSkinId: Identifier? = null
    private var cachedSkinTex: NativeImageBackedTexture? = null
    private var cachedLastModified: Long = -1

    private fun fileSkinSupplier(): Supplier<SkinTextures> = Supplier {
        val file = nativeSkin.absoluteFile.takeIf { it.exists() && it.isFile && it.canRead() }

        val id: Identifier
        if (file != null) {
            val lastModified = file.lastModified()
            if (cachedSkinId == null || lastModified != cachedLastModified) {
                cachedSkinTex?.close()
                FileInputStream(file).use { fis ->
                    val image = NativeImage.read(fis)
                    cachedSkinTex = NativeImageBackedTexture(image)
                    cachedSkinId = Identifier.of("liquidbounce", "skin-file")
                    mc.textureManager.registerTexture(cachedSkinId!!, cachedSkinTex!!)
                    cachedLastModified = lastModified
                }
            }
            id = cachedSkinId!!
        } else {
            if (cachedSkinId == null) {
                val image = NativeImage.read(
                    LiquidBounce.javaClass.getResourceAsStream("/resources/liquidbounce/image/skin/NachoNeko.png")!!
                )
                cachedSkinTex = NativeImageBackedTexture(image)
                cachedSkinId = Identifier.of("liquidbounce", "skin-default")
                mc.textureManager.registerTexture(cachedSkinId!!, cachedSkinTex!!)
            }
            id = cachedSkinId!!
        }

        SkinTextures(
            id,
            "",
            null,
            null,
            if (slim) SkinTextures.Model.SLIM else null,
            true
        )
    }


    @JvmStatic
    fun shouldApplyChanges(): Boolean = running
}
