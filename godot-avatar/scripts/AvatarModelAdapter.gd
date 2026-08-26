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

const VISEME_ALIASES := {"sil":["viseme_sil","mouthClose","mouth_close"],"aa":["viseme_aa","jawOpen","mouthOpen","mouth_open"],"oh":["viseme_O","mouthFunnel","mouthPucker"],"ee":["viseme_E","mouthSmile","mouthSmileLeft","mouthSmileRight"],"pp":["viseme_PP","mouthPressLeft","mouthPressRight"]}
const EXPRESSION_ALIASES := {"blink_l":["eyeBlinkLeft","eyesClosed","blinkLeft"],"blink_r":["eyeBlinkRight","eyesClosed","blinkRight"],"smile":["mouthSmile","mouthSmileLeft","mouthSmileRight"],"brow_up":["browInnerUp","browOuterUpLeft","browOuterUpRight"]}
const ANIMATION_ALIASES := {
    "idle":["DefaultAnim","Default","idle","Idle","standing","Standing"],
    "listening":["DefaultAnim","Default","listening","listen","Idle","idle"],
    "thinking":["DefaultAnim","Default","thinking","think","Idle","idle"],
    "talking":["DefaultAnim","Default","talking","talk","Idle","idle","Cheer"],
    "happy":["Cheer","DefaultAnim","happy","cheering","Cheering","celebrate","Idle","idle"],
    "game":["DefaultAnim","Cheer","game","Idle","idle"],
    "toast":["Cheer","DefaultAnim","toast","Idle","idle"],
    "dj":["Cheer","DefaultAnim","dj","dance","Dance","Idle","idle"],
    "dance":["Cheer","DefaultAnim","dance","Dance","Idle","idle"],
    "walk":["Walk","walk","walking","Walking"],
    "run":["Run","run","running","Running"]
}

func _ready() -> void:
    var root: Node = self
    if not model_root_path.is_empty():
        var resolved_root: Node = get_node_or_null(model_root_path)
        if resolved_root != null: root = resolved_root
    if not skeleton_path.is_empty(): skeleton = get_node_or_null(skeleton_path) as Skeleton3D
    if not face_mesh_path.is_empty(): face_mesh = get_node_or_null(face_mesh_path) as MeshInstance3D
    if not animation_tree_path.is_empty(): animation_tree = get_node_or_null(animation_tree_path) as AnimationTree
    _auto_discover(root); _index_blend_shapes()
    print("MARTIN_MODEL_ADAPTER skeleton=%s face=%s morphs=%d animations=%d" % [skeleton!=null,face_mesh!=null,blendshape_cache.size(),get_animation_names().size()])

func _auto_discover(root: Node) -> void:
    if root==null:return
    var stack:Array[Node]=[root];var best_face:MeshInstance3D=null;var best_morph_count:int=-1
    while not stack.is_empty():
        var node:Node=stack.pop_back() as Node
        if skeleton==null and node is Skeleton3D:skeleton=node as Skeleton3D
        if animation_tree==null and node is AnimationTree:animation_tree=node as AnimationTree
        if animation_player==null and node is AnimationPlayer:animation_player=node as AnimationPlayer
        if node is MeshInstance3D:
            var candidate:=node as MeshInstance3D
            if candidate.mesh!=null and candidate.mesh.has_method("get_blend_shape_count"):
                var count:int=int(candidate.mesh.call("get_blend_shape_count"))
                if count>best_morph_count:best_morph_count=count;best_face=candidate
        for child_value:Node in node.get_children():stack.push_back(child_value)
    if face_mesh==null and best_face!=null and best_morph_count>0:face_mesh=best_face

func is_production_model_ready()->bool:return skeleton!=null and animation_player!=null
func get_capabilities()->Dictionary:return {"skeleton":skeleton!=null,"face_mesh":face_mesh!=null,"blend_shapes":blendshape_cache.keys(),"animation_tree":animation_tree!=null,"animation_player":animation_player!=null,"animations":get_animation_names()}
func get_animation_names()->Array[String]:
    var result:Array[String]=[]
    if animation_player==null:return result
    for item:StringName in animation_player.get_animation_list():result.append(String(item))
    return result
func _index_blend_shapes()->void:
    blendshape_cache.clear()
    if face_mesh==null or face_mesh.mesh==null or not face_mesh.mesh.has_method("get_blend_shape_count"):return
    var blend_count:int=int(face_mesh.mesh.call("get_blend_shape_count"))
    for i:int in range(blend_count):blendshape_cache[String(face_mesh.mesh.call("get_blend_shape_name",i))]=i
func set_expression(name:String,weight:float)->void:_set_first_available(EXPRESSION_ALIASES.get(name,[name]) as Array,weight)
func set_viseme(name:String,weight:float)->void:_set_first_available(VISEME_ALIASES.get(name,[name]) as Array,weight)
func drive_simple_lipsync(level:float)->void:
    var value:float=clampf(level,0.0,1.0);set_viseme("aa",value);set_viseme("oh",value*0.35)
func clear_face()->void:
    if face_mesh==null:return
    for index_value:Variant in blendshape_cache.values():face_mesh.set_blend_shape_value(int(index_value),0.0)
func travel(state_name:String)->void:
    if animation_tree!=null:
        var playback:Variant=animation_tree.get("parameters/playback")
        if playback!=null and playback.has_method("travel"):playback.travel(state_name);return
    play_best_animation(state_name)
func play_best_animation(state_name:String)->bool:
    if animation_player==null:return false
    var aliases:Array=ANIMATION_ALIASES.get(state_name.to_lower(),[state_name]) as Array
    for alias_value:Variant in aliases:
        var requested:String=String(alias_value)
        if animation_player.has_animation(requested):animation_player.play(requested,0.20);return true
    var target:String=state_name.to_lower()
    for candidate_name:StringName in animation_player.get_animation_list():
        var s:String=String(candidate_name)
        if s.to_lower().contains(target):animation_player.play(candidate_name,0.20);return true
    return false
func _set_first_available(aliases:Array,weight:float)->void:
    if face_mesh==null:return
    for alias_value:Variant in aliases:
        var key:String=String(alias_value)
        if blendshape_cache.has(key):face_mesh.set_blend_shape_value(int(blendshape_cache[key]),clampf(weight,0.0,1.0));return
