package com.maru.writers;
import java.util.*;
import java.time.LocalDate;

/** Five editorial roles; these are workflow roles, NOT five online LLMs. */
public final class EditorialEngine {
 public static final String[][] WRITERS={
   {"WR-01","문세필","총괄 편성 · 구성"},
   {"WR-02","차율담","음악 및 악기 조사"},
   {"WR-03","백지온","출처 및 사실 검수"},
   {"WR-04","손다솜","사연과 대화"},
   {"WR-05","구재담","유머 및 최종 교정"}
 };
 public static final String[] SINGERS={
  "강태준","박도윤","이한결","서민호","최우진","정현석","윤재민","김성재",
  "오태민","한시우","임준호","백지훈","남도현","류승민","신하준","강민재",
  "조유성","노지완","문태오","차은호","윤서아","서하린","김나연","정다은",
  "박수빈","이가온","최예린","한유나","임소연","오지민","송채원","유하늘"
 };
 public static final String[] GENRES={"한국 발라드","한국 트로트","중국 음악","서양 음악","연주곡","록 발라드","컨트리 팝"};
 private static final Set<String> RESERVED=new HashSet<>();
 static{
   RESERVED.addAll(Arrays.asList(SINGERS));RESERVED.add("아환");RESERVED.add("윤솔");
 }
 public static boolean uniqueWriters(){
   Set<String> names=new HashSet<>(),ids=new HashSet<>();
   for(String[] w:WRITERS){
     if(RESERVED.contains(w[1]) || !names.add(w[1]) || !ids.add(w[0]))return false;
   }
   return WRITERS.length==5;
 }
 private static String safe(String input,int limit,String fallback) {
   String s=input==null?"":input.replaceAll("[\\p{Cntrl}]"," ").replaceAll("\\s+"," ").trim();
   if(s.isEmpty())return fallback;
   if(s.length()>limit)s=s.substring(0,limit);
   return s.replace("[","(").replace("]",")");
 }
 private static void line(StringBuilder out,String who,String phrase){
   out.append("[").append(who).append("] ").append(phrase).append("\n\n");
 }
 private EditorialEngine(){}
 /** Generate an ORIGINAL talk script from human-approved data only. */
 public static String write(String topic,String song,String genre,String singer,
                            String story,boolean storyApproved,String evidence,String source,
                            boolean researchApproved){
   String t=safe(topic,80,"오래된 추억"),title=safe(song,90,"확인된 노래");
   String g=safe(genre,40,"한국 발라드");
   String artist=safe(singer,30,"없음");
   if(!RESERVED.contains(artist) || artist.equals("아환") || artist.equals("윤솔"))artist="없음";
   String st=storyApproved?safe(story,800,""):"";
   String ev=researchApproved?safe(evidence,500,""):"";
   String src=researchApproved?safe(source,250,""):"";
   boolean withEvidence=!ev.isEmpty()&&!src.isEmpty()&&src.startsWith("https://");
   boolean withStory=!st.isEmpty();
   StringBuilder b=new StringBuilder(5500);
   b.append("MARU RADIO 작가국 · 대본 초안\n")
    .append("제작일: ").append(LocalDate.now()).append(" | 주제: ").append(t).append("\n")
    .append("작가: WR-01 문세필(구성), WR-02 차율담(음악), WR-03 백지온(검수), WR-04 손다솜(사연), WR-05 구재담(교정)\n")
    .append("발화자: 아환·윤솔").append(artist.equals("없음")?"":"·가상 가수 "+artist).append("\n")
    .append("주의: 이는 원고와 수동 큐시트입니다. 실제 음성·음악·BIGO 송출은 별도로 연결해야 합니다.\n")
    .append("━━━━━━━━━━━━━━━━━━━━\n\n");
   line(b,"큐","OPENING | 잔잔한 등록 인트로 음악. 진행 전 소리 확인.");
   line(b,"아환","안녕하세요. 아환의 MARU RADIO입니다. 오늘도 음악이 흐르는 작은 스튜디오에 오신 걸 환영합니다.");
   line(b,"윤솔","안녕하세요. 윤솔이에요. 오늘은 어떤 이야기를 준비하셨어요? 대본을 보니까 제목부터 궁금해지던데요.");
   line(b,"아환","오늘의 이야기는 '"+t+"'입니다. 누구에게는 평범한 말이지만, 누군가에겐 꽤 오래 남는 장면일 수도 있겠죠.");
   line(b,"윤솔","좋아요. 그런데 DJ님, 이야기에 빠져서 노래 소개를 잊으시면 안 돼요. 작가분들이 또 신호를 보낼 테니까요.");
   line(b,"아환","맞습니다. 오늘만큼은 제작진에게 혼나지 않으려 합니다. 잠깐 웃고, 천천히 출발해 볼까요?");
   line(b,"큐","SECTION 1 | 분위기 전환. 말 사이 1~2초 휴지.");
   line(b,"윤솔","요즘 저는 거창한 사건보다 사소한 기억이 더 오래 남는다는 생각을 해요. 그런 순간, 혹시 있으세요?");
   line(b,"아환","있죠. 문득 걸음을 멈추게 하는 풍경이나, 오래전에 듣던 멜로디 같은 겁니다. 소리 하나에 그때의 공기까지 떠오르기도 해요.");
   line(b,"윤솔","맞아요. 그래서 음악방송이 좋나 봐요. 누구나 다른 하루를 살았는데, 같은 노래 앞에서 잠시 나란히 앉을 수 있으니까요.");
   line(b,"아환","오늘은 그 나란한 자리에 '"+g+"'의 감성도 더해보겠습니다.");
   if(withEvidence){
     b.append("[검증 메모 · 방송 전 재확인] 출처: ").append(src).append("\n")
      .append("자료 발췌(확인자 승인): ").append(ev).append("\n\n");
     line(b,"아환","방송 준비 과정에서 관련 자료도 찾아봤습니다. 출처를 확인한 내용은 제작 메모에 남겨두었습니다. 사실 설명은 자료의 맥락을 살펴 신중히 전하겠습니다.");
   }else{
     line(b,"윤솔","오늘은 확인되지 않은 뉴스나 날씨를 섞지 않고, 음악과 사람의 이야기 자체를 나누는 편이 좋겠어요.");
   }
   line(b,"큐","SECTION 2 | 노래와 사람.");
   line(b,"아환","노래 한 곡을 처음 만들 때 무엇부터 떠올릴까요? 멜로디일까요, 아니면 어떤 장면일까요?");
   line(b,"윤솔","둘 다일 것 같은데요. 저는 먼저 색깔이 떠올라요. 따뜻한 악기 소리인지, 쓸쓸한 현악기인지 말이에요.");
   line(b,"아환","좋은 이야기네요. 듣는 사람의 마음을 먼저 생각하는 것도 음악 만드는 일의 일부겠죠.");
   if(!artist.equals("없음")){
     line(b,"아환","오늘의 가상 초대 가수 "+artist+"님도 함께합니다. 어서 오세요. 오늘 주제와 음악을 연결하면 어떤 생각이 드세요?");
     line(b,"가수:"+artist,"안녕하세요. 저는 한 곡이 짧은 여행처럼 들릴 때가 좋아요. 같은 음악이라도 누구와 함께 듣느냐에 따라 마음이 달라지잖아요.");
     line(b,"윤솔","그 말 공감해요. 그런데 DJ님, 초대 손님께서는 오셨는데 정작 커피는 준비하셨나요?");
     line(b,"아환","아차. 가상 스튜디오라 커피 향만 준비했습니다. 음악만큼은 진짜로 준비해야죠.");
     line(b,"가수:"+artist,"그럼 커피 대신 음악 한 잔 부탁드리겠습니다.");
   }else{
     line(b,"윤솔","초대 손님이 없어도 괜찮아요. 오늘은 듣는 분들의 기억이 우리 스튜디오의 게스트니까요.");
   }
   if(withStory){
     line(b,"큐","SECTION 3 | 운영자가 공개를 승인한 실제 사연만 낭독. 신상정보 점검.");
     line(b,"아환","이어서 공개가 승인된 사연을 소개하겠습니다. 한 분의 이야기를 조심스럽게 들어보죠.");
     line(b,"아환",st);
     line(b,"윤솔","사연을 들으니 마음이 여러 방향으로 움직이네요. 쉽게 답을 정하기보다는 잠시 함께 생각하는 시간이었으면 좋겠습니다.");
     line(b,"아환","사연을 보내주셔서 감사합니다. 오늘 노래가 조용한 동행이 되었으면 합니다.");
   }else{
     line(b,"큐","SECTION 3 | 승인 사연 없음: 사연 낭독 건너뜀.");
     line(b,"윤솔","오늘 청취자 사연이 없어도 괜찮아요. 듣고 계신 분마다 마음속에 각자의 이야기가 있을 테니까요.");
   }
   line(b,"큐","MUSIC INTRO | 실제 방송 보관함에 곡 파일이 있는지 확인할 것.");
   line(b,"아환","이제 다음 곡을 소개할게요. 준비한 제목은 '"+title+"'입니다.");
   line(b,"윤솔","좋은 노래는 설명보다 직접 듣는 편이 빠르죠. 함께 귀 기울여 볼까요?");
   line(b,"음악","등록 음원 '"+title+"' 확인 후 재생. 앱이 자동 재생하지는 않음.");
   line(b,"큐","POST MUSIC | 곡 종료 확인 후 시작. 다음 단계는 현장 상황에 맞게 선택.");
   line(b,"윤솔","음악이 끝났습니다. 어떤 장면이 떠오르셨나요? 같은 곡도 저마다 다르게 남겠죠.");
   line(b,"아환","그래서 오늘 주제인 '"+t+"'을 조금 더 오래 기억하게 될 것 같습니다.");
   line(b,"윤솔","말을 너무 많이 하면 노래가 섭섭해할 거예요. 오늘도 좋은 음악과 함께해 주세요.");
   line(b,"아환","여기까지 아환의 MARU RADIO였습니다. 함께해 주셔서 감사합니다. 다음 음악 시간에 다시 뵙겠습니다.");
   line(b,"큐","END | 화면/음악 페이드아웃. 실제 송출 상태 확인.");
   return b.toString();
 }
}