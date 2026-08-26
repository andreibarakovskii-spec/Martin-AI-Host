import bpy
import bmesh
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

try:
    if bpy.context.object is not None and bpy.context.object.mode != 'OBJECT':
        bpy.ops.object.mode_set(mode='OBJECT')
except Exception as exc:
    print('MARTIN_MODE_NORMALIZE_WARN', repr(exc))
for obj in bpy.context.selected_objects:
    obj.select_set(False)
arm.select_set(True)
bpy.context.view_layer.objects.active = arm

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

original_tags = {}
for obj in mesh_objects:
    mats = [slot.material.name for slot in obj.material_slots if slot.material is not None]
    original_tags[obj.name] = (obj.name + ' ' + ' '.join(mats)).lower()
    print('MARTIN_SOURCE_MESH', obj.name, 'materials=', ','.join(mats))
    print('MARTIN_VERTEX_GROUPS', obj.name, ','.join(vg.name for vg in obj.vertex_groups))
    for poly in obj.data.polygons:
        poly.use_smooth = True


def delete_weighted_part(obj, group_names, min_weight):
    group_ids = {vg.index: vg.name for vg in obj.vertex_groups if vg.name in group_names}
    if not group_ids:
        print('MARTIN_PART_GROUPS_MISSING', obj.name, ','.join(group_names))
        return 0
    indices = set()
    for vert in obj.data.vertices:
        for membership in vert.groups:
            if membership.group in group_ids and membership.weight >= min_weight:
                indices.add(vert.index)
                break
    if not indices:
        print('MARTIN_PART_EMPTY', obj.name, ','.join(group_names))
        return 0
    bm = bmesh.new()
    bm.from_mesh(obj.data)
    bm.verts.ensure_lookup_table()
    doomed = [bm.verts[i] for i in sorted(indices) if i < len(bm.verts)]
    bmesh.ops.delete(bm, geom=doomed, context='VERTS')
    bm.to_mesh(obj.data)
    obj.data.update()
    bm.free()
    print('MARTIN_PART_REMOVED', obj.name, ','.join(group_names), 'vertices=', len(indices))
    return len(indices)

# The CC0 source is one skinned mesh. Remove pilot-only geometry through its authored bone groups.
for obj in mesh_objects:
    delete_weighted_part(obj, ('Goggles', 'HatFlap.L', 'HatFlap.R'), 0.42)
    delete_weighted_part(obj, ('Scarf1', 'Scarf2'), 0.58)


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
                    bsdf.inputs['Emission Strength'].default_value = 1.15
            elif 'Emission' in bsdf.inputs:
                bsdf.inputs['Emission'].default_value = (*emission, 1.0)
    return m


def textured_material(name, image):
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
        bsdf.inputs['Roughness'].default_value = 0.78
    if 'Metallic' in bsdf.inputs:
        bsdf.inputs['Metallic'].default_value = 0.0
    nt.links.new(bsdf.outputs['BSDF'], out.inputs['Surface'])
    return m


def set_mat(obj, mat):
    if obj.data and hasattr(obj.data, 'materials'):
        obj.data.materials.clear()
        obj.data.materials.append(mat)

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

MAT_METAL = material('MartinMicMetal', (0.045, 0.050, 0.065), 0.55, 0.38)
MAT_GRILL = material('MartinMicGrill', (0.085, 0.095, 0.11), 0.68, 0.34)


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


def prep_mesh_operator():
    try:
        if bpy.context.object is not None and bpy.context.object.mode != 'OBJECT':
            bpy.ops.object.mode_set(mode='OBJECT')
    except Exception:
        pass
    if arm.name in bpy.context.view_layer.objects:
        bpy.context.view_layer.objects.active = arm


def add_uv_sphere(name, scale, mat):
    prep_mesh_operator()
    bpy.ops.mesh.primitive_uv_sphere_add(segments=24, ring_count=12, location=(0,0,0))
    o = bpy.context.object
    o.name = name
    o.scale = scale
    set_mat(o, mat)
    for p in o.data.polygons:
        p.use_smooth = True
    return o


def add_cylinder(name, radius, depth, mat, rotation=(0,0,0)):
    prep_mesh_operator()
    bpy.ops.mesh.primitive_cylinder_add(vertices=24, radius=radius, depth=depth, location=(0,0,0), rotation=rotation)
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
print('MARTIN_ATTACH_BONES', 'hand=', hand_bone)


def attach_to_bone(obj, bone_name, local_location=(0,0,0), local_rotation=(0,0,0)):
    if not bone_name:
        return False
    obj.parent = arm
    obj.parent_type = 'BONE'
    obj.parent_bone = bone_name
    obj.location = local_location
    obj.rotation_euler = local_rotation
    return True

# Compact handheld microphone. Children receive explicit local transforms after parenting so
# the legacy armature transform cannot turn the handle into a floor-length rod.
S = max(height, 1.0)
mic_root = bpy.data.objects.new('Martin_Microphone_Root', None)
bpy.context.scene.collection.objects.link(mic_root)
if hand_bone:
    attach_to_bone(mic_root, hand_bone, (0.0, 0.015*S, 0.0), (math.radians(8), 0, math.radians(-12)))
else:
    mic_root.location = center

handle = add_cylinder('Martin_Microphone_Handle', S*0.010, S*0.095, MAT_METAL, (math.radians(90),0,0))
handle.parent = mic_root
handle.location = (0.0, 0.0, 0.0)
handle.rotation_euler = (math.radians(90),0,0)
grille = add_uv_sphere('Martin_Microphone_Grille', (S*0.025,S*0.032,S*0.025), MAT_GRILL)
grille.parent = mic_root
grille.location = (0.0, S*0.052, 0.0)
grille.rotation_euler = (0,0,0)

arm.name = 'MartinSkeleton'
accessories = {handle, grille}
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
