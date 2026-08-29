package com.imagine.martinhost;
import org.junit.Test;import static org.junit.Assert.*;import java.nio.charset.StandardCharsets;

public class YandexMusicClientTest {
 @Test public void parsesSimpleDownloadInfoWithoutDomParser() throws Exception {
  String xml="<download-info><host>example.yandex.net</host><path>/abc?x=1&amp;y=2</path><ts>123</ts><s>secret</s></download-info>";
  String[] p=YandexMusicClient.parseDownloadInfo(xml.getBytes(StandardCharsets.UTF_8));
  assertArrayEquals(new String[]{"example.yandex.net","/abc?x=1&y=2","123","secret"},p);
 }
 @Test public void rejectsDoctypeAndEntities() throws Exception {
  String xml="<!DOCTYPE x [<!ENTITY a 'b'>]><download-info><host>&a;</host><path>/x</path><ts>1</ts><s>2</s></download-info>";
  try{YandexMusicClient.parseDownloadInfo(xml.getBytes(StandardCharsets.UTF_8));fail("must reject DTD");}catch(IllegalStateException expected){assertTrue(expected.getMessage().contains("Небезопасный"));}
 }
 @Test public void quizPrefersPreviewAndStableBitrate(){assertTrue(YandexMusicClient.candidateRank(true,true,192)<YandexMusicClient.candidateRank(true,false,192));assertTrue(YandexMusicClient.candidateRank(true,true,192)<YandexMusicClient.candidateRank(true,true,320));}
 @Test public void fullPlaybackPrefersNonPreview(){assertTrue(YandexMusicClient.candidateRank(false,false,192)<YandexMusicClient.candidateRank(false,true,192));}
}
