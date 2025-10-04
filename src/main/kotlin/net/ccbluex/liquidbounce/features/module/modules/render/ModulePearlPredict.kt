package net.ccbluex.liquidbounce.features.module.modules.render

import com.mojang.blaze3d.systems.RenderSystem
import net.ccbluex.liquidbounce.config.types.nesting.Choice
import net.ccbluex.liquidbounce.config.types.nesting.ChoiceConfigurable
import net.ccbluex.liquidbounce.config.types.nesting.ToggleableConfigurable
import net.ccbluex.liquidbounce.event.events.GameTickEvent
import net.ccbluex.liquidbounce.event.events.OverlayRenderEvent
import net.ccbluex.liquidbounce.event.events.WorldChangeEvent
import net.ccbluex.liquidbounce.event.events.WorldRenderEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.render.*
import net.ccbluex.liquidbounce.render.ItemStackListRenderer.Companion.drawItemStackList
import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.ccbluex.liquidbounce.render.utils.RenderUtils.getVector3fCameraBillboard
import net.ccbluex.liquidbounce.utils.client.registerAsDynamicImageFromClientResources
import net.ccbluex.liquidbounce.utils.entity.interpolateCurrentPosition
import net.ccbluex.liquidbounce.utils.render.WireframePlayer
import net.ccbluex.liquidbounce.utils.render.WorldToScreen
import net.ccbluex.liquidbounce.utils.render.trajectory.TrajectoryInfo
import net.ccbluex.liquidbounce.utils.render.trajectory.TrajectoryInfoRenderer
import net.minecraft.client.network.AbstractClientPlayerEntity
import net.minecraft.client.network.OtherClientPlayerEntity
import net.minecraft.client.render.VertexFormat
import net.minecraft.entity.player.PlayerModelPart
import net.minecraft.entity.projectile.thrown.EnderPearlEntity
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.MathHelper
import net.minecraft.util.math.Vec3d
import java.util.*

object ModulePearlPredict : ClientModule("PearlPredict", Category.RENDER, aliases = listOf("PearlESP")) {

    private val espMode = choices("EspMode", Model,
        arrayOf(Model, Wireframe, Marker)).apply {
        doNotIncludeAlways()
    }

    private val colorMode = choices("ColorMode", 0) {
        arrayOf(
            GenericSyncColorMode(it),
            GenericCustomColorMode(it, Color4b.LIQUID_BOUNCE, Color4b.CYAN),
            GenericStaticColorMode(it, Color4b.CYAN),
            GenericRainbowColorMode(it),
        )
    }
    private val highlight by boolean("HighLight",true)

    private object Trajectory : ToggleableConfigurable(this,"Trajectory",true){
        private val trajectoryMode = choices("Mode", TrajectoryMode.Predict,
        arrayOf(TrajectoryMode.Immediate, TrajectoryMode.Predict))
        private val size by float("Size", 0.33f, 0.1f..1.0f)
        private val alive by int("Alive", 50, 0..200, "ticks")
        private val Interval by float("Interval",0.1f,0f..0.5f,"tick")
        private val shrinkDuration by float("ShrinkDuration", 5f, 4f..10f, "tick")
        private val gradientSpeed by int("GradientSpeed", 5, 1..10)
        class Trail {
            val positions = ArrayDeque<Pair<Vec3d, Long>>()
            var simPositions: List<Vec3d> = emptyList()
            var nextIndex = 0
            var lastAddTick: Long = Long.MIN_VALUE
        }
        val pearlTrails = IdentityHashMap<EnderPearlEntity, Trail>()
        private var pooledPosArray = FloatArray(0)  // xyz xyz ...
        private var pooledTimeArray = LongArray(0)  // creation ticks per point

        sealed class TrajectoryMode(name: String) : Choice(name) {
            object Immediate : TrajectoryMode("Immediate") {
                override val parent: ChoiceConfigurable<*>
                    get() = trajectoryMode

            }

            object Predict : TrajectoryMode("Predict") {
                override val parent: ChoiceConfigurable<*>
                    get() = trajectoryMode
            }
        }

        private val currentAlive: Int
            get() = when (trajectoryMode.activeChoice) {
                is TrajectoryMode.Immediate -> alive
                is TrajectoryMode.Predict -> 5
            }

        @Suppress("unused")
        private val worldChangeHandler = handler<WorldChangeEvent> {
           pearlTrails.clear()
        }

        @Suppress("unused")
        private val tickHandler = handler<GameTickEvent> {
            val currentTime = mc.world?.time ?: return@handler
            if (!enabled) {
                return@handler
            }
            val pearls = world.entities.filterIsInstance<EnderPearlEntity>()

            pearls.forEach { pearl ->
                val trail = pearlTrails.getOrPut(pearl, ::Trail)

                if (trail.simPositions.isEmpty()) {
                    val renderer = TrajectoryInfoRenderer(
                        pearl,
                        pearl.velocity,
                        pearl.pos,
                        TrajectoryInfo.GENERIC,
                        TrajectoryInfoRenderer.Type.REAL,
                        Vec3d.ZERO
                    )
                    trail.simPositions = renderer.runSimulation(300).positions
                    trail.nextIndex = 0

                    if (trajectoryMode.activeChoice is TrajectoryMode.Predict) {
                        val throwTime = currentTime
                        val intervalTicks = (Interval * 20f).toLong().coerceAtLeast(1)
                        var timeOffset = 0L
                        trail.simPositions.forEachIndexed { idx, pos ->
                            if (idx % intervalTicks == 0L) {
                                trail.positions.add(pos to (throwTime + timeOffset))
                                timeOffset += intervalTicks
                            }
                        }
                        if (trail.positions.isNotEmpty()) {
                            trail.positions.removeLast()
                        }
                        trail.nextIndex = trail.simPositions.size
                    }
                }

                when (trajectoryMode.activeChoice) {
                    is TrajectoryMode.Immediate -> {

                        val intervalTicks = (Interval * 20f).toLong().coerceAtLeast(1L)

                        if (trail.lastAddTick == Long.MIN_VALUE || currentTime - trail.lastAddTick >= intervalTicks) {
                            trail.positions.add(pearl.pos to currentTime)
                            trail.lastAddTick = currentTime
                        }
                    }
                    is TrajectoryMode.Predict -> {
                    }
                }

                while (trail.positions.isNotEmpty() &&
                    currentTime - trail.positions.peekFirst().second > currentAlive) {
                    trail.positions.removeFirst()
                }
            }

            pearlTrails.keys.retainAll { pearl ->
                pearl.isAlive || pearlTrails[pearl]?.positions?.isNotEmpty() == true
            }
        }
        @Suppress("unused")
        private val worldRenderHandler = handler<WorldRenderEvent> { event ->
            if (pearlTrails.isEmpty()) {
                return@handler
            }
            if (!enabled) {
                return@handler
            }
            val currentTime = mc.world?.time ?: return@handler
            val matrixStack = event.matrixStack
            val (color1Base, color2Base) = colorMode.activeChoice.getColors(player)

            renderEnvironmentForWorld(matrixStack) {
                val billboard = getVector3fCameraBillboard()



                var totalPoints = 0
                for (trail in pearlTrails.values) {
                    totalPoints += trail.positions.size
                }
                if (totalPoints == 0) return@renderEnvironmentForWorld


                if (pooledPosArray.size < totalPoints * 3) pooledPosArray = FloatArray(totalPoints * 3)
                if (pooledTimeArray.size < totalPoints) pooledTimeArray = LongArray(totalPoints)

                var writeIndex = 0
                for (trail in pearlTrails.values) {
                    for ((pos, creationTick) in trail.positions) {
                        val base = writeIndex * 3
                        pooledPosArray[base    ] = pos.x.toFloat()
                        pooledPosArray[base + 1] = pos.y.toFloat()
                        pooledPosArray[base + 2] = pos.z.toFloat()
                        pooledTimeArray[writeIndex] = creationTick
                        writeIndex++
                    }
                }
                if (pearlTrails.values.all { it.positions.isEmpty() }) return@renderEnvironmentForWorld

                drawSparkleTrail(
                    positions = pooledPosArray,
                    creationTimes = pooledTimeArray,
                    totalSize = totalPoints,
                    env = this,
                    size = size,
                    currentTime = currentTime,
                    alive = currentAlive,
                    gradientSpeed = gradientSpeed,
                    primaryColor = color1Base,
                    secondaryColor = color2Base,
                    darkImprint = true,
                    fade = false,
                    shrink = true,
                    fadeDuration = 0f,
                    shrinkDuration = shrinkDuration,
                    distanceLimit = null,
                    cameraPos = billboard.pos,
                    right = billboard.right,
                    up = billboard.up,
                    time = billboard.time
                )
            }
        }

        override fun onEnabled() {
            pearlTrails.clear()
        }

        override fun onDisabled() {
            pearlTrails.clear()
        }
    }

    init {
        tree(Trajectory)
    }

    @Suppress("unused")
    private val renderHandler = handler<OverlayRenderEvent> { event ->
        if (!highlight){
            return@handler
        }
        renderEnvironmentForGUI {
            world.entities.forEach { entity ->
                if (entity !is EnderPearlEntity) return@forEach

                val pos = entity.interpolateCurrentPosition(event.tickDelta)
                val screenPos = WorldToScreen.calculateScreenPos(pos) ?: return@forEach

                event.context.drawItemStackList(listOf(ItemStack(Items.ENDER_PEARL)))
                    .centerX(screenPos.x)
                    .centerY(screenPos.y)
                    .centerZ(screenPos.z)
                    .scale(0.5f)
                    .rectBackground(0)
                    .rowLength(1)
                    .itemStackRenderer(
                        ItemStackListRenderer.SingleItemStackRenderer.nothing())
                    .draw()
            }
        }
    }

    abstract class RenderChoice(name: String) : Choice(name) {
        override val parent: ChoiceConfigurable<RenderChoice>
            get() = espMode

        protected fun getPearlLandingPosition(pearl: EnderPearlEntity): Vec3d? {
            val renderer = TrajectoryInfoRenderer(
                pearl,
                pearl.velocity,
                pearl.pos,
                TrajectoryInfo.GENERIC,
                TrajectoryInfoRenderer.Type.REAL,
                Vec3d.ZERO
            )
            return renderer.runSimulation(300).hitResult?.pos
        }
    }

    private val fakePlayers = WeakHashMap<AbstractClientPlayerEntity, OtherClientPlayerEntity>()

    private fun getOrCreateFakePlayer(owner: AbstractClientPlayerEntity): OtherClientPlayerEntity {
        return fakePlayers.getOrPut(owner) {
            object : OtherClientPlayerEntity(mc.world, owner.gameProfile) {
                override fun isPartVisible(part: PlayerModelPart): Boolean = true
                init {
                    this.refreshPositionAndAngles(owner.x, owner.y, owner.z, 0f, 0f)
                }
            }
        }
    }

    object Model : RenderChoice("Model") {
        private val lightAmount by float("LightAmount", 0.3f, 0.01f..1f)

        @Suppress("unused")
        private val worldRenderHandler = handler<WorldRenderEvent> { event ->
            val pearls = world.entities.filterIsInstance<EnderPearlEntity>()
            if (pearls.isEmpty() || !enabled) {
                return@handler
            }

            renderEnvironmentForWorld(event.matrixStack) {
                pearls.forEach { pearl ->
                    val landingPos = getPearlLandingPosition(pearl) ?: return@forEach
                    val renderPos = landingPos.add(0.0, 0.5, 0.0)
                    val light = world.getLightLevel(BlockPos.ofFloored(landingPos))
                    val reducedLight = (light * lightAmount.toDouble()).toInt()
                    val owner = if (pearl.owner is AbstractClientPlayerEntity) {
                        pearl.owner as AbstractClientPlayerEntity
                    } else {
                        world.players
                            .filterIsInstance<AbstractClientPlayerEntity>()
                            .minByOrNull { it.squaredDistanceTo(pearl) }
                            ?: return@forEach
                    }

                    val fakePlayer = getOrCreateFakePlayer(owner)

                    fakePlayer.setPos(renderPos.x, renderPos.y, renderPos.z)
                    fakePlayer.pitch = 0f
                    fakePlayer.yaw = owner.yaw
                    fakePlayer.prevX = renderPos.x
                    fakePlayer.prevY = renderPos.y
                    fakePlayer.prevZ = renderPos.z
                    fakePlayer.capeX = renderPos.x
                    fakePlayer.capeY = renderPos.y
                    fakePlayer.capeZ = renderPos.z
                    fakePlayer.prevCapeX = renderPos.x
                    fakePlayer.prevCapeY = renderPos.y
                    fakePlayer.prevCapeZ = renderPos.z

                    withPositionRelativeToCamera(renderPos) {
                        mc.entityRenderDispatcher.render(
                            fakePlayer,
                            0.0,
                            0.0,
                            0.0,
                            0f,
                            event.matrixStack,
                            mc.bufferBuilders.entityVertexConsumers,
                            reducedLight
                        )
                    }
                }
            }
        }
    }

    object Wireframe : RenderChoice("Wireframe") {
        private val fillAlpha by int("FillAlpha", 70, 0..255)
        private val outlineAlpha by int("OutlineAlpha", 150, 0..255)

        @Suppress("unused")
        private val worldRenderHandler = handler<WorldRenderEvent> { event ->
            val pearls = world.entities.filterIsInstance<EnderPearlEntity>()
            if (pearls.isEmpty() || !enabled) return@handler

            renderEnvironmentForWorld(event.matrixStack) {
                pearls.forEach { pearl ->
                    val landingPos = getPearlLandingPosition(pearl) ?: return@forEach
                    val renderPos = landingPos.add(0.0, 0.5, 0.0)
                    val baseColor = colorMode.activeChoice.getColor(null)
                    val fillColor = baseColor.withAlpha(fillAlpha)
                    val outlineColor = baseColor.withAlpha(outlineAlpha)

                    val wireframePlayer = WireframePlayer(renderPos, 0f, 0f)
                    wireframePlayer.render(event, fillColor, outlineColor)
                }
            }
        }
    }

    object Marker : RenderChoice("Marker") {
        private val opacity by float("Opacity", 1.0F, 0.25F..1F)
        private val pointerTexture: Identifier = "image/waypoint.png".registerAsDynamicImageFromClientResources()

        @Suppress("unused")
        private val worldRenderHandler = handler<WorldRenderEvent> { event ->
            val pearls = world.entities.filterIsInstance<EnderPearlEntity>()
            if (pearls.isEmpty() || !enabled) return@handler

            renderEnvironmentForWorld(event.matrixStack) {
                pearls.forEach { pearl ->
                    val landingPos = getPearlLandingPosition(pearl) ?: return@forEach
                    val renderPos = landingPos.add(0.0, 0.5, 0.0)
                    RenderSystem.setShaderTexture(0, pointerTexture)

                    withPositionRelativeToCamera(renderPos) {
                        matrixStack.apply {
                            multiply(mc.gameRenderer.camera.rotation)
                            scale(1f, 1f, 1f)
                            translate(-0.5f, -0.5f, 0f)
                        }

                        val alpha = MathHelper.clamp((255 * opacity).toInt(), 0, 255)
                        val color = Color4b.WHITE.withAlpha(alpha)

                        drawCustomMesh(
                            VertexFormat.DrawMode.QUADS,
                            VertexInputType.PosTexColor,
                        ) { matrix ->
                            vertex(matrix, 0f, 0f, 0f).texture(0f, 1f).color(color.toARGB())
                            vertex(matrix, 1f, 0f, 0f).texture(1f, 1f).color(color.toARGB())
                            vertex(matrix, 1f, 1f, 0f).texture(1f, 0f).color(color.toARGB())
                            vertex(matrix, 0f, 1f, 0f).texture(0f, 0f).color(color.toARGB())
                        }
                    }
                }
            }
        }
    }
}
