#version 410 core

in vec2 vUV;
in vec4 vColor;
flat in float vPhase;
in float vPulseMul;

out vec4 FragColor;

uniform sampler2D tex;
uniform int useImage;

void main() {
    vec4 samp = textureLod(tex, vUV, 0.0);

    vec3 rgb = samp.rgb * vPulseMul * vColor.rgb;
    float alpha = samp.a * vColor.a;
    FragColor = vec4(rgb, alpha);
}
