package com.shiostudios.dumplingrings.ui.board3d

/** GLSL ES 3.00 shaders for the 3D board. Lighting: key (warm, casts the contact shadow) + fill (cool) + rim, GGX specular,
 *  Fresnel, hemisphere ambient, dough albedo tile + procedural micro-normal, emissive glow for selection/hints. */
object Shaders {
    const val MAX_BUMPS = 24

    val RING_VS = """#version 300 es
precision highp float;
layout(location = 0) in vec4 aData;        // localAngle, tubeAngle, u, v
layout(location = 1) in float aCap;        // dome cap scale 0..1
uniform mat4 uViewProj;
uniform vec2 uCenter;      // board space
uniform float uMajor;      // major radius
uniform float uMinor;      // tube radius
uniform float uRot;        // ring rotation (rad)
uniform float uLift;       // z lift (release animation)
uniform vec2 uSlide;       // xy offset (release animation)
uniform float uScale;      // uniform scale (release animation)
uniform int uBumpCount;
uniform vec2 uBumps[$MAX_BUMPS]; // (worldAngleRad, sign*height)
uniform float uBumpSigma;        // rad
out vec3 vPos; out vec3 vNormal; out vec2 vUv; out float vCap;
float bumpAt(float wa) {
  float z = 0.0;
  for (int i = 0; i < $MAX_BUMPS; i++) { if (i >= uBumpCount) break;
    float d = wa - uBumps[i].x; d = d - 6.2831853 * floor((d + 3.14159265) / 6.2831853);
    z += uBumps[i].y * exp(-0.5 * (d * d) / (uBumpSigma * uBumpSigma)); }
  return z;
}
void main() {
  float la = aData.x; float ta = aData.y;
  float capScale = abs(aCap); float capDir = aCap < 0.0 ? -1.0 : 1.0; float vv = aData.w;
  float wa = la + uRot;
  float r = uMinor * capScale;
  // centreline point and frame
  vec2 radial = vec2(cos(wa), sin(wa));
  float z0 = uMinor * 1.02 + bumpAt(wa);
  // slope of the bump for the normal tilt
  float dz = (bumpAt(wa + 0.01) - bumpAt(wa - 0.01)) / 0.02 / max(uMajor, 1e-4);
  vec3 tangent = normalize(vec3(-radial.y, radial.x, dz));
  vec3 up = vec3(0.0, 0.0, 1.0);
  vec3 radial3 = vec3(radial, 0.0);
  vec3 n = normalize(cos(ta) * radial3 + sin(ta) * up);
  // make the normal orthogonal to the tilted tangent
  n = normalize(n - tangent * dot(n, tangent));
  vec3 p = vec3(uCenter + radial * uMajor, z0) + n * r;
  // dome caps: blend the normal towards the tube axis so the rounded end shades as a dome, not an open pipe
  float axial = sqrt(max(0.0, 1.0 - capScale * capScale));
  n = normalize(n * capScale + tangent * capDir * axial);
  p.xy = (p.xy - uCenter) * uScale + uCenter + uSlide; p.z = p.z * uScale + uLift;
  vPos = p; vNormal = n; vUv = vec2(aData.z, vv); vCap = capScale;
  gl_Position = uViewProj * vec4(p, 1.0);
}
"""

    val RING_FS = """#version 300 es
precision highp float;
in vec3 vPos; in vec3 vNormal; in vec2 vUv; in float vCap;
uniform sampler2D uAlbedo;
uniform vec3 uTint;        // material tint multiplier
uniform float uRough;
uniform vec3 uEye;
uniform vec3 uKeyDir; uniform vec3 uKeyCol;
uniform vec3 uFillDir; uniform vec3 uFillCol;
uniform vec3 uRimDir; uniform vec3 uRimCol;
uniform vec3 uAmbientSky; uniform vec3 uAmbientGround;
uniform vec3 uEmissive;    // selection / hint glow
uniform float uAlpha;
uniform float uDarken;     // locked rings
uniform vec3 uStripe;      // colour-id stripe colour (a=0 none)
uniform float uStripeOn;
out vec4 fragColor;
float ggx(vec3 n, vec3 h, float a) { float a2 = a * a; float ndh = max(dot(n, h), 0.0); float d = ndh * ndh * (a2 - 1.0) + 1.0; return a2 / (3.14159 * d * d + 1e-5); }
void main() {
  vec3 albedo = texture(uAlbedo, vUv * vec2(1.0, 1.0)).rgb * uTint;
  // procedural micro-bumps (dough grain) perturb the normal slightly
  float g1 = sin(vUv.x * 97.0 + vUv.y * 31.0) * sin(vUv.y * 53.0 - vUv.x * 17.0);
  vec3 n = normalize(vNormal + 0.06 * vec3(g1, -g1 * 0.7, 0.0));
  vec3 v = normalize(uEye - vPos);
  float rough = clamp(uRough, 0.08, 1.0); float a = rough * rough;
  vec3 col = albedo * mix(uAmbientGround, uAmbientSky, n.z * 0.5 + 0.5) * 0.32;
  vec3 lights[3]; vec3 lcols[3]; lights[0] = uKeyDir; lcols[0] = uKeyCol; lights[1] = uFillDir; lcols[1] = uFillCol; lights[2] = uRimDir; lcols[2] = uRimCol;
  for (int i = 0; i < 3; i++) {
    vec3 l = normalize(lights[i]); float ndl = max(dot(n, l), 0.0);
    vec3 h = normalize(l + v);
    float spec = ggx(n, h, a) * 0.1 * (1.0 - rough * 0.6);
    // soft wrap diffuse for dough
    float wrap = max((dot(n, l) + 0.18) / 1.18, 0.0); wrap *= wrap * 0.5 + 0.5 * wrap;
    col += (albedo * wrap * 0.85 + spec * ndl * 3.0) * lcols[i];
  }
  float fres = pow(1.0 - max(dot(n, v), 0.0), 3.0);
  col += fres * 0.12 * uKeyCol;
  // colour stripe on the outer top edge of the tube (colour-gate id)
  if (uStripeOn > 0.5) {
    // stripe along the top of the tube (tube angle ~90deg => v ~0.25); dash pattern differs per colour for colour-blind players
    float band = smoothstep(0.17, 0.21, vUv.y) * (1.0 - smoothstep(0.29, 0.33, vUv.y));
    float dash = 1.0;
    if (uStripe.g > uStripe.r && uStripe.g > uStripe.b) dash = step(0.5, fract(vUv.x * 3.0));          // jade: dashed
    else if (uStripe.b > uStripe.r) dash = step(0.65, fract(vUv.x * 6.0));                               // ube: dotted
    col = mix(col, uStripe * 1.15, band * dash * 0.95);
  }
  col = mix(col, col * 0.45, uDarken);
  col += uEmissive * (0.6 + 0.4 * fres);
  // filmic-ish tone map with a little contrast
  col = col / (col + 0.75) * 1.45; col = (col - 0.5) * 1.12 + 0.5;
  fragColor = vec4(pow(col, vec3(1.0 / 2.2)), uAlpha);
}
"""


    /** Meshy ring mesh in tube space (see RingModel): rebuilt for the level ring, rotated, woven; gaps cut by angle. */
    val GLBRING_VS = """#version 300 es
precision highp float;
layout(location = 0) in vec3 aTube;   // phi, rhoN, zN
layout(location = 1) in vec2 aUv;
layout(location = 2) in vec3 aNrm;    // normal in (radial, tangent, up) frame
uniform mat4 uViewProj;
uniform vec2 uCenter; uniform float uMajor; uniform float uMinor; uniform float uRot;
uniform float uLift; uniform vec2 uSlide; uniform float uScale;
uniform int uBumpCount; uniform vec2 uBumps[$MAX_BUMPS]; uniform float uBumpSigma;
out vec3 vPos; out vec3 vNormal; out vec2 vUv; out float vLocal;
float bumpAt(float wa) {
  float z = 0.0;
  for (int i = 0; i < $MAX_BUMPS; i++) { if (i >= uBumpCount) break;
    float d = wa - uBumps[i].x; d = d - 6.2831853 * floor((d + 3.14159265) / 6.2831853);
    z += uBumps[i].y * exp(-0.5 * (d * d) / (uBumpSigma * uBumpSigma)); }
  return z;
}
void main() {
  float la = aTube.x; float wa = la + uRot;
  vec2 radial = vec2(cos(wa), sin(wa));
  float z0 = uMinor * 1.02 + bumpAt(wa);
  float dz = (bumpAt(wa + 0.01) - bumpAt(wa - 0.01)) / 0.02 / max(uMajor, 1e-4);
  vec3 tangent = normalize(vec3(-radial.y, radial.x, dz));
  vec3 radial3 = vec3(radial, 0.0); vec3 up = vec3(0.0, 0.0, 1.0);
  vec3 p = vec3(uCenter + radial * (uMajor + aTube.y * uMinor), z0 + aTube.z * uMinor);
  vec3 n = normalize(aNrm.x * radial3 + aNrm.y * tangent + aNrm.z * up);
  p.xy = (p.xy - uCenter) * uScale + uCenter + uSlide; p.z = p.z * uScale + uLift;
  vPos = p; vNormal = n; vUv = aUv; vLocal = la;
  gl_Position = uViewProj * vec4(p, 1.0);
}
"""

    /** PBR metallic-roughness with normal map (screen-space TBN), analytic studio environment for reflections, gap discard. */
    val GLBRING_FS = """#version 300 es
precision highp float;
in vec3 vPos; in vec3 vNormal; in vec2 vUv; in float vLocal;
uniform sampler2D uAlbedo; uniform sampler2D uMetalRough; uniform sampler2D uNormalMap;
uniform float uHasMR; uniform float uHasNormal; uniform float uMetalFactor; uniform float uRoughFactor;
uniform vec3 uEye; uniform vec3 uKeyDir; uniform vec3 uKeyCol; uniform vec3 uFillDir; uniform vec3 uFillCol; uniform vec3 uRimDir; uniform vec3 uRimCol;
uniform vec3 uAmbientSky; uniform vec3 uAmbientGround; uniform vec3 uEmissive; uniform float uAlpha; uniform float uDarken;
uniform vec3 uStripe; uniform float uStripeOn;
uniform int uGapCount; uniform vec2 uGaps[4];   // (startRad, widthRad) in the ring's local frame
uniform float uCapRad;                            // dome length in radians: gaps are widened by this so the dome cap owns the edge
out vec4 fragColor;
float ggx(vec3 n, vec3 h, float a) { float a2 = a * a; float ndh = max(dot(n, h), 0.0); float d = ndh * ndh * (a2 - 1.0) + 1.0; return a2 / (3.14159 * d * d + 1e-5); }
float geo(float ndv, float ndl, float a) { float k = (a + 1.0) * (a + 1.0) / 8.0; return (ndv / (ndv * (1.0 - k) + k)) * (ndl / (ndl * (1.0 - k) + k)); }
vec3 env(vec3 d, float rough) {
  // studio environment: warm sky/ground gradient, a soft window reflection band and the key light as a bright blob
  float up = d.z * 0.5 + 0.5;
  vec3 e = mix(uAmbientGround * 1.4, uAmbientSky * 1.6, up);
  // softbox window: a sharp bright band for lustre on polished surfaces, broad on rough ones
  float band = smoothstep(0.42, 0.52, d.z) * (1.0 - smoothstep(0.66, 0.80, d.z));
  e += band * vec3(1.0, 0.98, 0.95) * mix(1.6, 0.4, rough);
  float sun = pow(max(dot(d, normalize(uKeyDir)), 0.0), mix(90.0, 8.0, rough));
  e += sun * uKeyCol * 1.8;
  float rimGlow = pow(max(dot(d, normalize(uRimDir)), 0.0), mix(40.0, 6.0, rough));
  e += rimGlow * uRimCol * 0.9;
  return e;
}
void main() {
  // gap cut (local angle): fragment inside any gap, widened by the dome length, is discarded (the domed caps cover the edges)
  float la = vLocal - 6.2831853 * floor(vLocal / 6.2831853);
  for (int i = 0; i < 4; i++) { if (i >= uGapCount) break;
    float rel = la - uGaps[i].x; rel = rel - 6.2831853 * floor(rel / 6.2831853);
    if (rel < uGaps[i].y) discard; }
  vec4 albedoA = texture(uAlbedo, vUv); vec3 albedo = pow(albedoA.rgb, vec3(2.2));   // sRGB texture -> linear light
  float metal = uMetalFactor; float rough = uRoughFactor;
  if (uHasMR > 0.5) { vec3 mr = texture(uMetalRough, vUv).rgb; rough *= mr.g; metal *= mr.b; }
  rough = clamp(rough, 0.06, 1.0);
  vec3 n = normalize(vNormal);
  if (uHasNormal > 0.5) {
    vec3 tn = texture(uNormalMap, vUv).xyz * 2.0 - 1.0;
    vec3 dp1 = dFdx(vPos); vec3 dp2 = dFdy(vPos); vec2 duv1 = dFdx(vUv); vec2 duv2 = dFdy(vUv);
    vec3 dp2perp = cross(dp2, n); vec3 dp1perp = cross(n, dp1);
    vec3 T = dp2perp * duv1.x + dp1perp * duv2.x; vec3 B = dp2perp * duv1.y + dp1perp * duv2.y;
    float invmax = inversesqrt(max(dot(T, T), dot(B, B)) + 1e-9);
    mat3 tbn = mat3(T * invmax, B * invmax, n);
    n = normalize(tbn * vec3(tn.xy * 0.8, tn.z));
  }
  vec3 v = normalize(uEye - vPos);
  float ndv = max(dot(n, v), 1e-3);
  vec3 F0 = mix(vec3(0.04), albedo, metal);
  vec3 diffCol = albedo * (1.0 - metal);
  float a = rough * rough;
  vec3 col = diffCol * mix(uAmbientGround, uAmbientSky, n.z * 0.5 + 0.5) * 0.35;
  vec3 lights[3]; vec3 lcols[3]; lights[0] = uKeyDir; lcols[0] = uKeyCol; lights[1] = uFillDir; lcols[1] = uFillCol; lights[2] = uRimDir; lcols[2] = uRimCol;
  for (int i = 0; i < 3; i++) {
    vec3 l = normalize(lights[i]); float ndl = max(dot(n, l), 0.0); vec3 h = normalize(l + v);
    vec3 F = F0 + (1.0 - F0) * pow(1.0 - max(dot(h, v), 0.0), 5.0);
    vec3 spec = ggx(n, h, a) * geo(ndv, ndl, a) * F / (4.0 * ndv * ndl + 1e-4);
    // clear-coat lobe: a tight second highlight that reads as polish/lacquer
    float cc = ggx(n, h, 0.05) * geo(ndv, ndl, 0.05) * (0.04 + 0.96 * pow(1.0 - max(dot(h, v), 0.0), 5.0)) / (4.0 * ndv * ndl + 1e-4);
    col += (diffCol / 3.14159 + spec + cc * 0.6) * ndl * lcols[i] * 2.4;
  }
  // environment reflection (the premium look of metal/stone/lacquer)
  vec3 r = reflect(-v, n);
  vec3 Fenv = F0 + (max(vec3(1.0 - rough), F0) - F0) * pow(1.0 - ndv, 5.0);
  col += env(r, rough) * Fenv * mix(0.9, 0.35, rough);
  if (uStripeOn > 0.5) { float band = smoothstep(0.17, 0.21, fract(vUv.y)) * (1.0 - smoothstep(0.29, 0.33, fract(vUv.y))); col = mix(col, uStripe * 1.15, band * 0.0); }
  col = mix(col, col * 0.45, uDarken);
  // selection / ghost glow as a warm rim light (reads on black onyx as well as on gold)
  col += uEmissive * (0.08 + 2.4 * pow(1.0 - ndv, 2.0));
  // filmic (ACES fitted) tone map keeps hot highlights and deep darks without clipping
  col *= 0.95;
  col = (col * (2.51 * col + 0.03)) / (col * (2.43 * col + 0.59) + 0.14);
  col = (col - 0.5) * 1.08 + 0.5;
  fragColor = vec4(pow(max(col, 0.0), vec3(1.0 / 2.2)), uAlpha);
}
"""

    /** Flat soft shadow: the ring mesh re-projected onto the table, blurred radially via alpha falloff by tube angle. */
    val SHADOW_FS = """#version 300 es
precision highp float;
in vec3 vPos; in vec3 vNormal; in vec2 vUv; in float vCap;
uniform float uAlpha;
out vec4 fragColor;
void main() { float a = uAlpha * smoothstep(0.0, 1.0, vCap) ; fragColor = vec4(0.12, 0.06, 0.02, a); }
"""

    val PROP_VS = """#version 300 es
precision highp float;
layout(location = 0) in vec3 aPos; layout(location = 1) in vec3 aNormal; layout(location = 2) in vec2 aUv;
uniform mat4 uViewProj; uniform mat4 uModel; uniform mat3 uNormalM;
out vec3 vPos; out vec3 vNormal; out vec2 vUv;
void main() { vec4 p = uModel * vec4(aPos, 1.0); vPos = p.xyz; vNormal = normalize(uNormalM * aNormal); vUv = aUv; gl_Position = uViewProj * p; }
"""

    val PROP_FS = """#version 300 es
precision highp float;
in vec3 vPos; in vec3 vNormal; in vec2 vUv;
uniform sampler2D uAlbedo; uniform float uHasTex; uniform vec3 uTint; uniform float uRough;
uniform vec3 uEye; uniform vec3 uKeyDir; uniform vec3 uKeyCol; uniform vec3 uFillDir; uniform vec3 uFillCol; uniform vec3 uRimDir; uniform vec3 uRimCol;
uniform vec3 uAmbientSky; uniform vec3 uAmbientGround; uniform vec3 uEmissive; uniform float uAlpha;
out vec4 fragColor;
float ggx(vec3 n, vec3 h, float a) { float a2 = a * a; float ndh = max(dot(n, h), 0.0); float d = ndh * ndh * (a2 - 1.0) + 1.0; return a2 / (3.14159 * d * d + 1e-5); }
void main() {
  vec3 albedo = mix(uTint, texture(uAlbedo, vUv).rgb * uTint, uHasTex);
  vec3 n = normalize(vNormal); vec3 v = normalize(uEye - vPos);
  float rough = clamp(uRough, 0.08, 1.0); float a = rough * rough;
  vec3 col = albedo * mix(uAmbientGround, uAmbientSky, n.z * 0.5 + 0.5) * 0.32;
  vec3 lights[3]; vec3 lcols[3]; lights[0] = uKeyDir; lcols[0] = uKeyCol; lights[1] = uFillDir; lcols[1] = uFillCol; lights[2] = uRimDir; lcols[2] = uRimCol;
  for (int i = 0; i < 3; i++) { vec3 l = normalize(lights[i]); float ndl = max(dot(n, l), 0.0); vec3 h = normalize(l + v);
    col += (albedo * max((dot(n, l) + 0.15) / 1.15, 0.0) * 0.85 + ggx(n, h, a) * 0.1 * ndl * 3.0) * lcols[i]; }
  col += pow(1.0 - max(dot(n, v), 0.0), 3.0) * 0.1 * uKeyCol + uEmissive;
  col = col / (col + 0.75) * 1.45; col = (col - 0.5) * 1.12 + 0.5;
  fragColor = vec4(pow(col, vec3(1.0 / 2.2)), uAlpha);
}
"""

    /** Screen-aligned textured quad on the board plane (glow discs, light pools). */
    val QUAD_VS = """#version 300 es
precision highp float;
layout(location = 0) in vec4 aPosUv;
uniform mat4 uViewProj; uniform vec3 uCenter; uniform vec2 uSize;
out vec2 vUv;
void main() { vec3 p = uCenter + vec3(aPosUv.x * uSize.x, aPosUv.y * uSize.y, 0.0); vUv = aPosUv.zw; gl_Position = uViewProj * vec4(p, 1.0); }
"""
    val QUAD_FS = """#version 300 es
precision highp float;
in vec2 vUv; uniform vec4 uColor; out vec4 fragColor;
void main() { vec2 d = vUv * 2.0 - 1.0; float r = length(d); float a = smoothstep(1.0, 0.0, r); fragColor = vec4(uColor.rgb, uColor.a * a * a); }
"""
}
