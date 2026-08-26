import bpy, math, os, sys
from mathutils import Vector

args = sys.argv
out_dir = "/tmp/martin_mansedj"
if "--" in args:
    user_args = args[args.index("--") + 1:]
    for i, arg in enumerate(user_args):
        if arg == "--output-dir" and i + 1 < len(user_args):
            out_dir = user_args[i + 1]
os.makedirs(out_dir, exist_ok=True)

# Remove source cameras/lights, keep all meshes and rig intact.
for obj in list(bpy.data.objects):
    if obj.type in {"CAMERA", "LIGHT"}:
        bpy.data.objects.remove(obj, do_unlink=True)

mesh_objects = [o for o in bpy.context.scene.objects if o.type == "MESH" and not o.hide_render]
if not mesh_objects:
    raise RuntimeError("No mesh objects found")

# Smooth without destructively applying modifiers.
for obj in mesh_objects:
    if obj.data:
        for poly in obj.data.polygons:
            poly.use_smooth = True

# Compute bounds from all renderable meshes.
mins = Vector((1e9, 1e9, 1e9))
maxs = Vector((-1e9, -1e9, -1e9))
for obj in mesh_objects:
    for corner in obj.bound_box:
        p = obj.matrix_world @ Vector(corner)
        mins.x = min(mins.x, p.x); mins.y = min(mins.y, p.y); mins.z = min(mins.z, p.z)
        maxs.x = max(maxs.x, p.x); maxs.y = max(maxs.y, p.y); maxs.z = max(maxs.z, p.z)
center = (mins + maxs) * 0.5
size = maxs - mins
radius = max(size.x, size.y, size.z) * 0.72
height = max(size.z, 0.5)

# Ground below character.
bpy.ops.mesh.primitive_plane_add(size=max(size.x, size.y, height) * 6.0, location=(center.x, center.y, mins.z - height * 0.01))
ground = bpy.context.object
ground.name = "MartinGround"
gmat = bpy.data.materials.new("MartinGroundMat")
gmat.diffuse_color = (0.012, 0.014, 0.021, 1.0)
gmat.roughness = 0.82
ground.data.materials.append(gmat)

def point_at(obj, target):
    direction = Vector(target) - obj.location
    obj.rotation_euler = direction.to_track_quat('-Z', 'Y').to_euler()

def add_area(name, loc, energy, area_size, color):
    bpy.ops.object.light_add(type='AREA', location=loc)
    light = bpy.context.object
    light.name = name
    light.data.energy = energy
    light.data.shape = 'DISK'
    light.data.size = area_size
    light.data.color = color
    point_at(light, center + Vector((0, 0, height * 0.1)))
    return light

# Broad premium studio light, intentionally neutral enough to judge the mesh.
add_area("Key", center + Vector((-radius*1.7, -radius*1.8, height*1.0)), 1100, height*1.4, (1.0, 0.84, 0.72))
add_area("Fill", center + Vector((radius*1.7, -radius*1.2, height*0.45)), 650, height*1.3, (0.55, 0.66, 1.0))
add_area("Rim", center + Vector((radius*1.25, radius*1.4, height*0.95)), 900, height*1.15, (1.0, 0.48, 0.20))

bpy.ops.object.camera_add()
cam = bpy.context.object
cam.data.lens = 62
bpy.context.scene.camera = cam

scene = bpy.context.scene
scene.render.engine = 'BLENDER_EEVEE_NEXT' if hasattr(bpy.types, 'EEVEE_NEXT') else scene.render.engine
try:
    scene.render.engine = 'BLENDER_EEVEE_NEXT'
except Exception:
    try: scene.render.engine = 'BLENDER_EEVEE'
    except Exception: pass
scene.render.resolution_x = 720
scene.render.resolution_y = 900
scene.render.resolution_percentage = 100
scene.render.image_settings.file_format = 'PNG'
scene.render.film_transparent = False
scene.world.color = (0.005, 0.006, 0.010)
try:
    scene.view_settings.look = 'AgX - Medium High Contrast'
except Exception:
    pass

# Four axis views plus a diagonal. One will expose the actual front irrespective of source orientation.
views = {
    "neg_y": Vector((0, -1, 0)),
    "pos_y": Vector((0, 1, 0)),
    "neg_x": Vector((-1, 0, 0)),
    "pos_x": Vector((1, 0, 0)),
    "diag": Vector((-0.72, -0.72, 0)),
}
distance = max(radius * 3.25, height * 1.85)
target = center + Vector((0, 0, height * 0.02))
for name, direction in views.items():
    cam.location = target + direction * distance + Vector((0, 0, height * 0.04))
    point_at(cam, target)
    scene.render.filepath = os.path.join(out_dir, f"mansedj_{name}.png")
    bpy.ops.render.render(write_still=True)
    print("MARTIN_MANSEDJ_RENDERED", scene.render.filepath)

print("MESH_COUNT", len(mesh_objects))
print("BOUNDS", tuple(round(v, 4) for v in (*mins, *maxs)))
