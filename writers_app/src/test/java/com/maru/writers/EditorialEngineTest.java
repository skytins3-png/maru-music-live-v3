package com.maru.writers;
import org.junit.Test;
import static org.junit.Assert.*;
public class EditorialEngineTest {
 @Test public void fiveAuthorsAreUniqueAndSeparate(){
   assertEquals(5,EditorialEngine.WRITERS.length);
   assertEquals(32,EditorialEngine.SINGERS.length);
   assertTrue(EditorialEngine.uniqueWriters());
 }
 @Test public void approvedStoryOnly(){
   String result=EditorialEngine.write("추억","다시 태어나면","한국 발라드","강태준",
     "개인 사연",false,"자료","https://ko.wikipedia.org/wiki/Test",false);
   assertFalse(result.contains("개인 사연"));
   assertFalse(result.contains("자료 발췌"));
   assertTrue(result.contains("[가수:강태준]"));
 }
 @Test public void sourceIsNotReadWithoutApproval(){
   String s=EditorialEngine.write("사연","곡","트로트","없음",
       "이야기",true,"비검증 기사","https://example.org/fake",false);
   assertFalse(s.contains("비검증 기사"));
   assertTrue(s.contains("이야기"));
 }
 @Test public void unknownSingerNotAdded(){
   String s=EditorialEngine.write("주제","노래","연주곡","문세필","",false,
       null,null,false);
   assertFalse(s.contains("[가수:문세필]"));
 }
 @Test public void rolesAreExactlyAuthorsNotOnAir(){
   String s=EditorialEngine.write("다정함","동네","한국 발라드","없음","",false,"", "",false);
   assertTrue(s.contains("[아환]"));
   assertTrue(s.contains("[윤솔]"));
   assertTrue(s.contains("[음악]"));
   assertFalse(s.contains("[문세필]"));
 }
}