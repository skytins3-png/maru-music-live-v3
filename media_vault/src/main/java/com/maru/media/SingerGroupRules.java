package com.maru.media;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
/** Fallback is a DISPLAY/ORGANIZATION group, not a claim about the real vocalist. */
public final class SingerGroupRules {
  public static final List<String> GROUPS=Arrays.asList("아환","아환의 친구들","후배들","중국친구","러시아친구");
  private static final Pattern CYRILLIC=Pattern.compile("[\\u0400-\\u04FF]");
  private SingerGroupRules(){}
  public static boolean isGroup(String name){return GROUPS.contains(name);}
  public static String suggest(String language,String genre,String title,String filename){
    String all=(title==null?"":title)+" "+(filename==null?"":filename);
    String g=genre==null?"":genre, l=language==null?"":language;
    if(CYRILLIC.matcher(all).find()||l.contains("러시아")||g.contains("러시아"))return "러시아친구";
    if(l.contains("중국")||g.contains("중국"))return "중국친구";
    if(g.contains("트로트")||g.contains("발라드")||g.contains("포크"))return "아환";
    if(g.contains("댄스")||g.contains("록")||g.contains("힙합")||g.contains("랩"))return "후배들";
    return "아환의 친구들";
  }
}
