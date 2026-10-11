#version 330
#extension GL_ARB_separate_shader_objects : require

uniform sampler2D InSampler;

layout(std140) uniform PropwashActionCam {
    vec4 WarpX;
    vec4 WarpY;
    vec4 WarpZ;
    vec4 Tangents;
    vec4 Sizes;
    vec4 Look;
};

layout(location = 0) in vec2 texCoord;

layout(location = 0) out vec4 fragColor;

uint hashU(uint v) {
    uint state = v * 747796405u + 2891336453u;
    uint word = ((state >> ((state >> 28u) + 4u)) ^ state) * 277803737u;
    return (word >> 22u) ^ word;
}

float hash3(float a, float b, float c) {
    uint h = hashU(uint(int(a)) ^ hashU(uint(int(b)) ^ hashU(uint(int(c)))));
    return float(h) * (1.0 / 4294967295.0);
}

vec3 fetch(vec2 uv) {
    return texture(InSampler, clamp(uv, vec2(0.0), vec2(1.0))).rgb;
}

vec3 catmullRom(vec2 uv, vec2 size) {
    vec2 position = uv * size;
    vec2 center = floor(position - 0.5) + 0.5;
    vec2 f = position - center;
    vec2 w0 = f * (-0.5 + f * (1.0 - 0.5 * f));
    vec2 w1 = 1.0 + f * f * (-2.5 + 1.5 * f);
    vec2 w2 = f * (0.5 + f * (2.0 - 1.5 * f));
    vec2 w3 = f * f * (-0.5 + 0.5 * f);
    vec2 w12 = w1 + w2;
    vec2 p0 = (center - 1.0) / size;
    vec2 p12 = (center + w2 / w12) / size;
    vec2 p3 = (center + 2.0) / size;
    vec3 result = fetch(vec2(p0.x, p0.y)) * w0.x * w0.y + fetch(vec2(p12.x, p0.y)) * w12.x * w0.y + fetch(vec2(p3.x, p0.y)) * w3.x * w0.y
            + fetch(vec2(p0.x, p12.y)) * w0.x * w12.y + fetch(vec2(p12.x, p12.y)) * w12.x * w12.y + fetch(vec2(p3.x, p12.y)) * w3.x * w12.y
            + fetch(vec2(p0.x, p3.y)) * w0.x * w3.y + fetch(vec2(p12.x, p3.y)) * w12.x * w3.y + fetch(vec2(p3.x, p3.y)) * w3.x * w3.y;
    return max(result, vec3(0.0));
}

void main() {
    vec2 ndc = texCoord * 2.0 - 1.0;
    vec3 ray = vec3(ndc.x * Tangents.x, ndc.y * Tangents.y, -1.0);
    vec3 source = WarpX.xyz * ray.x + WarpY.xyz * ray.y + WarpZ.xyz * ray.z;
    if (source.z > -1.0e-4) {
        fragColor = vec4(0.0, 0.0, 0.0, 1.0);
        return;
    }
    vec2 projected = source.xy / -source.z;
    vec2 uv = vec2(projected.x / Tangents.z, projected.y / Tangents.w) * 0.5 + 0.5;
    vec2 texel = 1.0 / Sizes.zw;
    float ratio = (Tangents.x * Sizes.z) / (Tangents.z * Sizes.x);
    float footprint = max(1.0, ratio);
    vec2 d = texel * footprint * 0.25;
    vec3 color;
    if (ratio < 1.0) {
        color = catmullRom(uv, Sizes.zw);
    } else {
        color = (fetch(uv + vec2(-d.x, -d.y)) + fetch(uv + vec2(d.x, -d.y)) + fetch(uv + vec2(-d.x, d.y)) + fetch(uv + vec2(d.x, d.y))) * 0.25;
    }
    vec2 w = texel * footprint * 0.9;
    vec3 blur = (fetch(uv + vec2(w.x, 0.0)) + fetch(uv - vec2(w.x, 0.0)) + fetch(uv + vec2(0.0, w.y)) + fetch(uv - vec2(0.0, w.y))) * 0.25;
    color = mix(blur, color, 1.0 + Look.x);
    float luma = dot(clamp(color, 0.0, 1.0), vec3(0.2126, 0.7152, 0.0722));
    float dark = 1.0 - smoothstep(0.0, 0.2, luma);
    vec2 cell = floor(texCoord * Sizes.xy);
    float grain = hash3(cell.x, cell.y, Look.z) - 0.5;
    vec3 chroma = vec3(hash3(cell.x, cell.y, Look.z + 11.0), hash3(cell.x, cell.y, Look.z + 23.0), hash3(cell.x, cell.y, Look.z + 37.0)) - 0.5;
    color += (vec3(grain) + chroma * 0.1) * Look.y * dark;
    fragColor = vec4(clamp(color, 0.0, 1.0), 1.0);
}
