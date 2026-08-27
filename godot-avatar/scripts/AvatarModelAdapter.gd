class_name AvatarModelAdapter
extends Node

@export var model_root_path: NodePath
@export var skeleton_path: NodePath
@export var face_mesh_path: NodePath
@export var animation_tree_path: NodePath

var skeleton: Skeleton3D
var face_mesh: MeshInstance3D
var animation_tree: AnimationTree
var animation_player: AnimationPlayer
var blendshape_cache: Dictionary = {}
var bone_cache: Dictionary = {}
var bone_base_positions: Dictionary = {}

const VISEME_ALIASES := {
    "sil":["viseme_sil","mouthClose","mouth_close"],
    "aa":["viseme_aa","jawOpen","mouthOpen","mouth_open"],
    "oh":["viseme_O","mouthFunnel","mouthPucker"],
    "ee":["viseme_E","mouthSmile","mouthSmileLeft","mouthSmileRight"],
    "pp":["viseme_PP","mouthPressLeft","mouthPressRight"]
}
const EXPRESSION_ALIASES := {
    "blink_l":["eyeBlinkLeft","eyesClosed","blinkLeft"],
    "blink_r":["eyeBlinkRight","eyesClosed","blinkRight"],
    "smile":["mouthSmile","mouthSmileLeft","mouthSmileRight"],
    "brow_up":["browInnerUp","browOuterUpLeft","browOuterUpRight"]
}
const ANIMATION_ALIASES := {
    "idle":["DefaultAnim","Default","idle","Idle","standing","Standing"],
    "listening":["Listening","listening","listen","DefaultAnim","Default","Idle","idle"],
    "thinking":["Listening","DefaultAnim","Default","thinking","think","Idle","idle"],
    "talking":["Talking","talking","talk","DefaultAnim","Default","Idle","idle","Cheer"],
    "happy":["Cheer","happy","cheering","Cheering","celebrate","DefaultAnim","Idle","idle"],
    "game":["Talking","DefaultAnim","Cheer","game","Idle","idle"],
    "toast":["Cheer","Talking","DefaultAnim","toast","Idle","idle"],
    "dj":["Dance","dance","Cheer","DefaultAnim","dj","Idle","idle"],
    "dance":["Dance","dance","Cheer","DefaultAnim","Idle","idle"],
    "walk":["Walk","walk","walking","Walking"],
    "run":["Run","run","running","Running"]
}
const BONE_NAMES := ["Head","Jaw","Eye.L","Eye.R","Lid.L","Lid.R","Ear.L","Ear.R","Chest","Tail1","Tail2","Tail3"]

func _ready() -> void:
    var root: Node = self
    if not model_root_path.is_empty():
        var resolved_root: Node = get_node_or_null(model_root_path)
        if resolved_root != null:
            root = resolved_root
    if not skeleton_path.is_empty(): skeleton = get_node_or_null(skeleton_path) as Skeleton3D
    if not face_mesh_path.is_empty(): face_mesh = get_node_or_null(face_mesh_path) as MeshInstance3D
    if not animation_tree_path.is_empty(): animation_tree = get_node_or_null(animation_tree_path) as AnimationTree
    _auto_discover(root)
    _index_blend_shapes()
    _index_bones()
    print("MARTIN_MODEL_ADAPTER skeleton=%s face=%s morphs=%d bones=%d animations=%d" % [skeleton!=null,face_mesh!=null,blendshape_cache.size(),bone_cache.size(),get_animation_names().size()])

func _auto_discover(root: Node) -> void:
    if root == null: return
    var stack:Array[Node] = [root]
    var best_face:MeshInstance3D = null
    var best_morph_count:int = -1
    while not stack.is_empty():
        var node:Node = stack.pop_back() as Node
        if skeleton == null and node is Skeleton3D: skeleton = node as Skeleton3D
        if animation_tree == null and node is AnimationTree: animation_tree = node as AnimationTree
        if animation_player == null and node is AnimationPlayer: animation_player = node as AnimationPlayer
        if node is MeshInstance3D:
            var candidate := node as MeshInstance3D
            if candidate.mesh != null and candidate.mesh.has_method("get_blend_shape_count"):
                var count:int = int(candidate.mesh.call("get_blend_shape_count"))
                if count > best_morph_count:
                    best_morph_count = count
                    best_face = candidate
        for child_value:Node in node.get_children(): stack.push_back(child_value)
    if face_mesh == null and best_face != null and best_morph_count > 0: face_mesh = best_face

func _index_blend_shapes() -> void:
    blendshape_cache.clear()
    if face_mesh == null or face_mesh.mesh == null or not face_mesh.mesh.has_method("get_blend_shape_count"): return
    var blend_count:int = int(face_mesh.mesh.call("get_blend_shape_count"))
    for i:int in range(blend_count): blendshape_cache[String(face_mesh.mesh.call("get_blend_shape_name",i))] = i

func _index_bones() -> void:
    bone_cache.clear()
    bone_base_positions.clear()
    if skeleton == null: return
    for name:String in BONE_NAMES:
        var idx:int = skeleton.find_bone(name)
        if idx >= 0:
            bone_cache[name] = idx
            bone_base_positions[name] = skeleton.get_bone_pose_position(idx)

func is_production_model_ready() -> bool: return skeleton != null and animation_player != null
func get_capabilities() -> Dictionary:
    return {"skeleton":skeleton!=null,"face_mesh":face_mesh!=null,"blend_shapes":blendshape_cache.keys(),"bones":bone_cache.keys(),"animation_tree":animation_tree!=null,"animation_player":animation_player!=null,"animations":get_animation_names()}

func get_animation_names() -> Array[String]:
    var result:Array[String] = []
    if animation_player == null: return result
    for item:StringName in animation_player.get_animation_list(): result.append(String(item))
    return result

func set_expression(name:String, weight:float) -> void:
    var value:float = clampf(weight,0.0,1.0)
    if _set_first_available(EXPRESSION_ALIASES.get(name,[name]) as Array,value): return
    match name:
        "blink_l": _set_lid("Lid.L",value)
        "blink_r": _set_lid("Lid.R",value)
        "smile": _set_jaw(value*0.10)
        "brow_up": _set_ear_perk(value)
        _: pass

func set_viseme(name:String, weight:float) -> void:
    var value:float = clampf(weight,0.0,1.0)
    if _set_first_available(VISEME_ALIASES.get(name,[name]) as Array,value): return
    if name in ["aa","oh"]: _set_jaw(value * (0.24 if name=="aa" else 0.13))

func drive_simple_lipsync(level:float) -> void:
    var value:float = clampf(level,0.0,1.0)
    var morph_driven:bool = _set_first_available(VISEME_ALIASES.get("aa",[]) as Array,value)
    if morph_driven:
        _set_first_available(VISEME_ALIASES.get("oh",[]) as Array,value*0.35)
    else:
        _set_jaw(0.025 + value*0.23)

func set_eye_look(x:float,y:float) -> void:
    var yaw:float = clampf(x,-1.0,1.0)*0.16
    var pitch:float = clampf(y,-1.0,1.0)*0.12
    var q:Quaternion = Quaternion.from_euler(Vector3(pitch,yaw,0.0))
    for name:String in ["Eye.L","Eye.R"]:
        var idx:int = int(bone_cache.get(name,-1))
        if idx >= 0: skeleton.set_bone_pose_rotation(idx,q)

func clear_face() -> void:
    if face_mesh != null:
        for index_value:Variant in blendshape_cache.values(): face_mesh.set_blend_shape_value(int(index_value),0.0)
    _set_jaw(0.0)
    _set_lid("Lid.L",0.0)
    _set_lid("Lid.R",0.0)
    _set_ear_perk(0.0)

func travel(state_name:String) -> void:
    if animation_tree != null:
        var playback:Variant = animation_tree.get("parameters/playback")
        if playback != null and playback.has_method("travel"):
            playback.travel(state_name)
            return
    play_best_animation(state_name)

func play_best_animation(state_name:String) -> bool:
    if animation_player == null: return false
    var aliases:Array = ANIMATION_ALIASES.get(state_name.to_lower(),[state_name]) as Array
    for alias_value:Variant in aliases:
        var requested:String = String(alias_value)
        if animation_player.has_animation(requested):
            animation_player.play(requested,0.24)
            return true
    var target:String = state_name.to_lower()
    for candidate_name:StringName in animation_player.get_animation_list():
        var s:String = String(candidate_name)
        if s.to_lower().contains(target):
            animation_player.play(candidate_name,0.24)
            return true
    return false

func _set_first_available(aliases:Array,weight:float) -> bool:
    if face_mesh == null: return false
    for alias_value:Variant in aliases:
        var key:String = String(alias_value)
        if blendshape_cache.has(key):
            face_mesh.set_blend_shape_value(int(blendshape_cache[key]),clampf(weight,0.0,1.0))
            return true
    return false

func _set_jaw(open_amount:float) -> void:
    if skeleton == null: return
    var idx:int = int(bone_cache.get("Jaw",-1))
    if idx >= 0: skeleton.set_bone_pose_rotation(idx,Quaternion.from_euler(Vector3(clampf(open_amount,0.0,0.30),0.0,0.0)))

func _set_lid(name:String,amount:float) -> void:
    if skeleton == null: return
    var idx:int = int(bone_cache.get(name,-1))
    if idx < 0: return
    var base:Vector3 = bone_base_positions.get(name,Vector3.ZERO) as Vector3
    skeleton.set_bone_pose_position(idx,base+Vector3(0.0,-0.105*clampf(amount,0.0,1.0),0.0))

func _set_ear_perk(amount:float) -> void:
    if skeleton == null: return
    var a:float = clampf(amount,0.0,1.0)*0.08
    var l:int = int(bone_cache.get("Ear.L",-1))
    var r:int = int(bone_cache.get("Ear.R",-1))
    if l >= 0: skeleton.set_bone_pose_rotation(l,Quaternion.from_euler(Vector3(0.0,0.0,-a)))
    if r >= 0: skeleton.set_bone_pose_rotation(r,Quaternion.from_euler(Vector3(0.0,0.0,a)))