package com.imagine.martinhost;

import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import org.apache.commons.compress.archivers.tar.*;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;

/** Verified, staged install. Existing LiteRT files are left untouched. */
public final class FastVoiceModel {
 static final String NAME="sherpa-onnx-supertonic-3-tts-int8-2026-05-11";
 static final String URL="https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/"+NAME+".tar.bz2";
 static final String SHA="82fa96f91c4ef8abaae3a14a3f4153facf88bed821d1f7331cec2700f432c427";
 static final String[] FILES={"duration_predictor.int8.onnx","text_encoder.int8.onnx","vector_estimator.int8.onnx","vocoder.int8.onnx","tts.json","unicode_indexer.bin","voice.bin"};
 public static synchronized File ensure(File files,BooleanSupplier cancelled,Consumer<String> progress)throws Exception{
  File base=new File(files,"fast-voice-v1"),target=new File(base,NAME);
  if(installed(target))return target;
  if(!base.isDirectory()&&!base.mkdirs())throw new IOException("Нет места для модели");
  File archive=new File(base,"download.part"),stage=new File(base,"staging");
  remove(stage);stage.mkdirs();
  try{
   check(cancelled);progress.accept("Загрузка новой голосовой модели: около 129 МБ");
   HttpURLConnection c=(HttpURLConnection)new URL(URL).openConnection();
   c.setConnectTimeout(15000);c.setReadTimeout(15000);
   MessageDigest digest=MessageDigest.getInstance("SHA-256");
   try{
    if(c.getResponseCode()!=200)throw new IOException("Модель: HTTP "+c.getResponseCode());
    long size=c.getContentLengthLong(),total=0;int last=-1;
    try(InputStream in=c.getInputStream();OutputStream out=new FileOutputStream(archive)){
     byte[] buf=new byte[65536];int n;
     while((n=in.read(buf))!=-1){check(cancelled);total+=n;if(total>200_000_000L)throw new IOException("Неожиданный размер модели");out.write(buf,0,n);digest.update(buf,0,n);int pct=size>0?(int)(total*100/size):0;if(pct!=last){last=pct;progress.accept("Загрузка INT8-модели: "+pct+"%");}}
    }
   }finally{c.disconnect();}
   StringBuilder hex=new StringBuilder();for(byte b:digest.digest())hex.append(String.format(java.util.Locale.US,"%02x",b));
   if(!SHA.contentEquals(hex))throw new IOException("Контрольная сумма модели не совпала");
   progress.accept("Проверка пройдена. Распаковка голосов…");
   try(TarArchiveInputStream tar=new TarArchiveInputStream(new BZip2CompressorInputStream(new BufferedInputStream(new FileInputStream(archive))))){
    TarArchiveEntry entry;byte[] buf=new byte[65536];long total=0;
    while((entry=tar.getNextTarEntry())!=null){check(cancelled);File out=safeTarget(stage,entry.getName());
     if(entry.isSymbolicLink()||entry.isLink())throw new IOException("Ссылки в архиве запрещены");
     if(entry.isDirectory()){out.mkdirs();continue;}if(!entry.isFile())continue;
     out.getParentFile().mkdirs();try(OutputStream dest=new FileOutputStream(out)){int n;while((n=tar.read(buf))!=-1){check(cancelled);total+=n;if(total>600_000_000L)throw new IOException("Слишком большой архив");dest.write(buf,0,n);}}
    }
   }
   File unpacked=new File(stage,NAME);if(!installed(unpacked))throw new IOException("Неполный пакет модели");
   check(cancelled);remove(target);if(!unpacked.renameTo(target))throw new IOException("Не удалось установить модель");return target;
  }finally{archive.delete();remove(stage);}
 }
 static File safeTarget(File stage,String name)throws IOException{
  File f=new File(stage,name);if(!f.getCanonicalPath().startsWith(stage.getCanonicalPath()+File.separator))throw new IOException("Недопустимый путь в архиве");return f;
 }
 static boolean installed(File d){for(String n:FILES){File f=new File(d,n);if(!f.isFile()||f.length()==0)return false;}return true;}
 private static void check(BooleanSupplier cancelled)throws IOException{if(cancelled.getAsBoolean()||Thread.currentThread().isInterrupted())throw new InterruptedIOException("Подготовка отменена");}
 private static void remove(File f)throws IOException{if(!f.exists())return;if(f.isDirectory()){File[] children=f.listFiles();if(children==null)throw new IOException("Не удалось прочитать каталог");for(File c:children)remove(c);}if(!f.delete())throw new IOException("Не удалось удалить временный файл");}
}
