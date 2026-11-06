import com.maru.radio.CatalogOrder;
import com.maru.radio.RadioShowLines;
import java.util.*;
public class CatalogOrderTest {
    public static void main(String[] args) {
        Map<String,Integer> available = new HashMap<>();
        List<String> order = new ArrayList<>();
        for (int i=0;i<10000;i++) available.put("h"+i,i);
        for (int i=9999;i>=0;i--) { order.add("h"+i); order.add("h"+i); }
        order.add("deleted");
        List<Integer> result=CatalogOrder.resolve(available,order);
        if(result.size()!=10000 || result.get(0)!=9999 || result.get(9999)!=0) throw new AssertionError("order, duplicate or missing-file handling");
        if(!CatalogOrder.resolve(available,Collections.emptyList()).isEmpty()) throw new AssertionError("empty playlist");
        String intro=RadioShowLines.ahwanIntro("새 노래.mp3","서하린").get(0).text;
        if(!intro.contains("서하린")||!intro.contains("새 노래")||intro.contains(".mp3"))throw new AssertionError("dynamic metadata intro");
        if(RadioShowLines.ahwanIntro(null,"미지정").get(0).text.contains("미지정의"))throw new AssertionError("unknown singer");
        System.out.println("PASS: 10000-track ordered playlist, duplicates, deleted tracks, dynamic Ahwan intro");
    }
}
