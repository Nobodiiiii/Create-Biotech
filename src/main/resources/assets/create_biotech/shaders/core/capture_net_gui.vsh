#version 150

#moj_import <minecraft:fog.glsl>

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV1;
in ivec2 UV2;
in vec3 Normal;

uniform sampler2D Sampler1;
uniform sampler2D Sampler2;
uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform int FogShape;
uniform vec3 Light0_Direction;
uniform vec3 Light1_Direction;

out float vertexDistance;
out vec4 vertexColor;
out vec4 lightMapColor;
out vec4 overlayColor;
out vec2 texCoord0;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    vertexDistance = fog_distance(Position, FogShape);

    // Light the visible side of the cloth. Ease through grazing angles instead of
    // abruptly flipping the normal, and keep enough ambient light for the inner walls.
    vec3 normal = normalize(Normal);
    float facing = normal.z / sqrt(normal.z * normal.z + 0.04);
    vec3 visibleNormal = normal * facing;
    float light0 = max(0.0, dot(Light0_Direction, visibleNormal));
    float light1 = max(0.0, dot(Light1_Direction, visibleNormal));
    float brightness = mix(0.64, 0.96, clamp(light0 + light1, 0.0, 1.0));
    vertexColor = vec4(Color.rgb * brightness, Color.a);

    lightMapColor = texelFetch(Sampler2, UV2 / 16, 0);
    overlayColor = texelFetch(Sampler1, UV1, 0);
    texCoord0 = UV0;
}
