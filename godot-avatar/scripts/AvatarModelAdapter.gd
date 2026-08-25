class_name AvatarModelAdapter
extends Node

## Adapter between Martin's AI behaviour and a production GLB/VRM character.
## The current procedural cat remains a fallback. A production model only needs
## compatible bones and/or facial blend shapes; the party/AI code does not change.

@export var skeleton_path: NodePath
@export var face_mesh_path: NodePath
@export var animation_tree_path: NodePath

var skeleton: Skeleton3D
var face_mesh: MeshInstance3D
var animation_tree: AnimationTree
var blendshape_cache: Dictionary = {}

const VISEME_ALIASES := {
    "sil": ["viseme_sil", "mouthClose", "mouth_close"],
    "aa": ["viseme_aa", "jawOpen", "mouthOpen", "mouth_open"],
    "oh": ["viseme_O", "mouthFunnel", "mouthPucker"],
    "ee": ["viseme_E", "mouthSmile", "mouthSmileLeft", "mouthSmileRight"],
    "pp": ["viseme_PP", "mouthPressLeft", "mouthPressRight"],
}

const EXPRESSION_ALIASES := {
    "blink_l": ["eyeBlinkLeft", "eyesClosed", "blinkLeft"],
    "blink_r": ["eyeBlinkRight", "eyesClosed", "blinkRight"],
    "smile": ["mouthSmile", "mouthSmileLeft", "mouthSmileRight"],
    "brow_up": ["browInnerUp", "browOuterUpLeft", "browOuterUpRight"],
}

func _ready() -> void:
    if not skeleton_path.is_empty(): skeleton = get_node_or_null(skeleton_path)
    if not face_mesh_path.is_empty(): face_mesh = get_node_or_null(face_mesh_path)
    if not animation_tree_path.is_empty(): animation_tree = get_node_or_null(animation_tree_path)
    _index_blend_shapes()

func is_production_model_ready() -> bool:
    return skeleton != null and face_mesh != null

func _index_blend_shapes() -> void:
    blendshape_cache.clear()
    if face_mesh == null or face_mesh.mesh == null: return
    for i in range(face_mesh.mesh.get_blend_shape_count()):
        blendshape_cache[String(face_mesh.mesh.get_blend_shape_name(i))] = i

func set_expression(name: String, weight: float) -> void:
    var aliases: Array = EXPRESSION_ALIASES.get(name, [name])
    _set_first_available(aliases, weight)

func set_viseme(name: String, weight: float) -> void:
    var aliases: Array = VISEME_ALIASES.get(name, [name])
    _set_first_available(aliases, weight)

func drive_simple_lipsync(level: float) -> void:
    var v := clampf(level, 0.0, 1.0)
    set_viseme("aa", v)
    set_viseme("oh", v * 0.35)

func clear_face() -> void:
    if face_mesh == null: return
    for index in blendshape_cache.values():
        face_mesh.set_blend_shape_value(index, 0.0)

func travel(state_name: String) -> void:
    if animation_tree == null: return
    var playback = animation_tree.get("parameters/playback")
    if playback != null and playback.has_method("travel"):
        playback.travel(state_name)

func _set_first_available(aliases: Array, weight: float) -> void:
    if face_mesh == null: return
    for alias in aliases:
        var key := String(alias)
        if blendshape_cache.has(key):
            face_mesh.set_blend_shape_value(int(blendshape_cache[key]), clampf(weight, 0.0, 1.0))
            return
