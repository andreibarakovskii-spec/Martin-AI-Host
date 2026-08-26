package com.imagine.martinhost;

import android.content.Context;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.util.AttributeSet;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Real-time 3D Martin NPC. No bitmap/avatar fallback is used here.
 * The character is built from shaded 3D meshes and animated by AI turn state.
 */
public final class Martin3DView extends GLSurfaceView {
    public enum State { IDLE, LISTENING, THINKING, TALKING, HAPPY, GAME, TOAST, DJ, SLEEPING }
    private final MartinRenderer renderer;

    public Martin3DView(Context c){ super(c); renderer=init(); }
    public Martin3DView(Context c, AttributeSet a){ super(c,a); renderer=init(); }

    private MartinRenderer init(){
        setEGLContextClientVersion(2);
        setPreserveEGLContextOnPause(true);
        MartinRenderer r=new MartinRenderer();
        setRenderer(r);
        setRenderMode(RENDERMODE_CONTINUOUSLY);
        return r;
    }

    public void setState(State s){ renderer.state=s==null?State.IDLE:s; }
    public void setSpeechLevel(float v){ renderer.speech=Math.max(0f,Math.min(1f,v)); }

    private static final class MartinRenderer implements GLSurfaceView.Renderer {
        private final float[] projection=new float[16], view=new float[16], vp=new float[16], model=new float[16], mvp=new float[16];
        private final float[] tmp=new float[16], tmp2=new float[16];
        private Mesh sphere, cone, cylinder;
        private int program, aPos, aNormal, uMvp, uModel, uColor, uLight;
        volatile State state=State.IDLE;
        volatile float speech=0f;
        private long start=System.nanoTime();

        @Override public void onSurfaceCreated(javax.microedition.khronos.egl.EGLConfig config){
            GLES20.glClearColor(0.018f,0.020f,0.040f,1f);
            GLES20.glEnable(GLES20.GL_DEPTH_TEST);
            GLES20.glEnable(GLES20.GL_CULL_FACE);
            GLES20.glCullFace(GLES20.GL_BACK);
            program=link(VERT,FRAG);
            aPos=GLES20.glGetAttribLocation(program,"aPos");
            aNormal=GLES20.glGetAttribLocation(program,"aNormal");
            uMvp=GLES20.glGetUniformLocation(program,"uMvp");
            uModel=GLES20.glGetUniformLocation(program,"uModel");
            uColor=GLES20.glGetUniformLocation(program,"uColor");
            uLight=GLES20.glGetUniformLocation(program,"uLight");
            sphere=Mesh.uvSphere(22,16);
            cone=Mesh.cone(24);
            cylinder=Mesh.cylinder(24);
        }

        @Override public void onSurfaceChanged(javax.microedition.khronos.opengles.GL10 gl,int w,int h){
            GLES20.glViewport(0,0,w,h);
            float aspect=w/(float)Math.max(1,h);
            Matrix.perspectiveM(projection,0,34f,aspect,.1f,100f);
            Matrix.setLookAtM(view,0,0f,1.75f,7.4f, 0f,1.45f,0f, 0f,1f,0f);
            Matrix.multiplyMM(vp,0,projection,0,view,0);
        }

        @Override public void onDrawFrame(javax.microedition.khronos.opengles.GL10 gl){
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT|GLES20.GL_DEPTH_BUFFER_BIT);
            GLES20.glUseProgram(program);
            GLES20.glUniform3f(uLight,-2.5f,6.0f,4.5f);
            float t=(System.nanoTime()-start)/1_000_000_000f;
            drawBackdrop(t);
            drawMartin(t);
        }

        private void drawBackdrop(float t){
            // subtle purple floor/halo, still true 3D geometry
            float pulse=.94f+.05f*(float)Math.sin(t*.9f);
            draw(sphere,0f,1.45f,-1.25f,3.6f*pulse,3.2f*pulse,.22f,0f,0f,0f,0.16f,0.06f,0.33f);
            draw(cylinder,0f,-.10f,0f,2.7f,.06f,2.7f,0f,0f,0f,0.075f,0.075f,0.12f);
        }

        private void drawMartin(float t){
            float bob=.025f*(float)Math.sin(t*2.05f);
            float headYaw=.0f, headPitch=0f, torsoYaw=.0f;
            float armL=-7f, armR=7f, foreR=0f;
            float mouth=0f;
            switch(state){
                case LISTENING:
                    headYaw=5f*(float)Math.sin(t*1.5f); headPitch=-4f; armL=-10f; armR=10f; break;
                case THINKING:
                    headYaw=10f; headPitch=7f; armR=-35f; foreR=-38f; break;
                case TALKING:
                    torsoYaw=2.5f*(float)Math.sin(t*2.2f); armR=-24f+8f*(float)Math.sin(t*3.1f); foreR=-18f; mouth=.12f+.18f*speech; break;
                case HAPPY:
                    bob=.06f*Math.abs((float)Math.sin(t*3.4f)); armL=-35f; armR=35f; break;
                case GAME:
                    armR=-48f; foreR=-42f; headYaw=-6f; break;
                case TOAST:
                    armR=-52f; foreR=-18f; headPitch=-3f; break;
                case DJ:
                    torsoYaw=7f*(float)Math.sin(t*3f); armL=-25f; armR=28f; break;
                case SLEEPING:
                    headPitch=14f; bob=0f; break;
                default: break;
            }

            // Feet and legs
            draw(sphere,-.38f,.30f+bob,0f,.30f,.48f,.32f,0,0,-5f,0.055f,0.06f,0.075f);
            draw(sphere,.38f,.30f+bob,0f,.30f,.48f,.32f,0,0,5f,0.055f,0.06f,0.075f);
            draw(sphere,-.38f,.08f+bob,.20f,.34f,.18f,.48f,0,0,0,0.045f,0.048f,0.06f);
            draw(sphere,.38f,.08f+bob,.20f,.34f,.18f,.48f,0,0,0,0.045f,0.048f,0.06f);

            // Hoodie torso
            draw(sphere,0f,1.14f+bob,0f,.92f,1.18f,.62f,0,torsoYaw,0,0.025f,0.03f,0.045f);
            draw(sphere,0f,1.66f+bob,-.30f,.70f,.34f,.38f,0,torsoYaw,0,0.035f,0.04f,0.055f); // hood
            // zipper stripe
            draw(cylinder,0f,1.03f+bob,.61f,.035f,.72f,.035f,0,0,0,0.19f,0.20f,0.24f);
            // chest M glow-ish badge
            draw(sphere,0f,1.18f+bob,.64f,.18f,.13f,.055f,0,0,0,0.28f,0.06f,0.65f);

            // Neck/head
            draw(sphere,0f,2.38f+bob,.02f,.76f,.70f,.66f,headPitch,headYaw,0,0.12f,0.125f,0.14f);
            // muzzle
            draw(sphere,-.20f,2.20f+bob,.58f,.28f,.22f,.20f,0,headYaw,0,0.34f,0.33f,0.34f);
            draw(sphere,.20f,2.20f+bob,.58f,.28f,.22f,.20f,0,headYaw,0,0.34f,0.33f,0.34f);
            // nose
            draw(sphere,0f,2.25f+bob,.78f,.12f,.09f,.09f,0,0,0,0.22f,0.11f,0.14f);
            // mouth/talking
            if(mouth>0.01f) draw(sphere,0f,2.08f+bob,.71f,.19f,.06f+mouth*.16f,.045f,0,0,0,0.15f,0.025f,0.035f);

            // Eyes + pupils
            draw(sphere,-.27f,2.49f+bob,.59f,.22f,.25f,.11f,0,0,0,0.52f,0.95f,0.22f);
            draw(sphere,.27f,2.49f+bob,.59f,.22f,.25f,.11f,0,0,0,0.52f,0.95f,0.22f);
            draw(sphere,-.27f,2.49f+bob,.695f,.085f,.13f,.045f,0,0,0,0.015f,0.02f,0.02f);
            draw(sphere,.27f,2.49f+bob,.695f,.085f,.13f,.045f,0,0,0,0.015f,0.02f,0.02f);
            // eye catchlights
            draw(sphere,-.22f,2.56f+bob,.735f,.035f,.035f,.025f,0,0,0,.9f,.9f,.92f);
            draw(sphere,.32f,2.56f+bob,.735f,.035f,.035f,.025f,0,0,0,.9f,.9f,.92f);

            // Ears
            draw(cone,-.43f,2.95f+bob,.02f,.31f,.58f,.31f,-7f,0,-12f,0.11f,0.115f,0.13f);
            draw(cone,.43f,2.95f+bob,.02f,.31f,.58f,.31f,-7f,0,12f,0.11f,0.115f,0.13f);
            draw(cone,-.43f,2.95f+bob,.11f,.17f,.38f,.17f,-7f,0,-12f,0.58f,0.25f,0.34f);
            draw(cone,.43f,2.95f+bob,.11f,.17f,.38f,.17f,-7f,0,12f,0.58f,0.25f,0.34f);

            // Arms / sleeves
            drawLimb(-.78f,1.62f+bob,.02f,-.62f,1.00f+bob,.40f,.24f,armL,0.025f,0.03f,0.045f);
            drawLimb(.78f,1.62f+bob,.02f,.67f,1.02f+bob,.42f,.24f,armR,0.025f,0.03f,0.045f);
            // right forearm gesture
            if(state==State.THINKING || state==State.TALKING || state==State.GAME || state==State.TOAST){
                drawLimb(.67f,1.02f+bob,.42f,.91f,1.55f+bob,.67f,.20f,foreR,0.09f,0.095f,0.11f);
                draw(sphere,.91f,1.58f+bob,.68f,.21f,.21f,.20f,0,0,0,0.13f,0.135f,0.15f);
            } else {
                draw(sphere,.64f,.84f+bob,.50f,.21f,.21f,.20f,0,0,0,0.13f,0.135f,0.15f);
                draw(sphere,-.61f,.84f+bob,.49f,.21f,.21f,.20f,0,0,0,0.13f,0.135f,0.15f);
            }

            // Tail behind body
            drawLimb(.55f,.80f+bob,-.48f,1.22f,1.22f+bob,-.62f,.16f,18f+8f*(float)Math.sin(t*1.7f),0.10f,0.105f,0.12f);
            drawLimb(1.22f,1.22f+bob,-.62f,1.34f,1.78f+bob,-.52f,.13f,10f*(float)Math.sin(t*1.7f),0.10f,0.105f,0.12f);

            // microphone in left paw, key visual from concept
            draw(cylinder,-.69f,.95f+bob,.63f,.07f,.48f,.07f,18f,0,-18f,0.12f,0.12f,0.14f);
            draw(sphere,-.84f,1.19f+bob,.70f,.15f,.18f,.15f,0,0,0,0.06f,0.065f,0.075f);
        }

        private void drawLimb(float ax,float ay,float az,float bx,float by,float bz,float radius,float extraRot,float r,float g,float b){
            float mx=(ax+bx)*.5f,my=(ay+by)*.5f,mz=(az+bz)*.5f;
            float dx=bx-ax,dy=by-ay,dz=bz-az;
            float len=(float)Math.sqrt(dx*dx+dy*dy+dz*dz);
            float yaw=(float)Math.toDegrees(Math.atan2(dx,dz));
            float pitch=-(float)Math.toDegrees(Math.atan2(dy,Math.sqrt(dx*dx+dz*dz)))+90f;
            draw(cylinder,mx,my,mz,radius,len*.5f,radius,pitch+extraRot,yaw,0,r,g,b);
        }

        private void draw(Mesh mesh,float x,float y,float z,float sx,float sy,float sz,float rx,float ry,float rz,float r,float g,float b){
            Matrix.setIdentityM(model,0);
            Matrix.translateM(model,0,x,y,z);
            Matrix.rotateM(model,0,ry,0,1,0);
            Matrix.rotateM(model,0,rx,1,0,0);
            Matrix.rotateM(model,0,rz,0,0,1);
            Matrix.scaleM(model,0,sx,sy,sz);
            Matrix.multiplyMM(mvp,0,vp,0,model,0);
            GLES20.glUniformMatrix4fv(uMvp,1,false,mvp,0);
            GLES20.glUniformMatrix4fv(uModel,1,false,model,0);
            GLES20.glUniform3f(uColor,r,g,b);
            mesh.draw(aPos,aNormal);
        }

        private static int shader(int type,String code){
            int s=GLES20.glCreateShader(type);GLES20.glShaderSource(s,code);GLES20.glCompileShader(s);
            int[] ok=new int[1];GLES20.glGetShaderiv(s,GLES20.GL_COMPILE_STATUS,ok,0);
            if(ok[0]==0)throw new RuntimeException(GLES20.glGetShaderInfoLog(s));return s;
        }
        private static int link(String vs,String fs){
            int p=GLES20.glCreateProgram();GLES20.glAttachShader(p,shader(GLES20.GL_VERTEX_SHADER,vs));GLES20.glAttachShader(p,shader(GLES20.GL_FRAGMENT_SHADER,fs));GLES20.glLinkProgram(p);
            int[] ok=new int[1];GLES20.glGetProgramiv(p,GLES20.GL_LINK_STATUS,ok,0);if(ok[0]==0)throw new RuntimeException(GLES20.glGetProgramInfoLog(p));return p;
        }

        private static final String VERT=
            "uniform mat4 uMvp; uniform mat4 uModel; attribute vec3 aPos; attribute vec3 aNormal; varying vec3 vN; varying vec3 vP;"+
            "void main(){ vec4 wp=uModel*vec4(aPos,1.0); vP=wp.xyz; vN=normalize(mat3(uModel)*aNormal); gl_Position=uMvp*vec4(aPos,1.0); }";
        private static final String FRAG=
            "precision mediump float; uniform vec3 uColor; uniform vec3 uLight; varying vec3 vN; varying vec3 vP;"+
            "void main(){ vec3 L=normalize(uLight-vP); float d=max(dot(normalize(vN),L),0.0); float rim=pow(1.0-max(dot(normalize(vN),normalize(vec3(0.0,0.0,1.0))),0.0),2.0); vec3 c=uColor*(0.28+0.72*d)+vec3(0.22,0.05,0.45)*rim*0.35; gl_FragColor=vec4(c,1.0); }";
    }

    private static final class Mesh {
        final FloatBuffer v,n; final ShortBuffer idx; final int count;
        Mesh(float[] vv,float[] nn,short[] ii){
            v=fb(vv);n=fb(nn);idx=sb(ii);count=ii.length;
        }
        void draw(int aPos,int aNormal){
            v.position(0);n.position(0);idx.position(0);
            GLES20.glEnableVertexAttribArray(aPos);GLES20.glVertexAttribPointer(aPos,3,GLES20.GL_FLOAT,false,0,v);
            GLES20.glEnableVertexAttribArray(aNormal);GLES20.glVertexAttribPointer(aNormal,3,GLES20.GL_FLOAT,false,0,n);
            GLES20.glDrawElements(GLES20.GL_TRIANGLES,count,GLES20.GL_UNSIGNED_SHORT,idx);
            GLES20.glDisableVertexAttribArray(aPos);GLES20.glDisableVertexAttribArray(aNormal);
        }
        static FloatBuffer fb(float[] a){FloatBuffer b=ByteBuffer.allocateDirect(a.length*4).order(ByteOrder.nativeOrder()).asFloatBuffer();b.put(a).position(0);return b;}
        static ShortBuffer sb(short[] a){ShortBuffer b=ByteBuffer.allocateDirect(a.length*2).order(ByteOrder.nativeOrder()).asShortBuffer();b.put(a).position(0);return b;}

        static Mesh uvSphere(int seg,int rings){
            List<Float> vv=new ArrayList<>(), nn=new ArrayList<>(); List<Short> ii=new ArrayList<>();
            for(int y=0;y<=rings;y++){
                float vy=y/(float)rings, th=(float)Math.PI*vy;
                float st=(float)Math.sin(th), ct=(float)Math.cos(th);
                for(int x=0;x<=seg;x++){
                    float vx=x/(float)seg, ph=(float)(Math.PI*2*vx);float sp=(float)Math.sin(ph),cp=(float)Math.cos(ph);
                    float px=st*cp,py=ct,pz=st*sp;vv.add(px);vv.add(py);vv.add(pz);nn.add(px);nn.add(py);nn.add(pz);
                }
            }
            for(int y=0;y<rings;y++)for(int x=0;x<seg;x++){short a=(short)(y*(seg+1)+x),b=(short)(a+seg+1);ii.add(a);ii.add(b);ii.add((short)(a+1));ii.add((short)(a+1));ii.add(b);ii.add((short)(b+1));}
            return new Mesh(fa(vv),fa(nn),sa(ii));
        }

        static Mesh cylinder(int seg){
            List<Float> vv=new ArrayList<>(),nn=new ArrayList<>();List<Short> ii=new ArrayList<>();
            for(int y=0;y<2;y++)for(int i=0;i<=seg;i++){float a=(float)(Math.PI*2*i/seg),x=(float)Math.cos(a),z=(float)Math.sin(a);vv.add(x);vv.add(y==0?-1f:1f);vv.add(z);nn.add(x);nn.add(0f);nn.add(z);} 
            for(int i=0;i<seg;i++){short a=(short)i,b=(short)(seg+1+i);ii.add(a);ii.add(b);ii.add((short)(a+1));ii.add((short)(a+1));ii.add(b);ii.add((short)(b+1));}
            return new Mesh(fa(vv),fa(nn),sa(ii));
        }

        static Mesh cone(int seg){
            List<Float> vv=new ArrayList<>(),nn=new ArrayList<>();List<Short> ii=new ArrayList<>();
            vv.add(0f);vv.add(1f);vv.add(0f);nn.add(0f);nn.add(1f);nn.add(0f);
            for(int i=0;i<=seg;i++){float a=(float)(Math.PI*2*i/seg),x=(float)Math.cos(a),z=(float)Math.sin(a);vv.add(x);vv.add(-1f);vv.add(z);float l=(float)Math.sqrt(1.25f);nn.add(x/l);nn.add(.5f/l);nn.add(z/l);} 
            for(int i=0;i<seg;i++){ii.add((short)0);ii.add((short)(1+i));ii.add((short)(2+i));}
            return new Mesh(fa(vv),fa(nn),sa(ii));
        }
        static float[] fa(List<Float> l){float[] a=new float[l.size()];for(int i=0;i<a.length;i++)a[i]=l.get(i);return a;}
        static short[] sa(List<Short> l){short[] a=new short[l.size()];for(int i=0;i<a.length;i++)a[i]=l.get(i);return a;}
    }
}
