import bpy
import math
import os
from mathutils import Vector

OUT = os.environ.get("MARTIN_OUT", "/tmp/martin.glb")
REAL_PREVIEW = os.environ.get("MARTIN_PREVIEW", "")
BASE_SCRIPT = os.path.join(os.path.dirname(__file__), "build_martin_v21.py")

# Reuse the tested skeleton, animation library, materials helpers and GLB setup from v2.1,
# but suppress its preview. We rebuild every visible object below while keeping the rig/actions.
os.environ["MARTIN_PREVIEW"] = ""
with open(BASE_SCRIPT, "r", encoding="utf-8") as fh:
    exec(compile(fh.read(), BASE_SCRIPT, "exec"), globals(), globals())
PREVIEW = REAL_PREVIEW

print("MARTIN_V22_REBUILD_START")

# Keep only the proven animated armature. All v2.1 visible geometry is replaced.
for obj in list(bpy.data.objects):
    if obj != arm:
        bpy.data.objects.remove(obj, do_unlink=True)

# Smoother, less toy-like palette. SurfacePolish in Godot adds the final micro-detail.
FUR22 = material("Martin Fur Grey V22", (0.185, 0.205, 0.235), roughness=0.86)
FUR_LIGHT22 = material("Martin Fur Silver V22", (0.34, 0.36, 0.395), roughness=0.88)
FUR_DARK22 = material("Martin Fur Dark V22", (0.065, 0.075, 0.095), roughness=0.90)
PINK22 = material("Martin Nose Pink V22", (0.52, 0.19, 0.23), roughness=0.48)
EAR_PINK22 = material("Martin Ear Pink V22", (0.48, 0.22, 0.27), roughness=0.64)
HOODIE22 = material("Martin Hoodie Black V22", (0.010, 0.012, 0.018), roughness=0.76)
CLOTH22 = material("Martin Cloth V22", (0.030, 0.035, 0.048), roughness=0.84)
GOLD22 = material("Martin Gold V22", (0.83, 0.48, 0.075), metallic=0.82, roughness=0.22)
GOLD_DARK22 = material("Martin Dark Gold V22", (0.24, 0.12, 0.020), metallic=0.70, roughness=0.34)
WHITE22 = material("Martin Eye White V22", (0.94, 0.95, 0.97), roughness=0.15)
IRIS22 = material("Martin Iris Amber V22", (0.94, 0.41, 0.035), metallic=0.04, roughness=0.16, emission=(0.16, 0.045, 0.003))
PUPIL22 = material("Martin Pupil V22", (0.002, 0.002, 0.004), roughness=0.06)
CORNEA22 = material("Martin Cornea V22", (0.72, 0.84, 1.0), roughness=0.020, alpha=0.10)
MOUTH22 = material("Martin Mouth V22", (0.018, 0.004, 0.008), roughness=0.46)
SOLE22 = material("Martin Sole V22", (0.11, 0.12, 0.14), roughness=0.74)
WHISKER22 = material("Martin Whisker V22", (0.68, 0.71, 0.77), roughness=0.72)


def v22_logo_text():
    bpy.ops.object.text_add(location=(0, -0.355, 1.56), rotation=(math.radians(90), 0, 0))
    obj = bpy.context.object
    obj.name = "Logo_M"
    obj.data.body = "M"
    obj.data.align_x = "CENTER"
    obj.data.align_y = "CENTER"
    obj.data.size = 0.30
    obj.data.extrude = 0.014
    obj.data.bevel_depth = 0.005
    obj.data.bevel_resolution = 2
    obj.data.materials.append(GOLD22)
    return rig(obj, "Chest")


# ---------- Cleaner cat silhouette ----------
# Torso is narrower and taller, removing the spherical toy-belly impression.
sphere("Hoodie_Torso", (0, 0.025, 1.54), (0.49, 0.315, 0.61), HOODIE22, "Chest", 48, 24)
sphere("Hoodie_Belly", (0, -0.294, 1.43), (0.355, 0.055, 0.355), CLOTH22, "Chest", 40, 20)
torus("Hoodie_Hood", (0, 0.010, 1.995), 0.315, 0.062, CLOTH22, "Chest", scale=(1.0, 0.72, 1.0))
v22_logo_text()
curve("Hoodie_String_L", [(-0.075, -0.337, 1.93), (-0.085, -0.365, 1.78), (-0.090, -0.365, 1.66)], 0.008, GOLD_DARK22, "Chest")
curve("Hoodie_String_R", [(0.075, -0.337, 1.93), (0.085, -0.365, 1.78), (0.090, -0.365, 1.66)], 0.008, GOLD_DARK22, "Chest")

# ---------- More feline head / face ----------
sphere("Fur_Head", (0, 0.010, 2.53), (0.525, 0.425, 0.535), FUR22, "Head", 56, 28)
# Side volumes keep a soft cat silhouette without making the whole muzzle white.
sphere("Fur_Cheek_L", (-0.220, -0.345, 2.355), (0.225, 0.125, 0.185), FUR22, "Head", 40, 20)
sphere("Fur_Cheek_R", (0.220, -0.345, 2.355), (0.225, 0.125, 0.185), FUR22, "Head", 40, 20)
sphere("Fur_Temple_L", (-0.355, -0.055, 2.47), (0.155, 0.145, 0.235), FUR22, "Head", 36, 18)
sphere("Fur_Temple_R", (0.355, -0.055, 2.47), (0.155, 0.145, 0.235), FUR22, "Head", 36, 18)
# Two smaller muzzle pads read more naturally than one large white oval.
sphere("Fur_Muzzle_L", (-0.092, -0.470, 2.305), (0.165, 0.090, 0.125), FUR_LIGHT22, "Head", 40, 20)
sphere("Fur_Muzzle_R", (0.092, -0.470, 2.305), (0.165, 0.090, 0.125), FUR_LIGHT22, "Head", 40, 20)
sphere("Fur_Chin", (0, -0.425, 2.185), (0.145, 0.070, 0.083), FUR_LIGHT22, "Jaw", 34, 17)

ear("Fur_Ear_L", (-0.335, 0.015, 2.82), 0.275, 0.435, 0.105, FUR22, "Ear.L")
ear("Fur_Ear_R", (0.335, 0.015, 2.82), 0.275, 0.435, 0.105, FUR22, "Ear.R")
ear("Ear_Inner_L", (-0.335, -0.055, 2.885), 0.145, 0.255, 0.022, EAR_PINK22, "Ear.L", -0.012)
ear("Ear_Inner_R", (0.335, -0.055, 2.885), 0.145, 0.255, 0.022, EAR_PINK22, "Ear.R", -0.012)

# Eyes are smaller and sit deeper in the face; amber remains the signature accent.
for side, x in (("L", -0.178), ("R", 0.178)):
    sphere("Eye_Sclera_" + side, (x, -0.418, 2.525), (0.132, 0.052, 0.150), WHITE22, "Eye." + side, 44, 22)
    sphere("Iris_Amber_" + side, (x, -0.466, 2.525), (0.068, 0.013, 0.081), IRIS22, "Eye." + side, 36, 18)
    sphere("Pupil_" + side, (x, -0.479, 2.525), (0.021, 0.008, 0.052), PUPIL22, "Eye." + side, 28, 14)
    sphere("Cornea_" + side, (x, -0.475, 2.525), (0.136, 0.017, 0.154), CORNEA22, "Eye." + side, 44, 22)
    sphere("Eye_Highlight_" + side, (x - 0.030, -0.491, 2.575), (0.014, 0.005, 0.019), WHITE22, "Eye." + side, 20, 10)
    # Same-color upper lid: functional blink without the old heavy eyebrow bar.
    sphere("Fur_Lid_" + side, (x, -0.466, 2.648), (0.132, 0.018, 0.028), FUR22, "Lid." + side, 32, 14)

sphere("Nose", (0, -0.555, 2.355), (0.060, 0.030, 0.041), PINK22, "Head", 30, 15)
sphere("Mouth_Interior", (0, -0.525, 2.226), (0.080, 0.014, 0.036), MOUTH22, "Jaw", 26, 13)
curve("Smile_L", [(0, -0.550, 2.275), (-0.052, -0.557, 2.240), (-0.112, -0.544, 2.235)], 0.007, MOUTH22, "Jaw")
curve("Smile_R", [(0, -0.550, 2.275), (0.052, -0.557, 2.240), (0.112, -0.544, 2.235)], 0.007, MOUTH22, "Jaw")
for side, sx in (("L", -1), ("R", 1)):
    for idx, dz in enumerate((0.035, 0.0, -0.035)):
        curve("Whisker_%s_%d" % (side, idx), [(0.125 * sx, -0.505, 2.325 + dz), (0.275 * sx, -0.548, 2.332 + dz * 0.35), (0.430 * sx, -0.520, 2.340 + dz * 0.10)], 0.0035, WHISKER22, "Head")

# ---------- Slimmer limbs / softer joints ----------
for side, sx in (("L", -1), ("R", 1)):
    sphere("Hoodie_UpperArm_" + side, (0.535 * sx, 0.005, 1.63), (0.155, 0.165, 0.300), HOODIE22, "UpperArm." + side, 36, 18)
    sphere("Hoodie_Forearm_" + side, (0.625 * sx, -0.015, 1.355), (0.130, 0.145, 0.235), CLOTH22, "Forearm." + side, 36, 18)
    paw_y = -0.060 if side == "L" else -0.160
    sphere("Fur_Paw_" + side, (0.650 * sx, paw_y, 1.135), (0.150, 0.135, 0.160), FUR_LIGHT22, "Hand." + side, 38, 19)
    sphere("Pants_Thigh_" + side, (0.220 * sx, 0.010, 0.790), (0.190, 0.200, 0.320), HOODIE22, "Thigh." + side, 36, 18)
    sphere("Pants_Shin_" + side, (0.220 * sx, -0.005, 0.485), (0.165, 0.175, 0.255), CLOTH22, "Shin." + side, 36, 18)
    sphere("Shoe_" + side, (0.220 * sx, -0.135, 0.215), (0.215, 0.285, 0.115), HOODIE22, "Foot." + side, 36, 18)
    sphere("Shoe_Sole_" + side, (0.220 * sx, -0.165, 0.135), (0.225, 0.295, 0.034), SOLE22, "Foot." + side, 32, 14)

# Tail is deliberately offset to camera-right so it remains readable in a phone portrait crop.
sphere("Fur_Tail1", (0.255, 0.165, 0.980), (0.250, 0.115, 0.125), FUR22, "Tail1", 34, 17)
sphere("Fur_Tail2", (0.465, 0.090, 1.075), (0.235, 0.110, 0.125), FUR22, "Tail2", 34, 17)
sphere("Fur_Tail3", (0.650, -0.020, 1.275), (0.135, 0.110, 0.235), FUR22, "Tail3", 34, 17)

# ---------- Headphones / handheld microphone ----------
headband_points = []
for i in range(15):
    a = math.radians(-70 + 140 * i / 14.0)
    headband_points.append((0.545 * math.sin(a), 0.025, 2.645 + 0.545 * math.cos(a)))
curve("Headphone_Band", headband_points, 0.034, HOODIE22, "Head")
for side, sx in (("L", -1), ("R", 1)):
    cylinder("Headphone_Cup_" + side, (0.535 * sx, -0.002, 2.515), 0.145, 0.090, HOODIE22, "Head", (0, math.radians(90), 0), 40)
    cylinder("Headphone_Gold_" + side, (0.582 * sx, -0.002, 2.515), 0.105, 0.014, GOLD22, "Head", (0, math.radians(90), 0), 40)

# Right paw holds the mic slightly in front of the chest instead of hiding it at the hip.
cylinder("Mic_Handle", (0.600, -0.255, 1.245), 0.030, 0.300, GOLD_DARK22, "Hand.R", (math.radians(-12), 0, 0), 32)
sphere("Mic_Grille", (0.600, -0.290, 1.405), (0.078, 0.072, 0.086), GOLD22, "Hand.R", 34, 17)

# Restore default action after rebuild.
arm.animation_data.action = bpy.data.actions.get("DefaultAnim")
scene.frame_set(1)

print("MARTIN_V22_RIG bones=%d meshes=%d actions=%d" % (
    len(arm.data.bones),
    len([o for o in bpy.data.objects if o.type == "MESH"]),
    len(bpy.data.actions),
))

# Export the actual production GLB after the visual rebuild.
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
if not os.path.exists(OUT) or os.path.getsize(OUT) < 200000:
    raise RuntimeError("Martin v2.2 GLB export missing or too small")
print("MARTIN_V22_GLB_OK", OUT, os.path.getsize(OUT))

# Preview is rendered from the same rigged scene, never from a mockup.
if PREVIEW:
    engines = {x.identifier for x in bpy.types.RenderSettings.bl_rna.properties["engine"].enum_items}
    scene.render.engine = "BLENDER_EEVEE_NEXT" if "BLENDER_EEVEE_NEXT" in engines else "BLENDER_EEVEE"
    scene.render.resolution_x = 720
    scene.render.resolution_y = 960
    scene.render.resolution_percentage = 100
    world = bpy.data.worlds.new("MartinWorldV22")
    scene.world = world
    world.use_nodes = True
    world.node_tree.nodes["Background"].inputs["Color"].default_value = (0.005, 0.006, 0.010, 1)
    world.node_tree.nodes["Background"].inputs["Strength"].default_value = 0.17

    def point_at_v22(obj, target):
        obj.rotation_euler = (Vector(target) - obj.location).to_track_quat("-Z", "Y").to_euler()

    bpy.ops.object.camera_add(location=(0, -8.0, 2.15))
    cam = bpy.context.object
    cam.data.lens = 64
    scene.camera = cam
    point_at_v22(cam, (0, 0, 1.60))

    lights = [
        ((-2.8, -3.8, 4.6), 960, 4.0, (1.0, 0.76, 0.56)),
        ((2.8, -2.7, 3.4), 540, 3.0, (0.48, 0.56, 1.0)),
        ((2.4, 2.0, 4.3), 780, 2.4, (0.92, 0.50, 0.24)),
    ]
    for idx, (loc, energy, size, color) in enumerate(lights):
        bpy.ops.object.light_add(type="AREA", location=loc)
        light = bpy.context.object
        light.name = "StudioV22_%d" % idx
        light.data.energy = energy
        light.data.size = size
        light.data.color = color
        point_at_v22(light, (0, 0, 1.65))

    bpy.ops.mesh.primitive_plane_add(size=20, location=(0, 0, -0.02))
    floor = bpy.context.object
    floor.name = "PreviewFloorV22"
    assign(floor, material("Preview Floor V22", (0.014, 0.012, 0.018), roughness=0.62))
    scene.render.filepath = PREVIEW
    bpy.ops.render.render(write_still=True)
    print("MARTIN_V22_PREVIEW_OK", PREVIEW)
