#version 330
#extension GL_ARB_separate_shader_objects : require

uniform sampler2D InSampler;
uniform sampler2D HistorySampler;

layout(std140) uniform PropwashVideo {
    vec4 Frame;
    vec4 Mode;
    vec4 Look;
    vec4 Glitch;
    vec4 Colour;
    vec4 Sync;
    vec4 Extra;
    vec4 Digital;
    vec4 Area;
};

layout(location = 0) in vec2 texCoord;

layout(location = 0) out vec4 fragColor;

const float ANALOG_LINES = 480.0;
const float BLANKING = 0.08;

vec2 areaCoord;

vec2 screenCoord(vec2 local) {
    return Area.xy + clamp(local, 0.0, 1.0) * Area.zw;
}

vec4 sampleIn(vec2 local) {
    return texture(InSampler, screenCoord(local));
}

vec4 sampleHistory(vec2 local) {
    return texture(HistorySampler, screenCoord(local));
}

uint hashU(uint v) {
    uint state = v * 747796405u + 2891336453u;
    uint word = ((state >> ((state >> 28u) + 4u)) ^ state) * 277803737u;
    return (word >> 22u) ^ word;
}

float hash3(float a, float b, float c) {
    uint h = hashU(uint(int(a)) ^ hashU(uint(int(b)) ^ hashU(uint(int(c)))));
    return float(h) * (1.0 / 4294967295.0);
}

float luma(vec3 c) {
    return dot(c, vec3(0.299, 0.587, 0.114));
}

float valueNoise(float x, float salt) {
    float i = floor(x);
    float f = x - i;
    float u = f * f * (3.0 - 2.0 * f);
    return mix(hash3(i, salt, 3.0), hash3(i + 1.0, salt, 3.0), u);
}

vec3 snowColour(vec2 cell, float seed, float contrast, float tint) {
    float n = 0.5 + (hash3(cell.x, cell.y, seed) - 0.5) * contrast;
    vec3 speck = vec3(
        hash3(cell.x, cell.y, seed + 101.0),
        hash3(cell.x, cell.y, seed + 202.0),
        hash3(cell.x, cell.y, seed + 303.0)) - 0.5;
    return clamp(vec3(n) + speck * tint * contrast, 0.0, 1.0);
}

vec2 analogRoll(vec2 uv, inout float seam) {
    if (Extra.x != 0.0) {
        uv.y = fract(uv.y + Extra.x);
        seam = smoothstep(0.0, 0.025, min(uv.y, 1.0 - uv.y));
    }
    return uv;
}

vec2 analogSyncShift(vec2 uv, float line, inout float blank, inout float smear) {
    float shift = Sync.y;
    if (shift == 0.0 || uv.y < Sync.z || uv.y > Sync.w) {
        return uv;
    }
    float span = max(Sync.w - Sync.z, 1.0e-3);
    float rel = (uv.y - Sync.z) / span;
    float taper = smoothstep(0.0, 0.14, rel) * smoothstep(1.0, 0.86, rel);
    float event = floor(Sync.z * 997.0 + Sync.w * 131.0);
    float group = floor(line / 14.0);
    float gap = span > 0.12 ? step(hash3(group, event, 41.0), 0.24) : 0.0;
    float ragged = (valueNoise(line * 0.21 + Frame.y * 0.37, event + 43.0) - 0.5) * 0.7 + (hash3(line, Frame.y, 47.0) - 0.5) * 0.18;
    float s = shift * mix(0.3, 1.0, taper) * (1.0 + ragged) * (1.0 - gap);
    float x = uv.x - s;
    if (x < 0.0) {
        blank = step(-BLANKING, x);
        smear = 1.0 - clamp(-x / BLANKING, 0.0, 1.0);
        x += 1.0 + BLANKING;
    } else if (x > 1.0) {
        blank = step(x, 1.0 + BLANKING);
        smear = 1.0 - clamp((x - 1.0) / BLANKING, 0.0, 1.0);
        x -= 1.0 + BLANKING;
    }
    uv.x = x;
    return uv;
}

vec2 analogTornLines(vec2 uv, float line, inout float streak) {
    if (Glitch.x <= 0.0) {
        return uv;
    }
    for (int k = 0; k < 3; k++) {
        float index = float(k);
        float start = floor(hash3(Glitch.y, index, 5.0) * ANALOG_LINES);
        float height = 1.0 + floor(hash3(Glitch.y, index, 6.0) * 4.0);
        if (line >= start && line < start + height) {
            uv.x += (hash3(Glitch.y, index, 7.0) - 0.5) * 0.16 * Glitch.x;
            streak = max(streak, hash3(Glitch.y, index, 8.0) * Glitch.x);
        }
    }
    return uv;
}

vec3 analogSoftLook(vec2 uv, vec2 res, float scale) {
    float bleed = Look.x * scale / res.x;
    vec3 c0 = sampleIn(uv).rgb;
    vec3 c1 = sampleIn(uv - vec2(bleed, 0.0)).rgb;
    vec3 c2 = sampleIn(uv - vec2(bleed * 2.0, 0.0)).rgb;
    vec3 chroma = (c0 + c1 + c2) / 3.0;
    float y = mix(luma(c0), luma(c1), 0.3);
    return vec3(y) + (chroma - vec3(luma(chroma)));
}

float analogMonoContrast(float y, float keep) {
    float mono = clamp(1.0 - keep, 0.0, 1.0);
    float stretched = clamp((y - 0.46) * 1.28 + 0.5, 0.0, 1.0);
    float curve = stretched * stretched * (3.0 - 2.0 * stretched);
    return mix(y, mix(stretched, curve, 0.35), mono);
}

float analogColourKeep(float line) {
    float keep = 1.0 - Colour.x;
    if (Colour.y > 0.0) {
        float y = line / ANALOG_LINES;
        float n = 0.7 * valueNoise(y * 7.0 + Colour.z, 19.0) + 0.3 * valueNoise(y * 23.0 - Colour.z * 1.7, 29.0);
        float lost = 1.0 - smoothstep(Colour.y - 0.05, Colour.y + 0.05, n);
        keep *= 1.0 - lost * smoothstep(0.0, 0.08, Colour.y);
    }
    return keep;
}

vec3 analogColourLoss(vec3 col, float keep, vec2 grain) {
    float y = luma(col);
    vec3 chroma = col - vec3(y);
    y = analogMonoContrast(y, keep);
    if (Colour.w > 0.0) {
        vec3 speck = vec3(
            hash3(grain.x, grain.y, Frame.y + 61.0),
            hash3(grain.x, grain.y, Frame.y + 62.0),
            hash3(grain.x, grain.y, Frame.y + 63.0)) - 0.5;
        chroma += (speck - vec3(luma(speck))) * Colour.w * 0.5;
    }
    return vec3(y) + chroma * keep;
}

vec3 analogFeed(vec2 res, float scale, vec2 grain, inout float seam, out float keep) {
    vec2 uv = analogRoll(areaCoord, seam);
    float line = floor(uv.y * ANALOG_LINES);
    float blank = 0.0;
    float smear = 0.0;
    float streak = 0.0;
    uv = analogSyncShift(uv, line, blank, smear);
    uv = analogTornLines(uv, line, streak);
    uv.x += (hash3(line, Frame.y, 11.0) - 0.5) * 2.0 * Glitch.w;
    vec3 col = analogSoftLook(uv, res, scale);
    col = mix(col, vec3(1.0), streak * 0.35);
    keep = analogColourKeep(line);
    col = analogColourLoss(col, keep, grain);
    vec3 blanking = vec3(0.2 + 0.08 * hash3(line, Frame.y, 49.0)) + vec3(luma(col)) * 0.35 * smear;
    return mix(col, blanking, blank);
}

vec3 analogSnow(vec3 col, vec2 grain, float keep) {
    float amount = Sync.x;
    if (amount <= 0.0) {
        return col;
    }
    vec3 n = snowColour(grain, Frame.y, Look.w, 0.12 * keep);
    float snow = smoothstep(0.14, 0.45, amount);
    col = mix(col, vec3(0.5), amount * 0.12 * snow);
    return mix(col, n, amount * 0.6 * snow) + (n - 0.5) * amount * mix(0.9, 0.4, snow);
}

vec3 analogSparkles(vec3 col, vec2 res, float scale) {
    if (Glitch.z <= 0.0) {
        return col;
    }
    float line = floor(areaCoord.y * ANALOG_LINES);
    float segment = max(8.0, 36.0 * scale);
    float px = areaCoord.x * res.x / segment;
    float cell = floor(px);
    if (hash3(cell, line, Frame.y + 71.0) < Glitch.z * 0.012) {
        float start = hash3(cell, line, Frame.y + 72.0) * 0.5;
        float span = 0.2 + 0.6 * hash3(cell, line, Frame.y + 73.0);
        float d = (fract(px) - start) / span;
        if (d >= 0.0 && d <= 1.0) {
            float white = step(0.25, hash3(cell, line, Frame.y + 74.0));
            col = mix(col, vec3(white), (1.0 - d * d) * 0.9);
        }
    }
    return col;
}

vec3 analogScreen(vec3 col, vec2 res, float scale) {
    if (Look.y > 0.0) {
        col *= 1.0 - Look.y * (0.5 + 0.5 * cos(areaCoord.y * res.y * 3.14159265 / max(1.0, scale)));
    }
    if (Look.z > 0.0) {
        vec2 d = (areaCoord - 0.5) * vec2(res.x / res.y, 1.0);
        col *= 1.0 - Look.z * smoothstep(0.35, 1.05, length(d));
    }
    return col;
}

vec3 feedStatic(vec3 col, vec2 grain) {
    if (Mode.z <= 0.0) {
        return col;
    }
    vec3 lost = Mode.w > 0.5 ? vec3(0.0) : snowColour(grain, Frame.y, Look.w, 0.12) * 0.9;
    return mix(col, lost, Mode.z);
}

vec3 digitalSample(vec2 uv, vec2 res) {
    float s = Extra.z;
    if (s >= 0.999) {
        return sampleIn(uv).rgb;
    }
    vec2 low = max(floor(res * s), vec2(1.0));
    vec2 p = uv * low - 0.5;
    vec2 i = floor(p);
    vec2 f = p - i;
    vec3 a = sampleIn((i + vec2(0.5, 0.5)) / low).rgb;
    vec3 b = sampleIn((i + vec2(1.5, 0.5)) / low).rgb;
    vec3 c = sampleIn((i + vec2(0.5, 1.5)) / low).rgb;
    vec3 d = sampleIn((i + vec2(1.5, 1.5)) / low).rgb;
    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}

vec3 digitalBlur(vec3 sharp, vec2 uv, vec2 res, float scale) {
    float radius = Digital.z * scale;
    if (radius <= 0.25) {
        return sharp;
    }
    vec2 o = radius / res;
    vec3 sum = sharp * 0.2;
    sum += sampleIn(uv + vec2(o.x, 0.0)).rgb * 0.1;
    sum += sampleIn(uv - vec2(o.x, 0.0)).rgb * 0.1;
    sum += sampleIn(uv + vec2(0.0, o.y)).rgb * 0.1;
    sum += sampleIn(uv - vec2(0.0, o.y)).rgb * 0.1;
    sum += sampleIn(uv + o).rgb * 0.1;
    sum += sampleIn(uv - o).rgb * 0.1;
    sum += sampleIn(uv + vec2(o.x, -o.y)).rgb * 0.1;
    sum += sampleIn(uv + vec2(-o.x, o.y)).rgb * 0.1;
    return sum;
}

vec3 digitalBlocks(vec3 col, vec2 uv, vec2 res, float scale) {
    if (Digital.x <= 0.0) {
        return col;
    }
    float size = max(4.0, Digital.y * scale);
    vec2 cell = floor(uv * res / size);
    float blockSeed = Extra.y;
    vec2 region = floor(cell / vec2(7.0, 2.0));
    float rowBand = floor(cell.y / 3.0);
    float regionHit = step(hash3(region.x, region.y, blockSeed), Digital.x * 0.9);
    float rowHit = step(hash3(rowBand, blockSeed, 23.0), Digital.x * 0.35) * step(hash3(cell.x, rowBand, blockSeed + 29.0), 0.8);
    float edge = step(hash3(cell.x, cell.y, blockSeed + 31.0), 0.85);
    float hit = max(regionHit, rowHit) * edge;
    if (hit <= 0.0) {
        return col;
    }
    float kind = hash3(region.x, region.y, blockSeed + 57.0);
    vec2 center = (cell + 0.5) * size / res;
    vec2 drag = vec2(0.0, (hash3(region.x, region.y, blockSeed + 61.0) - 0.3) * size * 2.0 / res.y);
    vec3 stale = sampleHistory(uv - drag).rgb;
    vec3 frozen = sampleHistory(center).rgb;
    vec3 smear = sampleIn(vec2(center.x, uv.y - drag.y)).rgb;
    vec3 broken = kind < 0.5 ? stale : (kind < 0.8 ? smear : frozen);
    return mix(col, broken, hit);
}

vec3 digitalFeed(vec2 res, float scale) {
    vec2 uv = areaCoord;
    vec3 col = digitalSample(uv, res);
    col = digitalBlur(col, uv, res, scale);
    col = digitalBlocks(col, uv, res, scale);
    if (Digital.w > 1.0) {
        col = floor(col * Digital.w + 0.5) / Digital.w;
    }
    return col;
}

void main() {
    areaCoord = (texCoord - Area.xy) / Area.zw;
    if (areaCoord.x < 0.0 || areaCoord.y < 0.0 || areaCoord.x > 1.0 || areaCoord.y > 1.0) {
        fragColor = vec4(0.0, 0.0, 0.0, 1.0);
        return;
    }
    vec2 res = max(Frame.zw * Area.zw, vec2(1.0));
    float scale = max(res.y / 1080.0, 0.25);
    vec2 grain = floor(areaCoord * res / vec2(max(1.0, 2.6 * scale), max(1.0, 1.5 * scale)));
    int mode = int(Mode.x + 0.5);
    float seam = 1.0;
    float keep = 1.0 - Colour.x;
    vec3 col;
    if (mode == 1) {
        col = analogFeed(res, scale, grain, seam, keep);
    } else if (mode == 2) {
        col = digitalFeed(res, scale);
    } else {
        col = sampleIn(areaCoord).rgb;
    }
    col = analogSnow(col, grain, keep);
    if (mode == 1) {
        col = analogSparkles(col, res, scale);
        col = analogScreen(col, res, scale);
    }
    col *= Mode.y * seam;
    col = feedStatic(col, grain);
    fragColor = vec4(clamp(col, 0.0, 1.0), 1.0);
}
