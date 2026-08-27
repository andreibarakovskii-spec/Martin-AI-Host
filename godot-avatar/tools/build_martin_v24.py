import bpy
import math
import os
from mathutils import Vector

OUT = os.environ.get("MARTIN_OUT", "/tmp/martin.glb")
PREVIEW = os.environ.get("MARTIN_PREVIEW", "")

print("MARTIN_V24_CC0_BASE_START", bpy.app.version_string)

scene = bpy.context.scene
scene.frame_start = 1
scene.frame_end = 48
scene.render.fps = 24

# Normalize context inherited from the Blender 2.77 CC0 source.
if bpy.context.object is not None and bpy.context.object.mode != "OBJECT":
    try:
        bpy.ops.object.mode_set(mode="OBJECT")
    except Exception as exc:
        print("MARTIN_V24_CONTEXT_WARN", repr(exc))

arm = bpy.data.objects.get("Armature")
cat = bpy.data.objects.get("CatPilot")
if arm is None or arm.type != "ARMATURE":
    raise RuntimeError("CC0 Cat Pilot armature missing")
if cat is None or cat.type != "MESH":
    raise RuntimeError("CC0 Cat Pilot mesh missing")

arm.name = "MartinSkeleton"
arm.data.name = "MartinSkeleton"
cat.name = "Fur_Body"

# Remove old scene helpers. Character mesh/rig/actions remain.
for obj in list(bpy.data.objects):
    if obj not in {arm, cat} and obj.type in {"CAMERA", "LIGHT"}:
        bpy.data.objects.remove(obj, do_unlink=True)


def mat(name, color, metallic=0.0, roughness=0.7, alpha=1.0, emission=None):
    m = bpy.data.materials.new(name)
    m.use_nodes = True
    m.diffuse_color = (*color, alpha)
    bsdf = m.node_tree.nodes.get("Principled BSDF")
    if bsdf:
        bsdf.inputs["Base Color"].default_value = (*color, 1.0)
        bsdf.inputs["Metallic"].default_value = metallic
        bsdf.inputs["Roughness"].default_value = roughness
        if "Alpha" in bsdf.inputs:
            bsdf.inputs["Alpha"].default_value = alpha
        if emission is not None:
            ekey = "Emission Color" if "Emission Color" in bsdf.inputs else "Emission"
            if ekey in bsdf.inputs:
                bsdf.inputs[ekey].default_value = (*emission, 1.0)
            if "Emission Strength" in bsdf.inputs:
                bsdf.inputs["Emission Strength"].default_value = 0.45
    if alpha < 0.999 and hasattr(m, "blend_method"):
        m.blend_method = "BLEND"
    return m


def fur_material(name, dark=(0.095, 0.105, 0.125), light=(0.205, 0.215, 0.235)):
    m = bpy.data.materials.new(name)
    m.use_nodes = True
    nt = m.node_tree
    bsdf = nt.nodes.get("Principled BSDF")
    noise = nt.nodes.new("ShaderNodeTexNoise")
    noise.inputs["Scale"].default_value = 42.0
    noise.inputs["Detail"].default_value = 5.0
    noise.inputs["Roughness"].default_value = 0.72
    noise.inputs["Distortion"].default_value = 0.12
    ramp = nt.nodes.new("ShaderNodeValToRGB")
    ramp.color_ramp.elements[0].position = 0.22
    ramp.color_ramp.elements[0].color = (*dark, 1.0)
    ramp.color_ramp.elements[1].position = 0.80
    ramp.color_ramp.elements[1].color = (*light, 1.0)
    bump = nt.nodes.new("ShaderNodeBump")
    bump.inputs["Strength"].default_value = 0.20
    bump.inputs["Distance"].default_value = 0.018
    nt.links.new(noise.outputs["Fac"], ramp.inputs["Fac"])
    nt.links.new(ramp.outputs["Color"], bsdf.inputs["Base Color"])
    nt.links.new(noise.outputs["Fac"], bump.inputs["Height"])
    nt.links.new(bump.outputs["Normal"], bsdf.inputs["Normal"])
    bsdf.inputs["Roughness"].default_value = 0.90
    return m


def cloth_material(name, color):
    m = mat(name, color, roughness=0.88)
    nt = m.node_tree
    bsdf = nt.nodes.get("Principled BSDF")
    noise = nt.nodes.new("ShaderNodeTexNoise")
    noise.inputs["Scale"].default_value = 115.0
    noise.inputs["Detail"].default_value = 2.0
    bump = nt.nodes.new("ShaderNodeBump")
    bump.inputs["Strength"].default_value = 0.10
    bump.inputs["Distance"].default_value = 0.006
    nt.links.new(noise.outputs["Fac"], bump.inputs["Height"])
    nt.links.new(bump.outputs["Normal"], bsdf.inputs["Normal"])
    return m


FUR = fur_material("Martin British Grey V24")
FUR_LIGHT = fur_material("Martin Muzzle Grey V24", (0.145, 0.150, 0.165), (0.31, 0.315, 0.33))
FUR_DARK = mat("Martin Fur Shadow V24", (0.045, 0.050, 0.062), roughness=0.92)
EAR_INNER = mat("Martin Ear Warm V24", (0.20, 0.105, 0.105), roughness=0.84)
NOSE = mat("Martin Nose V24", (0.018, 0.015, 0.018), roughness=0.30)
HOODIE = cloth_material("Martin Hoodie Black V24", (0.008, 0.009, 0.013))
HOODIE_SOFT = cloth_material("Martin Hoodie Knit V24", (0.020, 0.021, 0.027))
GOLD = mat("Martin Gold V24", (0.56, 0.28, 0.050), metallic=0.80, roughness=0.25)
GOLD_DARK = mat("Martin Antique Gold V24", (0.18, 0.080, 0.014), metallic=0.68, roughness=0.34)
EYE_OUTER = mat("Martin Eye Outer V24", (0.012, 0.008, 0.006), roughness=0.10)
IRIS = mat("Martin Amber Iris V24", (0.72, 0.225, 0.020), roughness=0.10, emission=(0.10, 0.020, 0.0005))
PUPIL = mat("Martin Pupil V24", (0.0005, 0.0005, 0.001), roughness=0.025)
CORNEA = mat("Martin Cornea V24", (0.28, 0.34, 0.42), roughness=0.015, alpha=0.055)
HILITE = mat("Martin Eye Highlight V24", (0.95, 0.98, 1.0), roughness=0.02, emission=(0.45, 0.45, 0.45))
MOUTH = mat("Martin Mouth V24", (0.018, 0.002, 0.004), roughness=0.48)
TONGUE = mat("Martin Tongue V24", (0.34, 0.048, 0.060), roughness=0.58)
TEETH = mat("Martin Teeth V24", (0.72, 0.68, 0.60), roughness=0.40)
SHOE = mat("Martin Shoe Canvas V24", (0.025, 0.027, 0.031), roughness=0.83)
SOLE = mat("Martin Shoe Sole V24", (0.60, 0.61, 0.62), roughness=0.78)
LACE = mat("Martin Shoe Lace V24", (0.72, 0.72, 0.72), roughness=0.75)
MIC = mat("Martin Mic V24", (0.010, 0.012, 0.015), metallic=0.24, roughness=0.40)
WHISKER = mat("Martin Whisker V24", (0.68, 0.69, 0.67), roughness=0.62)


def assign(obj, material):
    if hasattr(obj.data, "materials"):
        obj.data.materials.clear()
        obj.data.materials.append(material)
    if obj.type == "MESH":
        for poly in obj.data.polygons:
            poly.use_smooth = True
    return obj


def parent_bone(obj, bone_name):
    world = obj.matrix_world.copy()
    obj.parent = arm
    obj.parent_type = "BONE"
    obj.parent_bone = bone_name
    obj.matrix_world = world
    return obj


def sphere(name, loc, scale, material, bone, segments=36, rings=18):
    bpy.ops.mesh.primitive_uv_sphere_add(segments=segments, ring_count=rings, location=loc)
    obj = bpy.context.object
    obj.name = name
    obj.scale = scale
    assign(obj, material)
    return parent_bone(obj, bone)


def cylinder(name, loc, radius, depth, material, bone, rot=(0, 0, 0), vertices=32):
    bpy.ops.mesh.primitive_cylinder_add(vertices=vertices, radius=radius, depth=depth, location=loc, rotation=rot)
    obj = bpy.context.object
    obj.name = name
    assign(obj, material)
    bevel = obj.modifiers.new("SoftEdges", "BEVEL")
    bevel.width = min(radius * 0.18, depth * 0.05)
    bevel.segments = 2
    return parent_bone(obj, bone)


def torus(name, loc, major, minor, material, bone, rot=(0, 0, 0), scale=(1, 1, 1)):
    bpy.ops.mesh.primitive_torus_add(major_segments=40, minor_segments=10, major_radius=major, minor_radius=minor, location=loc, rotation=rot)
    obj = bpy.context.object
    obj.name = name
    obj.scale = scale
    assign(obj, material)
    return parent_bone(obj, bone)


def curve(name, points, bevel, material, bone, cyclic=False):
    cu = bpy.data.curves.new(name + "Curve", "CURVE")
    cu.dimensions = "3D"
    cu.resolution_u = 3
    cu.bevel_depth = bevel
    cu.bevel_resolution = 3
    sp = cu.splines.new("BEZIER")
    sp.bezier_points.add(len(points) - 1)
    for bp, co in zip(sp.bezier_points, points):
        bp.co = co
        bp.handle_left_type = "AUTO"
        bp.handle_right_type = "AUTO"
    sp.use_cyclic_u = cyclic
    obj = bpy.data.objects.new(name, cu)
    scene.collection.objects.link(obj)
    obj.data.materials.append(material)
    return parent_bone(obj, bone)


def text_mesh(name, body, loc, size, material, bone, extrude=0.004, bevel=0.0015):
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
    obj.data.materials.append(material)
    return parent_bone(obj, bone)


# ---------- Clean the CC0 base into a continuous fur body ----------
bpy.ops.object.select_all(action="DESELECT")
cat.select_set(True)
bpy.context.view_layer.objects.active = cat
if cat.data.shape_keys:
    # We use dedicated mobile-friendly eye geometry/lid bones instead of the legacy painted-eye shape key.
    for key in list(cat.data.shape_keys.key_blocks)[::-1]:
        try:
            cat.shape_key_remove(key)
        except Exception:
            pass

remove_groups = {"HatFlap.L", "HatFlap.R", "Goggles", "Scarf1", "Scarf2"}
remove_group_indices = {g.index for g in cat.vertex_groups if g.name in remove_groups}
remove_indices = []
if remove_group_indices:
    for v in cat.data.vertices:
        if any(g.group in remove_group_indices and g.weight > 0.60 for g in v.groups):
            remove_indices.append(v.index)
if remove_indices:
    for v in cat.data.vertices:
        v.select = v.index in set(remove_indices)
    bpy.ops.object.mode_set(mode="EDIT")
    bpy.ops.mesh.delete(type="VERT")
    bpy.ops.object.mode_set(mode="OBJECT")
print("MARTIN_V24_REMOVED_PILOT_VERTS", len(remove_indices))

assign(cat, FUR)

# Subdivide the continuous source mesh: 860 source vertices -> a smooth game-ready body while retaining weights.
sub = cat.modifiers.new("MartinSmoothTopology", "SUBSURF")
sub.subdivision_type = "CATMULL_CLARK"
sub.levels = 2
sub.render_levels = 2
# Put subdivision before the armature modifier so applying it cannot bake an animated pose.
for _ in range(8):
    idx = cat.modifiers.find(sub.name)
    if idx <= 0:
        break
    try:
        bpy.ops.object.modifier_move_up(modifier=sub.name)
    except Exception:
        break
try:
    bpy.ops.object.modifier_apply(modifier=sub.name)
except Exception as exc:
    print("MARTIN_V24_SUBDIV_APPLY_WARN", repr(exc))
for poly in cat.data.polygons:
    poly.use_smooth = True

# ---------- Add runtime-control face bones without disturbing source actions ----------
bpy.ops.object.select_all(action="DESELECT")
arm.select_set(True)
bpy.context.view_layer.objects.active = arm
if arm.mode != "OBJECT":
    bpy.ops.object.mode_set(mode="OBJECT")
bpy.ops.object.mode_set(mode="EDIT")
eb = arm.data.edit_bones
head_parent = eb.get("Head")
for name, head, tail in [
    ("Jaw", (0.0, -0.34, 1.93), (0.0, -0.54, 1.83)),
    ("Eye.L", (-0.22, -0.42, 2.13), (-0.22, -0.58, 2.13)),
    ("Eye.R", (0.22, -0.42, 2.13), (0.22, -0.58, 2.13)),
    ("Lid.L", (-0.22, -0.43, 2.25), (-0.22, -0.57, 2.25)),
    ("Lid.R", (0.22, -0.43, 2.25), (0.22, -0.57, 2.25)),
]:
    if eb.get(name) is None:
        b = eb.new(name)
        b.head = head
        b.tail = tail
        b.parent = head_parent
        b.use_deform = False
bpy.ops.object.mode_set(mode="OBJECT")
for pb in arm.pose.bones:
    pb.rotation_mode = "XYZ"

# Duplicate source actions under NPC state names expected by the app.
def ensure_action(dst, src):
    if bpy.data.actions.get(dst) is not None:
        return
    source = bpy.data.actions.get(src)
    if source is None:
        return
    copy = source.copy()
    copy.name = dst
    copy.use_fake_user = True

ensure_action("Listening", "DefaultAnim")
ensure_action("Talking", "Cheer")
ensure_action("Dance", "Cheer")
for action in bpy.data.actions:
    action.use_fake_user = True

# ---------- Reference-matched face volume ----------
# Chubby British-shorthair cheek/muzzle masses sit over the continuous head topology.
sphere("Fur_Cheek_L", (-0.255, -0.455, 1.935), (0.245, 0.115, 0.185), FUR, "Head", 40, 20)
sphere("Fur_Cheek_R", (0.255, -0.455, 1.935), (0.245, 0.115, 0.185), FUR, "Head", 40, 20)
sphere("Fur_Muzzle_L", (-0.087, -0.565, 1.875), (0.155, 0.070, 0.105), FUR_LIGHT, "Head", 38, 18)
sphere("Fur_Muzzle_R", (0.087, -0.565, 1.875), (0.155, 0.070, 0.105), FUR_LIGHT, "Head", 38, 18)
sphere("Fur_Chin", (0.0, -0.530, 1.785), (0.135, 0.050, 0.072), FUR_LIGHT, "Jaw", 34, 16)
sphere("Fur_Brow_L", (-0.210, -0.470, 2.275), (0.155, 0.042, 0.050), FUR, "Head", 32, 14)
sphere("Fur_Brow_R", (0.210, -0.470, 2.275), (0.155, 0.042, 0.050), FUR, "Head", 32, 14)

# Ear warmth overlays follow the source ear bones.
def inner_ear(name, x, bone):
    verts = [(x - 0.105, -0.405, 2.405), (x + 0.105, -0.405, 2.405), (x, -0.395, 2.545)]
    mesh = bpy.data.meshes.new(name + "Mesh")
    mesh.from_pydata(verts, [], [(0, 1, 2)])
    mesh.update()
    obj = bpy.data.objects.new(name, mesh)
    scene.collection.objects.link(obj)
    assign(obj, EAR_INNER)
    bevel = obj.modifiers.new("EarSoftEdge", "SOLIDIFY")
    bevel.thickness = 0.006
    return parent_bone(obj, bone)

inner_ear("Ear_Inner_L", -0.345, "Ear.L")
inner_ear("Ear_Inner_R", 0.345, "Ear.R")

# ---------- Signature oversized amber eyes ----------
for side, x in (("L", -0.205), ("R", 0.205)):
    bone = "Eye." + side
    sphere("Eye_Outer_" + side, (x, -0.525, 2.120), (0.150, 0.070, 0.166), EYE_OUTER, bone, 46, 22)
    sphere("Iris_Amber_" + side, (x, -0.590, 2.120), (0.118, 0.020, 0.132), IRIS, bone, 42, 20)
    sphere("Pupil_" + side, (x, -0.610, 2.120), (0.050, 0.010, 0.083), PUPIL, bone, 34, 16)
    sphere("Cornea_" + side, (x, -0.610, 2.120), (0.152, 0.022, 0.170), CORNEA, bone, 42, 20)
    sphere("Eye_Highlight_Main_" + side, (x - 0.043, -0.634, 2.178), (0.017, 0.005, 0.023), HILITE, bone, 16, 8)
    sphere("Eye_Highlight_Small_" + side, (x + 0.030, -0.631, 2.147), (0.007, 0.004, 0.009), HILITE, bone, 14, 7)
    # Bone moves this small fur lid downward for blinking in Godot.
    sphere("Fur_Lid_" + side, (x, -0.568, 2.262), (0.147, 0.022, 0.031), FUR, "Lid." + side, 32, 14)

# Small charcoal nose and controllable smiling mouth.
sphere("Nose", (0.0, -0.655, 1.925), (0.061, 0.028, 0.041), NOSE, "Head", 30, 14)
sphere("Nostril_L", (-0.019, -0.675, 1.929), (0.007, 0.004, 0.004), EYE_OUTER, "Head", 12, 6)
sphere("Nostril_R", (0.019, -0.675, 1.929), (0.007, 0.004, 0.004), EYE_OUTER, "Head", 12, 6)
sphere("Mouth_Interior", (0.0, -0.615, 1.790), (0.085, 0.012, 0.039), MOUTH, "Jaw", 26, 12)
sphere("Tongue", (0.0, -0.630, 1.773), (0.050, 0.006, 0.018), TONGUE, "Jaw", 20, 10)
sphere("Tooth_L", (-0.038, -0.632, 1.813), (0.010, 0.005, 0.017), TEETH, "Jaw", 14, 7)
sphere("Tooth_R", (0.038, -0.632, 1.813), (0.010, 0.005, 0.017), TEETH, "Jaw", 14, 7)
curve("Smile_L", [(0.0, -0.657, 1.868), (-0.048, -0.663, 1.830), (-0.113, -0.650, 1.824)], 0.0055, MOUTH, "Jaw")
curve("Smile_R", [(0.0, -0.657, 1.868), (0.048, -0.663, 1.830), (0.113, -0.650, 1.824)], 0.0055, MOUTH, "Jaw")

for side, sx in (("L", -1), ("R", 1)):
    for idx, dz in enumerate((0.045, 0.010, -0.028)):
        curve(
            "Whisker_%s_%d" % (side, idx),
            [(0.12 * sx, -0.620, 1.910 + dz), (0.29 * sx, -0.660, 1.915 + dz * 0.35), (0.48 * sx, -0.615, 1.920 + dz * 0.10)],
            0.0028, WHISKER, "Head"
        )

# ---------- Hoodie: rounded black volume with pocket and reference gold mark ----------
sphere("Hoodie_Torso", (0.0, -0.010, 1.220), (0.455, 0.310, 0.480), HOODIE, "Chest", 46, 22)
sphere("Hoodie_Belly", (0.0, -0.294, 1.175), (0.350, 0.042, 0.305), HOODIE_SOFT, "Chest", 38, 18)
sphere("Hoodie_Pocket", (0.0, -0.340, 1.075), (0.280, 0.035, 0.125), HOODIE_SOFT, "Chest", 34, 16)
torus("Hoodie_Hood", (0.0, -0.020, 1.575), 0.295, 0.062, HOODIE_SOFT, "Chest", scale=(1.0, 0.74, 1.0))
curve("Hoodie_String_L", [(-0.063, -0.333, 1.535), (-0.070, -0.350, 1.415), (-0.072, -0.350, 1.335)], 0.005, HOODIE_SOFT, "Chest")
curve("Hoodie_String_R", [(0.063, -0.333, 1.535), (0.070, -0.350, 1.415), (0.072, -0.350, 1.335)], 0.005, HOODIE_SOFT, "Chest")

# Stylized M made from slim gold strokes rather than a generic font.
curve("Logo_M_Left", [(-0.105, -0.350, 1.305), (-0.105, -0.352, 1.410), (0.0, -0.353, 1.335)], 0.009, GOLD, "Chest")
curve("Logo_M_Right", [(0.0, -0.353, 1.335), (0.105, -0.352, 1.410), (0.105, -0.350, 1.305)], 0.009, GOLD, "Chest")
text_mesh("Logo_MARTIN", "MARTIN", (0.0, -0.360, 1.235), 0.070, GOLD, "Chest", 0.0035, 0.001)

# Sleeves and plush paw cuffs. Source skinned arms remain underneath, so bends stay continuous.
for side, sx in (("L", -1), ("R", 1)):
    sphere("Hoodie_UpperArm_" + side, (0.425 * sx, -0.015, 1.265), (0.145, 0.150, 0.265), HOODIE, "Humerous." + side, 34, 16)
    sphere("Hoodie_Forearm_" + side, (0.520 * sx, -0.030, 1.030), (0.125, 0.135, 0.220), HOODIE_SOFT, "Forearm." + side, 32, 16)
    sphere("Fur_Paw_" + side, (0.545 * sx, -0.075, 0.850), (0.138, 0.125, 0.145), FUR, "Hand." + side, 36, 18)

# ---------- Neck headphones ----------
band = [(-0.405, 0.015, 1.585), (-0.330, -0.055, 1.530), (-0.220, -0.105, 1.500), (0.0, -0.128, 1.485), (0.220, -0.105, 1.500), (0.330, -0.055, 1.530), (0.405, 0.015, 1.585)]
curve("Headphone_NeckBand", band, 0.030, HOODIE, "Chest")
for side, sx in (("L", -1), ("R", 1)):
    cylinder("Headphone_Cup_" + side, (0.390 * sx, -0.245, 1.490), 0.125, 0.075, HOODIE, "Chest", (0, math.radians(90), 0), 36)
    cylinder("Headphone_GoldRing_" + side, (0.430 * sx, -0.245, 1.490), 0.095, 0.014, GOLD, "Chest", (0, math.radians(90), 0), 36)
    cylinder("Headphone_Center_" + side, (0.440 * sx, -0.245, 1.490), 0.060, 0.010, HOODIE_SOFT, "Chest", (0, math.radians(90), 0), 30)

# ---------- Black canvas sneakers ----------
for side, sx in (("L", -1), ("R", 1)):
    bone = "Foot." + side
    sphere("Sneaker_" + side, (0.215 * sx, -0.125, 0.155), (0.190, 0.245, 0.100), SHOE, bone, 34, 16)
    sphere("Sneaker_Toe_" + side, (0.215 * sx, -0.305, 0.155), (0.178, 0.083, 0.073), SOLE, bone, 30, 14)
    sphere("Sneaker_Sole_" + side, (0.215 * sx, -0.140, 0.085), (0.202, 0.255, 0.030), SOLE, bone, 30, 12)
    for li, yy in enumerate((-0.220, -0.185, -0.150)):
        curve("Sneaker_Lace_%s_%d" % (side, li), [((0.215 * sx) - 0.090, yy, 0.220), (0.215 * sx, yy - 0.010, 0.230), ((0.215 * sx) + 0.090, yy, 0.220)], 0.0045, LACE, bone)

# ---------- Handheld microphone in viewer-left paw ----------
cylinder("Mic_Handle", (-0.548, -0.180, 0.960), 0.028, 0.280, MIC, "Hand.L", (math.radians(-8), 0, 0), 28)
cylinder("Mic_GoldCollar", (-0.548, -0.198, 1.095), 0.038, 0.026, GOLD_DARK, "Hand.L", (math.radians(-8), 0, 0), 28)
sphere("Mic_Grille", (-0.548, -0.215, 1.150), (0.071, 0.067, 0.079), MIC, "Hand.L", 34, 16)
for ridx, rz in enumerate((1.128, 1.150, 1.172)):
    torus("Mic_GrilleRing_%d" % ridx, (-0.548, -0.215, rz), 0.061, 0.0035, GOLD_DARK, "Hand.L", scale=(1.0, 1.0, 0.82))

# Keep the source's excellent articulated tail; it is now gray fur and uses Tail1..Tail6.

# Default pose for preview/export validation.
arm.animation_data_create()
if bpy.data.actions.get("DefaultAnim") is not None:
    arm.animation_data.action = bpy.data.actions.get("DefaultAnim")
scene.frame_set(1)

# Export only the character objects; no preview studio objects yet.
os.makedirs(os.path.dirname(OUT), exist_ok=True)
bpy.ops.object.select_all(action="SELECT")
bpy.context.view_layer.objects.active = arm
print("MARTIN_V24_RIG bones=%d body_verts=%d meshes=%d actions=%s" % (
    len(arm.data.bones), len(cat.data.vertices), len([o for o in bpy.data.objects if o.type == "MESH"]), ",".join(a.name for a in bpy.data.actions)
))
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
if not os.path.exists(OUT) or os.path.getsize(OUT) < 350000:
    raise RuntimeError("Martin v2.4 GLB export missing or unexpectedly small")
print("MARTIN_V24_GLB_OK", OUT, os.path.getsize(OUT))

if PREVIEW:
    engines = {x.identifier for x in bpy.types.RenderSettings.bl_rna.properties["engine"].enum_items}
    scene.render.engine = "BLENDER_EEVEE_NEXT" if "BLENDER_EEVEE_NEXT" in engines else "BLENDER_EEVEE"
    scene.render.resolution_x = 720
    scene.render.resolution_y = 960
    scene.render.resolution_percentage = 100
    scene.view_settings.look = "AgX - Medium High Contrast" if "AgX - Medium High Contrast" in [i.name for i in bpy.types.ColorManagedViewSettings.bl_rna.properties['look'].enum_items] else scene.view_settings.look

    world = bpy.data.worlds.get("MartinWorldV24") or bpy.data.worlds.new("MartinWorldV24")
    scene.world = world
    world.use_nodes = True
    world.node_tree.nodes["Background"].inputs["Color"].default_value = (0.003, 0.003, 0.005, 1)
    world.node_tree.nodes["Background"].inputs["Strength"].default_value = 0.10

    def point_at(obj, target):
        obj.rotation_euler = (Vector(target) - obj.location).to_track_quat("-Z", "Y").to_euler()

    cam_data = bpy.data.cameras.new("MartinPreviewCameraData")
    cam = bpy.data.objects.new("MartinPreviewCamera", cam_data)
    scene.collection.objects.link(cam)
    cam.location = (0.0, -6.55, 1.40)
    cam.data.lens = 66
    point_at(cam, (0.0, -0.02, 1.33))
    scene.camera = cam

    for idx, (loc, energy, size, color) in enumerate([
        ((-2.6, -3.2, 4.0), 900, 3.8, (1.0, 0.68, 0.44)),
        ((2.3, -2.2, 3.2), 420, 3.0, (0.35, 0.45, 0.85)),
        ((2.0, 1.4, 3.7), 720, 2.4, (1.0, 0.40, 0.14)),
        ((0.0, -2.7, 2.2), 160, 1.8, (1.0, 0.88, 0.72)),
    ]):
        ld = bpy.data.lights.new("MartinPreviewLightData%d" % idx, "AREA")
        light = bpy.data.objects.new("MartinPreviewLight%d" % idx, ld)
        scene.collection.objects.link(light)
        light.location = loc
        light.data.energy = energy
        light.data.shape = "DISK"
        light.data.size = size
        light.data.color = color
        point_at(light, (0.0, 0.0, 1.35))

    floor_mesh = bpy.data.meshes.new("MartinPreviewFloorMesh")
    floor_mesh.from_pydata([(-8, -8, 0), (8, -8, 0), (8, 8, 0), (-8, 8, 0)], [], [(0, 1, 2, 3)])
    floor_mesh.update()
    floor = bpy.data.objects.new("MartinPreviewFloor", floor_mesh)
    scene.collection.objects.link(floor)
    floor.location.z = -0.015
    assign(floor, mat("MartinPreviewFloorMat", (0.010, 0.008, 0.012), roughness=0.70))

    scene.render.filepath = PREVIEW
    bpy.ops.render.render(write_still=True)
    print("MARTIN_V24_PREVIEW_OK", PREVIEW)
