import bpy
import math
import os
from mathutils import Vector

OUT = os.environ.get('MARTIN_OUT', '/tmp/martin.glb')
TEXTURE = os.environ.get('MARTIN_TEXTURE', '')

print('MARTIN_BLENDER_VERSION', bpy.app.version_string)
print('MARTIN_OBJECTS_BEGIN')
for o in bpy.data.objects:
    print('OBJ', o.name, o.type)
print('MARTIN_OBJECTS_END')

armatures = [o for o in bpy.data.objects if o.type == 'ARMATURE']
if not armatures:
    raise RuntimeError('No armature found in CatPilot source')
arm = max(armatures, key=lambda o: len(o.data.bones))
print('MARTIN_ARMATURE', arm.name, 'bones=', len(arm.data.bones))
print('MARTIN_BONES', ','.join(b.name for b in arm.data.bones))
print('MARTIN_ACTIONS', ','.join(a.name for a in bpy.data.actions))

# Old Blender files can open with a stale mode/active object. Normalize the context once,
# then avoid context-sensitive bpy.ops for non-mesh helpers.
try:
    if bpy.context.object is not None and bpy.context.object.mode != 'OBJECT':
        bpy.ops.object.mode_set(mode='OBJECT')
except Exception as exc:
    print('MARTIN_MODE_NORMALIZE_WARN', repr(exc))
for obj in bpy.context.selected_objects:
    obj.select_set(False)
arm.select_set(True)
bpy.context.view_layer.objects.active = arm

# Remove scene-only helpers and obvious pilot accessories when they are separate objects.
remove_tokens = ('camera', 'light', 'plane', 'propeller', 'goggle', 'glass', 'hook', 'bag', 'satchel')
for obj in list(bpy.data.objects):
    low = obj.name.lower()
    if obj is arm:
        continue
    if obj.type in {'CAMERA', 'LIGHT'} or any(t in low for t in remove_tokens):
        bpy.data.objects.remove(obj, do_unlink=True)

# Re-establish a valid active object after deleting helpers from the legacy source file.
arm.select_set(True)
bpy.context.view_layer.objects.active = arm

# Replace texture references with the generated Martin texture while preserving source UVs.
martin_image = None
if TEXTURE and os.path.exists(TEXTURE):
    martin_image = bpy.data.images.load(TEXTURE, check_existing=False)
    martin_image.name = 'MartinTexture'
    for mat in bpy.data.materials:
        if not mat.use_nodes:
            continue
        for node in mat.node_tree.nodes:
            if node.type == 'TEX_IMAGE' and node.image is not None:
                node.image = martin_image
                print('MARTIN_TEXTURE_REBOUND', mat.name, node.name)

mesh_objects = [o for o in bpy.data.objects if o.type == 'MESH']
if not mesh_objects:
    raise RuntimeError('No cat mesh found in CatPilot source')
for obj in mesh_objects:
    for poly in obj.data.polygons:
        poly.use_smooth = True


def material(name, color, metallic=0.0, roughness=0.6, emission=None):
    m = bpy.data.materials.get(name) or bpy.data.materials.new(name)
    m.diffuse_color = (*color, 1.0)
    m.use_nodes = True
    bsdf = m.node_tree.nodes.get('Principled BSDF')
    if bsdf:
        if 'Base Color' in bsdf.inputs:
            bsdf.inputs['Base Color'].default_value = (*color, 1.0)
        if 'Metallic' in bsdf.inputs:
            bsdf.inputs['Metallic'].default_value = metallic
        if 'Roughness' in bsdf.inputs:
            bsdf.inputs['Roughness'].default_value = roughness
        if emission is not None:
            if 'Emission Color' in bsdf.inputs:
                bsdf.inputs['Emission Color'].default_value = (*emission, 1.0)
                if 'Emission Strength' in bsdf.inputs:
                    bsdf.inputs['Emission Strength'].default_value = 1.8
            elif 'Emission' in bsdf.inputs:
                bsdf.inputs['Emission'].default_value = (*emission, 1.0)
    return m

MAT_BLACK = material('MartinHoodieBlack', (0.012, 0.014, 0.022), 0.02, 0.48)
MAT_METAL = material('MartinMicMetal', (0.055, 0.06, 0.075), 0.65, 0.26)
MAT_GRILL = material('MartinMicGrill', (0.12, 0.125, 0.15), 0.75, 0.20)
MAT_VIOLET = material('MartinViolet', (0.38, 0.08, 0.85), 0.12, 0.32, (0.25, 0.03, 0.75))


def world_bounds(objects):
    pts = []
    for obj in objects:
        if obj.type != 'MESH':
            continue
        for corner in obj.bound_box:
            pts.append(obj.matrix_world @ Vector(corner))
    if not pts:
        return Vector((-1,-1,-1)), Vector((1,1,1))
    lo = Vector((min(p.x for p in pts), min(p.y for p in pts), min(p.z for p in pts)))
    hi = Vector((max(p.x for p in pts), max(p.y for p in pts), max(p.z for p in pts)))
    return lo, hi

lo, hi = world_bounds(mesh_objects)
size = hi - lo
center = (hi + lo) * 0.5
height = max(size.z, size.y, 0.001)
print('MARTIN_BOUNDS', tuple(round(v,4) for v in lo), tuple(round(v,4) for v in hi), 'size=', tuple(round(v,4) for v in size))
vertical_y = size.y >= size.z
print('MARTIN_UP_AXIS', 'Y' if vertical_y else 'Z')


def set_mat(obj, mat):
    if obj.data and hasattr(obj.data, 'materials'):
        obj.data.materials.clear()
        obj.data.materials.append(mat)


def prep_mesh_operator():
    # Mesh primitive operators are safe in background mode once the legacy file is in OBJECT mode
    # with a valid active object/view layer. This is called before every primitive for robustness.
    try:
        if bpy.context.object is not None and bpy.context.object.mode != 'OBJECT':
            bpy.ops.object.mode_set(mode='OBJECT')
    except Exception:
        pass
    if arm.name in bpy.context.view_layer.objects:
        bpy.context.view_layer.objects.active = arm


def add_uv_sphere(name, location, scale, mat):
    prep_mesh_operator()
    bpy.ops.mesh.primitive_uv_sphere_add(segments=32, ring_count=16, location=location)
    o = bpy.context.object
    o.name = name
    o.scale = scale
    set_mat(o, mat)
    for p in o.data.polygons:
        p.use_smooth = True
    return o


def add_cylinder(name, location, radius, depth, mat, rotation=(0,0,0)):
    prep_mesh_operator()
    bpy.ops.mesh.primitive_cylinder_add(vertices=32, radius=radius, depth=depth, location=location, rotation=rotation)
    o = bpy.context.object
    o.name = name
    set_mat(o, mat)
    for p in o.data.polygons:
        p.use_smooth = True
    return o


def add_torus(name, location, major, minor, mat, rotation=(0,0,0)):
    prep_mesh_operator()
    bpy.ops.mesh.primitive_torus_add(major_radius=major, minor_radius=minor, major_segments=40, minor_segments=12, location=location, rotation=rotation)
    o = bpy.context.object
    o.name = name
    set_mat(o, mat)
    for p in o.data.polygons:
        p.use_smooth = True
    return o


def find_bone(tokens):
    candidates = []
    for bone in arm.data.bones:
        low = bone.name.lower().replace('-', '_').replace('.', '_')
        score = sum(1 for t in tokens if t in low)
        if score:
            candidates.append((score, bone.name))
    candidates.sort(reverse=True)
    return candidates[0][1] if candidates else None

hand_bone = find_bone(('hand_l','handleft','left_hand','wrist_l','paw_l','l_hand','hand'))
chest_bone = find_bone(('chest','spine2','spine_2','spine1','spine','torso'))
head_bone = find_bone(('head','neck'))
print('MARTIN_ATTACH_BONES', 'hand=', hand_bone, 'chest=', chest_bone, 'head=', head_bone)


def attach_to_bone(obj, bone_name, local_location, local_rotation=(0,0,0)):
    if not bone_name:
        return False
    obj.parent = arm
    obj.parent_type = 'BONE'
    obj.parent_bone = bone_name
    obj.location = local_location
    obj.rotation_euler = local_rotation
    return True

# Empty is created through the Data API because bpy.ops.object.empty_add() is invalid
# in the UI context stored in this legacy Blender 2.77 source file when opened headlessly.
mic_root = bpy.data.objects.new('Martin_Microphone_Root', None)
bpy.context.scene.collection.objects.link(mic_root)
if hand_bone:
    attach_to_bone(mic_root, hand_bone, (0.0, 0.0, 0.0), (math.radians(8), math.radians(4), math.radians(-18)))
else:
    mic_root.location = center

S = max(height, 1.0)
handle = add_cylinder('Martin_Microphone_Handle', (0,0,0), S*0.018, S*0.22, MAT_METAL, (math.radians(90),0,0))
grille = add_uv_sphere('Martin_Microphone_Grille', (0, S*0.13, 0), (S*0.037,S*0.047,S*0.037), MAT_GRILL)
for o in (handle, grille):
    o.parent = mic_root
mic_root.location = (S*0.02, S*0.01, S*0.025)

hood_major = S*0.085
hood_minor = S*0.026
hood = add_torus('Martin_Hood_Collar', center, hood_major, hood_minor, MAT_BLACK, (math.radians(90),0,0))
if chest_bone:
    attach_to_bone(hood, chest_bone, (0.0, S*0.08, S*0.02), (math.radians(90),0,0))

badge = add_uv_sphere('Martin_Chest_Badge', center, (S*0.035,S*0.012,S*0.035), MAT_VIOLET)
if chest_bone:
    attach_to_bone(badge, chest_bone, (0.0, S*0.12, S*0.09), (0,0,0))

# Text logo is decorative; a failure here must never block the rigged avatar export.
try:
    prep_mesh_operator()
    bpy.ops.object.text_add(location=center)
    txt = bpy.context.object
    txt.name = 'Martin_M_Logo'
    txt.data.body = 'M'
    txt.data.align_x = 'CENTER'
    txt.data.align_y = 'CENTER'
    txt.data.size = S*0.055
    txt.data.extrude = S*0.0025
    txt.data.bevel_depth = S*0.001
    set_mat(txt, MAT_VIOLET)
    bpy.ops.object.convert(target='MESH')
    if chest_bone:
        attach_to_bone(txt, chest_bone, (0.0, S*0.125, S*0.103), (math.radians(90),0,0))
except Exception as exc:
    print('MARTIN_LOGO_WARN', repr(exc))

arm.name = 'MartinSkeleton'
accessories = {handle, grille, hood, badge}
for i, obj in enumerate([o for o in bpy.data.objects if o.type == 'MESH' and o not in accessories]):
    if not obj.name.startswith('Martin_'):
        obj.name = f'MartinMesh_{i:02d}'

for act in bpy.data.actions:
    act.use_fake_user = True

os.makedirs(os.path.dirname(OUT), exist_ok=True)
# export_selected defaults to false; do not call context-sensitive object.select_all here.
bpy.ops.export_scene.gltf(
    filepath=OUT,
    export_format='GLB',
    export_apply=False,
    export_animations=True,
    export_skins=True,
    export_morph=True,
    export_all_influences=True,
    export_yup=True,
    export_cameras=False,
    export_lights=False,
)

if not os.path.exists(OUT) or os.path.getsize(OUT) < 10000:
    raise RuntimeError('GLB export missing or unexpectedly small')
print('MARTIN_GLB_OK', OUT, os.path.getsize(OUT))
