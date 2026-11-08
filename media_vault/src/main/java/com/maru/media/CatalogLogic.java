package com.maru.media;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.regex.Pattern;
public final class CatalogLogic {
 public static final String[] LANGUAGES={"전체","한국노래","중국노래","서양노래","일본노래","검토필요"};
 public static final String[] GENRES={"미확인","트로트","발라드","연주","록발라드","컨트리팝","댄스","기타"};
 private static final Pattern UUID=Pattern.compile("^[a-f0-9]{8}-[a-f0-9-]{27,}",Pattern.CASE_INSENSITIVE);
 private static final Pattern HANGUL=Pattern.compile("[가-힣]");
 private static final Pattern HAN=Pattern.compile("[\u4e00-\u9fff]");
 private static final Pattern JAPANESE=Pattern.compile("[\u3040-\u30ff]");
 private CatalogLogic(){}
 public static boolean audio(String filename){
   if(filename==null)return false;
   String n=filename.toLowerCase(Locale.ROOT);
   return n.endsWith(".mp3")||n.endsWith(".m4a")||n.endsWith(".wav");
 }
 public static boolean zip(String filename){
   return filename!=null && filename.toLowerCase(Locale.ROOT).endsWith(".zip");
 }
 public static String base(String filename){
   if(filename==null)return "이름 없는 곡";
   int p=filename.lastIndexOf('/');
   String n=p>=0?filename.substring(p+1):filename;
   int dot=n.lastIndexOf('.');
   if(dot>=0)n=n.substring(0,dot);
   n=n.replaceAll("[\\p{Cntrl}]+"," ").trim();
   return n.isEmpty()?"이름 없는 곡":n;
 }
 public static String group(String filename){
   String n=base(filename);
   if(UUID.matcher(n).matches())return "검토필요";
   // Legacy export names append Korean '복사용' annotations to English songs.
   if(n.startsWith("Earth Survival")||n.startsWith("Fourteen Days")||n.startsWith("Just Another")
          ||n.startsWith("The Captain"))return "서양노래";
   if(JAPANESE.matcher(n).find())return "일본노래";
   if(HAN.matcher(n).find())return "중국노래";
   if(HANGUL.matcher(n).find())return "한국노래";
   if(n.matches(".*[A-Za-z].*"))return "서양노래";
   return "검토필요";
 }
 public static String hashHex(byte[] bytes){
   StringBuilder b=new StringBuilder(bytes.length*2);
   for(byte x:bytes)b.append(Character.forDigit((x>>>4)&15,16)).append(Character.forDigit(x&15,16));
   return b.toString();
 }
 public static boolean sha256(String s){
   return s!=null&&s.matches("[a-f0-9]{64}");
 }
 public static String cleanText(String input,int max){
   if(input==null)return "";
   String s=input.replaceAll("[\\p{Cntrl}]+"," ").replaceAll("\\s+"," ").trim();
   return s.length()>max?s.substring(0,max):s;
 }
}