import com.maru.radio.*;
import java.util.*;
public class RadioGuestShowTest {
 public static void main(String[] args) {
  Set<String> titles=new HashSet<>();
  for(RadioGuestShow.Guest g:RadioGuestShow.GUESTS){
   if(!titles.add(g.title))throw new AssertionError("duplicate title");
   if(RadioGuestShow.find(g.title+" — "+g.name+".mp3",g.name)!=g)throw new AssertionError("filename mapping");
   if(RadioGuestShow.find(g.title,"other")!=null)throw new AssertionError("wrong guest");
   List<RadioShowLines.Line> lines=RadioGuestShow.intro(g.title,g.name,true,"");
   if(lines.size()!=6||!lines.get(2).speaker.equals(g.name))throw new AssertionError("host guest order");
   if(!lines.get(5).text.contains(g.title))throw new AssertionError("wrong playback introduction");
  }
  if(titles.size()!=30)throw new AssertionError("roster");
  if(RadioGuestShow.intro("unknown",null,false,"").size()!=1)throw new AssertionError("unknown artist fabricated");
  if(!RadioGuestShow.intro("unknown",null,false,"previous").get(0).text.contains("previous"))throw new AssertionError("back announce");
  System.out.println("PASS: 30 distinct guest scripts, filename matching, safe fallback and programme transitions");
 }
}
