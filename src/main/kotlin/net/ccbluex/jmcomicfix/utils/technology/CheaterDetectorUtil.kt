@file:Suppress("detekt:all")
package net.ccbluex.jmcomicfix.utils.technology

import net.ccbluex.liquidbounce.features.module.modules.misc.antibot.ModuleAntiBot
import net.ccbluex.liquidbounce.features.module.modules.misc.anticheat.ModuleAntiCheat
import net.ccbluex.liquidbounce.utils.block.getState
import net.ccbluex.liquidbounce.utils.client.chat
import net.ccbluex.liquidbounce.utils.client.mc
import net.ccbluex.liquidbounce.utils.entity.PlayerSimulationCache
import net.minecraft.block.*
import net.minecraft.entity.Entity
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.effect.StatusEffects
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.registry.tag.FluidTags
import net.minecraft.util.Formatting
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import net.minecraft.util.math.MathHelper
import net.minecraft.util.math.Vec3d
import kotlin.math.*

object CheaterDetectorUtil {

    data class SimulationResult(
        val velocity: Vec3d,
        val pos: Vec3d,
        val fallDistance:Float,
        val onGround: Boolean,
        val isTouchingHorizontally: Boolean,
        val isSprinting: Boolean,
        val isJumping: Boolean,
        val isTouchingWater: Boolean,
        val isInLava: Boolean,
        val isOnLadder: Boolean,
        val isInsideWeb: Boolean,

    )

    private fun simulateAndCheck(
        entity: ModuleAntiCheat.HackerLivingBase,
        ticks: Int = 1
    ): SimulationResult {
        val player = entity.player
        val cache = if (player == mc.player) {
            PlayerSimulationCache.getSimulationForLocalPlayer()
        } else {
            PlayerSimulationCache.getSimulationForOtherPlayers(player)
        }
        val snapshot = cache.getSnapshotAt(ticks)

        return SimulationResult(
            velocity = snapshot.velocity,
            pos = snapshot.pos,
            fallDistance = snapshot.fallDistance,
            onGround = snapshot.onGround,
            isTouchingHorizontally = snapshot.clipLedged,
            isSprinting = player.isSprinting,
            isJumping = player.jumping,
            isTouchingWater = player.isTouchingWater,
            isInLava = player.fluidHeight.getDouble(FluidTags.LAVA) > 0.0,
            isOnLadder = cache.simulatedPlayer.isClimbing(),
            isInsideWeb = player.world.getBlockState(player.blockPos).block == Blocks.COBWEB,

        )
    }


    private fun getBaseSpeed(
        entity: ModuleAntiCheat.HackerLivingBase,
        simulation: SimulationResult,
        airFactor: Double,
        groundFactor: Double,
        jumpFactor: Double
    ): Double {
        val horizontalSpeed = sqrt(simulation.velocity.x.pow(2) + simulation.velocity.z.pow(2))
        val baseSpeed = if (simulation.onGround) {
            horizontalSpeed * groundFactor
        } else {
            horizontalSpeed * airFactor +
                jumpFactor * 0.42 * (if (simulation.isSprinting) 1.3 else 1.0) * 0.98.pow(entity.jumpTick.toDouble())
        }
        val sprintMultiplier = if (checkHead(entity.player.blockPos) && simulation.isSprinting) 2.7 else 1.0
        val blockMultiplier = when (getNearGroundBlock(entity.player, false)?.block) {
            is BedBlock, is IceBlock, is SkullBlock, is SlabBlock, is StairsBlock,
            is DragonEggBlock, is AbstractPressurePlateBlock, is StainedGlassPaneBlock -> 2.7
            else -> 1.0
        }
        val velocityFactor = horizontalSpeed / 8000.0 *
            0.98.pow(floor((System.currentTimeMillis() - entity.bypassTime) / 50.0)) * 1.7
        return (baseSpeed + velocityFactor) * sprintMultiplier * blockMultiplier
    }

    fun checkCelerity(
        entity: ModuleAntiCheat.HackerLivingBase,
        airFactor: Double,
        jumpFactor: Double,
        groundFactor: Double
    ): String {
        val simulation = simulateAndCheck(entity)
        val detectedSpeed = sqrt(entity.player.velocity.x.pow(2) + entity.player.velocity.z.pow(2))
        val possibleSpeed = getBaseSpeed(entity, simulation, airFactor, groundFactor, jumpFactor)
        return if (detectedSpeed > possibleSpeed && !entity.player.horizontalCollision) {
            "Moving faster than expected.(DETECTED=%.4f b/s|POSSIBLE=%.4f b/s)".format(detectedSpeed, possibleSpeed)
        } else "LEGIT"
    }

    fun checkSlowDown(
        entity: ModuleAntiCheat.HackerLivingBase,
        slowFactor: Double,
        airFactor: Double,
        jumpFactor: Double,
        groundFactor: Double,
        webFactor: Double
    ): String {
        val simulation = simulateAndCheck(entity)
        val detectedSpeed = sqrt(entity.player.velocity.x.pow(2) + entity.player.velocity.z.pow(2))
        val baseSpeed = getBaseSpeed(entity, simulation, airFactor, groundFactor, jumpFactor)
        return when {
            entity.player.itemUseTime > 5 && detectedSpeed > baseSpeed * slowFactor -> {
                "Moving faster than expected(USING).(DETECTED=%.4f b/s|POSSIBLE=%.4f b/s)"
                    .format(detectedSpeed, baseSpeed * slowFactor)
            }
            simulation.isInsideWeb && detectedSpeed > baseSpeed * webFactor -> {
                "Moving faster than expected(WEB).(DETECTED=%.4f b/s|POSSIBLE=%.4f b/s)"
                    .format(detectedSpeed, baseSpeed * webFactor)
            }
            else -> "LEGIT"
        }
    }

    private fun getBaseJumpHeight(simulation: SimulationResult, entity: ModuleAntiCheat.HackerLivingBase): Double {
        val jumpBoost = if (entity.player.hasStatusEffect(StatusEffects.JUMP_BOOST)) {
            1.0 + 0.5 * (entity.player.getStatusEffect(StatusEffects.JUMP_BOOST)!!.amplifier + 1)
        } else {
            1.0
        }
        return 0.42 * jumpBoost + simulation.velocity.y * 0.98.pow(floor((System.currentTimeMillis() - entity.bypassTime) / 50.0))
    }

    private val airTicksMap = mutableMapOf<Entity, Int>()
    private val waterBypassTicks = mutableMapOf<PlayerEntity, Int>()
    private val jumpBypassTicks = mutableMapOf<PlayerEntity, Int>()

    fun checkPhysic(entity: ModuleAntiCheat.HackerLivingBase): String {
        if (entity.player.isInsideWaterOrBubbleColumn) {
            waterBypassTicks[entity.player] = 10
        } else {
            waterBypassTicks[entity.player] = (waterBypassTicks[entity.player] ?: 0).coerceAtLeast(0) - 1
        }
        if ((waterBypassTicks[entity.player] ?: 0) > 0) return "LEGIT"

        val simulation = simulateAndCheck(entity)
        val currentAirTicks = airTicksMap.getOrDefault(entity.player, 0)
        if (entity.player.isOnGround) {
            airTicksMap[entity.player] = 0
        } else {
            airTicksMap[entity.player] = currentAirTicks + 1
        }
        val airTicks = airTicksMap[entity.player] ?: 0

        val prediction = abs(
            if (abs((entity.lastMotionY - 0.08) * 0.9800000190734863) < 0.005)
                -0.0784000015258789
            else
                (entity.lastMotionY - 0.08) * 0.9800000190734863
        )

        val difference = abs(abs(entity.player.velocity.y / 0.9800000190734863 + 0.08) - prediction)

        return when {
            difference > 0.0001 && !simulation.onGround && airTicks > 15 &&
                !simulation.isTouchingHorizontally && !simulation.isTouchingWater &&
                !simulation.isInLava && !simulation.isOnLadder && !entity.player.isUsingItem && entity.player.velocity.y != 0.0 -> {
                "PredictionA.[Y=${entity.player.velocity.y}, PRE=$prediction, DEL=$difference]"
            }
            entity.player.velocity.y % 0.5 == 0.0 && entity.player.velocity.y % 0.015625 != 0.0 &&
                !simulation.onGround && airTicks > 5 -> {
                "VClip-like movement."
            }
            entity.lastMotionY == entity.player.velocity.y && !simulation.onGround &&
                airTicks > 3 && !simulation.isInsideWeb && !simulation.isOnLadder -> {
                "Stable motion in midair."
            }
            entity.lastMotionY < 0.0 && entity.player.velocity.y > 0.0 && !simulation.onGround &&
                airTicks > 3 && !simulation.isOnLadder && !simulation.isTouchingWater &&
                !simulation.isInLava -> {
                "Air jump."
            }
            simulation.isOnLadder && airTicks > 1 && entity.player.velocity.y > 0.2 -> {
                "Fast Ladder."
            }
            entity.player.velocity.y < -4.0 -> {
                "Fall too fast."
            }
            simulation.isTouchingHorizontally && entity.player.velocity.y > 0.1 &&
                airTicks > 15 && !entity.player.hasStatusEffect(StatusEffects.JUMP_BOOST) &&
                entity.player.hurtTime <= 0 && !simulation.isInsideWeb && !simulation.isOnLadder &&
                !simulation.isTouchingWater -> {
                "Try to climb wall(s) without ladder/vine"
            }
            entity.player.velocity.y + 0.09800000190734881 > 0.001 &&
                airTicks > (if (entity.player.handSwinging) 48 else 18) &&
                entity.player.velocity.y > -0.5 && !simulation.isOnLadder && !simulation.onGround &&
                entity.player.hurtTime < 2 && !simulation.isTouchingWater && !simulation.isInsideWeb && !entity.player.horizontalCollision -> {
                "Too long time in air."
            }
            entity.player.pos.y < entity.player.prevY && !simulation.onGround &&
                ((entity.player.pos.y - entity.player.prevY) % 1.0 == 0.0 ||
                    (entity.player.pos.y - entity.player.prevY) % 1.0 == 0.5 &&
                    entity.player.pos.y - entity.player.prevY != 0.5) -> {
                "Modulo position."
            }
            else -> "LEGIT"
        }
    }


    fun checkExperimentalSprint(entity: ModuleAntiCheat.HackerLivingBase): String {
        val simulation = simulateAndCheck(entity)
        return when {
            entity.player.hungerManager.foodLevel < 6 && simulation.isSprinting -> "Sprint hungrily."
            entity.player.hasStatusEffect(StatusEffects.BLINDNESS) && simulation.isSprinting -> "Sprint blindly."
            (simulation.isTouchingWater || simulation.isInLava) && simulation.isSprinting && !entity.player.isSwimming -> "Sprint in liquid."
            entity.player === mc.player && simulation.isSprinting && !simulation.isJumping && !simulation.onGround && entity.player.forwardSpeed <= 0f -> "Sprint without forward input."
            else -> "LEGIT"
        }
    }


    fun checkGround(entity: ModuleAntiCheat.HackerLivingBase): String {
        val simulation = simulateAndCheck(entity)
        val expectedOnGround = entity.player.pos.y % 0.015625 == 0.0
        return if (entity.player.isOnGround == expectedOnGround && simulation.onGround == expectedOnGround) {
            "LEGIT"
        } else {
            "Spoof on-ground value.(Client=${entity.player.isOnGround}|Probably=${simulation.onGround})"
        }
    }

    fun checkAutoBlock(attacker: ModuleAntiCheat.HackerLivingBase): String {
        val simulation = simulateAndCheck(attacker)
        return if (attacker.player.isBlocking && simulation.isSprinting && attacker.player.handSwingProgress in 0.0f..1.0f) {
            "Swing while blocking"
        } else {
            "LEGIT"
        }
    }
    fun getSpeedFromPos(entity: Entity): Float {
        val deltaX: Double = entity.pos.x - entity.prevX
        val deltaZ: Double = entity.pos.z - entity.prevZ
        return sqrt(deltaX * deltaX + deltaZ * deltaZ).toFloat()
    }

    fun checkPrediction(entity: ModuleAntiCheat.HackerLivingBase): String {
        val simulation = simulateAndCheck(entity, 1)
        val predictedSpeed = simulation.velocity.horizontalLength()
        val actualSpeed =getSpeedFromPos(entity.player)
        return if (actualSpeed > predictedSpeed + 0.022) {
            "PredictionB.(OVER-XZ=${actualSpeed - predictedSpeed})"
        } else {
            "LEGIT"
        }
    }

    fun checkKillAura(attacker: ModuleAntiCheat.HackerLivingBase, entity: LivingEntity): String {
        val simulation = simulateAndCheck(attacker)
        val posY = attacker.player.pos.y
        val intPosY = posY.toInt()
        return when {
            (posY % 0.015625 < 1.0E-8 && posY % 0.015625 > 0.0 ||
                -posY + intPosY > 0.0 && -posY + intPosY < 1.0E-5 ||
                -posY + intPosY == 0.0625) && attacker.player.hurtTime <= 0 -> {
                "Critical-hack-like position."
            }
            !attacker.player.canSee(entity) && simulation.isSprinting -> {
                "Attack through wall(s)."
            }
            else -> "LEGIT"
        }
    }


    fun checkJump(entity: ModuleAntiCheat.HackerLivingBase): String {
        if (entity.player.isInsideWaterOrBubbleColumn || getNearGroundBlock(entity.player, false)?.block is SlimeBlock) {
            jumpBypassTicks[entity.player] = 10
        } else {
            jumpBypassTicks[entity.player] = (jumpBypassTicks[entity.player] ?: 0).coerceAtLeast(0) - 1
        }
        if ((jumpBypassTicks[entity.player] ?: 0) > 0) return "LEGIT"

        val simulation = simulateAndCheck(entity)
        val detectedJump = entity.player.velocity.y
        val possibleJump = getBaseJumpHeight(simulation, entity)

        return when {
            detectedJump > possibleJump -> {
                "Jump too high.(DETECTED=%.2f|POSSIBLE=%.2f)".format(detectedJump, possibleJump)
            }
            abs(detectedJump - 0.41999998688697815) in 0.0..1.0E-4 -> {
                "Modify jump motion."
            }
            else -> "LEGIT"
        }
    }

    fun getRotationsNeededFrom2Entities(attacker: Entity, target: Entity): FloatArray {
        val deltaX: Double = target.pos.x - attacker.pos.x
        val deltaY: Double =
            target.pos.y + target.eyePos.y - (attacker.pos.y + attacker.eyePos.y) - 0.15
        val deltaZ: Double = target.pos.z - attacker.pos.z
        val distance = sqrt(deltaX * deltaX + deltaZ * deltaZ)
        val yaw = Math.toDegrees(-atan2(deltaX, deltaZ)).toFloat()
        val pitch = (-Math.toDegrees(atan2(deltaY, distance))).toFloat()
        return floatArrayOf(yaw, pitch)
    }
    fun checkAimAssist(attacker: ModuleAntiCheat.HackerLivingBase, entity: LivingEntity, maxAimSpeed: Double): String {
        val simulation = simulateAndCheck(attacker)
        val fixedYaw = MathHelper.wrapDegrees(attacker.player.yaw)
        val fixedPrevYaw = MathHelper.wrapDegrees(attacker.player.prevYaw)
        val deltaYaw = abs(fixedPrevYaw - fixedYaw)
        val deltaPitch = abs(attacker.player.pitch - attacker.player.prevPitch)
        val yawAccel = deltaYaw - attacker.lastDYaw
        val pitchAccel = deltaPitch - attacker.lastDPitch
        val dyAccel = abs(attacker.lastYAccel - yawAccel)
        val dpAccel = abs(attacker.lastPAccel - pitchAccel)
        val rotations = getRotationsNeededFrom2Entities(attacker.player, entity)
        val nightXDelta = floatArrayOf(
            abs(rotations[0] - fixedYaw),
            abs(rotations[1] - attacker.player.pitch)
        )


        return when {
            attacker.lastDYaw == deltaYaw && deltaYaw > 25.0f -> "Duplicate yaw change."
            attacker.lastDPitch == deltaPitch && deltaPitch > 25.0f -> "Duplicate pitch change."
            deltaPitch == 0.0f && deltaYaw > 7.825 && abs(attacker.player.pitch) < 60.0f &&
                attacker.player.distanceTo(entity) > 1.0 && simulation.velocity.horizontalLength() > 0.03 -> {
                "Rotate straight."
            }
            attacker.player.distanceTo(entity) > 1.0f && simulation.velocity.horizontalLength() > 0.03 &&
                nightXDelta[0] > 0.0f && nightXDelta[0] < 0.001f && nightXDelta[1] > 0.0f &&
                nightXDelta[1] < 0.001f && maxOf(deltaYaw, deltaPitch) > 5.0f -> {
                "Accurate!?"
            }
            deltaYaw > maxAimSpeed -> "Rotate too fast."
            deltaYaw > 15.0f && abs(deltaPitch) < 0.1 && abs(attacker.player.pitch) < 65.0f -> {
                "Ratio rotation."
            }
            abs(deltaYaw) < 1.0E-7 && deltaPitch > 7.825f -> {
                "Too low yaw change with large pitch change."
            }
            yawAccel > 20.0f && abs(pitchAccel) < 0.05 && abs(attacker.player.pitch) < 60.0f &&
                attacker.player.distanceTo(entity) > 1.0 -> {
                "Large yaw acceleration without pitch acceleration."
            }
            (dyAccel > 35.0f || dpAccel > 35.0f) && attacker.player.distanceTo(entity) > 1.0 -> {
                "Accelerate rotation too quick."
            }
            deltaYaw > 15.0f && abs(floor(deltaYaw.toDouble()) - deltaYaw.toDouble()) < 1.0E-10 &&
                deltaYaw.toInt() % 15 == 0 -> {
                "Prefect rotation step(s)."
            }
            abs(deltaYaw) > 30.0f && abs(deltaPitch) < 0.05 -> {
                "Rotating straight.V2"
            }
            abs(attacker.player.pitch) > 90.0f -> "Where is ur head?"
            else -> "LEGIT"
        }
    }

    @Suppress("UNUSED")
    fun analyzeAim(now: FloatArray, last: FloatArray, lastAccel: FloatArray): Boolean {
        val deltaYaw = now[0]
        val lastDYaw = last[0]
        val lastDPitch = last[1]
        val deltaPitch = abs(now[1])
        val yawAccel = abs(deltaYaw - last[0])
        val pitchAccel = deltaPitch - last[1]
        val dyAccel = abs(lastAccel[0] - yawAccel)
        val dpAccel = abs(lastAccel[1] - pitchAccel)

        return lastDYaw == deltaYaw && deltaYaw > 25.0f ||
            abs(deltaYaw - deltaPitch) < 7.5 ||
            lastDPitch == deltaPitch && deltaPitch > 25.0f ||
            deltaYaw > 15.0f && abs(deltaPitch) < 0.1 ||
            abs(deltaYaw) < 0.6 && deltaPitch > 7.825f ||
            yawAccel > 20.0f && abs(pitchAccel) < 0.05 ||
            abs(dyAccel) > 35.0f || abs(dpAccel) > 35.0f ||
            deltaYaw > 15.0f && abs(floor(deltaYaw.toDouble()) - deltaYaw.toDouble()) < 1.0E-10 &&
            deltaYaw.toInt() % 15 == 0 ||
            abs(deltaYaw) > 30.0f && abs(deltaPitch) < 0.05
    }

    fun checkAutoClick(samples: ArrayList<Int>, m: Int): String {
        val distinct = samples.distinct().size
        val cps = samples.size / (samples.maxOrNull()?.minus(samples.minOrNull() ?: 0)?.div(1000.0) ?: 1.0)
        val difference = abs(round(cps) - cps)
        val deviation = samples.map { (it - samples.average()).pow(2) }.average().let { sqrt(it) }
        val skewness = samples.map { (it - samples.average()).pow(3) }.average() / deviation.pow(3)
        val variance = samples.map { (it - samples.average()).pow(2) }.average()
        val scaled = variance / 1000.0
        val kurtosis = samples.map { (it - samples.average()).pow(4) }.average() / deviation.pow(4)

        return when {
            distinct < 13 -> "Not enough distinct values.(AVERAGE=$cps|DISTINCT=$distinct)"
            cps > m -> "Left clicking too quick.(AVERAGE=$cps|POSSIBLE=$m)"
            deviation < 167.0 -> "Too low standard deviation.(AVERAGE=$cps|DEVIATION=$deviation)"
            difference < 1.0E-5 -> "Rounded cps.(AVERAGE=$cps)"
            skewness < -0.01 -> "Too low skewness values.(AVERAGE=$cps|SKEWNESS=$skewness)"
            scaled < 28.2 -> "Too low variance.(AVERAGE=$cps|VARIANCE=$scaled)"
            kurtosis / 1000.0 < 41.78 -> "Too low kurtosis.(AVERAGE=$cps|KURTOSIS=${kurtosis / 1000.0})"
            else -> "LEGIT"
        }
    }

    fun checkVelocity(entity: ModuleAntiCheat.HackerLivingBase): String {
        val simulation = simulateAndCheck(entity)
        val motion = sqrt(simulation.velocity.x.pow(2) + simulation.velocity.z.pow(2)) *
            0.98.pow(floor((System.currentTimeMillis() - entity.bypassTime) / 50.0))
        val percent = sqrt(entity.player.velocity.x.pow(2) + entity.player.velocity.z.pow(2)) / motion
        return if (percent < 0.30000001192092896 && entity.player.hurtTime >= 8 &&
            !simulation.isTouchingHorizontally && !simulation.onGround) {
            "Ignore server velocity."
        } else {
            "LEGIT"
        }
    }

    fun checkReach(attacker: ModuleAntiCheat.HackerLivingBase, entity: LivingEntity, sprintRange: Double, normalRange: Double): String {
        val simulation = simulateAndCheck(attacker)
        var pingFix = 0.0f
        if (!mc.isInSingleplayer) {
            pingFix = mc.networkHandler?.getPlayerListEntry(attacker.player.uuid)?.latency?.toFloat()?.div(1000.0f)?.times(0.5f) ?: 0.0f
        }
        val distance = attacker.player.distanceTo(entity)
        val maxRange = if (simulation.isSprinting) sprintRange else normalRange
        return if (distance > maxRange + pingFix) {
            "Modify interact distance.(AVERAGE=$distance)"
        } else {
            "LEGIT"
        }
    }
    fun isNotValidToCheck(entity: Entity, selfCheck: Boolean, botCheck: Boolean, otherPlayersCheck: Boolean): Boolean {
        if (entity !is PlayerEntity) return true
        if (entity.isCreative || entity.isInvulnerable || entity.isSpectator || entity.abilities.allowFlying) return true
        if (botCheck && ModuleAntiBot.isBot(entity)) return true
        if (!selfCheck && entity == mc.player) return true
        if (!otherPlayersCheck && entity != mc.player) return true
        return false
    }


    fun flagMessage(description: String, t1: String, entity: ModuleAntiCheat.HackerLivingBase, mainType: String) {
        val type = "$mainType / $t1"
        val message = "${Formatting.RESET}${Formatting.GRAY}${entity.player.name.string}${Formatting.WHITE} failed ${Formatting.AQUA}$type${Formatting.WHITE} (vl:${entity.vl}.0)${Formatting.GRAY}: $description"

        chat(message)
    }

    fun flagEntity(entity: ModuleAntiCheat.HackerLivingBase, description: String, type: String) {
        entity.vl++
        when (type) {
            "Exploit" -> flagMessage(description, "Exploit", entity, "WORLD")
            "AutoClick" -> flagMessage(description, "AutoClick", entity, "COMBAT")
            "Celerity" -> flagMessage(description, "Celerity", entity, "MOVEMENT")
            "NoSlowDown" -> flagMessage(description, "NoSlow", entity, "MOVEMENT")
            "Flight" -> flagMessage(description, "Fly", entity, "MOVEMENT")
            "Prediction" -> flagMessage(description, "Prediction", entity, "MOVEMENT")
            "Jump" -> flagMessage(description, "Jump", entity, "MOVEMENT")
            "Velocity" -> flagMessage(description, "Velocity", entity, "MOVEMENT")
            "Sprint" -> flagMessage(description, "Sprint", entity, "MOVEMENT")
            "Aura" -> flagMessage(description, "Aura", entity, "COMBAT")
            "AimAssist" -> flagMessage(description, "AimAssist", entity, "COMBAT")
            "AutoBlock" -> flagMessage(description, "AutoBlock", entity, "COMBAT")
            "InteractDistance" -> flagMessage(description, "Reach", entity, "COMBAT")
        }
    }

    fun getNearGroundBlock(entity: Entity, strict: Boolean): BlockState? {
        var y = entity.pos.y
        while (y > 1.0) {
            val pos = BlockPos.ofFloored(entity.pos.x, y, entity.pos.z)
            val blockState = pos.getState()
            if (blockState?.isAir == false) return blockState
            if (!strict) {
                val positions = listOf(
                    pos.offset(Direction.NORTH), pos.offset(Direction.SOUTH),
                    pos.offset(Direction.EAST), pos.offset(Direction.WEST),
                    pos.offset(Direction.NORTH, 1).offset(Direction.EAST, -1),
                    pos.offset(Direction.NORTH, 1).offset(Direction.EAST, 1),
                    pos.offset(Direction.SOUTH, 1).offset(Direction.EAST, 1),
                    pos.offset(Direction.SOUTH, 1).offset(Direction.EAST, -1)
                )
                positions.forEach { if (it.getState()?.isAir == false) return it.getState() }
            }
            y -= 0.5
        }
        return null
    }
    fun checkHead(pos: BlockPos): Boolean {
        return pos.offset(Direction.UP, 1).getState()?.isAir == false ||
            pos.offset(Direction.UP, 2).getState()?.isAir == false ||
            pos.offset(Direction.UP, 3).getState()?.isAir == false
    }
}
