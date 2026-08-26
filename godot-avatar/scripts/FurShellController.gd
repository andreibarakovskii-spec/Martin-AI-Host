class_name FurShellController
extends Node

const FUR_SHADER := preload("res://shaders/martin_fur_shell.gdshader")

@export var model_root_path: NodePath
@export_range(1, 5, 1) var shell_count := 3
@export_range(0.001, 0.03, 0.001) var max_fur_length := 0.012
@export_range(0.0, 1.0, 0.01) var base_alpha := 0.24
@export var fur_color := Color(0.22, 0.24, 0.29, 1.0)

var shell_nodes: Array[MeshInstance3D] = []

func _ready() -> void:
    var root := get_node_or_null(model_root_path) if not model_root_path.is_empty() else get_parent()
    if root == null:
        push_warning("MARTIN_FUR_NO_ROOT")
        return
    await get_tree().process_frame
    _build_shells(root)

func _build_shells(root: Node) -> void:
    var meshes: Array[MeshInstance3D] = []
    _collect_meshes(root, meshes)
    var source_count := 0
    for source in meshes:
        if not _looks_like_fur(source):
            continue
        source_count += 1
        for layer in range(1, shell_count + 1):
            var shell := MeshInstance3D.new()
            shell.name = "%s_FurShell_%d" % [source.name, layer]
            shell.mesh = source.mesh
            shell.skin = source.skin
            shell.skeleton = source.skeleton
            shell.transform = source.transform
            shell.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_OFF
            shell.extra_cull_margin = max_fur_length * 2.0
            var ratio := float(layer) / float(shell_count)
            var material := ShaderMaterial.new()
            material.shader = FUR_SHADER
            material.set_shader_parameter("fur_color", fur_color)
            material.set_shader_parameter("shell_offset", max_fur_length * ratio)
            material.set_shader_parameter("shell_alpha", base_alpha * (1.0 - ratio * 0.28))
            material.set_shader_parameter("shell_ratio", ratio)
            material.set_shader_parameter("strand_density", 58.0 - ratio * 8.0)
            material.set_shader_parameter("scruffiness", 0.16 + ratio * 0.10)
            shell.material_override = material
            source.get_parent().add_child(shell)
            shell_nodes.append(shell)
    print("MARTIN_FUR_READY sources=%d shells=%d" % [source_count, shell_nodes.size()])

func _collect_meshes(node: Node, out: Array[MeshInstance3D]) -> void:
    if node is MeshInstance3D:
        out.append(node as MeshInstance3D)
    for child in node.get_children():
        _collect_meshes(child, out)

func _looks_like_fur(mesh_node: MeshInstance3D) -> bool:
    if mesh_node.mesh == null or not mesh_node.visible:
        return false
    # Martin v2 explicitly marks only real furry surfaces. This prevents the
    # fallback Cat Pilot, clothes, eyes and accessories from receiving shells.
    return mesh_node.name.begins_with("Fur_")
