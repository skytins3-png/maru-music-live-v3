package com.maru.musiclive;
import java.util.*;
/**
 * MARU editorial department — no writer is a singer, announcer, or on-air guest.
 * WR identifiers are intentionally disjoint from the singer (1..32) catalog.
 */
public final class WriterRoomCatalog {
    public enum Duty { SHOWRUNNER, MUSIC_RESEARCH, FACT_CHECK, AUDIENCE_STORY, COMEDY_REVIEW }
    public static final class Writer {
        public final String id;
        public final String name;
        public final Duty duty;
        public final String responsibility;
        private Writer(String id,String name,Duty duty,String responsibility){
            this.id=id;this.name=name;this.duty=duty;this.responsibility=responsibility;
        }
    }
    private static final List<Writer> WRITERS=Collections.unmodifiableList(Arrays.asList(
        new Writer("WR-01","문세필",Duty.SHOWRUNNER,"구성표, 오프닝, 곡 사이 연결, 러닝타임"),
        new Writer("WR-02","차율담",Duty.MUSIC_RESEARCH,"장르, 악기, 노래와 실제 등록 음원 확인"),
        new Writer("WR-03","백지온",Duty.FACT_CHECK,"출처 검증, 날씨·뉴스 시각 확인, 불확실성 표시"),
        new Writer("WR-04","손다솜",Duty.AUDIENCE_STORY,"승인된 청취자 사연, 공감 대화, 개인 정보 보호"),
        new Writer("WR-05","구재담",Duty.COMEDY_REVIEW,"자연스러운 유머, 반복·어색한 표현 제거, 최종 검수")
    ));
    private WriterRoomCatalog(){}
    public static List<Writer> all(){return WRITERS;}
    /** Validate all registered writer identities, never rename or edit singers. */
    public static boolean noOverlap(Collection<String> singerAndHostNames){
        Set<String> used=new HashSet<>();
        if(singerAndHostNames!=null)
            for(String name:singerAndHostNames)if(name!=null)used.add(name.trim());
        Set<String> ids=new HashSet<>(), names=new HashSet<>();
        for(Writer w:WRITERS){
            if(!ids.add(w.id)||!names.add(w.name)||used.contains(w.name))return false;
        }
        return WRITERS.size()==5;
    }
}
