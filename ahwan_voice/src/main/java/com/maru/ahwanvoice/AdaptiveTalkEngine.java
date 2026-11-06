package com.maru.ahwanvoice;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Offline, deterministic adaptive show-script planner. Not an LLM or a
 * self-training voice model. It learns only from explicit producer ratings
 * stored privately on the device. No network, private stories, or raw
 * listener comments are collected or used for training.
 */
public final class AdaptiveTalkEngine {
    public static final String[] TOPICS = {
        "퇴근 후 한숨", "밥상과 음식", "친구와 추억", "작은 생활 개그",
        "계절과 산책", "취미와 연습", "동네 풍경", "여행 이야기",
        "가족의 말 한마디", "오늘의 작은 발견", "음악 만드는 순간", "서로 응원하기"
    };
    public static final int AUTO_TOPIC = -1;
    private static final int RECENT_LIMIT = 6;
    private static final int MAX_RATING = 5;
    private static final String[] INTROS = {
        "바쁜 하루 끝에는 별것 아닌 말이 큰 위로가 되죠.",
        "누구나 하나쯤은 소소하지만 이야기하고 싶은 순간이 있잖아요.",
        "커다란 사건보다 평범한 하루에 더 좋은 이야기가 숨어 있더라고요."
    };

    // Original, broadcaster-safe micro-dialogues. All guest stories are explicitly fictional.
    private static final String[][] MATERIAL = {
        {"하루가 끝나는 풍경",
         "퇴근길에 가장 먼저 생각나는 건 뭘까요?",
         "제 노래 속 주인공은 현관 앞에서 신발을 벗고 이렇게 말해요. '오늘도 무사히 돌아왔구나.'",
         "작은 안도감이 참 크죠. 퇴근길 사람들에게 어떤 음악을 들려주고 싶어요?",
         "서두르지 않아도 되는 멜로디요. 오늘 수고한 마음이 잠깐 앉아 쉬었으면 해요.",
         "여러분은 퇴근 뒤 어떤 순간에 가장 편안해지세요?"},
        {"우리 집 밥상",
         "비싼 음식보다 기억에 남는 한 끼가 있나요?",
         "가상 이야기 속에선 식탁 위 달걀말이 한 접시가 주인공이에요. 모양은 삐뚤어도 웃음은 반듯하거든요.",
         "그럼 요리 실력보다 같이 웃는 실력이 중요하겠네요.",
         "맞아요. 냄비는 가끔 눌어붙어도 좋은 대화는 오래 남잖아요.",
         "청취자 여러분, 가장 기억에 남는 집밥은 무엇인가요?"},
        {"오래된 친구",
         "오래된 친구가 갑자기 연락하면 뭐부터 이야기할까요?",
         "제 작품 속 두 친구는 '그때 네가 빌린 지우개 아직 갖고 있어?'부터 시작해요.",
         "세월을 거슬러 가는 열쇠가 참 작네요.",
         "그래서 친구의 장점은 설명이 길지 않아도 그날로 돌아갈 수 있다는 거죠.",
         "오래된 친구에게 한마디 남긴다면 어떤 말을 할까요?"},
        {"웃어야 사는 날",
         "오늘은 가볍게 웃어볼까요. 휴대폰을 찾다가 뭘 발견할까요?",
         "손에 쥔 휴대폰이요! 더 웃긴 건 그걸로 손전등까지 켰다는 거예요.",
         "아, 범인은 끝까지 현장에 있었군요.",
         "네, 사건 담당 형사도 본인이었죠. 이런 작은 실수는 웃고 넘길 만해요.",
         "여러분의 무해한 실수담도 하나씩 들려주세요."},
        {"바람 부는 오후",
         "걷기 좋은 날에는 어디로 가고 싶으세요?",
         "제 가상 노래 속에선 먼 명소보다 집 앞 골목이 좋아요. 어제 없던 낙엽이 오늘은 길을 안내하거든요.",
         "어제와 같은 길이 조금 달라 보이겠어요.",
         "마음이 바쁘지 않으면 풍경도 천천히 들리는 것 같아요.",
         "오늘 하늘은 어떤 색으로 기억되시나요?"},
        {"조금씩 배우는 취미",
         "처음 배운 취미 중에 유난히 어렵던 게 있나요?",
         "상상 속 제 연주자는 기타 첫 코드를 잡고 손가락에게 휴가를 주고 싶어 했어요.",
         "손가락들이 단체로 파업할 뻔했네요.",
         "그래도 한 소절 성공한 날에는 작은 무대 하나를 얻은 기분이죠.",
         "여러분이 요즘 조금씩 배우고 있는 건 뭔가요?"},
        {"동네의 숨은 명소",
         "동네에서 가장 정다운 소리는 뭔가요?",
         "저는 가상 풍경에 빵집 문 열리는 종소리를 넣고 싶어요. 그 소리를 들으면 아침이 한 장면이 되거든요.",
         "소리만으로 동네가 떠오른다는 말이 좋네요.",
         "정겨운 풍경을 곡으로 옮기면 듣는 분들도 자기 동네를 떠올릴 수 있겠죠.",
         "여러분 동네를 떠올리게 하는 소리는 무엇인가요?"},
        {"어디론가 떠나는 마음",
         "멀리 떠나지 않아도 여행 같은 순간이 있을까요?",
         "가상 이야기의 주인공은 처음 가본 버스 종점에서 엉뚱하게 최고의 국수를 만나요.",
         "목적지보다 국수 한 그릇이 더 오래 기억되는 여행이군요.",
         "그날의 맛까지 음악에 담을 수 있다면 좋겠어요. 짧은 여행도 이야기를 남기니까요.",
         "가장 소박했지만 기억에 남은 여행은 어디였나요?"},
        {"가족이 건네는 말",
         "가족에게서 들은 사소한 말이 오래 남을 때가 있죠?",
         "제 가상 이야기에는 '춥지 않게 입고 가'라는 짧은 말이 있어요. 그게 아주 큰 응원처럼 들리죠.",
         "길지 않아도 마음을 전할 수 있다는 뜻이군요.",
         "네. 그래서 이런 문장을 노래 속에선 서두르지 않고 불러주고 싶어요.",
         "여러분 마음에 남아 있는 따뜻한 한마디는 뭔가요?"},
        {"별거 아닌 좋은 일",
         "오늘 있었던 작지만 반가운 일 하나를 상상해 볼까요?",
         "장바구니 바닥에서 잃어버린 줄 알았던 동전이 나오는 장면이요. 보물은 아닌데 괜히 승리한 기분이에요.",
         "그 동전으로 세상을 다 가진 표정을 짓는군요.",
         "소소한 행복이 원래 좀 과장된 표정으로 찾아오기도 하잖아요.",
         "오늘 여러분의 작은 승리는 무엇이었나요?"},
        {"멜로디의 탄생",
         "이번 노래에는 어떤 감정을 담고 싶으세요?",
         "가상 음악가로서 저는 이 곡을 '말로 다 못한 고마움'에서 시작한다고 설정했어요.",
         "그럼 처음은 조용하다가 후렴에서 마음이 열리겠네요.",
         "맞아요. 다만 실제 제작 기록과 가사는 확인된 부분만 소개하고 싶어요.",
         "여러분은 노래를 들을 때 가사와 멜로디 중 무엇이 먼저 와닿나요?"},
        {"서로의 하루 응원",
         "오늘 청취자에게 꼭 들려주고 싶은 말이 있다면요?",
         "제 작품 속 문장은 이거예요. '잘한 일이 하나도 없는 날에도, 하루를 버틴 건 분명한 일이야.'",
         "듣는 순간 마음이 조금 편해지는 말이네요.",
         "노래도 그런 휴식 같은 것이면 좋겠어요. 누군가의 밤을 재촉하지 않는 음악이요.",
         "오늘 수고한 사람에게 따뜻한 한마디를 남겨주세요."}
    };

    public static final class Memory {
        public final int[] liked = new int[TOPICS.length];
        public final int[] disliked = new int[TOPICS.length];
        public final ArrayDeque<Integer> recent = new ArrayDeque<>();
        public int episodes;

        /** Stored data are aggregate ratings and topic IDs, not private listener text. */
        public String serialize() {
            StringBuilder sb = new StringBuilder("v1|").append(episodes).append('|');
            for (int i = 0; i < TOPICS.length; i++) {
                if (i>0) sb.append(',');
                sb.append(liked[i]).append(':').append(disliked[i]);
            }
            sb.append('|');
            boolean first=true;
            for (int idx : recent) {
                if (!first) sb.append(',');
                first=false;
                sb.append(idx);
            }
            return sb.toString();
        }

        public static Memory deserialize(String raw) {
            Memory m = new Memory();
            if (raw==null || raw.length()>1024) return m;
            try {
                String[] parts=raw.split("\\|",-1);
                if(parts.length!=4 || !"v1".equals(parts[0])) return m;
                m.episodes=Math.min(1000000,Math.max(0,Integer.parseInt(parts[1])));
                String[] rates=parts[2].split(",");
                if(rates.length != TOPICS.length) return new Memory();
                for (int i=0;i<rates.length;i++) {
                    String[] pair=rates[i].split(":");
                    if(pair.length!=2) return new Memory();
                    m.liked[i]=clamp(Integer.parseInt(pair[0]),0,MAX_RATING);
                    m.disliked[i]=clamp(Integer.parseInt(pair[1]),0,MAX_RATING);
                }
                if(!parts[3].isEmpty()) {
                    for(String index:parts[3].split(",")) {
                        int n=Integer.parseInt(index);
                        if(n>=0 && n<TOPICS.length) m.recent.addLast(n);
                    }
                    while(m.recent.size()>RECENT_LIMIT)m.recent.removeFirst();
                }
            } catch (RuntimeException e) { return new Memory(); }
            return m;
        }
    }

    public static final class Suggestion {
        public final int topic;
        public final String title;
        public final String script;
        public Suggestion(int topic,String title,String script) {
            this.topic=topic;
            this.title=title;
            this.script=script;
        }
    }

    public static Suggestion suggest(Memory mem,int requested,int singerId,String singerName) {
        if(mem==null)throw new IllegalArgumentException("memory required");
        if(singerName==null || singerName.trim().isEmpty())throw new IllegalArgumentException("singer required");
        if(requested<AUTO_TOPIC || requested>=TOPICS.length)throw new IllegalArgumentException("unknown topic");
        int topic=requested==AUTO_TOPIC ? choose(mem,singerId) : requested;
        int variation=Math.floorMod(mem.episodes + Math.floorMod(singerId,3),INTROS.length);
        String[] lines=MATERIAL[topic];
        String host="아환",guest=singerName.trim();
        String script="[방송 안내] 지금부터 가상 AI 가수 "+guest+"의 창작 인터뷰를 소개합니다.\n\n"
                +host+": "+INTROS[variation]+" "+lines[1]+"\n"
                +guest+" (가상 이야기): "+lines[2]+"\n"
                +host+": "+lines[3]+"\n"
                +guest+" (가상 이야기): "+lines[4]+"\n"
                +host+": "+lines[5]+"\n\n"
                +host+": 이제 "+guest+"의 **실제 등록된 승인 음원**을 함께 듣겠습니다.\n"
                +"[제작자 확인] 사실관계·권리·표현을 검토하고, 본인 승인 녹음으로만 방송하세요.";
        return new Suggestion(topic,lines[0],script);
    }

    private static int choose(Memory m,int singerId) {
        int best=-1, bestScore=Integer.MIN_VALUE;
        int pivot=Math.floorMod(m.episodes+singerId,TOPICS.length);
        for(int n=0;n<TOPICS.length;n++){
            int i=(pivot+n)%TOPICS.length;
            int score=clamp(m.liked[i]-m.disliked[i],-MAX_RATING,MAX_RATING)*3;
            int rank=0;
            for(Integer past:m.recent) if(past==i) rank++;
            if(rank>0) score-=100; // Never repeat a recently aired topic in automatic mode.
            score-=n/3; // Rotate among similarly popular topics.
            if(score>bestScore) {bestScore=score;best=i;}
        }
        return best;
    }

    public static void record(Memory m,int topic,int rating) {
        if(m==null || topic<0 || topic>=TOPICS.length || rating < -1 || rating > 1)
            throw new IllegalArgumentException("invalid feedback");
        if(rating>0)m.liked[topic]=clamp(m.liked[topic]+1,0,MAX_RATING);
        if(rating<0)m.disliked[topic]=clamp(m.disliked[topic]+1,0,MAX_RATING);
        m.recent.remove(topic);
        m.recent.addLast(topic);
        while(m.recent.size()>RECENT_LIMIT)m.recent.removeFirst();
        m.episodes=Math.min(1000000,m.episodes+1);
    }

    public static int topicCount(){return TOPICS.length;}
    private static int clamp(int value,int low,int high){return Math.max(low,Math.min(high,value));}
    private AdaptiveTalkEngine(){}
}
