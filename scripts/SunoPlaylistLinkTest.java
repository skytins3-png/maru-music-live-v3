import com.maru.radio.SunoPlaylistLink;
public class SunoPlaylistLinkTest {
 public static void main(String[] args) {
  String valid="https://suno.com/playlist/abc-123";
  if(!valid.equals(SunoPlaylistLink.normalize(" https://www.suno.com/playlist/abc-123?tracking=1 ")))throw new AssertionError("normalize");
  for(String s:new String[]{"http://suno.com/playlist/x","https://suno.com.evil.test/playlist/x","https://suno.com@evil.test/playlist/x","intent://suno.com/playlist/x","https://suno.com/song/x","https://suno.com/playlist/","https://suno.com:99/playlist/x"})
   if(SunoPlaylistLink.normalize(s)!=null)throw new AssertionError(s);
  System.out.println("PASS official playlist URL validation");
 }
}
