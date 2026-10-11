#version 330
#extension GL_ARB_separate_shader_objects : require

uniform sampler2D FrameSampler;

layout(std140) uniform PropwashActionCamPack {
    vec4 Frame;
};

layout(location = 0) in vec2 texCoord;

layout(location = 0) out vec4 fragColor;

vec3 pixel(int x, int y, int height) {
    return texelFetch(FrameSampler, ivec2(x, height - 1 - y), 0).rgb;
}

float lumaOf(vec3 c) {
    return dot(c, vec3(0.2126, 0.7152, 0.0722));
}

float toY(vec3 c) {
    return (16.0 + 219.0 * lumaOf(c)) / 255.0;
}

float toCb(vec3 c) {
    return clamp((128.0 + 224.0 * (c.b - lumaOf(c)) / 1.8556) / 255.0, 0.0, 1.0);
}

float toCr(vec3 c) {
    return clamp((128.0 + 224.0 * (c.r - lumaOf(c)) / 1.5748) / 255.0, 0.0, 1.0);
}

void main() {
    int width = int(Frame.x + 0.5);
    int height = int(Frame.y + 0.5);
    int tx = int(floor(texCoord.x * Frame.z));
    int ty = int(floor(texCoord.y * Frame.w));
    vec4 result = vec4(0.0);
    if (ty < height) {
        for (int k = 0; k < 4; k++) {
            result[k] = toY(pixel(tx * 4 + k, ty, height));
        }
    } else {
        int row = ty - height;
        int quarter = height / 4;
        bool red = row >= quarter;
        if (red) {
            row -= quarter;
        }
        int texelsPerRow = width / 8;
        int chromaRow = 2 * row + (tx >= texelsPerRow ? 1 : 0);
        int chromaX = 4 * (tx >= texelsPerRow ? tx - texelsPerRow : tx);
        for (int k = 0; k < 4; k++) {
            int x = 2 * (chromaX + k);
            int y = 2 * chromaRow;
            vec3 c = (pixel(x, y, height) + pixel(x + 1, y, height) + pixel(x, y + 1, height) + pixel(x + 1, y + 1, height)) * 0.25;
            result[k] = red ? toCr(c) : toCb(c);
        }
    }
    fragColor = result;
}
