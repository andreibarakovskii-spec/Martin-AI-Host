import bpy
import math
import os
from mathutils import Vector

OUT = os.environ.get("MARTIN_OUT", "/tmp/martin.glb")
REAL_PREVIEW = os.environ.get("MARTIN_PREVIEW", "")
BASE_SCRIPT = os.path.join(os.path.dirname(__file__), "build_martin_v21.py")

# Reuse only the proven skeleton, animation library, export helpers and material helpers.
# The v2.1 visible geometry is discarded and rebuilt to match the supplied Martin turnaround.
os.environ["MARTIN_PREVIEW"] = ""
with open(BASE_SCRIPT, "r", encoding="utf-8") as fh:
    exec(compile(fh.read(), BASE_SCRIPT, "exec"), globals(), globals())
PREVIEW = REAL_PREVIEW

print("MARTIN_V23_REFERENCE_REBUILD_START")

# Keep the tested rig/actions and replace every visible object.
for obj in list(bpy.data.objects):
    if obj != arm:
        bpy.data.objects.remove(obj, do_unlink=True)

# ---------- Character-sheet palette ----------
FUR23 = material("Martin British Grey V23", (0.175, 0.185, 0.205), roughness=0.90)
FUR_LIGHT23 = material("Martin Muzzle Grey V23", (0.245, 0.252, 0.268), roughness=0.91)
FUR_DARK23 = material("Martin Fur Shadow V23", (0.075, 0.082, 0.095), roughness=0.92)
EAR23 = material("Martin Ear Warm V23", (0.235, 0.155, 0.158), roughness=0.82)
NOSE23 = material("Martin Nose Charcoal V23", (0.035, 0.028, 0.030), roughness=0.43)
HOODIE23 = material("Martin Hoodie Black V23", (0.009, 0.010, 0.014), roughness=0.83)
HOODIE_DETAIL23 = material("Martin Hoodie Knit V23", (0.023, 0.024, 0.030), roughness=0.91)
GOLD23 = material("Martin Warm Gold V23", (0.70, 0.40, 0.095), metallic=0.72, roughness=0.27)
GOLD_DARK23 = material("Martin Antique Gold V23", (0.22, 0.105, 0.022), metallic=0.62, roughness=0.38)
EYE_RIM23 = material("Martin Eye Rim V23", (0.008, 0.006, 0.006), roughness=0.12)
IRIS23 = material("Martin Amber Iris V23", (0.93, 0.43, 0.050), metallic=0.02, roughness=0.12, emission=(0.10, 0.028, 0.001))
PUPIL23 = material("Martin Pupil V23", (0.001, 0.001, 0.002), roughness=0.04)
CORNEA23 = material("Martin Cornea V23", (0.65, 0.78, 1.0), roughness=0.015, alpha=0.095)
HIGHLIGHT23 = material("Martin Eye Highlight V23", (0.98, 0.985, 1.0), roughness=0.03, emission=(0.35, 0.35, 0.35))
MOUTH23 = material("Martin Mouth Interior V23", (0.030, 0.004, 0.006), roughness=0.50)
TONGUE23 = material("Martin Tongue V23", (0.42, 0.075, 0.085), roughness=0.58)
TOOTH23 = material("Martin Teeth V23", (0.86, 0.82, 0.72), roughness=0.42)
SOLE23 = material("Martin Sneaker Sole V23", (0.63, 0.64, 0.65), roughness=0.78)
RUBBER23 = material("Martin Sneaker Rubber V23", (0.075, 0.078, 0.082), roughness=0.80)
LACE23 = material("Martin Sneaker Lace V23", (0.76, 0.77, 0.77), roughness=0.72)
MIC_BLACK23 = material("Martin Mic Black V23", (0.018, 0.020, 0.024), metallic=0.24, roughness=0.42)
WHISKER23 = material("Martin Whisker V23", (0.72, 0.72, 0.70), roughness=0.66)


def roughen_fur(obj, amplitude=0.0025, frequency=90.0):
    """Deterministic silhouette micro-breakup; no texture asset or paid dependency."""
    if obj is None or obj.type != "MESH":
        return obj
    obj.data.update()
    seed = sum(ord(c) for c in obj.name) * 0.017
    for v in obj.data.vertices:
        p = v.co
        n = v.normal.normalized()
        noise = (
            math.sin((p.x + seed) * frequency)
            + math.sin((p.y - seed * 0.7) * frequency * 0.83)
            + math.sin((p.z + seed * 1.3) * frequency * 1.17)
        ) / 3.0
        v.co += n * (noise * amplitude)
    obj.data.update()
    return obj


def fur_sphere(name, loc, scale, mat, bone, segments=48, rings=24, amplitude=0.0025):
    obj = sphere(name, loc, scale, mat, bone, segments, rings)
    return roughen_fur(obj, amplitude)


def text_mesh(name, body, loc, size, mat, bone, extrude=0.010, bevel=0.003):
    bpy.ops.object.text_add(location=loc, rotation=(math.radians(90), 0, 0))
    obj = bpy.context.object
    obj.name = name
    obj.data.body = body
    obj.data.align_x = "CENTER"
    obj.data.align_y = "CENTER"
    obj.data.size = size
    obj.data.extrude = extrude
    obj.data.bevel_depth = bevel
    obj.data.bevel_resolution = 2
    obj.data.materials.append(mat)
    return rig(obj, bone)


# ---------- Hoodie / short, stocky body ----------
sphere("Hoodie_Torso", (0, 0.025, 1.50), (0.50, 0.335, 0.60), HOODIE23, "Chest", 52, 26)
sphere("Hoodie_Belly", (0, -0.300, 1.43), (0.375, 0.050, 0.36), HOODIE_DETAIL23, "Chest", 44, 22)
sphere("Hoodie_Pocket", (0, -0.347, 1.34), (0.300, 0.035, 0.155), HOODIE_DETAIL23, "Chest", 40, 18)
torus("Hoodie_Hood", (0, 0.015, 1.985), 0.325, 0.070, HOODIE_DETAIL23, "Chest", scale=(1.0, 0.74, 1.0))
curve("Hoodie_String_L", [(-0.072, -0.338, 1.94), (-0.078, -0.360, 1.79), (-0.080, -0.360, 1.67)], 0.006, HOODIE_DETAIL23, "Chest")
curve("Hoodie_String_R", [(0.072, -0.338, 1.94), (0.078, -0.360, 1.79), (0.080, -0.360, 1.67)], 0.006, HOODIE_DETAIL23, "Chest")
sphere("Hoodie_StringTip_L", (-0.080, -0.360, 1.655), (0.014, 0.010, 0.018), GOLD_DARK23, "Chest", 16, 8)
sphere("Hoodie_StringTip_R", (0.080, -0.360, 1.655), (0.014, 0.010, 0.018), GOLD_DARK23, "Chest", 16, 8)

# Reference sheet: compact gold M and MARTIN wordmark.
text_mesh("Logo_M", "M", (0, -0.356, 1.60), 0.235, GOLD23, "Chest", 0.009, 0.003)
text_mesh("Logo_MARTIN", "MARTIN", (0, -0.358, 1.445), 0.085, GOLD23, "Chest", 0.006, 0.002)

# ---------- British-shorthair-like head ----------
fur_sphere("Fur_Head", (0, 0.010, 2.52), (0.565, 0.445, 0.535), FUR23, "Head", 64, 32, 0.0030)
fur_sphere("Fur_Cheek_L", (-0.245, -0.350, 2.345), (0.260, 0.145, 0.205), FUR23, "Head", 48, 24, 0.0027)
fur_sphere("Fur_Cheek_R", (0.245, -0.350, 2.345), (0.260, 0.145, 0.205), FUR23, "Head", 48, 24, 0.0027)
fur_sphere("Fur_Muzzle_L", (-0.092, -0.476, 2.305), (0.170, 0.095, 0.125), FUR_LIGHT23, "Head", 44, 22, 0.0018)
fur_sphere("Fur_Muzzle_R", (0.092, -0.476, 2.305), (0.170, 0.095, 0.125), FUR_LIGHT23, "Head", 44, 22, 0.0018)
fur_sphere("Fur_Chin", (0, -0.438, 2.205), (0.155, 0.073, 0.092), FUR_LIGHT23, "Jaw", 38, 18, 0.0016)
fur_sphere("Fur_Brow_L", (-0.178, -0.358, 2.650), (0.160, 0.070, 0.060), FUR23, "Head", 36, 16, 0.0015)
fur_sphere("Fur_Brow_R", (0.178, -0.358, 2.650), (0.160, 0.070, 0.060), FUR23, "Head", 36, 16, 0.0015)

# Smaller upright ears with softened bases.
ear("Fur_Ear_L", (-0.355, 0.010, 2.825), 0.250, 0.390, 0.105, FUR23, "Ear.L")
ear("Fur_Ear_R", (0.355, 0.010, 2.825), 0.250, 0.390, 0.105, FUR23, "Ear.R")
ear("Ear_Inner_L", (-0.355, -0.050, 2.875), 0.135, 0.235, 0.018, EAR23, "Ear.L", -0.014)
ear("Ear_Inner_R", (0.355, -0.050, 2.875), 0.135, 0.235, 0.018, EAR23, "Ear.R", -0.014)
fur_sphere("Fur_EarBase_L", (-0.332, 0.006, 2.790), (0.165, 0.120, 0.115), FUR23, "Ear.L", 34, 16, 0.0017)
fur_sphere("Fur_EarBase_R", (0.332, 0.006, 2.790), (0.165, 0.120, 0.115), FUR23, "Ear.R", 34, 16, 0.0017)

# ---------- Signature oversized amber eyes ----------
for side, x in (("L", -0.190), ("R", 0.190)):
    sphere("Eye_Rim_" + side, (x, -0.420, 2.535), (0.157, 0.058, 0.168), EYE_RIM23, "Eye." + side, 52, 26)
    sphere("Iris_Amber_" + side, (x, -0.471, 2.535), (0.124, 0.014, 0.137), IRIS23, "Eye." + side, 48, 24)
    sphere("Pupil_" + side, (x, -0.484, 2.535), (0.050, 0.008, 0.086), PUPIL23, "Eye." + side, 38, 18)
    sphere("Cornea_" + side, (x, -0.481, 2.535), (0.160, 0.020, 0.171), CORNEA23, "Eye." + side, 52, 26)
    sphere("Eye_Highlight_Main_" + side, (x - 0.040, -0.502, 2.590), (0.019, 0.006, 0.025), HIGHLIGHT23, "Eye." + side, 20, 10)
    sphere("Eye_Highlight_Small_" + side, (x + 0.028, -0.500, 2.565), (0.008, 0.004, 0.010), HIGHLIGHT23, "Eye." + side, 16, 8)
    # Same-fur lids are bone-driven for blinking.
    fur_sphere("Fur_Lid_" + side, (x, -0.452, 2.666), (0.150, 0.022, 0.032), FUR23, "Lid." + side, 36, 16, 0.0010)

# Dark, small triangular-looking nose and soft smile.
sphere("Nose", (0, -0.560, 2.356), (0.063, 0.032, 0.044), NOSE23, "Head", 34, 16)
sphere("Nostril_L", (-0.020, -0.585, 2.360), (0.008, 0.005, 0.005), EYE_RIM23, "Head", 12, 6)
sphere("Nostril_R", (0.020, -0.585, 2.360), (0.008, 0.005, 0.005), EYE_RIM23, "Head", 12, 6)
sphere("Mouth_Interior", (0, -0.526, 2.225), (0.090, 0.014, 0.042), MOUTH23, "Jaw", 28, 14)
sphere("Tongue", (0, -0.540, 2.207), (0.054, 0.009, 0.020), TONGUE23, "Jaw", 24, 12)
sphere("Tooth_L", (-0.040, -0.544, 2.244), (0.012, 0.006, 0.020), TOOTH23, "Jaw", 16, 8)
sphere("Tooth_R", (0.040, -0.544, 2.244), (0.012, 0.006, 0.020), TOOTH23, "Jaw", 16, 8)
curve("Smile_L", [(0, -0.558, 2.292), (-0.050, -0.565, 2.255), (-0.118, -0.550, 2.247)], 0.0065, MOUTH23, "Jaw")
curve("Smile_R", [(0, -0.558, 2.292), (0.050, -0.565, 2.255), (0.118, -0.550, 2.247)], 0.0065, MOUTH23, "Jaw")

for side, sx in (("L", -1), ("R", 1)):
    for idx, dz in enumerate((0.045, 0.010, -0.028)):
        curve(
            "Whisker_%s_%d" % (side, idx),
            [(0.125 * sx, -0.515, 2.335 + dz),
             (0.285 * sx, -0.555, 2.340 + dz * 0.35),
             (0.475 * sx, -0.520, 2.345 + dz * 0.10)],
            0.0032, WHISKER23, "Head"
        )

# ---------- Sleeves, paws and exposed furry legs ----------
for side, sx in (("L", -1), ("R", 1)):
    sphere("Hoodie_UpperArm_" + side, (0.525 * sx, 0.005, 1.62), (0.165, 0.175, 0.305), HOODIE23, "UpperArm." + side, 40, 20)
    sphere("Hoodie_Forearm_" + side, (0.615 * sx, -0.020, 1.345), (0.145, 0.155, 0.245), HOODIE_DETAIL23, "Forearm." + side, 38, 18)
    fur_sphere("Fur_Paw_" + side, (0.645 * sx, -0.080, 1.120), (0.158, 0.142, 0.164), FUR23, "Hand." + side, 42, 20, 0.0026)

    fur_sphere("Fur_Thigh_" + side, (0.220 * sx, 0.012, 0.790), (0.192, 0.198, 0.305), FUR23, "Thigh." + side, 40, 20, 0.0025)
    fur_sphere("Fur_Shin_" + side, (0.220 * sx, -0.002, 0.500), (0.166, 0.175, 0.250), FUR23, "Shin." + side, 38, 18, 0.0024)

    # Black canvas-style sneaker with white/grey sole and laces.
    sphere("Sneaker_" + side, (0.220 * sx, -0.145, 0.222), (0.220, 0.295, 0.120), RUBBER23, "Foot." + side, 40, 18)
    sphere("Sneaker_Toe_" + side, (0.220 * sx, -0.355, 0.222), (0.205, 0.100, 0.090), SOLE23, "Foot." + side, 32, 14)
    sphere("Sneaker_Sole_" + side, (0.220 * sx, -0.170, 0.132), (0.232, 0.305, 0.037), SOLE23, "Foot." + side, 36, 14)
    for li, yy in enumerate((-0.245, -0.205, -0.165)):
        curve(
            "Sneaker_Lace_%s_%d" % (side, li),
            [((0.220 * sx) - 0.105, yy, 0.295),
             ((0.220 * sx), yy - 0.012, 0.307),
             ((0.220 * sx) + 0.105, yy, 0.295)],
            0.006, LACE23, "Foot." + side
        )

# ---------- Thick curved tail ----------
fur_sphere("Fur_Tail1", (0.250, 0.185, 0.980), (0.260, 0.125, 0.135), FUR23, "Tail1", 38, 18, 0.0027)
fur_sphere("Fur_Tail2", (0.480, 0.120, 1.075), (0.245, 0.120, 0.135), FUR23, "Tail2", 38, 18, 0.0027)
fur_sphere("Fur_Tail3", (0.665, -0.015, 1.290), (0.140, 0.118, 0.250), FUR23, "Tail3", 38, 18, 0.0028)
fur_sphere("Fur_TailTip", (0.690, -0.055, 1.470), (0.135, 0.112, 0.150), FUR23, "Tail3", 36, 16, 0.0028)

# ---------- Headphones around the neck, matching the reference ----------
neck_band_points = [
    (-0.42, 0.000, 2.015),
    (-0.34, -0.055, 1.950),
    (-0.23, -0.105, 1.900),
    (0.00, -0.135, 1.875),
    (0.23, -0.105, 1.900),
    (0.34, -0.055, 1.950),
    (0.42, 0.000, 2.015),
]
curve("Headphone_NeckBand", neck_band_points, 0.035, HOODIE23, "Chest")
for side, sx in (("L", -1), ("R", 1)):
    cylinder("Headphone_Cup_" + side, (0.420 * sx, -0.235, 1.880), 0.145, 0.090, HOODIE23, "Chest", (0, math.radians(90), 0), 42)
    cylinder("Headphone_GoldRing_" + side, (0.468 * sx, -0.235, 1.880), 0.110, 0.016, GOLD23, "Chest", (0, math.radians(90), 0), 42)
    cylinder("Headphone_Center_" + side, (0.478 * sx, -0.235, 1.880), 0.072, 0.012, HOODIE_DETAIL23, "Chest", (0, math.radians(90), 0), 36)

# ---------- Black microphone with metallic grille ----------
# Placed in viewer-left paw as in the hero image.
cylinder("Mic_Handle", (-0.640, -0.235, 1.220), 0.031, 0.310, MIC_BLACK23, "Hand.L", (math.radians(-10), 0, 0), 32)
cylinder("Mic_GoldCollar", (-0.640, -0.255, 1.375), 0.043, 0.030, GOLD_DARK23, "Hand.L", (math.radians(-10), 0, 0), 32)
sphere("Mic_Grille", (-0.640, -0.275, 1.435), (0.081, 0.075, 0.090), MIC_BLACK23, "Hand.L", 40, 20)
# tiny grille ribs for a visibly microphone-like silhouette
for ridx, rz in enumerate((1.405, 1.435, 1.465)):
    torus("Mic_GrilleRing_%d" % ridx, (-0.640, -0.275, rz), 0.070, 0.0045, GOLD_DARK23, "Hand.L", scale=(1.0, 1.0, 0.80))

# Restore known default action after mesh rebuild.
arm.animation_data.action = bpy.data.actions.get("DefaultAnim")
scene.frame_set(1)

print("MARTIN_V23_RIG bones=%d meshes=%d actions=%d" % (
    len(arm.data.bones),
    len([o for o in bpy.data.objects if o.type == "MESH"]),
    len(bpy.data.actions),
))

# Export the actual runtime GLB.
os.makedirs(os.path.dirname(OUT), exist_ok=True)
bpy.ops.object.select_all(action="SELECT")
bpy.context.view_layer.objects.active = arm
bpy.ops.export_scene.gltf(
    filepath=OUT,
    export_format="GLB",
    export_apply=False,
    export_animations=True,
    export_skins=True,
    export_morph=True,
    export_all_influences=True,
    export_yup=True,
    export_cameras=False,
    export_lights=False,
)
if not os.path.exists(OUT) or os.path.getsize(OUT) < 240000:
    raise RuntimeError("Martin v2.3 GLB export missing or too small")
print("MARTIN_V23_GLB_OK", OUT, os.path.getsize(OUT))

# Preview is rendered from this same generated, rigged scene.
if PREVIEW:
    engines = {x.identifier for x in bpy.types.RenderSettings.bl_rna.properties["engine"].enum_items}
    scene.render.engine = "BLENDER_EEVEE_NEXT" if "BLENDER_EEVEE_NEXT" in engines else "BLENDER_EEVEE"
    scene.render.resolution_x = 720
    scene.render.resolution_y = 960
    scene.render.resolution_percentage = 100
    world = bpy.data.worlds.new("MartinWorldV23")
    scene.world = world
    world.use_nodes = True
    world.node_tree.nodes["Background"].inputs["Color"].default_value = (0.004, 0.004, 0.006, 1)
    world.node_tree.nodes["Background"].inputs["Strength"].default_value = 0.13

    def point_at_v23(obj, target):
        obj.rotation_euler = (Vector(target) - obj.location).to_track_quat("-Z", "Y").to_euler()

    bpy.ops.object.camera_add(location=(0, -7.8, 2.10))
    cam = bpy.context.object
    cam.data.lens = 67
    scene.camera = cam
    point_at_v23(cam, (0, 0, 1.58))

    lights = [
        ((-2.9, -3.7, 4.7), 1050, 4.2, (1.0, 0.73, 0.50)),
        ((2.8, -2.9, 3.5), 580, 3.2, (0.42, 0.52, 0.90)),
        ((2.5, 2.0, 4.4), 850, 2.5, (1.0, 0.48, 0.18)),
        ((0.0, -3.0, 2.7), 230, 2.0, (1.0, 0.93, 0.82)),
    ]
    for idx, (loc, energy, size, color) in enumerate(lights):
        bpy.ops.object.light_add(type="AREA", location=loc)
        light = bpy.context.object
        light.name = "StudioV23_%d" % idx
        light.data.energy = energy
        light.data.size = size
        light.data.color = color
        point_at_v23(light, (0, 0, 1.68))

    bpy.ops.mesh.primitive_plane_add(size=20, location=(0, 0, -0.02))
    floor = bpy.context.object
    floor.name = "PreviewFloorV23"
    assign(floor, material("Preview Floor V23", (0.012, 0.010, 0.014), roughness=0.66))
    scene.render.filepath = PREVIEW
    bpy.ops.render.render(write_still=True)
    print("MARTIN_V23_PREVIEW_OK", PREVIEW)
