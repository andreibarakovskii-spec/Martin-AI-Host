package com.imagine.martinhost;

import java.io.*;
import java.net.*;
import java.security.MessageDigest;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import org.apache.commons.compress.archivers.tar.*;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;

/** Verified staged install for the app-owned Russian Piper/VITS voice. */
public final class FastVoiceModel {
 static final String NAME="vits-piper-ru_RU-ruslan-medium";
 static final String URL="https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/"+NAME+".tar.bz2";
 static final String SHA="0690b1cad01f86e8db9ba988af24898bdc1af774e23cb2e46b9c730269b6fd83";
 static final String MODEL="ru_RU-ruslan-medium.onnx";
 public static synchronized File ensure(File files,BooleanSupplier cancelled,Consumer<String> progress)throws Exception{
  File base=new File(files,"ima-voice-v2"),target=new File(base,NAME);
  if(installed(target))return target;
  if(!base.isDirectory()&&!base.mkdirs())throw new IOException("Нет места для голосовой модели");
  File archive=new File(base,"download.part"),stage=new File(base,"staging");
  remove(stage);if(!stage.mkdirs()&&!stage.isDirectory())throw new IOException("Не удалось создать временный каталог");
  try{
   check(cancelled);progress.accept("Загрузка голоса IMA: около 67 МБ");
   HttpURLConnection c=(HttpURLConnection)new URL(URL).openConnection();
   c.setConnectTimeout(15000);c.setReadTimeout(20000);c.setInstanceFollowRedirects(true);
   MessageDigest digest=MessageDigest.getInstance("SHA-256");
   try{
    if(c.getResponseCode()!=200)throw new IOException("Модель: HTTP "+c.getResponseCode());
    long size=c.getContentLengthLong(),total=0;int last=-1;
    try(InputStream in=c.getInputStream();OutputStream out=new FileOutputStream(archive)){
     byte[] buf=new byte[65536];int n;
     while((n=in.read(buf))!=-1){check(cancelled);total+=n;if(total>100_000_000L)throw new IOException("Неожиданный размер модели");out.write(buf,0,n);digest.update(buf,0,n);int pct=size>0?(int)(total*100/size):0;if(pct!=last){last=pct;progress.accept("Загрузка голоса IMA: "+pct+"%");}}
    }
   }finally{c.disconnect();}
   StringBuilder hex=new StringBuilder();for(byte b:digest.digest())hex.append(String.format(java.util.Locale.US,"%02x",b));
   if(!SHA.contentEquals(hex))throw new IOException("Контрольная сумма голосовой модели не совпала");
   progress.accept("Проверка пройдена. Установка голоса IMA…");
   try(TarArchiveInputStream tar=new TarArchiveInputStream(new BZip2CompressorInputStream(new BufferedInputStream(new FileInputStream(archive))))){
    TarArchiveEntry entry;byte[] buf=new byte[65536];long total=0;
    while((entry=tar.getNextTarEntry())!=null){check(cancelled);File out=safeTarget(stage,entry.getName());
     if(entry.isSymbolicLink()||entry.isLink())throw new IOException("Ссылки в архиве запрещены");
     if(entry.isDirectory()){if(!out.mkdirs()&&!out.isDirectory())throw new IOException("Не удалось создать каталог модели");continue;}if(!entry.isFile())continue;
     File parent=out.getParentFile();if(!parent.mkdirs()&&!parent.isDirectory())throw new IOException("Не удалось создать каталог модели");
     try(OutputStream dest=new FileOutputStream(out)){int n;while((n=tar.read(buf))!=-1){check(cancelled);total+=n;if(total>250_000_000L)throw new IOException("Слишком большой архив");dest.write(buf,0,n);}}
    }
   }
   File unpacked=new File(stage,NAME);if(!installed(unpacked))throw new IOException("Неполный пакет голоса IMA");
   check(cancelled);remove(target);if(!unpacked.renameTo(target))throw new IOException("Не удалось установить голос IMA");return target;
  }finally{archive.delete();remove(stage);}
 }
 static File safeTarget(File stage,String name)throws IOException{
  File f=new File(stage,name);String root=stage.getCanonicalPath()+File.separator;if(!f.getCanonicalPath().startsWith(root))throw new IOException("Недопустимый путь в архиве");return f;
 }
 static boolean installed(File d){
  File model=new File(d,MODEL),tokens=new File(d,"tokens.txt"),data=new File(d,"espeak-ng-data");
  if(!model.isFile()||model.length()==0||!tokens.isFile()||tokens.length()==0||!data.isDirectory())return false;
  File[] children=data.listFiles();return children!=null&&children.length>0;
 }
 private static void check(BooleanSupplier cancelled)throws IOException{if(cancelled.getAsBoolean()||Thread.currentThread().isInterrupted())throw new InterruptedIOException("Подготовка отменена");}
 private static void remove(File f)throws IOException{if(!f.exists())return;if(f.isDirectory()){File[] children=f.listFiles();if(children==null)throw new IOException("Не удалось прочитать каталог");for(File c:children)remove(c);}if(!f.delete())throw new IOException("Не удалось удалить временный файл");}
}
