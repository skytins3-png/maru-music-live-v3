package com.maru.media;
import org.junit.Test;
import static org.junit.Assert.*;
public class CatalogLogicTest {
 @Test public void genresAreNeverGuessed(){assertEquals("미확인",CatalogLogic.GENRES[0]);}
 @Test public void classifyByVisibleTitleOnly(){
   assertEquals("중국노래",CatalogLogic.group("年年相伴.mp3"));
   assertEquals("한국노래",CatalogLogic.group("흔들리는 사랑.mp3"));
   assertEquals("일본노래",CatalogLogic.group("はじまりの心.mp3"));
   assertEquals("서양노래",CatalogLogic.group("Only You.mp3"));
   assertEquals("검토필요",CatalogLogic.group("393a0c0a-f144-496a-a321-4d4c81ea8340.mp3"));
 }
 @Test public void onlyAudioAndSupportedArchives(){
   assertTrue(CatalogLogic.audio("sample.MP3"));
   assertTrue(CatalogLogic.audio("sample.m4a"));
   assertTrue(CatalogLogic.audio("sample.wav"));
   assertFalse(CatalogLogic.audio("secret.apk"));
   assertTrue(CatalogLogic.zip("album.zip"));
 }
 @Test public void hashFormatting(){
   assertEquals("00ff",CatalogLogic.hashHex(new byte[]{0,(byte)255}));
   assertFalse(CatalogLogic.sha256("00ff"));
 }
}