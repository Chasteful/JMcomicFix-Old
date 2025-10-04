#version 410 core

layout(location = 0) in vec2 aCorner;    // (-0.5,-0.5) .. (0.5,0.5)
layout(location = 1) in vec2 aUV;

layout(location = 2) in vec3 iPosition;  // per-instance
layout(location = 3) in float iSize;     // per-instance (world size)
layout(location = 4) in vec4 iColor;     // per-instance RGBA (0..1)
layout(location = 5) in float iPhase;    // per-instance

uniform mat4 projectionView; // modelViewProjection
uniform vec3 camRight;
uniform vec3 camUp;
uniform float globalTime;
uniform float glowMultiplier;

out vec2 vUV;
out vec4 vColor;
flat out float vPhase;
out float vPulseMul;

void main() {
    vec3 right = camRight * aCorner.x * iSize;
    vec3 up    = camUp    * aCorner.y * iSize;
    vec3 worldPos = iPosition + right + up;
    gl_Position = projectionView * vec4(worldPos, 1.0);
    vUV = aUV;
    vColor = iColor;
    vPhase = iPhase;

    vPulseMul = 0.75 + 0.25 * sin(globalTime * 0.2 + vPhase) * glowMultiplier;
}
