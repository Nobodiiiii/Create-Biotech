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

    // Thin cloth receives light on both sides. Keep the contrast gentle and independent
    // of the camera so an inner wall cannot turn into a dark stripe as the net moves.
    vec3 normal = normalize(Normal);
    float light0 = abs(dot(Light0_Direction, normal));
    float light1 = abs(dot(Light1_Direction, normal));
    float brightness = mix(0.72, 0.94, clamp(max(light0, light1), 0.0, 1.0));
    vertexColor = vec4(Color.rgb * brightness, Color.a);

    lightMapColor = texelFetch(Sampler2, UV2 / 16, 0);
    overlayColor = texelFetch(Sampler1, UV1, 0);
    texCoord0 = UV0;
}
