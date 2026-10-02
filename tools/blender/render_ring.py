"""Cycles preview render of a Meshy ring GLB.  Usage: python3 tools/blender/render_ring.py <ring.glb> <out.png>"""
import bpy, math, sys
bpy.ops.wm.read_factory_settings(use_empty=True)
bpy.ops.import_scene.gltf(filepath=sys.argv[1])
obj=[o for o in bpy.context.scene.objects if o.type=='MESH'][0]
print("verts", len(obj.data.vertices), "dims", tuple(round(d,3) for d in obj.dimensions))
sc=bpy.context.scene; sc.render.engine='CYCLES'; sc.cycles.samples=48; sc.cycles.device='CPU'
sc.render.resolution_x=sc.render.resolution_y=512
w=bpy.data.worlds.new("w"); sc.world=w; w.use_nodes=True  # noqa: Blender 5 still needs this
bg=w.node_tree.nodes['Background']; bg.inputs[0].default_value=(0.9,0.75,0.55,1); bg.inputs[1].default_value=0.6
for loc,e in (((2,-2,3),800),((-3,1,2),250)):
    bpy.ops.object.light_add(type='AREA',location=loc); L=bpy.context.object; L.data.energy=e; L.data.size=2
    L.rotation_euler=(math.radians(50),0,math.atan2(loc[1],loc[0])+math.pi/2)
bpy.ops.object.camera_add(location=(0,-1.6*max(obj.dimensions),1.1*max(obj.dimensions)))
cam=bpy.context.object; sc.camera=cam
c=cam.constraints.new('TRACK_TO'); c.target=obj
sc.render.filepath=sys.argv[2]; bpy.ops.render.render(write_still=True)
