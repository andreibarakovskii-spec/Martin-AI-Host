import bpy
import math
import os
from mathutils import Vector

OUT = os.environ.get("MARTIN_OUT", "/tmp/martin.glb")
PREVIEW = os.environ.get("MARTIN_PREVIEW", "")

print("MARTIN_V21_BLENDER", bpy.app.version_string)

# Deterministic empty scene.
bpy.ops.object.select_all(action="SELECT")
bpy.ops.object.delete(use_global=False)
scene = bpy.context.scene
scene.frame_start = 1
scene.frame_end = 48
scene.render.fps = 24


def material(name, rgb, metallic=0.0, roughness=0.65, alpha=1.0, emission=None):
    m = bpy.data.materials.new(name)
    m.use_nodes = True
    m.diffuse_color = (*rgb, alpha)
    bsdf = m.node_tree.nodes.get("Principled BSDF")
    if bsdf:
        if "Base Color" in bsdf.inputs:
            bsdf.inputs["Base Color"].default_value = (*rgb, 1.0)
        if "Metallic" in bsdf.inputs:
            bsdf.inputs["Metallic"].default_value = metallic
        if "Roughness" in bsdf.inputs:
            bsdf.inputs["Roughness"].default_value = roughness
        if "Alpha" in bsdf.inputs:
            bsdf.inputs["Alpha"].default_value = alpha
        if emission is not None:
            ek = "Emission Color" if "Emission Color" in bsdf.inputs else "Emission"
            if ek in bsdf.inputs:
                bsdf.inputs[ek].default_value = (*emission, 1.0)
            if "Emission Strength" in bsdf.inputs:
                bsdf.inputs["Emission Strength"].default_value = 0.45
    if alpha < 0.999:
        m.blend_method = "BLEND"
        if hasattr(m, "show_transparent_back"):
            m.show_transparent_back = False
    return m


FUR = material("Martin Fur Grey", (0.245, 0.265, 0.305), roughness=0.84)
FUR_LIGHT = material("Martin Fur Silver", (0.40, 0.425, 0.475), roughness=0.86)
FUR_DARK = material("Martin Fur Dark", (0.115, 0.13, 0.16), roughness=0.88)
PINK = material("Martin Warm Pink", (0.72, 0.38, 0.43), roughness=0.58)
HOODIE = material("Martin Hoodie Black", (0.015, 0.018, 0.025), roughness=0.74)
CLOTH = material("Martin Cloth Detail", (0.038, 0.045, 0.060), roughness=0.82)
GOLD = material("Martin Gold", (0.78, 0.48, 0.105), metallic=0.78, roughness=0.24)
GOLD_DARK = material("Martin Dark Gold", (0.28, 0.15, 0.030), metallic=0.68, roughness=0.32)
WHITE = material("Martin Eye White", (0.94, 0.95, 0.96), roughness=0.16)
IRIS = material("Martin Iris Amber", (0.94, 0.48, 0.055), metallic=0.08, roughness=0.18, emission=(0.20, 0.055, 0.005))
PUPIL = material("Martin Pupil", (0.003, 0.003, 0.005), roughness=0.08)
CORNEA = material("Martin Cornea", (0.70, 0.82, 1.0), roughness=0.025, alpha=0.13)
MOUTH = material("Martin Mouth", (0.020, 0.006, 0.010), roughness=0.48)
SOLE = material("Martin Shoe Sole", (0.13, 0.14, 0.16), roughness=0.76)


def smooth(obj):
    if obj.type == "MESH":
        for p in obj.data.polygons:
            p.use_smooth = True


def assign(obj, mat):
    obj.data.materials.clear()
    obj.data.materials.append(mat)
    smooth(obj)


# ---------- Skeleton ----------
bpy.ops.object.armature_add(enter_editmode=True, location=(0, 0, 0))
arm = bpy.context.object
arm.name = "MartinSkeleton"
arm.data.name = "MartinSkeleton"
for old in list(arm.data.edit_bones):
    arm.data.edit_bones.remove(old)

bones = {}

def add_bone(name, head, tail, parent=None):
    b = arm.data.edit_bones.new(name)
    b.head = head
    b.tail = tail
    if parent:
        b.parent = bones[parent]
    bones[name] = b


add_bone("Root", (0, 0, 0), (0, 0, 0.26))
add_bone("Hips", (0, 0, 0.76), (0, 0, 1.10), "Root")
add_bone("Spine", (0, 0, 1.10), (0, 0, 1.48), "Hips")
add_bone("Chest", (0, 0, 1.48), (0, 0, 1.93), "Spine")
add_bone("Neck", (0, 0, 1.93), (0, 0, 2.12), "Chest")
add_bone("Head", (0, 0, 2.12), (0, 0, 2.64), "Neck")
add_bone("Jaw", (0, -0.34, 2.34), (0, -0.55, 2.25), "Head")
for side, x in (("L", -0.21), ("R", 0.21)):
    add_bone("Eye." + side, (x, -0.42, 2.54), (x, -0.59, 2.54), "Head")
    add_bone("Lid." + side, (x, -0.43, 2.68), (x, -0.58, 2.68), "Head")
    ex = -0.39 if side == "L" else 0.39
    tx = -0.50 if side == "L" else 0.50
    add_bone("Ear." + side, (ex, 0, 2.83), (tx, 0, 3.22), "Head")

add_bone("UpperArm.L", (-0.48, 0, 1.79), (-0.70, 0, 1.47), "Chest")
add_bone("Forearm.L", (-0.70, 0, 1.47), (-0.73, -0.01, 1.16), "UpperArm.L")
add_bone("Hand.L", (-0.73, -0.01, 1.16), (-0.73, -0.05, 0.98), "Forearm.L")
add_bone("UpperArm.R", (0.48, 0, 1.79), (0.70, 0, 1.47), "Chest")
add_bone("Forearm.R", (0.70, 0, 1.47), (0.73, -0.01, 1.16), "UpperArm.R")
add_bone("Hand.R", (0.73, -0.01, 1.16), (0.73, -0.05, 0.98), "Forearm.R")
add_bone("Thigh.L", (-0.25, 0, 0.98), (-0.25, 0, 0.62), "Hips")
add_bone("Shin.L", (-0.25, 0, 0.62), (-0.25, 0, 0.28), "Thigh.L")
add_bone("Foot.L", (-0.25, 0, 0.28), (-0.25, -0.23, 0.11), "Shin.L")
add_bone("Thigh.R", (0.25, 0, 0.98), (0.25, 0, 0.62), "Hips")
add_bone("Shin.R", (0.25, 0, 0.62), (0.25, 0, 0.28), "Thigh.R")
add_bone("Foot.R", (0.25, 0, 0.28), (0.25, -0.23, 0.11), "Shin.R")
add_bone("Tail1", (0, 0.27, 0.94), (0.28, 0.35, 0.93), "Hips")
add_bone("Tail2", (0.28, 0.35, 0.93), (0.52, 0.34, 1.11), "Tail1")
add_bone("Tail3", (0.52, 0.34, 1.11), (0.66, 0.28, 1.39), "Tail2")
bpy.ops.object.mode_set(mode="POSE")
for pb in arm.pose.bones:
    pb.rotation_mode = "XYZ"
bpy.ops.object.mode_set(mode="OBJECT")


def make_mesh(obj):
    if obj.type != "MESH":
        bpy.ops.object.select_all(action="DESELECT")
        obj.select_set(True)
        bpy.context.view_layer.objects.active = obj
        bpy.ops.object.convert(target="MESH")
    bpy.ops.object.select_all(action="DESELECT")
    obj.select_set(True)
    bpy.context.view_layer.objects.active = obj
    bpy.ops.object.transform_apply(location=True, rotation=True, scale=True)
    smooth(obj)
    return obj


def rig(obj, bone_name):
    obj = make_mesh(obj)
    vg = obj.vertex_groups.new(name=bone_name)
    vg.add(list(range(len(obj.data.vertices))), 1.0, "REPLACE")
    mod = obj.modifiers.new("MartinArmature", "ARMATURE")
    mod.object = arm
    mod.use_vertex_groups = True
    return obj


def sphere(name, loc, scale, mat, bone, segments=36, rings=18):
    bpy.ops.mesh.primitive_uv_sphere_add(segments=segments, ring_count=rings, location=loc)
    obj = bpy.context.object
    obj.name = name
    obj.scale = scale
    assign(obj, mat)
    return rig(obj, bone)


def cylinder(name, loc, radius, depth, mat, bone, rot=(0, 0, 0), vertices=32):
    bpy.ops.mesh.primitive_cylinder_add(vertices=vertices, radius=radius, depth=depth, location=loc, rotation=rot)
    obj = bpy.context.object
    obj.name = name
    assign(obj, mat)
    bevel = obj.modifiers.new("SoftEdges", "BEVEL")
    bevel.width = min(radius * 0.18, depth * 0.05)
    bevel.segments = 2
    return rig(obj, bone)


def torus(name, loc, major, minor, mat, bone, rot=(0, 0, 0), scale=(1, 1, 1)):
    bpy.ops.mesh.primitive_torus_add(major_segments=40, minor_segments=10, major_radius=major, minor_radius=minor, location=loc, rotation=rot)
    obj = bpy.context.object
    obj.name = name
    obj.scale = scale
    assign(obj, mat)
    return rig(obj, bone)


def ear(name, loc, width, height, depth, mat, bone, forward=-0.01):
    w = width * 0.5
    d = depth * 0.5
    verts = [(-w, -d + forward, 0), (w, -d + forward, 0), (0, -d + forward, height),
             (-w, d + forward, 0), (w, d + forward, 0), (0, d + forward, height)]
    faces = [(0, 1, 2), (5, 4, 3), (0, 3, 4, 1), (1, 4, 5, 2), (2, 5, 3, 0)]
    mesh = bpy.data.meshes.new(name + "Mesh")
    mesh.from_pydata(verts, [], faces)
    mesh.update()
    obj = bpy.data.objects.new(name, mesh)
    scene.collection.objects.link(obj)
    obj.location = loc
    assign(obj, mat)
    bevel = obj.modifiers.new("SoftEar", "BEVEL")
    bevel.width = 0.028
    bevel.segments = 3
    return rig(obj, bone)


def curve(name, pts, bevel, mat, bone):
    data = bpy.data.curves.new(name + "Curve", "CURVE")
    data.dimensions = "3D"
    data.resolution_u = 2
    data.bevel_depth = bevel
    data.bevel_resolution = 3
    spl = data.splines.new("BEZIER")
    spl.bezier_points.add(len(pts) - 1)
    for p, co in zip(spl.bezier_points, pts):
        p.co = co
        p.handle_left_type = "AUTO"
        p.handle_right_type = "AUTO"
    obj = bpy.data.objects.new(name, data)
    scene.collection.objects.link(obj)
    data.materials.append(mat)
    return rig(obj, bone)


def logo_text():
    bpy.ops.object.text_add(location=(0, -0.405, 1.56), rotation=(math.radians(90), 0, 0))
    obj = bpy.context.object
    obj.name = "Logo_M"
    obj.data.body = "M"
    obj.data.align_x = "CENTER"
    obj.data.align_y = "CENTER"
    obj.data.size = 0.38
    obj.data.extrude = 0.020
    obj.data.bevel_depth = 0.006
    obj.data.bevel_resolution = 2
    obj.data.materials.append(GOLD)
    return rig(obj, "Chest")


# ---------- Body and clothing ----------
sphere("Hoodie_Torso", (0, 0.02, 1.53), (0.57, 0.36, 0.68), HOODIE, "Chest", 40, 20)
sphere("Hoodie_Belly", (0, -0.31, 1.45), (0.47, 0.095, 0.49), CLOTH, "Chest", 32, 16)
torus("Hoodie_Hood", (0, 0.015, 2.00), 0.37, 0.085, CLOTH, "Chest", scale=(1.0, 0.74, 1.0))
logo_text()
curve("Hoodie_String_L", [(-0.10, -0.395, 1.93), (-0.12, -0.43, 1.72), (-0.13, -0.43, 1.57)], 0.012, GOLD_DARK, "Chest")
curve("Hoodie_String_R", [(0.10, -0.395, 1.93), (0.12, -0.43, 1.72), (0.13, -0.43, 1.57)], 0.012, GOLD_DARK, "Chest")

# ---------- Head ----------
sphere("Fur_Head", (0, 0, 2.54), (0.59, 0.49, 0.59), FUR, "Head", 48, 24)
sphere("Fur_Cheek_L", (-0.255, -0.405, 2.38), (0.30, 0.16, 0.23), FUR_LIGHT, "Head", 36, 18)
sphere("Fur_Cheek_R", (0.255, -0.405, 2.38), (0.30, 0.16, 0.23), FUR_LIGHT, "Head", 36, 18)
sphere("Fur_Muzzle", (0, -0.505, 2.32), (0.27, 0.12, 0.17), FUR_LIGHT, "Head", 36, 18)
sphere("Fur_Chin", (0, -0.46, 2.19), (0.20, 0.10, 0.11), FUR_LIGHT, "Jaw", 30, 15)

ear("Fur_Ear_L", (-0.385, 0.0, 2.84), 0.34, 0.48, 0.15, FUR, "Ear.L")
ear("Fur_Ear_R", (0.385, 0.0, 2.84), 0.34, 0.48, 0.15, FUR, "Ear.R")
ear("Ear_Inner_L", (-0.385, -0.086, 2.90), 0.19, 0.30, 0.026, PINK, "Ear.L", -0.02)
ear("Ear_Inner_R", (0.385, -0.086, 2.90), 0.19, 0.30, 0.026, PINK, "Ear.R", -0.02)

for side, x in (("L", -0.205), ("R", 0.205)):
    sphere("Eye_Sclera_" + side, (x, -0.485, 2.55), (0.165, 0.075, 0.190), WHITE, "Eye." + side, 40, 20)
    sphere("Iris_Amber_" + side, (x, -0.552, 2.55), (0.088, 0.020, 0.108), IRIS, "Eye." + side, 32, 16)
    sphere("Pupil_" + side, (x, -0.570, 2.55), (0.032, 0.010, 0.070), PUPIL, "Eye." + side, 24, 12)
    sphere("Cornea_" + side, (x, -0.563, 2.55), (0.171, 0.022, 0.196), CORNEA, "Eye." + side, 40, 20)
    sphere("Eye_Highlight_" + side, (x - 0.042, -0.585, 2.615), (0.021, 0.006, 0.027), WHITE, "Eye." + side, 20, 10)
    sphere("Fur_Lid_" + side, (x, -0.568, 2.695), (0.18, 0.025, 0.040), FUR_DARK, "Lid." + side, 30, 12)

sphere("Nose", (0, -0.630, 2.38), (0.090, 0.047, 0.056), PINK, "Head", 28, 14)
sphere("Mouth_Interior", (0, -0.580, 2.245), (0.105, 0.020, 0.052), MOUTH, "Jaw", 24, 12)
curve("Smile_L", [(0, -0.622, 2.28), (-0.07, -0.628, 2.235), (-0.15, -0.610, 2.24)], 0.012, MOUTH, "Jaw")
curve("Smile_R", [(0, -0.622, 2.28), (0.07, -0.628, 2.235), (0.15, -0.610, 2.24)], 0.012, MOUTH, "Jaw")
for side, sx in (("L", -1), ("R", 1)):
    for idx, dz in enumerate((0.045, 0.0, -0.045)):
        curve("Whisker_%s_%d" % (side, idx), [(0.15 * sx, -0.57, 2.34 + dz), (0.34 * sx, -0.61, 2.35 + dz * 0.45), (0.50 * sx, -0.57, 2.36 + dz * 0.15)], 0.006, FUR_LIGHT, "Head")

# ---------- Limbs ----------
for side, sx in (("L", -1), ("R", 1)):
    sphere("Hoodie_UpperArm_" + side, (0.60 * sx, 0, 1.61), (0.20, 0.21, 0.36), HOODIE, "UpperArm." + side, 28, 14)
    sphere("Hoodie_Forearm_" + side, (0.72 * sx, -0.01, 1.30), (0.17, 0.18, 0.29), CLOTH, "Forearm." + side, 28, 14)
    sphere("Fur_Paw_" + side, (0.73 * sx, -0.065, 1.05), (0.19, 0.17, 0.19), FUR_LIGHT, "Hand." + side, 30, 15)
    sphere("Pants_Thigh_" + side, (0.25 * sx, 0, 0.78), (0.235, 0.245, 0.36), HOODIE, "Thigh." + side, 28, 14)
    sphere("Pants_Shin_" + side, (0.25 * sx, -0.01, 0.44), (0.205, 0.215, 0.29), CLOTH, "Shin." + side, 28, 14)
    sphere("Shoe_" + side, (0.25 * sx, -0.16, 0.18), (0.25, 0.33, 0.145), HOODIE, "Foot." + side, 28, 14)
    sphere("Shoe_Sole_" + side, (0.25 * sx, -0.19, 0.095), (0.26, 0.34, 0.050), SOLE, "Foot." + side, 28, 12)

sphere("Fur_Tail1", (0.17, 0.32, 0.98), (0.28, 0.145, 0.15), FUR, "Tail1", 28, 14)
sphere("Fur_Tail2", (0.40, 0.33, 1.05), (0.26, 0.14, 0.15), FUR, "Tail2", 28, 14)
sphere("Fur_Tail3", (0.59, 0.29, 1.27), (0.15, 0.14, 0.27), FUR, "Tail3", 28, 14)

# ---------- Headphones and microphone ----------
headband_points = []
for i in range(13):
    a = math.radians(-72 + 144 * i / 12.0)
    headband_points.append((0.62 * math.sin(a), 0.02, 2.67 + 0.62 * math.cos(a)))
curve("Headphone_Band", headband_points, 0.045, HOODIE, "Head")
for side, sx in (("L", -1), ("R", 1)):
    cylinder("Headphone_Cup_" + side, (0.605 * sx, -0.005, 2.54), 0.18, 0.105, HOODIE, "Head", (0, math.radians(90), 0), 36)
    cylinder("Headphone_Gold_" + side, (0.663 * sx, -0.005, 2.54), 0.135, 0.018, GOLD, "Head", (0, math.radians(90), 0), 36)

cylinder("Mic_Handle", (0.73, -0.125, 1.08), 0.037, 0.28, GOLD_DARK, "Hand.R", (math.radians(10), 0, 0), 28)
sphere("Mic_Grille", (0.73, -0.170, 1.24), (0.095, 0.088, 0.105), GOLD, "Hand.R", 28, 14)

# ---------- Animation ----------
arm.animation_data_create()
KEY_BONES = ["Root", "Hips", "Spine", "Chest", "Neck", "Head", "Jaw", "UpperArm.L", "UpperArm.R", "Forearm.L", "Forearm.R", "Thigh.L", "Thigh.R", "Shin.L", "Shin.R", "Tail1", "Tail2", "Tail3", "Ear.L", "Ear.R"]


def reset_pose():
    for pb in arm.pose.bones:
        pb.rotation_mode = "XYZ"
        pb.rotation_euler = (0, 0, 0)
        pb.location = (0, 0, 0)
        pb.scale = (1, 1, 1)


def key_pose(frame, rotations=None, locations=None):
    reset_pose()
    rotations = rotations or {}
    locations = locations or {}
    for name, value in rotations.items():
        pb = arm.pose.bones.get(name)
        if pb:
            pb.rotation_euler = value
    for name, value in locations.items():
        pb = arm.pose.bones.get(name)
        if pb:
            pb.location = value
    for name in KEY_BONES:
        pb = arm.pose.bones.get(name)
        if pb:
            pb.keyframe_insert("rotation_euler", frame=frame, group=name)
            pb.keyframe_insert("location", frame=frame, group=name)


def action(name, poses):
    act = bpy.data.actions.new(name)
    act.use_fake_user = True
    arm.animation_data.action = act
    for frame, rotations, locations in poses:
        key_pose(frame, rotations, locations)
    for fc in act.fcurves:
        for kp in fc.keyframe_points:
            kp.interpolation = "BEZIER"
    print("MARTIN_ACTION", name, "curves=", len(act.fcurves))
    return act


idle = [
    (1, {"Chest": (0.012, 0, 0), "Head": (-0.008, 0, -0.012), "Tail1": (0, 0.04, 0.05), "Ear.L": (0, 0, 0.020), "Ear.R": (0, 0, -0.015)}, {}),
    (24, {"Chest": (-0.012, 0, 0), "Head": (0.010, 0, 0.015), "Tail1": (0, -0.05, -0.05), "Tail2": (0, 0.04, 0.06), "Ear.L": (0, 0, -0.015), "Ear.R": (0, 0, 0.021)}, {"Hips": (0, 0, 0.010)}),
    (48, {"Chest": (0.012, 0, 0), "Head": (-0.008, 0, -0.012), "Tail1": (0, 0.04, 0.05), "Ear.L": (0, 0, 0.020), "Ear.R": (0, 0, -0.015)}, {})]
action("DefaultAnim", idle)
action("Listening", idle)
action("Talking", idle)
action("Cheer", [
    (1, {}, {}),
    (12, {"UpperArm.L": (0, -1.15, -0.20), "UpperArm.R": (0, 1.05, 0.16), "Forearm.L": (0, -0.35, 0), "Forearm.R": (0, 0.30, 0), "Head": (-0.08, 0, 0.04), "Tail2": (0, 0.22, 0.16)}, {"Hips": (0, 0, 0.045)}),
    (28, {"UpperArm.L": (0, -0.85, 0.14), "UpperArm.R": (0, 0.92, -0.12), "Head": (0.05, 0, -0.06), "Tail2": (0, -0.20, -0.14)}, {"Hips": (0, 0, 0.015)}),
    (48, {}, {})])
action("Walk", [
    (1, {"UpperArm.L": (0, 0.26, 0), "UpperArm.R": (0, -0.26, 0), "Thigh.L": (0, -0.26, 0), "Thigh.R": (0, 0.26, 0)}, {}),
    (13, {"UpperArm.L": (0, -0.26, 0), "UpperArm.R": (0, 0.26, 0), "Thigh.L": (0, 0.26, 0), "Thigh.R": (0, -0.26, 0), "Tail1": (0, 0.10, 0)}, {"Hips": (0, 0, 0.020)}),
    (25, {"UpperArm.L": (0, 0.26, 0), "UpperArm.R": (0, -0.26, 0), "Thigh.L": (0, -0.26, 0), "Thigh.R": (0, 0.26, 0)}, {}),
    (37, {"UpperArm.L": (0, -0.26, 0), "UpperArm.R": (0, 0.26, 0), "Thigh.L": (0, 0.26, 0), "Thigh.R": (0, -0.26, 0), "Tail1": (0, -0.10, 0)}, {"Hips": (0, 0, 0.020)}),
    (48, {"UpperArm.L": (0, 0.26, 0), "UpperArm.R": (0, -0.26, 0), "Thigh.L": (0, -0.26, 0), "Thigh.R": (0, 0.26, 0)}, {})])
action("Run", [
    (1, {"UpperArm.L": (0, 0.50, 0), "UpperArm.R": (0, -0.50, 0), "Thigh.L": (0, -0.48, 0), "Thigh.R": (0, 0.48, 0), "Chest": (0.07, 0, 0)}, {}),
    (12, {"UpperArm.L": (0, -0.50, 0), "UpperArm.R": (0, 0.50, 0), "Thigh.L": (0, 0.48, 0), "Thigh.R": (0, -0.48, 0), "Chest": (0.09, 0, 0), "Tail1": (0, 0.18, 0)}, {"Hips": (0, 0, 0.040)}),
    (24, {"UpperArm.L": (0, 0.50, 0), "UpperArm.R": (0, -0.50, 0), "Thigh.L": (0, -0.48, 0), "Thigh.R": (0, 0.48, 0), "Chest": (0.07, 0, 0)}, {}),
    (36, {"UpperArm.L": (0, -0.50, 0), "UpperArm.R": (0, 0.50, 0), "Thigh.L": (0, 0.48, 0), "Thigh.R": (0, -0.48, 0), "Chest": (0.09, 0, 0), "Tail1": (0, -0.18, 0)}, {"Hips": (0, 0, 0.040)}),
    (48, {"UpperArm.L": (0, 0.50, 0), "UpperArm.R": (0, -0.50, 0), "Thigh.L": (0, -0.48, 0), "Thigh.R": (0, 0.48, 0), "Chest": (0.07, 0, 0)}, {})])
action("Dance", [
    (1, {"UpperArm.L": (0, -0.55, -0.32), "UpperArm.R": (0, 0.48, 0.30), "Chest": (0, 0, -0.08)}, {}),
    (16, {"UpperArm.L": (0, 0.38, 0.30), "UpperArm.R": (0, 0.90, -0.24), "Chest": (0, 0, 0.11), "Head": (0, 0, -0.08)}, {"Hips": (0, 0, 0.030)}),
    (32, {"UpperArm.L": (0, -0.90, -0.24), "UpperArm.R": (0, -0.38, 0.30), "Chest": (0, 0, -0.11), "Head": (0, 0, 0.08)}, {}),
    (48, {"UpperArm.L": (0, -0.55, -0.32), "UpperArm.R": (0, 0.48, 0.30), "Chest": (0, 0, -0.08)}, {})])
arm.animation_data.action = bpy.data.actions.get("DefaultAnim")
scene.frame_set(1)

mesh_count = len([o for o in scene.objects if o.type == "MESH"])
print("MARTIN_V21_RIG bones=", len(arm.data.bones), "meshes=", mesh_count, "actions=", ",".join(a.name for a in bpy.data.actions))

# Export real runtime model.
os.makedirs(os.path.dirname(OUT), exist_ok=True)
bpy.ops.object.select_all(action="SELECT")
bpy.ops.export_scene.gltf(filepath=OUT, export_format="GLB", export_apply=False, export_animations=True, export_skins=True, export_morph=True, export_all_influences=True, export_yup=True, export_cameras=False, export_lights=False)
if not os.path.exists(OUT) or os.path.getsize(OUT) < 200000:
    raise RuntimeError("Martin v2.1 GLB export missing or too small")
print("MARTIN_V21_GLB_OK", OUT, os.path.getsize(OUT))

# Studio preview from the same scene, not a mockup.
if PREVIEW:
    scene.render.engine = "BLENDER_EEVEE_NEXT" if "BLENDER_EEVEE_NEXT" in {x.identifier for x in bpy.types.RenderSettings.bl_rna.properties["engine"].enum_items} else "BLENDER_EEVEE"
    scene.render.resolution_x = 720
    scene.render.resolution_y = 960
    scene.render.resolution_percentage = 100
    world = bpy.data.worlds.new("MartinWorld")
    scene.world = world
    world.use_nodes = True
    world.node_tree.nodes["Background"].inputs["Color"].default_value = (0.006, 0.007, 0.012, 1)
    world.node_tree.nodes["Background"].inputs["Strength"].default_value = 0.20

    def point_at(obj, target):
        obj.rotation_euler = (Vector(target) - obj.location).to_track_quat("-Z", "Y").to_euler()

    bpy.ops.object.camera_add(location=(0, -8.4, 2.12))
    cam = bpy.context.object
    cam.data.lens = 62
    scene.camera = cam
    point_at(cam, (0, 0, 1.62))

    lights = [
        ((-3.0, -4.0, 4.8), 1050, 4.0, (1.0, 0.72, 0.48)),
        ((3.0, -2.8, 3.4), 650, 3.0, (0.42, 0.50, 1.0)),
        ((2.6, 2.0, 4.5), 900, 2.5, (0.78, 0.40, 1.0)),
    ]
    for idx, (loc, energy, size, color) in enumerate(lights):
        bpy.ops.object.light_add(type="AREA", location=loc)
        light = bpy.context.object
        light.name = "StudioLight_%d" % idx
        light.data.energy = energy
        light.data.size = size
        light.data.color = color
        point_at(light, (0, 0, 1.7))

    bpy.ops.mesh.primitive_plane_add(size=20, location=(0, 0, -0.02))
    floor = bpy.context.object
    floor.name = "PreviewFloor"
    assign(floor, material("Preview Floor", (0.017, 0.014, 0.022), roughness=0.58))
    scene.render.filepath = PREVIEW
    bpy.ops.render.render(write_still=True)
    print("MARTIN_V21_PREVIEW_OK", PREVIEW)
