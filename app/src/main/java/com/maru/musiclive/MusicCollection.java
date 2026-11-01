package com.maru.musiclive;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Independent region/style/source facets; no exclusive playlist pigeonholes. */
public final class MusicCollection {
    public static final String ALL = "all";
    public static final String KOREAN = "korean";
    public static final String CHINESE = "chinese";
    public static final String WESTERN = "western";
    public static final String INSTRUMENTAL = "instrumental";
    public static final String OTHER = "other";
    public static final String TROT = "trot";
    public static final String BALLAD = "ballad";
    public static final String DANCE = "dance";
    public static final String ROCK = "rock";
    public static final String FOLK = "folk";
    public static final String SUNO = "suno";
    public static final String DEVICE = "device";
    public static final String PLATFORM = "platform";
    public static final String LISTENER = "listener";

    public static final String[] REGIONS = {KOREAN, CHINESE, WESTERN, INSTRUMENTAL, OTHER};
    public static final String[] STYLES = {TROT, BALLAD, DANCE, ROCK, FOLK, OTHER};
    public static final String[] SOURCES = {SUNO, DEVICE, PLATFORM, LISTENER};
    public static final String[] GROUPS = {ALL, CHINESE, KOREAN, WESTERN, TROT, BALLAD,
            INSTRUMENTAL, DANCE, ROCK, FOLK, SUNO, DEVICE, PLATFORM, LISTENER, OTHER};
    private MusicCollection() {}

    public static String label(String key) {
        if (CHINESE.equals(key)) return "중국노래 모음";
        if (KOREAN.equals(key)) return "한국노래 모음";
        if (WESTERN.equals(key)) return "서양노래 모음";
        if (TROT.equals(key)) return "트로트 모음";
        if (BALLAD.equals(key)) return "발라드 모음";
        if (INSTRUMENTAL.equals(key)) return "연주곡 모음";
        if (DANCE.equals(key)) return "댄스곡 모음";
        if (ROCK.equals(key)) return "록 모음";
        if (FOLK.equals(key)) return "포크 모음";
        if (SUNO.equals(key)) return "Suno 원곡";
        if (DEVICE.equals(key)) return "모바일 저장소";
        if (PLATFORM.equals(key)) return "MARU 공개곡";
        if (LISTENER.equals(key)) return "청취자 승인곡";
        if (OTHER.equals(key)) return "미분류";
        return "전체 노래";
    }
    public static boolean isRegion(String value) { return contains(REGIONS,value); }
    public static boolean isStyle(String value) { return contains(STYLES,value); }
    public static boolean isSource(String value) { return contains(SOURCES,value); }
    private static boolean contains(String[] values,String value) {
        for(String item:values)if(item.equals(value))return true;
        return false;
    }
    public static String detectRegion(String title) {
        String text=title==null?"":title;
        if(text.matches(".*(?:연주|독주|반주|합주|피아노|고쟁|얼후|纯音乐|演奏|instrumental|orchestra).*"))return INSTRUMENTAL;
        if(text.matches(".*[\\p{IsHan}].*"))return CHINESE;
        if(text.matches(".*[가-힣].*"))return KOREAN;
        if(text.matches(".*[A-Za-z].*"))return WESTERN;
        return OTHER;
    }
    public static String detectStyle(String title) {
        String t=(title==null?"":title).toLowerCase(Locale.ROOT);
        if(t.contains("트로트")||t.contains("trot")||t.contains("지루박"))return TROT;
        if(t.contains("발라드")||t.contains("ballad")||t.contains("情歌"))return BALLAD;
        if(t.contains("댄스")||t.contains("dance"))return DANCE;
        if(t.contains("록")||t.contains("rock"))return ROCK;
        if(t.contains("포크")||t.contains("folk"))return FOLK;
        return OTHER;
    }
    public static boolean matches(String group,String region,String style,String source) {
        return ALL.equals(group)||group.equals(region)||group.equals(style)||group.equals(source);
    }
    public static List<String> filtered(List<String> ids,List<String> regions,
                                         List<String> styles,List<String> sources,String group) {
        if(ids==null||regions==null||styles==null||sources==null
                ||ids.size()!=regions.size()||ids.size()!=styles.size()
                ||ids.size()!=sources.size())return Collections.emptyList();
        List<String> result=new ArrayList<>();
        for(int i=0;i<ids.size();i++)
            if(matches(group,regions.get(i),styles.get(i),sources.get(i)))
                result.add(ids.get(i));
        return result;
    }
}
