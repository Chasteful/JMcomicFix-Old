package net.ccbluex.jmcomicfix.features.module.modules.render


import net.ccbluex.liquidbounce.config.types.NamedChoice
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.utils.session.KilledTarget
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.block.Blocks
import net.minecraft.client.sound.PositionedSoundInstance
import net.minecraft.entity.Entity
import net.minecraft.entity.EntityType
import net.minecraft.entity.LightningEntity
import net.minecraft.entity.LivingEntity
import net.minecraft.particle.ParticleTypes
import net.minecraft.sound.SoundEvents

object ModuleKillEffects : ClientModule("KillEffects", Category.RENDER) {

    enum class Effect(override val choiceName: String) : NamedChoice {
        BLOOD("Blood"),
        LIGHTNING("Lightning"),
        EXPLOSION("Explosion"),
        FIREWORK("Firework"),
        SOUL("Soul Particles"),
        SMOKE("Smoke Cloud"),
        FREEZE("Freeze Effect"),
        PORTAL("Portal"),
        WITCH("Witch Magic")
    }

    private val effects by multiEnumChoice(
        "Effects",
        Effect.BLOOD,
        canBeNone = false
    )
    private val onlyPlayer by boolean("Only Player", true)
    private val volume by float("Volume", 1f, 0f..1f)
    private val particleCount by int("Particle Count", 20, 5..100)

    private val renderEntities = mutableMapOf<Entity, Long>()

    init {
        ClientTickEvents.END_CLIENT_TICK.register {
            val now = System.currentTimeMillis()
            mc.world ?: return@register

            // Check for new kills
            KilledTarget.tick()

            // Process new kills
            KilledTarget.getKilledEntities().forEach { entity ->
                if (!onlyPlayer || entity.type == EntityType.PLAYER) {
                    if (!renderEntities.containsKey(entity)) {
                        renderEntities[entity] = now
                        onKillEffect(entity)
                    }
                }
            }

            // Clear old effects
            val effectIterator = renderEntities.entries.iterator()
            while (effectIterator.hasNext()) {
                val (entity, time) = effectIterator.next()
                if (now - time > 3000L) effectIterator.remove()
            }
        }
    }
@Suppress("LongMethod")
    private fun onKillEffect(entity: Entity) {
        val world = mc.world ?: return
        val pos = entity.pos
        val x = pos.x
        val y = pos.y
        val z = pos.z

        if (Effect.LIGHTNING in effects) {
            val bolt = LightningEntity(EntityType.LIGHTNING_BOLT, world)
            bolt.refreshPositionAfterTeleport(pos)
            bolt.setCosmetic(true)
            world.addEntity(bolt)

            mc.soundManager.play(
                PositionedSoundInstance.master(
                    SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER,
                    volume,
                    1f
                )
            )
        }

        if (Effect.EXPLOSION in effects) {
            mc.soundManager.play(
                PositionedSoundInstance.master(
                    SoundEvents.ENTITY_GENERIC_EXPLODE.value(),
                    volume,
                    1f
                )
            )

            repeat(particleCount) {
                world.addParticle(
                    ParticleTypes.EXPLOSION,
                    x + (Math.random() - 0.5) * 2,
                    y + Math.random() * 2,
                    z + (Math.random() - 0.5) * 2,
                    0.0, 0.0, 0.0
                )
            }
        }

        if (Effect.BLOOD in effects && entity is LivingEntity) {
            world.addBlockBreakParticles(
                entity.blockPos.up(1),
                Blocks.REDSTONE_BLOCK.defaultState
            )
        }

        if (Effect.FIREWORK in effects) {
            repeat(particleCount) {
                world.addParticle(
                    ParticleTypes.FIREWORK,
                    x + (Math.random() - 0.5) * 2,
                    y + Math.random() * 2,
                    z + (Math.random() - 0.5) * 2,
                    (Math.random() - 0.5) * 0.1,
                    Math.random() * 0.1,
                    (Math.random() - 0.5) * 0.1
                )
            }
            mc.soundManager.play(
                PositionedSoundInstance.master(
                    SoundEvents.ENTITY_FIREWORK_ROCKET_LAUNCH,
                    volume,
                    1f
                )
            )
        }

        if (Effect.SOUL in effects) {
            repeat(particleCount) {
                world.addParticle(
                    ParticleTypes.SOUL,
                    x + (Math.random() - 0.5) * 2,
                    y + Math.random() * 2,
                    z + (Math.random() - 0.5) * 2,
                    (Math.random() - 0.5) * 0.05,
                    Math.random() * 0.05,
                    (Math.random() - 0.5) * 0.05
                )
            }
        }

        if (Effect.SMOKE in effects) {
            repeat(particleCount) {
                world.addParticle(
                    ParticleTypes.LARGE_SMOKE,
                    x + (Math.random() - 0.5) * 2,
                    y + Math.random() * 2,
                    z + (Math.random() - 0.5) * 2,
                    0.0, 0.05, 0.0
                )
            }
        }

        if (Effect.FREEZE in effects) {
            repeat(particleCount) {
                world.addParticle(
                    ParticleTypes.SNOWFLAKE,
                    x + (Math.random() - 0.5) * 2,
                    y + Math.random() * 2,
                    z + (Math.random() - 0.5) * 2,
                    0.0, 0.0, 0.0
                )
            }
            mc.soundManager.play(
                PositionedSoundInstance.master(
                    SoundEvents.BLOCK_GLASS_BREAK,
                    volume,
                    1f
                )
            )
        }

        if (Effect.PORTAL in effects) {
            repeat(particleCount * 2) {
                world.addParticle(
                    ParticleTypes.REVERSE_PORTAL,
                    x + (Math.random() - 0.5) * 2,
                    y + Math.random() * 2,
                    z + (Math.random() - 0.5) * 2,
                    (Math.random() - 0.5) * 0.1,
                    Math.random() * 0.1,
                    (Math.random() - 0.5) * 0.1
                )
            }
            mc.soundManager.play(
                PositionedSoundInstance.master(
                    SoundEvents.BLOCK_PORTAL_AMBIENT,
                    volume,
                    1f
                )
            )
        }

        if (Effect.WITCH in effects) {
            repeat(particleCount) {
                world.addParticle(
                    ParticleTypes.WITCH,
                    x + (Math.random() - 0.5) * 2,
                    y + Math.random() * 2,
                    z + (Math.random() - 0.5) * 2,
                    0.0, 0.0, 0.0
                )
            }
            mc.soundManager.play(
                PositionedSoundInstance.master(
                    SoundEvents.ENTITY_WITCH_AMBIENT,
                    volume,
                    1f
                )
            )
        }
    }
}
