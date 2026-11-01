package com.maru.musiclive;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.zip.ZipFile;

/** A download is never an unattended install; Android shows its installer approval UI. */
public final class MaruOneUpdateInstaller {
    private static final long MAX_APK_BYTES = 100L * 1024 * 1024;
    private MaruOneUpdateInstaller(){}

    private static boolean trustedRedirect(URL url) {
        String host = url.getHost().toLowerCase(java.util.Locale.US);
        return "https".equalsIgnoreCase(url.getProtocol())
                && ("github.com".equals(host)
                || "release-assets.githubusercontent.com".equals(host)
                || "objects.githubusercontent.com".equals(host));
    }

    private static HttpURLConnection openDownload(String url) throws Exception {
        URL next=new URL(url);
        for(int i=0;i<7;i++){
            if(!trustedRedirect(next))throw new SecurityException("검증되지 않은 다운로드 주소");
            HttpURLConnection c=(HttpURLConnection)next.openConnection();
            c.setConnectTimeout(12000);c.setReadTimeout(30000);
            c.setInstanceFollowRedirects(false);
            c.setRequestProperty("User-Agent","MARU-ONE-Safe-Updater");
            int status=c.getResponseCode();
            if(status==200)return c;
            if(status==301||status==302||status==303||status==307||status==308){
                String location=c.getHeaderField("Location");c.disconnect();
                if(location==null)throw new SecurityException("다운로드 경로 없음");
                next=new URL(next,location);continue;
            }
            c.disconnect();
            throw new java.io.IOException("APK 다운로드 오류 HTTP "+status);
        }
        throw new SecurityException("다운로드 경로 이동이 너무 많습니다.");
    }

    /** Call only from a background executor after a successful signed release check. */
    public static File downloadAndVerify(Activity activity,MaruOneUpdateChecker.Result release)
            throws Exception {
        if(!release.available()||!MaruOneUpdateChecker.trustedUrl(
                release.latestVersion,release.apkUrl))throw new SecurityException("잘못된 업데이트 파일");
        if(!MaruOneUpdateChecker.installedSigner(activity)
                .equalsIgnoreCase(release.signerSha256))
            throw new SecurityException("현재 앱 서명과 배포 서명이 다릅니다.");
        File dir=new File(activity.getCacheDir(),"maru-verified-updates");
        if(!dir.exists()&&!dir.mkdirs())throw new java.io.IOException("임시 저장 공간을 열 수 없습니다.");
        File temporary=new File(dir,"download-partial.apk");
        File apk=new File(dir,"MARU-ONE-v"+release.latestVersion+".apk");
        if(temporary.exists())temporary.delete();
        HttpURLConnection conn=null;
        try{
            conn=openDownload(release.apkUrl);
            MessageDigest digest=MessageDigest.getInstance("SHA-256");
            long count=0;
            try(InputStream in=conn.getInputStream();
                FileOutputStream out=new FileOutputStream(temporary)){
                byte[] chunk=new byte[64*1024];int read;
                while((read=in.read(chunk))!=-1){
                    count+=read;
                    if(count>MAX_APK_BYTES)throw new SecurityException("APK 크기 제한 초과");
                    digest.update(chunk,0,read);out.write(chunk,0,read);
                }
                out.getFD().sync();
            }
            if(count<1_000_000)throw new java.io.IOException("다운로드 파일이 너무 작습니다.");
            String got=MaruOneUpdateChecker.shaHex(digest.digest());
            if(!got.equalsIgnoreCase(release.apkSha256))
                throw new SecurityException("APK SHA256 검사 실패. 설치하지 않습니다.");
            try(ZipFile archive=new ZipFile(temporary)){
                if(archive.getEntry("AndroidManifest.xml")==null
                        ||archive.getEntry("classes.dex")==null)
                    throw new SecurityException("APK 구성 파일 오류");
            }
            PackageInfo candidate=activity.getPackageManager().getPackageArchiveInfo(
                    temporary.getAbsolutePath(),MaruOneUpdateChecker.signerFlags());
            if(candidate==null||!MaruOneUpdateChecker.PACKAGE.equals(candidate.packageName)
                    ||MaruOneUpdateChecker.versionCode(candidate)!=release.versionCode)
                throw new SecurityException("APK 앱 식별자 또는 버전이 다릅니다.");
            Signature[] certs=MaruOneUpdateChecker.archiveSigners(candidate);
            if(certs==null||certs.length!=1||!MaruOneUpdateChecker.signatureSha256(certs[0])
                    .equalsIgnoreCase(release.signerSha256))
                throw new SecurityException("APK의 실제 서명 인증서가 일치하지 않습니다.");
            if(apk.exists()&&!apk.delete())throw new java.io.IOException("이전 다운로드 정리 실패");
            if(!temporary.renameTo(apk))throw new java.io.IOException("검증한 파일 저장 실패");
            return apk;
        }finally{
            if(conn!=null)conn.disconnect();
            if(temporary.exists())temporary.delete();
        }
    }

    /** User-initiated action; the OS installer requires a separate approval. */
    public static boolean requestInstall(Activity activity,File file) {
        if(Build.VERSION.SDK_INT>=26
                && !activity.getPackageManager().canRequestPackageInstalls()){
            Intent permission=new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:"+activity.getPackageName()));
            activity.startActivity(permission);
            android.widget.Toast.makeText(activity,
                    "이 출처의 설치를 허용하면 MARU 설치 화면이 자동으로 열립니다.",1).show();
            return false;
        }
        Uri content=FileProvider.getUriForFile(activity,
                activity.getPackageName()+".maru-updates",file);
        Intent intent=new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(content,"application/vnd.android.package-archive");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        activity.startActivity(intent);
        return true;
    }
}
