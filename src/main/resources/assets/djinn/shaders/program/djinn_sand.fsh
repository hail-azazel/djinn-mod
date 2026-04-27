#version 150

uniform sampler2D DiffuseSampler;
uniform float GameTime;

in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec2 heat = vec2(
        sin((texCoord.y + GameTime * 5.0) * 38.0) + sin((texCoord.y - GameTime * 2.0) * 71.0),
        cos((texCoord.x - GameTime * 4.0) * 31.0)
    ) * 0.0045;
    vec4 color = texture(DiffuseSampler, texCoord + heat);
    float sun = smoothstep(0.62, 0.0, distance(texCoord, vec2(0.5, 0.2)));
    float flare = pow(max(0.0, 1.0 - abs(texCoord.y - 0.35) * 4.0), 3.0) * 0.22;
    vec3 amber = vec3(1.0, 0.78, 0.34);
    vec3 lapis = vec3(0.17, 0.28, 0.78);
    vec3 lit = mix(color.rgb, amber, 0.24 + sun * 0.45);
    lit += amber * (sun * 1.2 + flare);
    lit = mix(lit, lapis, 0.04 * sin((texCoord.x + GameTime) * 24.0));
    fragColor = vec4(lit, color.a);
}
