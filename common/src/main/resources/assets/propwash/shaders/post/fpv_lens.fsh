#version 330
#extension GL_ARB_separate_shader_objects : require

uniform sampler2D InSampler;

layout(std140) uniform PropwashLens {
    vec4 Size;
    vec4 Area;
    vec4 Lens;
    vec4 Look;
};

layout(location = 0) in vec2 texCoord;

layout(location = 0) out vec4 fragColor;

const float MAX_THETA = 1.5;
const int MAX_TAPS = 4;
const float EDGE_MIN = 0.0312;
const float EDGE_SHARE = 0.125;
const float EDGE_SPAN = 8.0;

vec3 catmullRom(vec2 uv) {
    vec2 position = uv * Size.xy;
    vec2 centre = floor(position - 0.5) + 0.5;
    vec2 f = position - centre;
    vec2 w0 = f * (-0.5 + f * (1.0 - 0.5 * f));
    vec2 w1 = 1.0 + f * f * (-2.5 + 1.5 * f);
    vec2 w2 = f * (0.5 + f * (2.0 - 1.5 * f));
    vec2 w3 = f * f * (-0.5 + 0.5 * f);
    vec2 w12 = w1 + w2;
    vec2 p0 = (centre - 1.0) * Size.zw;
    vec2 p12 = (centre + w2 / w12) * Size.zw;
    vec2 p3 = (centre + 2.0) * Size.zw;
    vec3 colour = texture(InSampler, vec2(p0.x, p0.y)).rgb * w0.x * w0.y
            + texture(InSampler, vec2(p12.x, p0.y)).rgb * w12.x * w0.y
            + texture(InSampler, vec2(p3.x, p0.y)).rgb * w3.x * w0.y
            + texture(InSampler, vec2(p0.x, p12.y)).rgb * w0.x * w12.y
            + texture(InSampler, vec2(p12.x, p12.y)).rgb * w12.x * w12.y
            + texture(InSampler, vec2(p3.x, p12.y)).rgb * w3.x * w12.y
            + texture(InSampler, vec2(p0.x, p3.y)).rgb * w0.x * w3.y
            + texture(InSampler, vec2(p12.x, p3.y)).rgb * w12.x * w3.y
            + texture(InSampler, vec2(p3.x, p3.y)).rgb * w3.x * w3.y;
    return max(colour, vec3(0.0));
}

float unmapRadius(float radius, float bend) {
    if (abs(bend) < 1.0e-6) {
        return radius;
    }
    if (bend > 0.0) {
        float b = sqrt(bend);
        return atan(b * radius) / b;
    }
    float a = sqrt(-bend);
    float x = a * radius;
    if (x >= 0.999999) {
        return MAX_THETA;
    }
    return atanh(x) / a;
}

float mapDerivative(float theta, float bend) {
    if (abs(bend) < 1.0e-6) {
        return 1.0;
    }
    if (bend > 0.0) {
        float c = cos(sqrt(bend) * theta);
        return 1.0 / (c * c);
    }
    float t = tanh(sqrt(-bend) * theta);
    return 1.0 - t * t;
}

float luma(vec3 c) {
    return dot(c, vec3(0.299, 0.587, 0.114));
}

vec3 upscale(vec2 uv) {
    vec2 texel = Size.zw;
    vec3 nw = texture(InSampler, uv + vec2(-0.5, -0.5) * texel).rgb;
    vec3 ne = texture(InSampler, uv + vec2(0.5, -0.5) * texel).rgb;
    vec3 sw = texture(InSampler, uv + vec2(-0.5, 0.5) * texel).rgb;
    vec3 se = texture(InSampler, uv + vec2(0.5, 0.5) * texel).rgb;
    vec3 centre = catmullRom(uv);
    float lnw = luma(nw);
    float lne = luma(ne);
    float lsw = luma(sw);
    float lse = luma(se);
    float lm = luma(centre);
    float lowest = min(lm, min(min(lnw, lne), min(lsw, lse)));
    float highest = max(lm, max(max(lnw, lne), max(lsw, lse)));
    if (highest - lowest < max(EDGE_MIN, highest * EDGE_SHARE)) {
        return centre;
    }
    vec2 across = vec2(-((lnw + lne) - (lsw + lse)), (lnw + lsw) - (lne + lse));
    float reduce = max((lnw + lne + lsw + lse) * 0.03125, 1.0 / 128.0);
    float scale = 1.0 / (min(abs(across.x), abs(across.y)) + reduce);
    vec2 along = clamp(across * scale, vec2(-EDGE_SPAN), vec2(EDGE_SPAN)) * texel;
    vec3 inner = 0.5 * (texture(InSampler, uv + along * (1.0 / 3.0 - 0.5)).rgb + texture(InSampler, uv + along * (2.0 / 3.0 - 0.5)).rgb);
    vec3 outer = inner * 0.5 + 0.25 * (texture(InSampler, uv - along * 0.5).rgb + texture(InSampler, uv + along * 0.5).rgb);
    float lo = luma(outer);
    return lo < lowest || lo > highest ? inner : outer;
}

vec3 radialBox(vec2 uv, vec2 direction, float footprint) {
    int taps = int(min(float(MAX_TAPS), ceil(footprint)));
    vec2 stride = direction * (footprint / float(taps)) * Size.zw;
    vec3 sum = vec3(0.0);
    for (int i = 0; i < MAX_TAPS; i++) {
        if (i >= taps) {
            break;
        }
        sum += texture(InSampler, uv + (float(i) - 0.5 * float(taps - 1)) * stride).rgb;
    }
    return sum / float(taps);
}

void main() {
    vec2 pixel = texCoord * Size.xy;
    vec2 low = Area.xy * Size.xy;
    vec2 high = (Area.xy + Area.zw) * Size.xy;
    if (pixel.x < low.x || pixel.y < low.y || pixel.x > high.x || pixel.y > high.y) {
        fragColor = vec4(0.0, 0.0, 0.0, 1.0);
        return;
    }
    vec2 offset = pixel - (Area.xy + 0.5 * Area.zw) * Size.xy;
    float radius = length(offset);
    float focal = Lens.x;
    float edge = clamp((Lens.y - radius) / max(Look.x, 1.0e-3) + 0.5, 0.0, 1.0);
    if (edge <= 0.0) {
        fragColor = vec4(0.0, 0.0, 0.0, 1.0);
        return;
    }
    float bend = Look.y;
    float theta = min(unmapRadius(radius / focal, bend), MAX_THETA);
    vec2 direction = radius > 1.0e-4 ? offset / radius : vec2(1.0, 0.0);
    float slope = tan(theta);
    vec2 uv = 0.5 + 0.5 * direction * slope / Lens.zw;
    float scale = 0.5 * Size.x / Lens.z;
    float radial = scale * (1.0 + slope * slope) / (focal * mapDerivative(theta, bend));
    vec3 colour;
    if (radial <= 0.9) {
        colour = upscale(uv);
    } else if (radial >= 1.6) {
        colour = radialBox(uv, direction, radial);
    } else {
        colour = mix(upscale(uv), radialBox(uv, direction, max(radial, 1.0)), smoothstep(0.9, 1.6, radial));
    }
    fragColor = vec4(colour * edge, 1.0);
}
