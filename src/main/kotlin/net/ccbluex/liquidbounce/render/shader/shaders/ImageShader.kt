package net.ccbluex.liquidbounce.render.shader.shaders

import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.ccbluex.liquidbounce.render.shader.Shader
import net.ccbluex.liquidbounce.render.shader.UniformProvider
import net.ccbluex.liquidbounce.utils.io.resourceToString
import org.joml.Vector3f
import org.lwjgl.opengl.GL20

object ImageShaderData {
    var globalTime = 0f
    var glowMultiplier = 1f
    var useImage = 1
    var camRight = Vector3f(1f, 0f, 0f)
    var camUp = Vector3f(0f, 1f, 0f)
    var projectionView: FloatArray = FloatArray(16)
}

object ImageShader : Shader(
    resourceToString("/resources/liquidbounce/shaders/fireflies_instanced.vert"),
    resourceToString("/resources/liquidbounce/shaders/fireflies_instanced.frag"),
    arrayOf(
        UniformProvider("tex") { pointer -> GL20.glUniform1i(pointer, 0) },
        UniformProvider("globalTime") { pointer ->
            GL20.glUniform1f(pointer, ImageShaderData.globalTime)
        },
        UniformProvider("glowMultiplier") { pointer ->
            GL20.glUniform1f(pointer, ImageShaderData.glowMultiplier)
        },
        UniformProvider("useImage") { pointer ->
            GL20.glUniform1i(pointer, ImageShaderData.useImage)
        },
        UniformProvider("camRight") { pointer ->
            GL20.glUniform3f(
                pointer,
                ImageShaderData.camRight.x,
                ImageShaderData.camRight.y,
                ImageShaderData.camRight.z
            )
        },
        UniformProvider("camUp") { pointer ->
            GL20.glUniform3f(
                pointer,
                ImageShaderData.camUp.x,
                ImageShaderData.camUp.y,
                ImageShaderData.camUp.z
            )
        },
        UniformProvider("projectionView") { pointer ->
            GL20.glUniformMatrix4fv(pointer, false, ImageShaderData.projectionView)
        }
    )
)
