import bpy
import math
import os
from mathutils import Vector

PREVIEW = os.environ.get("CATPILOT_PREVIEW", "/tmp/catpilot-inspect.png")

print("CATPILOT_INSPECT_BLENDER", bpy.app.version_string)
print("CATPILOT_SCENE_OBJECTS", len(bpy.data.objects))

# The old Blender 2.77 file can open in Pose/Edit context. Normalize context first.
if bpy.context.object is not None and bpy.context.object.mode != "OBJECT":
    try:
        bpy.ops.object.mode_set(mode="OBJECT")
    except Exception as exc:
        print("CATPILOT_CONTEXT_MODE_WARN", repr(exc))

for obj in bpy.data.objects:
    extra = ""
    if obj.type == "MESH":
        keys = []
        if obj.data.shape_keys:
            keys = [k.name for k in obj.data.shape_keys.key_blocks]
        extra = " verts=%d polys=%d shapekeys=%s" % (len(obj.data.vertices), len(obj.data.polygons), ",".join(keys))
    elif obj.type == "ARMATURE":
        extra = " bones=%d" % len(obj.data.bones)
    print("CATPILOT_OBJECT", obj.name, obj.type, extra)

for arm in [o for o in bpy.data.objects if o.type == "ARMATURE"]:
    print("CATPILOT_ARMATURE", arm.name, "bones=", ",".join(b.name for b in arm.data.bones))

print("CATPILOT_ACTIONS", ",".join(a.name for a in bpy.data.actions))

for obj in bpy.data.objects:
    if obj.type in {"CAMERA", "LIGHT"}:
        obj.hide_render = True

meshes = [o for o in bpy.data.objects if o.type == "MESH" and not o.hide_render]
if not meshes:
    raise RuntimeError("No Cat Pilot meshes")

mins = Vector((1e9, 1e9, 1e9))
maxs = Vector((-1e9, -1e9, -1e9))
for obj in meshes:
    for corner in obj.bound_box:
        p = obj.matrix_world @ Vector(corner)
        mins.x = min(mins.x, p.x); mins.y = min(mins.y, p.y); mins.z = min(mins.z, p.z)
        maxs.x = max(maxs.x, p.x); maxs.y = max(maxs.y, p.y); maxs.z = max(maxs.z, p.z)
center = (mins + maxs) * 0.5
size = maxs - mins
print("CATPILOT_BOUNDS", tuple(mins), tuple(maxs), tuple(size))

scene = bpy.context.scene
engines = {x.identifier for x in bpy.types.RenderSettings.bl_rna.properties["engine"].enum_items}
scene.render.engine = "BLENDER_EEVEE_NEXT" if "BLENDER_EEVEE_NEXT" in engines else "BLENDER_EEVEE"
scene.render.resolution_x = 720
scene.render.resolution_y = 960
scene.render.resolution_percentage = 100
scene.render.film_transparent = False

world = bpy.data.worlds.get("CatPilotInspectWorld") or bpy.data.worlds.new("CatPilotInspectWorld")
scene.world = world
world.use_nodes = True
world.node_tree.nodes["Background"].inputs["Color"].default_value = (0.006, 0.007, 0.010, 1)
world.node_tree.nodes["Background"].inputs["Strength"].default_value = 0.22

height = max(size.z, 0.1)
distance = height * 2.55
cam_loc = Vector((center.x, mins.y - distance, center.z + height * 0.03))
cam_data = bpy.data.cameras.new("CatPilotInspectCameraData")
cam = bpy.data.objects.new("CatPilotInspectCamera", cam_data)
scene.collection.objects.link(cam)
cam.location = cam_loc
cam.data.lens = 62
cam.rotation_euler = (center - cam.location).to_track_quat("-Z", "Y").to_euler()
scene.camera = cam

for idx, (loc, energy, area, color) in enumerate([
    ((center.x - height, center.y - height, center.z + height), 900, 4.0, (1.0, 0.78, 0.58)),
    ((center.x + height, center.y - height * 0.65, center.z + height * 0.55), 500, 3.0, (0.48, 0.58, 1.0)),
    ((center.x + height * 0.8, center.y + height * 0.8, center.z + height * 0.8), 750, 2.5, (1.0, 0.50, 0.22)),
]):
    light_data = bpy.data.lights.new("CatPilotInspectLightData%d" % idx, type="AREA")
    light = bpy.data.objects.new("CatPilotInspectLight%d" % idx, light_data)
    scene.collection.objects.link(light)
    light.location = loc
    light.data.energy = energy
    light.data.shape = "DISK"
    light.data.size = area
    light.data.color = color
    light.rotation_euler = (center - light.location).to_track_quat("-Z", "Y").to_euler()

# Ground via data API, avoiding operator context inherited from the old file.
verts = [(-4, -4, 0), (4, -4, 0), (4, 4, 0), (-4, 4, 0)]
faces = [(0, 1, 2, 3)]
floor_mesh = bpy.data.meshes.new("CatPilotInspectFloorMesh")
floor_mesh.from_pydata(verts, [], faces)
floor_mesh.update()
floor = bpy.data.objects.new("CatPilotInspectFloor", floor_mesh)
scene.collection.objects.link(floor)
floor.location = (center.x, center.y, mins.z - 0.01)
floor.scale = (max(size.x, size.y, height), max(size.x, size.y, height), 1.0)
mat = bpy.data.materials.new("CatPilotInspectFloorMat")
mat.use_nodes = True
bsdf = mat.node_tree.nodes.get("Principled BSDF")
if bsdf:
    bsdf.inputs["Base Color"].default_value = (0.016, 0.014, 0.018, 1)
    bsdf.inputs["Roughness"].default_value = 0.78
floor.data.materials.append(mat)

scene.render.filepath = PREVIEW
bpy.ops.render.render(write_still=True)
print("CATPILOT_INSPECT_PREVIEW_OK", PREVIEW)
