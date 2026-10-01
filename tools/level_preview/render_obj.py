#!/usr/bin/env python3
"""Tiny numpy software rasteriser for OBJ meshes (flat shading, z-buffer) used to eyeball exported ring meshes."""
import sys, numpy as np
from PIL import Image
def load(path):
    V=[];F=[]
    for l in open(path):
        if l.startswith('v '): V.append([float(x) for x in l.split()[1:4]])
        elif l.startswith('f '): F.append([int(x.split('/')[0])-1 for x in l.split()[1:4]])
    return np.array(V),np.array(F)
def render(V,F,S=700,tilt=35):
    t=np.radians(tilt); c=np.array([0.5,0.5,0]); P=V-c
    # rotate about x so we look down at tilt
    R=np.array([[1,0,0],[0,np.cos(t),-np.sin(t)],[0,np.sin(t),np.cos(t)]]); Q=P@R.T
    sx=(Q[:,0]*1.6+0.5)*S; sy=(0.5-Q[:,1]*1.6)*S; sz=Q[:,2]
    img=np.full((S,S,3),np.array([0.82,0.66,0.46])); zb=np.full((S,S),-1e9)
    L=np.array([-0.45,0.35,0.82]); L/=np.linalg.norm(L)
    for f in F:
        a,b,cc=V[f]; n=np.cross(b-a,cc-a); nn=np.linalg.norm(n)
        if nn<1e-12: continue
        n/=nn; shade=0.25+0.75*max(0,n@L)+0.3*max(0,n@np.array([0.6,-0.2,0.5]))
        xs=sx[f];ys=sy[f];zs=sz[f]
        x0,x1=int(max(0,xs.min())),int(min(S-1,xs.max()))+1; y0,y1=int(max(0,ys.min())),int(min(S-1,ys.max()))+1
        if x1<=x0 or y1<=y0: continue
        X,Y=np.meshgrid(np.arange(x0,x1),np.arange(y0,y1))
        d=(xs[1]-xs[0])*(ys[2]-ys[0])-(xs[2]-xs[0])*(ys[1]-ys[0])
        if abs(d)<1e-9: continue
        w1=((X-xs[0])*(ys[2]-ys[0])-(xs[2]-xs[0])*(Y-ys[0]))/d; w2=((xs[1]-xs[0])*(Y-ys[0])-(X-xs[0])*(ys[1]-ys[0]))/d; w0=1-w1-w2
        m=(w0>=0)&(w1>=0)&(w2>=0); z=w0*zs[0]+w1*zs[1]+w2*zs[2]
        upd=m&(z>zb[y0:y1,x0:x1]); zb[y0:y1,x0:x1][upd]=z[upd]
        col=np.array([0.93,0.83,0.66])*shade; img[y0:y1,x0:x1][upd]=col
    return Image.fromarray((np.clip(img,0,1)**(1/2.2)*255).astype(np.uint8))
if __name__=='__main__':
    V,F=load(sys.argv[1]); render(V,F).save(sys.argv[2]); print(sys.argv[2], len(V), len(F))
