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

# Normalize the old Blender scene before any edits.
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
remove_tokens = (
    'camera', 'light', 'plane', 'propeller', 'goggle', 'glass',
    'hook', 'bag', 'satchel', 'pilot_hat', 'helmet', 'aviator'
)
for obj in list(bpy.data.objects):
    low = obj.name.lower()
    if obj is arm:
        continue
    if obj.type in {'CAMERA', 'LIGHT'} or any(t in low for t in remove_tokens):
        print('MARTIN_REMOVE', obj.name)
        bpy.data.objects.remove(obj, do_unlink=True)

arm.select_set(True)
bpy.context.view_layer.objects.active = arm

mesh_objects = [o for o in bpy.data.objects if o.type == 'MESH']
if not mesh_objects:
    raise RuntimeError('No cat mesh found in CatPilot source')

# Keep a snapshot of original names/materials before replacing the legacy Blender 2.77 materials.
original_tags = {}
for obj in mesh_objects:
    mats = [slot.material.name for slot in obj.material_slots if slot.material is not None]
    original_tags[obj.name] = (obj.name + ' ' + ' '.join(mats)).lower()
    print('MARTIN_SOURCE_MESH', obj.name, 'materials=', ','.join(mats))
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
                    bsdf.inputs['Emission Strength'].default_value = 1.25
            elif 'Emission' in bsdf.inputs:
                bsdf.inputs['Emission'].default_value = (*emission, 1.0)
    return m


def textured_material(name, image):
    """Build a fresh modern node material instead of mutating legacy Blender 2.77 materials."""
    m = bpy.data.materials.get(name) or bpy.data.materials.new(name)
    m.use_nodes = True
    nt = m.node_tree
    nt.nodes.clear()
    out = nt.nodes.new('ShaderNodeOutputMaterial')
    bsdf = nt.nodes.new('ShaderNodeBsdfPrincipled')
    tex = nt.nodes.new('ShaderNodeTexImage')
    tex.image = image
    tex.interpolation = 'Linear'
    if hasattr(image, 'colorspace_settings'):
        image.colorspace_settings.name = 'sRGB'
    nt.links.new(tex.outputs['Color'], bsdf.inputs['Base Color'])
    if 'Alpha' in tex.outputs and 'Alpha' in bsdf.inputs:
        nt.links.new(tex.outputs['Alpha'], bsdf.inputs['Alpha'])
    if 'Roughness' in bsdf.inputs:
        bsdf.inputs['Roughness'].default_value = 0.72
    if 'Metallic' in bsdf.inputs:
        bsdf.inputs['Metallic'].default_value = 0.0
    nt.links.new(bsdf.outputs['BSDF'], out.inputs['Surface'])
    return m


def set_mat(obj, mat):
    if obj.data and hasattr(obj.data, 'materials'):
        obj.data.materials.clear()
        obj.data.materials.append(mat)

# Critical fix: the CC0 CatPilot file uses legacy materials. Rebinding image nodes did not
# affect meshes reliably after glTF export, so create and assign a new Principled material.
martin_image = None
if TEXTURE and os.path.exists(TEXTURE):
    martin_image = bpy.data.images.load(TEXTURE, check_existing=False)
    martin_image.name = 'MartinTexture'
    martin_texture_mat = textured_material('MartinCatTexture', martin_image)
    for obj in mesh_objects:
        set_mat(obj, martin_texture_mat)
        print('MARTIN_TEXTURE_ASSIGNED', obj.name)
else:
    print('MARTIN_TEXTURE_MISSING', TEXTURE)

MAT_BLACK = material('MartinHoodieBlack', (0.010, 0.012, 0.019), 0.01, 0.72)
MAT_METAL = material('MartinMicMetal', (0.045, 0.050, 0.065), 0.55, 0.34)
MAT_GRILL = material('MartinMicGrill', (0.095, 0.10, 0.12), 0.68, 0.30)
MAT_VIOLET = material('MartinViolet', (0.30, 0.045, 0.70), 0.08, 0.40, (0.16, 0.015, 0.48))
MAT_GREEN = material('MartinEyeGreen', (0.015, 0.25, 0.055), 0.0, 0.30, (0.005, 0.055, 0.012))

# If the source exposes eyes/irises as separate meshes/materials, make them unmistakably green.
for obj in mesh_objects:
    tag = original_tags.get(obj.name, '')
    if 'eye' in tag or 'iris' in tag:
        set_mat(obj, MAT_GREEN)
        print('MARTIN_GREEN_EYES', obj.name)


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


def prep_mesh_operator():
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

# Hand microphone. The large torus "hood collar" from the first pass is intentionally removed:
# it crossed the muzzle in several animations and made the character read as a ring, not a hoodie.
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

# Minimal chest mark. Keep geometry clear of the face and let the dark texture define the outfit.
badge = add_uv_sphere('Martin_Chest_Badge', center, (S*0.030,S*0.009,S*0.030), MAT_VIOLET)
if chest_bone:
    attach_to_bone(badge, chest_bone, (0.0, S*0.115, S*0.082), (0,0,0))

try:
    prep_mesh_operator()
    bpy.ops.object.text_add(location=center)
    txt = bpy.context.object
    txt.name = 'Martin_M_Logo'
    txt.data.body = 'M'
    txt.data.align_x = 'CENTER'
    txt.data.align_y = 'CENTER'
    txt.data.size = S*0.047
    txt.data.extrude = S*0.002
    txt.data.bevel_depth = S*0.0008
    set_mat(txt, MAT_VIOLET)
    bpy.ops.object.convert(target='MESH')
    if chest_bone:
        attach_to_bone(txt, chest_bone, (0.0, S*0.121, S*0.094), (math.radians(90),0,0))
except Exception as exc:
    print('MARTIN_LOGO_WARN', repr(exc))

arm.name = 'MartinSkeleton'
accessories = {handle, grille, badge}
for i, obj in enumerate([o for o in bpy.data.objects if o.type == 'MESH' and o not in accessories]):
    if not obj.name.startswith('Martin_'):
        obj.name = f'MartinMesh_{i:02d}'

for act in bpy.data.actions:
    act.use_fake_user = True

os.makedirs(os.path.dirname(OUT), exist_ok=True)
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
