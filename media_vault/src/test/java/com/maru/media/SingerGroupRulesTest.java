package com.maru.media;
import org.junit.Test;
import static org.junit.Assert.*;
public final class SingerGroupRulesTest {
 @Test public void genreAndLanguageRecommendOnlyAllowedGroups(){
   assertEquals("아환",SingerGroupRules.suggest("한국노래","트로트","신나는 인생","a.mp3"));
   assertEquals("아환",SingerGroupRules.suggest("한국노래","발라드","그리움","a.mp3"));
   assertEquals("후배들",SingerGroupRules.suggest("한국노래","댄스","춤을 추자","b.mp3"));
   assertEquals("중국친구",SingerGroupRules.suggest("중국노래","발라드","云南的清晨","c.mp3"));
   assertEquals("러시아친구",SingerGroupRules.suggest("서양노래","발라드","Тихая ночь","d.mp3"));
   assertEquals("아환의 친구들",SingerGroupRules.suggest("서양노래","연주","Music","e.mp3"));
   assertEquals("아환의 친구들",SingerGroupRules.suggest("검토필요","미확인",null,null));
 }
 @Test public void onlyFiveArtistNamesAreAllowed(){
   assertEquals(5,SingerGroupRules.GROUPS.size());
   for(String s:SingerGroupRules.GROUPS)assertTrue(SingerGroupRules.isGroup(s));
   assertFalse(SingerGroupRules.isGroup("유명 실존가수"));
 }
}