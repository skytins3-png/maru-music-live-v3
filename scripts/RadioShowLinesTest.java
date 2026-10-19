import com.maru.radio.RadioShowLines;
import java.util.List;
public class RadioShowLinesTest {
  public static void main(String[] args) {
    for (int cat=1;cat<=4;cat++) {
      List<RadioShowLines.Line> result=RadioShowLines.intro(cat, "안녕.mp3",1);
      if(result.size()!=3)throw new AssertionError("3 host lines required");
      for (RadioShowLines.Line l:result) {
        if(l.text==null||l.text.isEmpty())throw new AssertionError("empty dialog");
        if(l.speaker==null||l.speaker.isEmpty())throw new AssertionError("no host");
      }
    }
    if(!RadioShowLines.intro(2,"你好.m4a",1).get(1).locale.getLanguage().equals("zh"))throw new AssertionError("Chinese host missing");
    if(!RadioShowLines.intro(3,"Summer.mp3",1).get(1).locale.getLanguage().equals("en"))throw new AssertionError("English host missing");
    if(!RadioShowLines.tidy("<script>.mp3").equals("script"))throw new AssertionError("title not sanitized");
    if(RadioShowLines.tidy(null).isEmpty())throw new AssertionError("null title");
    if(RadioShowLines.tidy("x".repeat(1000)).length()!=48)throw new AssertionError("long title");
    System.out.println("MARU native original-song DJ script contract: PASS");
  }
}
