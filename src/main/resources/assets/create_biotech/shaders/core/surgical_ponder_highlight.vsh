#version 150

#moj_import <minecraft:fog.glsl>

in vec3 Position;
in vec4 Color;
in vec3 Normal;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform float StrokeWidth;
uniform vec2 ScreenSize;
uniform int FogShape;

out float vertexDistance;
out vec4 vertexColor;

void main() {
    vec4 start = ProjMat * ModelViewMat * vec4(Position, 1.0);
    vec4 end = ProjMat * ModelViewMat * vec4(Position + Normal, 1.0);
    vec2 screenDirection = (end.xy / end.w - start.xy / start.w) * ScreenSize;
    float projectedLength = length(screenDirection);
    vec2 direction = projectedLength > 0.00001 ? screenDirection / projectedLength : vec2(1.0, 0.0);
    vec2 offset = vec2(-direction.y, direction.x) * StrokeWidth / ScreenSize;
    if (offset.x < 0.0) offset *= -1.0;

    // The LINES vertex format duplicates each endpoint to form a screen-space quad.
    // Expand only X/Y; keep the original Z/W so scene occlusion stays exact.
    gl_Position = start;
    gl_Position.xy += offset * (gl_VertexID % 2 == 0 ? 1.0 : -1.0) * start.w;
    vertexDistance = fog_distance(Position, FogShape);
    vertexColor = Color;
}
