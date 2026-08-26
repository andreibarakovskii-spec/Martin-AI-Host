import bpy, math, os, sys
from mathutils import Vector

# Headless preview renderer for a CC0 humanoid-cat Blender asset.
# The source asset is downloaded by CI and is NOT committed to the repo yet.

args = sys.argv
out_path = "/tmp/martin_cc0_candidate.png"
if "--" in args:
    user_args = args[args.index("--") + 1:]
    for i, arg in enumerate(user_args):
        if arg == "--output" and i + 1 < len(user_args):
            out_path = user_args[i + 1]

# Clean cameras/lights only; preserve the imported character and rig if present.
for obj in list(bpy.data.objects):
    if obj.type in {"CAMERA", "LIGHT"}:
        bpy.data.objects.remove(obj, do_unlink=True)

mesh_objects = [o for o in bpy.context.scene.objects if o.type == "MESH"]
if not mesh_objects:
    raise RuntimeError("No mesh objects found in source cat.blend")

# Make the existing asset read as a polished mobile character without destroying topology.
for obj in mesh_objects:
    if obj.data:
        for poly in obj.data.polygons:
            poly.use_smooth = True
    # A tiny subdivision pass is enough to remove faceting while preserving the original silhouette.
    if len(obj.data.vertices) < 70000:
        mod = obj.modifiers.new(name="MartinPreviewSmooth", type="SUBSURF")
        mod.subdivision_type = "CATMULL_CLARK"
        mod.levels = 1
        mod.render_levels = 1

# Compute world-space character bounds.
mins = Vector((1e9, 1e9, 1e9))
maxs = Vector((-1e9, -1e9, -1e9))
for obj in mesh_objects:
    for corner in obj.bound_box:
        p = obj.matrix_world @ Vector(corner)
        mins.x, mins.y, mins.z = min(mins.x, p.x), min(mins.y, p.y), min(mins.z, p.z)
        maxs.x, maxs.y, maxs.z = max(maxs.x, p.x), max(maxs.y, p.y), max(maxs.z, p.z)
center = (mins + maxs) * 0.5
size = maxs - mins
height = max(size.z, 0.5)
width = max(size.x, 0.5)

# Ground.
bpy.ops.mesh.primitive_plane_add(size=max(height, width) * 5.0, location=(center.x, center.y, mins.z - height * 0.015))
ground = bpy.context.object
ground.name = "MartinPreviewGround"
mat_ground = bpy.data.materials.new("MartinPreviewGroundMat")
mat_ground.diffuse_color = (0.018, 0.020, 0.028, 1.0)
mat_ground.roughness = 0.82
ground.data.materials.append(mat_ground)

# Camera looking along +Y from the front.
bpy.ops.object.camera_add(location=(center.x, mins.y - max(height * 2.25, width * 3.0), center.z + height * 0.04))
cam = bpy.context.object
bpy.context.scene.camera = cam
cam.data.lens = 58

def point_at(obj, target):
    direction = Vector(target) - obj.location
    obj.rotation_euler = direction.to_track_quat('-Z', 'Y').to_euler()

point_at(cam, (center.x, center.y, center.z + height * 0.02))

# Studio key/fill/rim setup.
def area(name, location, energy, size_value, color):
    bpy.ops.object.light_add(type='AREA', location=location)
    l = bpy.context.object
    l.name = name
    l.data.energy = energy
    l.data.shape = 'DISK'
    l.data.size = size_value
    l.data.color = color
    point_at(l, (center.x, center.y, center.z + height * 0.12))
    return l

area("Key", (center.x - width * 1.8, center.y - height * 1.4, center.z + height * 1.2), 900, height * 1.7, (1.0, 0.82, 0.68))
area("Fill", (center.x + width * 1.6, center.y - height * 1.0, center.z + height * 0.5), 500, height * 1.5, (0.48, 0.62, 1.0))
area("Rim", (center.x + width * 1.3, center.y + height * 0.8, center.z + height * 0.9), 700, height * 1.2, (1.0, 0.45, 0.18))

scene = bpy.context.scene
scene.render.resolution_x = 768
scene.render.resolution_y = 768
scene.render.resolution_percentage = 100
scene.render.image_settings.file_format = 'PNG'
scene.render.filepath = out_path
scene.render.film_transparent = False
scene.world.color = (0.006, 0.008, 0.014)

# Work across Blender versions available on GitHub runners.
try:
    scene.render.engine = 'BLENDER_EEVEE_NEXT'
except Exception:
    try:
        scene.render.engine = 'BLENDER_EEVEE'
    except Exception:
        pass

scene.view_settings.look = 'AgX - Medium High Contrast' if 'AgX - Medium High Contrast' in [i.name for i in bpy.types.ColorManagedViewSettings.bl_rna.properties['look'].enum_items] else scene.view_settings.look

os.makedirs(os.path.dirname(out_path), exist_ok=True)
bpy.ops.render.render(write_still=True)
print("MARTIN_CC0_CANDIDATE_RENDERED", out_path)
print("MESHES", [(o.name, len(o.data.vertices)) for o in mesh_objects])
print("BOUNDS", tuple(round(v, 4) for v in (*mins, *maxs)))
