import com.maru.radio.BigoReceptionChecklist;

public final class BigoReceptionChecklistTest {
  private static void require(boolean pass,String msg) {
    if(!pass)throw new AssertionError(msg);
  }
  private static boolean[] all(boolean state) {
    boolean[] result=new boolean[BigoReceptionChecklist.CHECKS];
    java.util.Arrays.fill(result,state);
    return result;
  }
  public static void main(String[] args) {
    require(!BigoReceptionChecklist.acceptable(null),"null must not pass");
    require(!BigoReceptionChecklist.acceptable(new boolean[4]),"missing items must fail");
    require(!BigoReceptionChecklist.acceptable(new boolean[6]),"unrecognized items must fail");
    require(BigoReceptionChecklist.completed(null)==0,"null count");
    require(!BigoReceptionChecklist.acceptable(all(false)),"empty cannot pass");
    require(BigoReceptionChecklist.acceptable(all(true)),"all explicit confirmations pass");
    require(BigoReceptionChecklist.completed(all(true))==5,"all count");
    for(int i=0;i<5;i++){
      boolean[] flags=all(true);flags[i]=false;
      require(!BigoReceptionChecklist.acceptable(flags),"missing checkpoint "+i);
      require(BigoReceptionChecklist.completed(flags)==4,"missing count "+i);
      require(!BigoReceptionChecklist.diagnostic(flags).isEmpty(),"missing diagnostics "+i);
    }
    boolean[] noAudio={true,true,false,false,false};
    require(BigoReceptionChecklist.diagnostic(noAudio).contains("외부 앱 음성"),"silent output explanation");
    boolean[] musicOnly={true,true,false,true,false};
    require(BigoReceptionChecklist.diagnostic(musicOnly).contains("TTS"),"host missing explanation");
    boolean[] talkOnly={true,true,true,false,false};
    require(BigoReceptionChecklist.diagnostic(talkOnly).contains("음악 입력"),"song missing explanation");
    boolean[] skip={true,true,true,true,false};
    require(BigoReceptionChecklist.diagnostic(skip).contains("전환"),"song transition explanation");
    require(BigoReceptionChecklist.diagnostic(all(true)).contains("외부 서버 인증은 아닙니다"),"avoid false confidence");
    System.out.println("MARU BIGO separate-device listener checklist PASS");
  }
}
