import bpy
import math
import os
from mathutils import Vector

# MARTIN_V2_PROCEDURAL
OUT = os.environ.get('MARTIN_OUT', '/tmp/martin.glb')
PREVIEW = os.environ.get('MARTIN_PREVIEW', '')

print('MARTIN_BLENDER_VERSION', bpy.app.version_string)

# Start from a deterministic empty scene. The avatar is generated from primitives so
# the production build no longer depends on a low-poly third-party character mesh.
bpy.ops.object.select_all(action='SELECT')
bpy.ops.object.delete(use_global=False)

scene = bpy.context.scene
scene.frame_start = 1
scene.frame_end = 48
scene.render.fps = 24


def mat(name, color, metallic=0.0, rough=0.65, alpha=1.0, emission=None):
    m = bpy.data.materials.new(name)
    m.use_nodes = True
    m.diffuse_color = (color[0], color[1], color[2], alpha)
    bsdf = m.node_tree.nodes.get('Principled BSDF')
    if bsdf:
        if 'Base Color' in bsdf.inputs: bsdf.inputs['Base Color'].default_value = (*color, 1.0)
        if 'Metallic' in bsdf.inputs: bsdf.inputs['Metallic'].default_value = metallic
        if 'Roughness' in bsdf.inputs: bsdf.inputs['Roughness'].default_value = rough
        if 'Alpha' in bsdf.inputs: bsdf.inputs['Alpha'].default_value = alpha
        if emission is not None:
            key = 'Emission Color' if 'Emission Color' in bsdf.inputs else 'Emission'
            if key in bsdf.inputs: bsdf.inputs[key].default_value = (*emission, 1.0)
            if 'Emission Strength' in bsdf.inputs: bsdf.inputs['Emission Strength'].default_value = 0.55
    if alpha < 0.999:
        m.blend_method = 'BLEND'
        if hasattr(m, 'use_screen_refraction'): m.use_screen_refraction = True
        if hasattr(m, 'show_transparent_back'): m.show_transparent_back = False
    return m

FUR = mat('Martin Fur Grey', (0.255, 0.272, 0.305), rough=0.82)
FUR_LIGHT = mat('Martin Muzzle Silver', (0.42, 0.435, 0.47), rough=0.84)
FUR_DARK = mat('Martin Fur Shadow', (0.155, 0.17, 0.20), rough=0.86)
PINK = mat('Martin Ear Nose Warm', (0.76, 0.43, 0.48), rough=0.56)
BLACK = mat('Martin Hoodie Black', (0.018, 0.021, 0.029), rough=0.70)
BLACK_SOFT = mat('Martin Cloth Detail', (0.045, 0.052, 0.068), rough=0.80)
GOLD = mat('Martin Gold', (0.72, 0.44, 0.105), metallic=0.76, rough=0.24)
GOLD_DARK = mat('Martin Dark Gold', (0.31, 0.17, 0.035), metallic=0.68, rough=0.31)
WHITE = mat('Martin Eye White', (0.92, 0.93, 0.94), rough=0.16)
IRIS = mat('Martin Iris Amber', (0.92, 0.47, 0.055), metallic=0.08, rough=0.20, emission=(0.25,0.075,0.01))
PUPIL = mat('Martin Pupil', (0.004,0.004,0.006), rough=0.10)
CORNEA = mat('Martin Cornea', (0.75,0.86,1.0), rough=0.03, alpha=0.16)
MOUTH = mat('Martin Mouth', (0.018,0.008,0.012), rough=0.48)
SOLE = mat('Martin Sole', (0.16,0.17,0.19), rough=0.72)


def assign(obj, material):
    obj.data.materials.clear()
    obj.data.materials.append(material)
    for p in obj.data.polygons: p.use_smooth = True


def uv(name, loc, scale, material, segments=32, rings=16):
    bpy.ops.mesh.primitive_uv_sphere_add(segments=segments, ring_count=rings, location=loc)
    o = bpy.context.object
    o.name = name
    o.scale = scale
    assign(o, material)
    return o


def cyl(name, loc, radius, depth, material, rot=(0,0,0), vertices=32):
    bpy.ops.mesh.primitive_cylinder_add(vertices=vertices, radius=radius, depth=depth, location=loc, rotation=rot)
    o = bpy.context.object
    o.name = name
    assign(o, material)
    bevel = o.modifiers.new('Soft edges','BEVEL'); bevel.width = min(radius*0.22, depth*0.06); bevel.segments = 2
    return o


def torus(name, loc, major, minor, material, rot=(0,0,0)):
    bpy.ops.mesh.primitive_torus_add(major_segments=40, minor_segments=10, location=loc, major_radius=major, minor_radius=minor, rotation=rot)
    o=bpy.context.object; o.name=name; assign(o,material); return o


def prism_ear(name, loc, width, height, depth, material, tilt=0.0):
    w=width*0.5; h=height; d=depth*0.5
    verts=[(-w,-d,0),(w,-d,0),(0,-d,h),(-w,d,0),(w,d,0),(0,d,h)]
    faces=[(0,1,2),(5,4,3),(0,3,4,1),(1,4,5,2),(2,5,3,0)]
    mesh=bpy.data.meshes.new(name+'Mesh'); mesh.from_pydata(verts,[],faces); mesh.update()
    o=bpy.data.objects.new(name,mesh); scene.collection.objects.link(o); o.location=loc; o.rotation_euler[1]=tilt; assign(o,material)
    bevel=o.modifiers.new('Soft ear','BEVEL'); bevel.width=0.035; bevel.segments=3
    return o


def curve_obj(name, pts, bevel, material, cyclic=False):
    cu=bpy.data.curves.new(name+'Curve','CURVE'); cu.dimensions='3D'; cu.resolution_u=2; cu.bevel_depth=bevel; cu.bevel_resolution=3
    sp=cu.splines.new('BEZIER'); sp.bezier_points.add(len(pts)-1)
    for p,co in zip(sp.bezier_points,pts):
        p.co=co; p.handle_left_type='AUTO'; p.handle_right_type='AUTO'
    sp.use_cyclic_u=cyclic
    o=bpy.data.objects.new(name,cu); scene.collection.objects.link(o); o.data.materials.append(material); return o


def text_obj(name, body, loc, size, material):
    bpy.ops.object.text_add(location=loc, rotation=(math.radians(90),0,0))
    o=bpy.context.object; o.name=name; o.data.body=body; o.data.align_x='CENTER'; o.data.align_y='CENTER'; o.data.size=size; o.data.extrude=0.025; o.data.bevel_depth=0.008; o.data.bevel_resolution=3; o.data.materials.append(material)
    return o

# ---------- Armature ----------
bpy.ops.object.armature_add(enter_editmode=True, location=(0,0,0))
arm=bpy.context.object; arm.name='MartinSkeleton'; arm.data.name='MartinSkeleton'
for b in list(arm.data.edit_bones): arm.data.edit_bones.remove(b)

bones={}
def bone(name, head, tail, parent=None):
    b=arm.data.edit_bones.new(name); b.head=head; b.tail=tail
    if parent: b.parent=bones[parent]
    bones[name]=b; return b

bone('Root',(0,0,0),(0,0,0.28))
bone('Hips',(0,0,0.78),(0,0,1.16),'Root')
bone('Spine',(0,0,1.16),(0,0,1.52),'Hips')
bone('Chest',(0,0,1.52),(0,0,2.00),'Spine')
bone('Neck',(0,0,2.00),(0,0,2.22),'Chest')
bone('Head',(0,0,2.22),(0,0,2.78),'Neck')
bone('Jaw',(0,-0.34,2.38),(0,-0.56,2.28),'Head')
for s,x in [('L',-0.27),('R',0.27)]:
    bone('Eye.'+s,(x,-0.43,2.60),(x,-0.64,2.60),'Head')
    bone('Lid.'+s,(x,-0.45,2.75),(x,-0.62,2.75),'Head')
    bone('Ear.'+s,(x*1.65,0,2.88),(x*2.05,0,3.30),'Head')

bone('UpperArm.L',(-0.52,0,1.86),(-0.78,0,1.48),'Chest')
bone('Forearm.L',(-0.78,0,1.48),(-0.78,-0.01,1.12),'UpperArm.L')
bone('Hand.L',(-0.78,-0.01,1.12),(-0.78,-0.05,0.92),'Forearm.L')
bone('UpperArm.R',(0.52,0,1.86),(0.78,0,1.48),'Chest')
bone('Forearm.R',(0.78,0,1.48),(0.78,-0.01,1.12),'UpperArm.R')
bone('Hand.R',(0.78,-0.01,1.12),(0.78,-0.05,0.92),'Forearm.R')

bone('Thigh.L',(-0.28,0,1.02),(-0.28,0,0.64),'Hips')
bone('Shin.L',(-0.28,0,0.64),(-0.28,0,0.26),'Thigh.L')
bone('Foot.L',(-0.28,0,0.26),(-0.28,-0.25,0.10),'Shin.L')
bone('Thigh.R',(0.28,0,1.02),(0.28,0,0.64),'Hips')
bone('Shin.R',(0.28,0,0.64),(0.28,0,0.26),'Thigh.R')
bone('Foot.R',(0.28,0,0.26),(0.28,-0.25,0.10),'Shin.R')

bone('Tail1',(0,0.26,0.95),(0.34,0.34,0.92),'Hips')
bone('Tail2',(0.34,0.34,0.92),(0.62,0.32,1.10),'Tail1')
bone('Tail3',(0.62,0.32,1.10),(0.74,0.26,1.42),'Tail2')
bpy.ops.object.mode_set(mode='POSE')
for pb in arm.pose.bones: pb.rotation_mode='XYZ'
bpy.ops.object.mode_set(mode='OBJECT')


def parent_bone(obj, bname):
    world=obj.matrix_world.copy(); obj.parent=arm; obj.parent_type='BONE'; obj.parent_bone=bname; obj.matrix_world=world

# ---------- Character geometry ----------
hoodie=uv('Hoodie_Torso',(0,0,1.55),(0.66,0.43,0.76),BLACK,40,20); parent_bone(hoodie,'Chest')
hoodie_belly=uv('Hoodie_Belly',(0,-0.34,1.48),(0.54,0.12,0.54),BLACK_SOFT,32,16); parent_bone(hoodie_belly,'Chest')
hood=torus('Hoodie_Hood',(0,-0.02,2.03),0.43,0.115,BLACK_SOFT,rot=(0,0,0)); hood.scale.y=0.74; parent_bone(hood,'Chest')
logo=text_obj('Logo_M','M',(0,-0.485,1.57),0.42,GOLD); parent_bone(logo,'Chest')

head=uv('Fur_Head',(0,0,2.57),(0.70,0.57,0.69),FUR,48,24); parent_bone(head,'Head')
cheek_l=uv('Fur_Cheek_L',(-0.30,-0.39,2.42),(0.39,0.26,0.30),FUR_LIGHT,36,18); parent_bone(cheek_l,'Head')
cheek_r=uv('Fur_Cheek_R',(0.30,-0.39,2.42),(0.39,0.26,0.30),FUR_LIGHT,36,18); parent_bone(cheek_r,'Head')
muzzle=uv('Fur_Muzzle',(0,-0.53,2.34),(0.33,0.16,0.22),FUR_LIGHT,36,18); parent_bone(muzzle,'Head')
chin=uv('Fur_Chin',(0,-0.48,2.20),(0.24,0.14,0.13),FUR_LIGHT,32,16); parent_bone(chin,'Jaw')

for side,x,tilt in [('L',-0.47,math.radians(-13)),('R',0.47,math.radians(13))]:
    e=prism_ear('Fur_Ear_'+side,(x,0.02,2.94),0.42,0.62,0.18,FUR,tilt); parent_bone(e,'Ear.'+side)
    inner=prism_ear('Ear_Inner_'+side,(x,-0.095,3.00),0.23,0.36,0.035,PINK,tilt); parent_bone(inner,'Ear.'+side)

for side,x in [('L',-0.255),('R',0.255)]:
    b='Eye.'+side
    eye=uv('Eye_Sclera_'+side,(x,-0.475,2.61),(0.205,0.125,0.235),WHITE,40,20); parent_bone(eye,b)
    iris=uv('Iris_Amber_'+side,(x,-0.588,2.61),(0.105,0.030,0.125),IRIS,32,16); parent_bone(iris,b)
    pupil=uv('Pupil_'+side,(x,-0.615,2.61),(0.050,0.017,0.072),PUPIL,28,14); parent_bone(pupil,b)
    cornea=uv('Cornea_'+side,(x,-0.606,2.61),(0.214,0.045,0.244),CORNEA,40,20); parent_bone(cornea,b)
    hi=uv('Eye_Highlight_'+side,(x-0.050,-0.651,2.675),(0.029,0.012,0.036),WHITE,20,10); parent_bone(hi,b)
    lid=uv('Fur_Lid_'+side,(x,-0.625,2.785),(0.225,0.038,0.055),FUR_DARK,32,14); parent_bone(lid,'Lid.'+side)

nose=uv('Nose',(0,-0.682,2.42),(0.115,0.065,0.075),PINK,28,14); parent_bone(nose,'Head')
mouth=uv('Mouth_Interior',(0,-0.646,2.255),(0.145,0.028,0.075),MOUTH,28,12); parent_bone(mouth,'Jaw')
for side in (-1,1):
    pts=[(0.0,-0.684,2.30),(0.08*side,-0.699,2.25),(0.17*side,-0.667,2.27)]
    c=curve_obj('Mouth_Smile_'+('L' if side<0 else 'R'),pts,0.018,MOUTH); parent_bone(c,'Jaw')
    for idx,dz in enumerate((0.05,0.0,-0.05)):
        pts=[(0.16*side,-0.63,2.38+dz),(0.38*side,-0.68,2.40+dz*0.5),(0.58*side,-0.64,2.41+dz*0.2)]
        w=curve_obj('Whisker_%s_%d'%('L' if side<0 else 'R',idx),pts,0.008,FUR_LIGHT); parent_bone(w,'Head')

for side,sx in [('L',-1),('R',1)]:
    ua=uv('Hoodie_UpperArm_'+side,(0.66*sx,0.0,1.64),(0.23,0.24,0.43),BLACK,28,14); ua.rotation_euler[1]=math.radians(10*sx); parent_bone(ua,'UpperArm.'+side)
    fa=uv('Hoodie_Forearm_'+side,(0.78*sx,-0.015,1.30),(0.19,0.20,0.34),BLACK_SOFT,28,14); parent_bone(fa,'Forearm.'+side)
    hand=uv('Fur_Paw_'+side,(0.78*sx,-0.07,1.02),(0.22,0.20,0.22),FUR_LIGHT,32,16); parent_bone(hand,'Hand.'+side)

for side,sx in [('L',-1),('R',1)]:
    th=uv('Pants_Thigh_'+side,(0.29*sx,0,0.78),(0.27,0.28,0.42),BLACK,28,14); parent_bone(th,'Thigh.'+side)
    sh=uv('Pants_Shin_'+side,(0.29*sx,-0.01,0.42),(0.235,0.25,0.34),BLACK_SOFT,28,14); parent_bone(sh,'Shin.'+side)
    shoe=uv('Shoe_'+side,(0.29*sx,-0.17,0.16),(0.28,0.38,0.17),BLACK,28,14); parent_bone(shoe,'Foot.'+side)
    sole=uv('Shoe_Sole_'+side,(0.29*sx,-0.21,0.075),(0.29,0.39,0.055),SOLE,28,12); parent_bone(sole,'Foot.'+side)

tail_specs=[('Tail1',(0.20,0.32,0.94),(0.35,0.17,0.18),'Tail1'),('Tail2',(0.48,0.32,1.02),(0.32,0.16,0.17),'Tail2'),('Tail3',(0.68,0.27,1.28),(0.18,0.16,0.31),'Tail3')]
for name,loc,scale,bn in tail_specs:
    t=uv('Fur_'+name,loc,scale,FUR,30,15); parent_bone(t,bn)

band_pts=[]
for i in range(11):
    a=math.radians(-70 + 140*i/10)
    band_pts.append((0.72*math.sin(a),0.05,2.69+0.72*math.cos(a)))
band=curve_obj('Headphone_Band',band_pts,0.055,BLACK); parent_bone(band,'Head')
for side,sx in [('L',-1),('R',1)]:
    cup=cyl('Headphone_Cup_'+side,(0.70*sx,-0.01,2.61),0.22,0.12,BLACK,rot=(0,math.radians(90),0),vertices=36); parent_bone(cup,'Head')
    ring=cyl('Headphone_Gold_'+side,(0.765*sx,-0.01,2.61),0.165,0.018,GOLD,rot=(0,math.radians(90),0),vertices=36); parent_bone(ring,'Head')

mic_handle=cyl('Mic_Handle',(0.79,-0.16,1.02),0.045,0.34,GOLD_DARK,rot=(math.radians(14),0,0),vertices=28); parent_bone(mic_handle,'Hand.R')
mic_head=uv('Mic_Grille',(0.79,-0.205,1.22),(0.12,0.11,0.13),GOLD,28,14); parent_bone(mic_head,'Hand.R')

# ---------- Animation actions ----------
arm.animation_data_create()
important=['Root','Hips','Spine','Chest','Neck','Head','Jaw','UpperArm.L','UpperArm.R','Forearm.L','Forearm.R','Thigh.L','Thigh.R','Shin.L','Shin.R','Tail1','Tail2','Tail3','Ear.L','Ear.R']

def reset_pose():
    for pb in arm.pose.bones:
        pb.rotation_mode='XYZ'; pb.rotation_euler=(0,0,0); pb.location=(0,0,0); pb.scale=(1,1,1)

def key_all(frame, rot=None, loc=None):
    rot=rot or {}; loc=loc or {}
    reset_pose()
    for n,v in rot.items():
        if n in arm.pose.bones: arm.pose.bones[n].rotation_euler=v
    for n,v in loc.items():
        if n in arm.pose.bones: arm.pose.bones[n].location=v
    for n in important:
        pb=arm.pose.bones.get(n)
        if pb:
            pb.keyframe_insert('rotation_euler',frame=frame,group=n)
            pb.keyframe_insert('location',frame=frame,group=n)

def make_action(name, keys):
    act=bpy.data.actions.new(name); act.use_fake_user=True; arm.animation_data.action=act
    for frame,rot,loc in keys: key_all(frame,rot,loc)
    for fc in act.fcurves:
        for kp in fc.keyframe_points: kp.interpolation='BEZIER'
    print('MARTIN_ACTION',name,'curves=',len(act.fcurves))
    return act

idle_keys=[
    (1, {'Chest':(0.015,0,0),'Head':(-0.010,0,-0.015),'Tail1':(0,0.05,0.06),'Ear.L':(0,0,0.025),'Ear.R':(0,0,-0.018)}, {'Hips':(0,0,0)}),
    (24,{'Chest':(-0.015,0,0),'Head':(0.012,0,0.018),'Tail1':(0,-0.06,-0.05),'Tail2':(0,0.04,0.07),'Ear.L':(0,0,-0.018),'Ear.R':(0,0,0.026)}, {'Hips':(0,0,0.012)}),
    (48,{'Chest':(0.015,0,0),'Head':(-0.010,0,-0.015),'Tail1':(0,0.05,0.06),'Ear.L':(0,0,0.025),'Ear.R':(0,0,-0.018)}, {'Hips':(0,0,0)})]
make_action('DefaultAnim',idle_keys)
make_action('Cheer',[
    (1,{},{}),
    (10,{'UpperArm.L':(0,-1.55,-0.18),'UpperArm.R':(0,1.55,0.18),'Forearm.L':(0,-0.35,0),'Forearm.R':(0,0.35,0),'Head':(-0.10,0,0),'Tail2':(0,0.25,0.18)}, {'Hips':(0,0,0.06)}),
    (22,{'UpperArm.L':(0,-1.35,0.10),'UpperArm.R':(0,1.35,-0.10),'Head':(0.05,0,0.08),'Tail2':(0,-0.22,-0.16)}, {'Hips':(0,0,0.015)}),
    (36,{'UpperArm.L':(0,-1.55,-0.18),'UpperArm.R':(0,1.55,0.18),'Head':(-0.08,0,-0.06),'Tail2':(0,0.24,0.18)}, {'Hips':(0,0,0.05)}),
    (48,{}, {})])
make_action('Walk',[
    (1,{'UpperArm.L':(0,0.32,0),'UpperArm.R':(0,-0.32,0),'Thigh.L':(0,-0.30,0),'Thigh.R':(0,0.30,0)},{}),
    (13,{'UpperArm.L':(0,-0.32,0),'UpperArm.R':(0,0.32,0),'Thigh.L':(0,0.30,0),'Thigh.R':(0,-0.30,0),'Tail1':(0,0.12,0)}, {'Hips':(0,0,0.025)}),
    (25,{'UpperArm.L':(0,0.32,0),'UpperArm.R':(0,-0.32,0),'Thigh.L':(0,-0.30,0),'Thigh.R':(0,0.30,0)},{}),
    (37,{'UpperArm.L':(0,-0.32,0),'UpperArm.R':(0,0.32,0),'Thigh.L':(0,0.30,0),'Thigh.R':(0,-0.30,0),'Tail1':(0,-0.12,0)}, {'Hips':(0,0,0.025)}),
    (48,{'UpperArm.L':(0,0.32,0),'UpperArm.R':(0,-0.32,0),'Thigh.L':(0,-0.30,0),'Thigh.R':(0,0.30,0)}, {})])
make_action('Run',[
    (1,{'UpperArm.L':(0,0.58,0),'UpperArm.R':(0,-0.58,0),'Thigh.L':(0,-0.55,0),'Thigh.R':(0,0.55,0),'Chest':(0.08,0,0)},{}),
    (9,{'UpperArm.L':(0,-0.58,0),'UpperArm.R':(0,0.58,0),'Thigh.L':(0,0.55,0),'Thigh.R':(0,-0.55,0),'Chest':(0.10,0,0),'Tail1':(0,0.22,0)}, {'Hips':(0,0,0.045)}),
    (17,{'UpperArm.L':(0,0.58,0),'UpperArm.R':(0,-0.58,0),'Thigh.L':(0,-0.55,0),'Thigh.R':(0,0.55,0),'Chest':(0.08,0,0)},{}),
    (25,{'UpperArm.L':(0,-0.58,0),'UpperArm.R':(0,0.58,0),'Thigh.L':(0,0.55,0),'Thigh.R':(0,-0.55,0),'Chest':(0.10,0,0),'Tail1':(0,-0.22,0)}, {'Hips':(0,0,0.045)}),
    (33,{'UpperArm.L':(0,0.58,0),'UpperArm.R':(0,-0.58,0),'Thigh.L':(0,-0.55,0),'Thigh.R':(0,0.55,0),'Chest':(0.08,0,0)},{}),
    (48,{'UpperArm.L':(0,0.58,0),'UpperArm.R':(0,-0.58,0),'Thigh.L':(0,-0.55,0),'Thigh.R':(0,0.55,0),'Chest':(0.08,0,0)}, {})])
make_action('Dance',[
    (1,{'UpperArm.L':(0,-0.6,-0.4),'UpperArm.R':(0,0.5,0.35),'Chest':(0,0,-0.08)},{}),
    (12,{'UpperArm.L':(0,0.4,0.35),'UpperArm.R':(0,1.0,-0.25),'Chest':(0,0,0.12),'Head':(0,0,-0.10)}, {'Hips':(0,0,0.035)}),
    (24,{'UpperArm.L':(0,-1.0,-0.25),'UpperArm.R':(0,-0.4,0.35),'Chest':(0,0,-0.12),'Head':(0,0,0.10)},{}),
    (36,{'UpperArm.L':(0,0.5,0.35),'UpperArm.R':(0,0.9,-0.25),'Chest':(0,0,0.12)}, {'Hips':(0,0,0.035)}),
    (48,{'UpperArm.L':(0,-0.6,-0.4),'UpperArm.R':(0,0.5,0.35),'Chest':(0,0,-0.08)}, {})])
make_action('Talking',idle_keys)
make_action('Listening',idle_keys)
arm.animation_data.action=bpy.data.actions.get('DefaultAnim')

mesh_count=len([o for o in scene.objects if o.type=='MESH'])
print('MARTIN_V2_RIG bones=',len(arm.data.bones),'meshes=',mesh_count,'actions=',','.join(a.name for a in bpy.data.actions))

os.makedirs(os.path.dirname(OUT),exist_ok=True)
bpy.ops.object.select_all(action='SELECT')
bpy.ops.export_scene.gltf(filepath=OUT,export_format='GLB',export_apply=False,export_animations=True,export_skins=True,export_morph=True,export_all_influences=True,export_yup=True,export_cameras=False,export_lights=False)
if not os.path.exists(OUT) or os.path.getsize(OUT)<200000:
    raise RuntimeError('Martin v2 GLB export missing or too small')
print('MARTIN_V2_GLB_OK',OUT,os.path.getsize(OUT))

if PREVIEW:
    scene.render.engine='BLENDER_EEVEE'
    scene.render.resolution_x=720; scene.render.resolution_y=960; scene.render.resolution_percentage=100
    world=bpy.data.worlds.new('MartinWorld'); scene.world=world; world.use_nodes=True
    world.node_tree.nodes['Background'].inputs['Color'].default_value=(0.006,0.007,0.012,1); world.node_tree.nodes['Background'].inputs['Strength'].default_value=0.20
    bpy.ops.object.camera_add(location=(0,-7.2,2.35))
    cam=bpy.context.object; scene.camera=cam
    def point_at(obj,pt): obj.rotation_euler=(Vector(pt)-obj.location).to_track_quat('-Z','Y').to_euler()
    point_at(cam,(0,0,1.65)); cam.data.lens=58
    for name,loc,energy,size,color in [
        ('Key',(-3.2,-4.0,4.8),1050,4.0,(1.0,0.72,0.45)),
        ('Fill',(3.2,-2.8,3.2),650,3.0,(0.42,0.48,1.0)),
        ('Rim',(2.6,2.0,4.4),900,2.5,(0.78,0.40,1.0))]:
        bpy.ops.object.light_add(type='AREA',location=loc); l=bpy.context.object; l.name=name; l.data.energy=energy; l.data.size=size; l.data.color=color; point_at(l,(0,0,1.7))
    bpy.ops.mesh.primitive_plane_add(size=20,location=(0,0,-0.02)); floor=bpy.context.object; assign(floor,mat('PreviewFloor',(0.018,0.015,0.022),rough=0.58))
    scene.render.filepath=PREVIEW
    bpy.ops.render.render(write_still=True)
    print('MARTIN_V2_PREVIEW_OK',PREVIEW)
