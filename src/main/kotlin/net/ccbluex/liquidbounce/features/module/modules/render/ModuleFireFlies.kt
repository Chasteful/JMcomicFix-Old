package net.ccbluex.liquidbounce.features.module.modules.render

import com.mojang.blaze3d.platform.GlStateManager
import com.mojang.blaze3d.systems.RenderSystem
import net.ccbluex.liquidbounce.event.events.GameTickEvent
import net.ccbluex.liquidbounce.event.events.WorldRenderEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.render.*
import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.ccbluex.liquidbounce.render.utils.RenderUtils
import net.ccbluex.liquidbounce.render.utils.RenderUtils.getVector3fCameraBillboard
import net.ccbluex.liquidbounce.utils.client.registerAsDynamicImageFromClientResources
import net.ccbluex.liquidbounce.utils.math.generateRandomVelocity
import net.ccbluex.liquidbounce.utils.math.generateSpawnPosition
import net.minecraft.client.render.VertexFormat
import net.minecraft.util.Identifier
import org.joml.Vector3f
import java.io.File
import java.util.concurrent.ThreadLocalRandom
import kotlin.math.sin

object ModuleFireFlies : ClientModule("FireFlies", Category.RENDER) {
    private val colorMode = choices(this, "ColorMode") {
        arrayOf(
            GenericSyncColorMode(it),
            GenericCustomColorMode(it, Color4b.LIQUID_BOUNCE, Color4b.CYAN),
            GenericStaticColorMode(it, Color4b.YELLOW),
            GenericRainbowColorMode(it)
        )
    }

    private val darkImprint by boolean("DarkImprint", true)
    private val scale by float("Scale", 1.0f, 0.1f..10f)
    private val spawnDelay by float("SpawnDelay", 1.0f, 1.0f..10.0f)
    private val spawnleCount by int("SpawnCount", 64, 10..10000)
    private val maxLiveCount by int("MaxLiveCount", 1337, 50..50000)
    private val maxQuadsPerFrame by int("MaxQuadsPerFrame",2333,2000..50000)
    private val customFile by file("Custom")

    private var cachedTexture: Identifier? = null
    private var cachedFile: File? = null

    private val fireFliesTexture: Identifier
        get() {
            val file = customFile
            return if (file.isFile) {
                if (file != cachedFile) {
                    cachedTexture = file.inputStream().use { stream ->
                        stream.registerAsDynamicImageFromClientResources("fireflies")
                    }
                    cachedFile = file
                }
                cachedTexture!!
            } else {
                if (cachedTexture == null || cachedFile != null) {
                    cachedTexture = "image/firepart.png".registerAsDynamicImageFromClientResources()
                    cachedFile = null
                }
                cachedTexture!!
            }
        }

    private val particles = ArrayDeque<FireFly>()

    private val rand get() = ThreadLocalRandom.current()
    private var spawnTimer = 0f

    private data class FireFly(
        val pos: Vector3f,
        val prevPos: Vector3f,
        val creationTime: Long,
        val maxAlive: Long,
        val velocity: Vector3f,
        val phase: Float = (ThreadLocalRandom.current().nextFloat() * 13f)
    )

    override fun onEnabled() {
        particles.clear()
    }

    override fun onDisabled() {
        particles.clear()
    }

    @Suppress("unused")
    private val updateHandler = handler<GameTickEvent> {
        val player = mc.player ?: return@handler
        val currentTime = mc.world?.time ?: return@handler

        val it = particles.iterator()
        while (it.hasNext()) {
            val particle = it.next()

            particle.prevPos.set(particle.pos)

            particle.pos.add(particle.velocity)

            if (rand.nextFloat() < 0.05f) {
                val nv = generateRandomVelocity(rand)
                particle.velocity.set(nv.x, nv.y, nv.z)
            }
        }


        val playerX = player.x.toFloat(); val playerY = player.y.toFloat(); val playerZ = player.z.toFloat()
        val maxDistance = 25f
        val maxDistSq = maxDistance * maxDistance
        if (particles.isNotEmpty()) {
            val survivors = ArrayDeque<FireFly>(particles.size)
            val itr = particles.iterator()
            while (itr.hasNext()) {
                val p = itr.next()
                val dx = p.pos.x - playerX; val dy = p.pos.y - playerY; val dz = p.pos.z - playerZ
                val distSq = dx * dx + dy * dy + dz * dz
                if (distSq <= maxDistSq && (currentTime - p.creationTime) <= p.maxAlive) survivors.addLast(p)
            }
            if (survivors.size != particles.size) {
                particles.clear()
                particles.addAll(survivors)
            }
        }

        spawnTimer += 1f
        if (spawnTimer >= spawnDelay) {
            spawnTimer = 0f
            val canSpawn = (maxLiveCount - particles.size).coerceAtLeast(0)
            val spawnCount = spawnleCount.coerceAtMost(canSpawn)
            repeat(spawnCount) {
                val pos = generateSpawnPosition(rand)
                val vel = generateRandomVelocity(rand)
                particles.addLast(
                    FireFly(
                        Vector3f(pos.x, pos.y, pos.z),
                        Vector3f(pos.x, pos.y, pos.z),
                        currentTime,
                        6000L,
                        vel
                    )
                )
            }
        }
    }

    @Suppress("unused","LongParameterList")
    private val renderHandler = handler<WorldRenderEvent> { event ->
        if (particles.isEmpty()) return@handler
        val player = mc.player ?: return@handler
        val currentTime = mc.world?.time ?: return@handler
        val matrixStack = event.matrixStack
        val partialTicks = event.partialTicks

        renderEnvironmentForWorld(matrixStack) {
            RenderSystem.enableBlend()
            RenderSystem.blendFuncSeparate(
                GlStateManager.SrcFactor.SRC_ALPHA,
                if (darkImprint) GlStateManager.DstFactor.ONE else GlStateManager.DstFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SrcFactor.ZERO,
                GlStateManager.DstFactor.ONE
            )
            RenderSystem.depthMask(false)
            RenderSystem.setShaderTexture(0, fireFliesTexture)
            RenderSystem.enableDepthTest()
            val camera = mc.gameRenderer.camera
            val (color1Base, color2Base) = colorMode.activeChoice.getColors(player)

            val time = currentTime.toFloat()
            val maxDistSq = 25.0f * 25.0f
            val camX = camera.pos.x.toFloat()
            val camY = camera.pos.y.toFloat()
            val camZ = camera.pos.z.toFloat()
            val playerX = player.x.toFloat()
            val playerY = player.y.toFloat()
            val playerZ = player.z.toFloat()

            val billboard = getVector3fCameraBillboard()


            val blendStep = 128
            val cycle = (Math.PI.toFloat() * 2f)
            val sinTable = FloatArray(blendStep) { i ->
                val angle = (i.toFloat() / blendStep.toFloat()) * cycle
                ((sin(angle) + 1f) * 0.5f)
            }

            val blendLookup = IntArray(blendStep) { i ->
                val b = color1Base.blend(color2Base, sinTable[i])
                b.toARGB()
            }
            val colors = IntArray(maxQuadsPerFrame)
            val positions = FloatArray(maxQuadsPerFrame * 3)
            val sizes = FloatArray(maxQuadsPerFrame)
            var index = 0

            val itr = particles.iterator()
            while (itr.hasNext() && index < maxQuadsPerFrame) {
                val particle = itr.next()

                val px = particle.prevPos.x
                val py = particle.prevPos.y
                val pz = particle.prevPos.z
                val nx = particle.pos.x
                val ny = particle.pos.y
                val nz = particle.pos.z
                val interpX = px + (nx - px) * partialTicks
                val interpY = py + (ny - py) * partialTicks
                val interpZ = pz + (nz - pz) * partialTicks

                val dx = interpX - playerX
                val dy = interpY - playerY
                val dz = interpZ - playerZ
                val distSq = dx * dx + dy * dy + dz * dz
                if (distSq > maxDistSq) continue

                val lifeFrac = ((currentTime - particle.creationTime)/ particle.maxAlive.toFloat()).coerceIn(0f, 1f)
                val progress = 1f - lifeFrac
                if (progress <= 0f) continue

                var phasePos = (time * 0.2f + particle.phase) % cycle
                if (phasePos < 0f) phasePos += cycle
                val idx = ((phasePos / cycle) * (blendStep - 1)).toInt()
                val argb = blendLookup[idx]
                val rgbOnly = argb and 0x00FFFFFF
                val blendedAlpha = (argb ushr 24) and 0xFF
                val alpha = (blendedAlpha * progress).toInt()
                if (alpha <= 0) continue

                colors[index] = ((alpha and 0xFF) shl 24) or rgbOnly
                positions[index * 3] = interpX - camX
                positions[index * 3 + 1] = interpY - camY
                positions[index * 3 + 2] = interpZ - camZ
                sizes[index] = (0.1f + 0.05f * (1f - progress)) * scale
                index++
            }

            if (index > 0) {
                drawCustomMesh(
                    VertexFormat.DrawMode.QUADS,
                    VertexInputType.PosTexColor,
                ) { mat ->
                    for (i in 0 until index) {
                        val cx = positions[i * 3]
                        val cy = positions[i * 3 + 1]
                        val cz = positions[i * 3 + 2]
                        val half = sizes[i] * 0.5f

                        RenderUtils.emitQuadInline(
                            colors[i],
                            cx, cy, cz,
                            mat,
                            billboard.right.x * half,
                            billboard.right.y * half,
                            billboard.right.z * half,
                            billboard.up.x * half,
                            billboard.up.y * half,
                            billboard.up.z * half,
                            this
                        )
                    }
                }
            }

            RenderSystem.depthMask(true)
            RenderSystem.disableBlend()
        }
    }


}
