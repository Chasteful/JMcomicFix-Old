package net.ccbluex.jmcomicfix.features.module.modules.player.autoClutch

import net.ccbluex.jmcomicfix.features.module.modules.world.stuck.ModuleAutoStuck
import net.ccbluex.liquidbounce.config.types.NamedChoice
import net.ccbluex.liquidbounce.event.events.GameTickEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.utils.aiming.RotationManager
import net.ccbluex.liquidbounce.utils.aiming.RotationsConfigurable
import net.ccbluex.liquidbounce.utils.aiming.data.Rotation
import net.ccbluex.liquidbounce.utils.client.SilentHotbar
import net.ccbluex.liquidbounce.utils.inventory.HotbarItemSlot
import net.ccbluex.liquidbounce.utils.inventory.Slots
import net.ccbluex.liquidbounce.utils.inventory.useHotbarSlotOrOffhand
import net.ccbluex.liquidbounce.utils.kotlin.Priority
import net.minecraft.block.Blocks
import net.minecraft.item.Items
import net.minecraft.util.math.Box
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

@Suppress("TooManyFunctions")
object ModuleAutoClutch : ClientModule("AutoClutch", Category.PLAYER) {
    init {
        enableLock()
    }
    @Suppress("unused")
    private val mode by enumChoice("Algorithm", Algorithm.SimulatedAnnealing)

    private val maxIterations by int("MaxIterations", 25000, 2000..50000)
    private val maxRepeat by int("MaxRepeat", 50000, 2000..50000)
    private val initialTemperature by float("InitialTemp", 15f, 5f..30f)
    private val minTemperature by float("MinTemp", 0.01f, 0.01f..0.1f)
    private val coolingRate by float("CoolingRate", 0.99f, 0.95f..0.99f)
    private val aimThreshold by float("AimThreshold", 0.5f, 0.1f..1f)
    private val aimPitch by floatRange("PitchRange", -90f..90f, -90f..90f)
    private val slotResetDelay by int("SlotResetDelay", 1, 0..10, "ticks")
    private val silenceSlot by boolean("SilenceHotbar", true)
    private val rotationConfig = tree(RotationsConfigurable(this))

    enum class State { IDLE, FINDING_PEARL, CALCULATING, ROTATING, THROWING }

    var state = State.IDLE
    private var bestSolution: Rotation? = null
    private var bestEnergy = Double.MAX_VALUE
    private var currentSolution = Rotation(0f, 0f)
    private var currentEnergy = Double.MAX_VALUE
    private var temperature = initialTemperature
    private var iterations = 0
    private var triggerPosition: Vec3d? = null
    private var pearlSlot: HotbarItemSlot? = null
    private var lastPlayerPosition: Vec3d? = null


    @Suppress("unused")
    private val tickHandler = handler<GameTickEvent> {
        if (!ModuleAutoStuck.shouldEnableStuck) {
            return@handler
        }
        checkPlayerMovement()
        when (state) {
            State.IDLE -> checkActivationConditions()
            State.FINDING_PEARL -> findPearl()
            State.CALCULATING -> calculateSolution()
            State.ROTATING -> rotateToSolution()
            State.THROWING -> throwPearl()
        }
    }

    private fun checkPlayerMovement() {
        val currentPos = player.pos
        lastPlayerPosition?.let { lastPos ->
            if (currentPos.distanceTo(lastPos) > 1.0) {
                resetAllVariables()
            }
        }
        lastPlayerPosition = currentPos
    }

    private fun resetAllVariables() {
        state = State.IDLE
        bestSolution = null
        bestEnergy = Double.MAX_VALUE
        currentSolution = Rotation(0f, 0f)
        currentEnergy = Double.MAX_VALUE
        temperature = initialTemperature
        iterations = 0
        triggerPosition = null
        pearlSlot = null
    }

    private fun checkActivationConditions() {
        if (!ModuleAutoStuck.isInAir && !isBlockUnder(
                x = player.pos.x,
                y = player.pos.y,
                z = player.pos.z,
                height = 30.0,
                boundingBox = true
            )
        ) {

            triggerPosition = player.pos
            state = State.FINDING_PEARL
        }
    }

    private fun findPearl() {
        val pearlSlot = Slots.OffhandWithHotbar.findSlot(Items.ENDER_PEARL)?.hotbarSlotForServer
        if (pearlSlot == null) {
            state = State.IDLE
            return
        }

        if (silenceSlot) {

            SilentHotbar.selectSlotSilently(this, pearlSlot, slotResetDelay)
        } else {
            player.inventory.selectedSlot = pearlSlot
        }

        resetAnnealing()
        state = State.CALCULATING
    }

    private fun resetAnnealing() {

        currentSolution = Rotation(
            getRandomInRange(-180f, 180f).toFloat(),
            getRandomInRange(-90f, 90f).toFloat(),
        )


        currentEnergy = assessRotation(currentSolution)
        bestSolution = currentSolution
        bestEnergy = currentEnergy


        temperature = initialTemperature
        iterations = 0
    }

    fun getRandomInRange(min: Float, max: Float): Int {
        return (Math.random() * (max - min).toDouble() + min.toDouble()).toInt()
    }

    private fun calculateSolution() {
        val scaledTemperature = temperature * 100
        repeat(maxRepeat) {

            if (iterations >= maxIterations || temperature < minTemperature) {

                state = State.ROTATING
                return@repeat
            }


            val newSolution = Rotation(
                (currentSolution.yaw + getRandomInRange(-temperature * 18f, temperature * 18f)),
                (currentSolution.pitch + getRandomInRange(-temperature * 9f, temperature * 9f))
                    .coerceIn(aimPitch)
            )


            val newEnergy = assessRotation(newSolution)


            val deltaEnergy = newEnergy - currentEnergy


            if (deltaEnergy < 0 || Random.Default.nextDouble() < exp(-deltaEnergy / temperature)) {
                currentSolution = newSolution
                currentEnergy = newEnergy


                if (currentEnergy < bestEnergy) {
                    bestSolution = currentSolution
                    bestEnergy = currentEnergy
                }
            }

            temperature = (scaledTemperature * coolingRate) / 100
            iterations++
        }
    }

    private fun rotateToSolution() {
        if (!mc.options.pickItemKey.isPressed) {

            bestSolution?.let {

                RotationManager.setRotationTarget(
                    rotationConfig.toRotationTarget(it),
                    priority = Priority.IMPORTANT_FOR_USAGE_1,
                    provider = this,
                )

                if (RotationManager.serverRotation.angleTo(it) <= aimThreshold) {
                    state = State.THROWING
                }
            } ?: run {

                state = State.IDLE
            }
        }
    }

    private fun throwPearl() {
        Slots.Hotbar.findSlot(Items.ENDER_PEARL)?.let {
            useHotbarSlotOrOffhand(it, 0, bestSolution?.yaw ?: 0f, bestSolution?.pitch ?: 0f)
        }

        state = State.IDLE

    }

    private fun assessRotation(rotation: Rotation): Double {
        val pearlPos = simulatePearlTrajectory(rotation)
        return assessPosition(pearlPos)
    }

    private fun assessPosition(pos: Vec3d): Double {

        val groundPos = BlockPos(pos.x.toInt(), (pos.y - 0.5).toInt(), pos.z.toInt())
        val groundState = world.getBlockState(groundPos)
        val hasGround = !groundState.isAir && groundState.block != Blocks.WATER && groundState.block != Blocks.LAVA


        val playerBox = Box(
            pos.x - 0.3, pos.y, pos.z - 0.3,
            pos.x + 0.3, pos.y + player.height, pos.z + 0.3
        )
        val collisions = world.getBlockCollisions(player, playerBox).iterator()
        val hasSpace = !collisions.hasNext()


        val horizontalDistance = distanceSq2D(pos, triggerPosition ?: player.pos)


        return when {
            !hasGround -> 10000.0
            !hasSpace -> 5000.0 + horizontalDistance
            else -> horizontalDistance
        }
    }

    private fun simulatePearlTrajectory(rotation: Rotation): Vec3d {

        val yaw = Math.toRadians(rotation.yaw.toDouble())
        val pitch = Math.toRadians(rotation.pitch.toDouble())


        val velocity = 1.5


        var motionX = -sin(yaw) * cos(pitch) * velocity
        var motionY = -sin(pitch) * velocity
        var motionZ = cos(yaw) * cos(pitch) * velocity


        var posX = player.x
        var posY = player.eyeY
        var posZ = player.z


        repeat(40) {

            motionY -= 0.03


            posX += motionX
            posY += motionY
            posZ += motionZ


            motionX *= 0.99
            motionY *= 0.99
            motionZ *= 0.99
        }

        return Vec3d(posX, posY, posZ)
    }

    private fun distanceSq2D(a: Vec3d, b: Vec3d): Double {
        val dx = a.x - b.x
        val dz = a.z - b.z
        return dx * dx + dz * dz
    }

    private fun isBlockUnder(x: Double, y: Double, z: Double, height: Double, boundingBox: Boolean): Boolean {
        val world = mc.world ?: return false
        val player = mc.player ?: return false

        if (boundingBox) {
            var offset = 0.0
            while (offset < height) {

                val playerBox = player.boundingBox.offset(0.0, -offset, 0.0)


                if (world.getBlockCollisions(player, playerBox).iterator().hasNext()) {
                    return true
                }
                offset += 0.5
            }
        } else {
            var offset = 0.0
            while (offset < height) {
                val blockPos = BlockPos(x.toInt(), (y - offset).toInt(), z.toInt())
                val blockState = world.getBlockState(blockPos)

                if (!blockState.isAir && !blockState.fluidState.isEmpty) {
                    return true
                }
                offset += 0.5
            }
        }
        return false
    }
    enum class Algorithm(override val choiceName: String) : NamedChoice {
        SimulatedAnnealing("SimulatedAnnealing")

    }
    override fun disable() {
        resetAllVariables()
        lastPlayerPosition = null
    }
}
