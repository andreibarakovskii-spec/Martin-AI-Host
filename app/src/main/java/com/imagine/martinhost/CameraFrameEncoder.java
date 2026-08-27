package com.imagine.martinhost;
import android.graphics.*;import android.media.Image;import java.io.ByteArrayOutputStream;import java.nio.ByteBuffer;
/** One requested frame, never persisted. Handles padded/interleaved YUV planes. */
final class CameraFrameEncoder {
 static byte[] jpeg(Image image,int rotation){int w=image.getWidth(),h=image.getHeight();byte[] nv21=new byte[w*h*3/2];Image.Plane[] planes=image.getPlanes();
  for(int p=0;p<3;p++){ByteBuffer b=planes[p].getBuffer().duplicate();int base=b.position(),rs=planes[p].getRowStride(),ps=planes[p].getPixelStride();int width=p==0?w:w/2,height=p==0?h:h/2;
   for(int y=0;y<height;y++)for(int x=0;x<width;x++){int dest=p==0?y*w+x:w*h+2*(y*width+x)+(p==1?1:0);nv21[dest]=b.get(base+y*rs+x*ps);}}
  ByteArrayOutputStream raw=new ByteArrayOutputStream();new YuvImage(nv21,ImageFormat.NV21,w,h,null).compressToJpeg(new Rect(0,0,w,h),70,raw);
  byte[] compressed=raw.toByteArray();Bitmap bitmap=BitmapFactory.decodeByteArray(compressed,0,compressed.length);if(bitmap==null)throw new IllegalStateException("Empty camera frame");
  Matrix matrix=new Matrix();matrix.postRotate(rotation);float scale=Math.min(1f,640f/Math.max(w,h));matrix.postScale(scale,scale);Bitmap output=Bitmap.createBitmap(bitmap,0,0,w,h,matrix,true);ByteArrayOutputStream result=new ByteArrayOutputStream();output.compress(Bitmap.CompressFormat.JPEG,65,result);if(output!=bitmap)output.recycle();bitmap.recycle();return result.toByteArray();
 }
}
