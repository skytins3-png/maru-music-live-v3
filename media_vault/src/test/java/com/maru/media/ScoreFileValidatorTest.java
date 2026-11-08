package com.maru.media;
import java.io.*;
import org.junit.Test;
import static org.junit.Assert.*;
public final class ScoreFileValidatorTest {
 private byte[] doc(String marker){
   byte[] b=new byte[24];byte[] mark=marker.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
   System.arraycopy(mark,0,b,0,Math.min(mark.length,b.length));return b;
 }
 @Test public void acceptsPdfPngJpegWebp()throws Exception{
   assertEquals("application/pdf",ScoreFileValidator.validate(new ByteArrayInputStream(doc("%PDF-1.5"))));
   byte[] png=doc("none");byte[] sig=new byte[]{(byte)0x89,'P','N','G',13,10,26,10};
   System.arraycopy(sig,0,png,0,sig.length);
   assertEquals("image/png",ScoreFileValidator.validate(new ByteArrayInputStream(png)));
   byte[] jpeg=doc("none");jpeg[0]=(byte)0xff;jpeg[1]=(byte)0xd8;jpeg[2]=(byte)0xff;
   assertEquals("image/jpeg",ScoreFileValidator.validate(new ByteArrayInputStream(jpeg)));
   byte[] webp=doc("RIFF0000WEBP");
   assertEquals("image/webp",ScoreFileValidator.validate(new ByteArrayInputStream(webp)));
 }
 @Test public void rejectsFakeScore()throws Exception{
   try{ScoreFileValidator.validate(new ByteArrayInputStream(doc("MZ fake EXE")));fail("must reject disguised executable");}
   catch(IOException expected){assertTrue(expected.getMessage().contains("악보"));}
 }
 @Test public void rejectsEmptyFile()throws Exception{
   try{ScoreFileValidator.validate(new ByteArrayInputStream(new byte[0]));fail("must reject empty");}
   catch(IOException expected){}
 }
}