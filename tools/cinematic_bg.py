import numpy as np
from PIL import Image, ImageFilter
from scipy.ndimage import gaussian_filter
W,H=1600,900
rng=np.random.default_rng(7)
yy,xx=np.mgrid[0:H,0:W].astype(np.float32)
u=xx/W; v=yy/H
# ---- base: deep charcoal with a cool tint, lighter towards the upper right, dark corners
base=np.zeros((H,W,3),np.float32)
top=np.array([22,22,27],np.float32); bot=np.array([7,7,9],np.float32)
base+= top*(1-v[...,None])+bot*v[...,None]
# soft studio light upper right
d=np.sqrt(((u-0.78)*1.6)**2+((v-0.05)*1.2)**2)
base+= (np.clip(1-d,0,1)**2.2)[...,None]*np.array([46,46,54],np.float32)
# soft floor glow bottom (where the menu stands)
d2=np.sqrt(((u-0.5)*1.2)**2+((v-1.05)*2.6)**2)
base+= (np.clip(1-d2,0,1)**2)[...,None]*np.array([14,14,18],np.float32)
# gentle fog banks
fog=gaussian_filter(rng.random((H//8,W//8)).astype(np.float32),6)
fog=(fog-fog.min())/(fog.max()-fog.min())
fog=np.array(Image.fromarray((fog*255).astype(np.uint8)).resize((W,H),Image.BICUBIC)).astype(np.float32)/255
base+= (fog**2*10)[...,None]
# vignette
vd=np.sqrt(((u-0.5)/0.75)**2+((v-0.48)/0.75)**2)
base*= (1-np.clip(vd-0.45,0,1)**1.6*0.85)[...,None]
# dither to avoid banding
base+= rng.uniform(-0.6,0.6,(H,W,1))
Image.fromarray(np.clip(base,0,255).astype(np.uint8)).save('cinematic_bg.png',optimize=True)

# ---- light layer (moves more with the mouse): long soft beams from the top right down onto the player
L=np.zeros((H,W),np.float32)
asp=W/H
def beam(x0,y0,x1,y1,w0,w1,strength):
    global L
    px=(u-x0)*asp; py=v-y0
    dx=(x1-x0)*asp; dy=y1-y0
    ln=np.hypot(dx,dy); dx/=ln; dy/=ln
    along=(px*dx+py*dy)/ln            # 0 at the source, 1 at the end
    dist=np.abs(px*dy-py*dx)
    width=w0+(w1-w0)*np.clip(along,0,1)
    fade=np.clip(along,0,1)**0.3*np.clip(1.15-along,0,1)
    L+=strength*np.exp(-(dist/width)**2)*fade
beam(0.93,-0.05,0.70,1.05,0.010,0.09,0.28)
beam(0.97,-0.05,0.80,1.05,0.006,0.05,0.30)
beam(0.88,-0.05,0.58,1.05,0.012,0.12,0.16)
beam(1.02,-0.05,0.90,1.05,0.004,0.04,0.22)
beam(0.95,-0.05,0.76,1.05,0.003,0.02,0.35)
L=gaussian_filter(L,2)
# the spot where the light lands (the player stands there)
L+=np.exp(-(((u-0.76)/0.16)**2+((v-0.62)/0.30)**2))*0.18
L+=np.exp(-(((u-0.94)/0.08)**2+((v+0.02)/0.06)**2))*0.5
L=np.clip(L,0,1)
alpha=(L*120)+rng.uniform(-0.5,0.5,(H,W))
rgba=np.zeros((H,W,4),np.uint8); rgba[...,:3]=255; rgba[...,3]=np.clip(alpha,0,255).astype(np.uint8)
Image.fromarray(rgba,'RGBA').save('cinematic_light.png',optimize=True)
# preview composite
b=np.array(Image.open('cinematic_bg.png')).astype(np.float32)
a=rgba[...,3:4].astype(np.float32)/255
comp=b*(1-a)+255*a
Image.fromarray(np.clip(comp,0,255).astype(np.uint8)).save('preview.png')
