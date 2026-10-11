#version 330
#extension GL_ARB_separate_shader_objects : require

uniform sampler2D InSampler;
uniform sampler2D DepthSampler;

layout(std140) uniform PropwashHeadlight {
    mat4 InvProj;
    vec4 Light;
    vec4 Cone;
    vec4 Range;
};

layout(location = 0) in vec2 texCoord;

layout(location = 0) out vec4 fragColor;

const float HUE_TRUST_FROM = 0.03;
const float HUE_TRUST_TO = 0.14;
const float HOTSPOT_COS = 0.992546;
const float HOTSPOT_BOOST = 1.1;
const vec3 WARM = vec3(1.0, 0.9, 0.74);
const float WARM_EDGE = 0.0;
const float WARM_CORE = 0.5;
const float MIN_DEPTH = 1.0e-6;
const float MIN_W = 1.0e-9;
const float MAX_COORD = 1.0e7;
const float MAX_GAIN = 1000.0;

float luma(vec3 c) {
    return dot(c, vec3(0.299, 0.587, 0.114));
}

bool finite3(vec3 v) {
    return all(lessThan(abs(v), vec3(MAX_COORD))) && !any(isnan(v)) && !any(isinf(v));
}

vec4 viewPoint(vec2 uv, float depth) {
    float z = Cone.w > 0.5 ? depth : depth * 2.0 - 1.0;
    return InvProj * vec4(uv * 2.0 - 1.0, z, 1.0);
}

void main() {
    vec3 col = texture(InSampler, texCoord).rgb;
    float depth = texture(DepthSampler, texCoord).r;
    vec4 p = viewPoint(texCoord, clamp(depth, MIN_DEPTH, 1.0));
    bool solid = abs(p.w) > MIN_W;
    vec3 pos = solid ? p.xyz / p.w : vec3(0.0);
    solid = solid && finite3(pos);
    pos = solid ? pos : vec3(0.0);
    vec3 normal = cross(dFdx(pos), dFdy(pos));
    bool valid = solid && depth > 0.0 && depth < 1.0 && Light.w > 0.0 && Light.w < MAX_GAIN;
    if (!valid) {
        fragColor = vec4(col, 1.0);
        return;
    }
    float dist = length(pos);
    vec3 dir = pos / max(dist, 1.0e-4);
    float edge = smoothstep(Cone.y, Cone.x, -dir.z);
    float cone = Cone.z + (1.0 - Cone.z) * edge * edge;
    float core = smoothstep(Cone.x, HOTSPOT_COS, -dir.z);
    cone *= 1.0 + HOTSPOT_BOOST * core;
    vec3 tint = mix(vec3(1.0), WARM, mix(WARM_EDGE, WARM_CORE, core));
    float ratio = dist / max(Range.y, 1.0e-3);
    float falloff = 1.0 / (1.0 + ratio * ratio);
    float cut = 1.0 - smoothstep(Range.x * 0.7, Range.x, dist);
    float normalLength = length(normal);
    bool oriented = normalLength > 1.0e-6 && !isinf(normalLength) && !isnan(normalLength);
    float facing = oriented ? clamp(abs(dot(normal / normalLength, dir)), 0.0, 1.0) : 1.0;
    float lambert = mix(1.0, facing, Range.z);
    float y = luma(col);
    float headroom = 1.0 - smoothstep(0.05, 0.85, y);
    float reach = Light.w * cone * falloff * cut * lambert;
    float lit = reach * headroom;
    vec3 base = mix(vec3(y), col, smoothstep(HUE_TRUST_FROM, HUE_TRUST_TO, y));
    vec3 boosted = col + (base * lit + vec3(Range.w * reach)) * Light.rgb * tint;
    bool sane = finite3(boosted) && all(greaterThanEqual(boosted, col));
    fragColor = vec4(sane ? clamp(boosted, 0.0, 1.0) : col, 1.0);
}
