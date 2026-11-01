package com.maru.musiclive;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;

/**
 * MARU ONE update metadata and signature policy.
 * Never offers a different-package or different-signer APK as an update.
 * Only returns release metadata that includes APK SHA256 and certificate SHA256.
 */
public final class MaruOneUpdateChecker {
    private static final String RELEASES =
            "https://api.github.com/repos/skytins3-png/maru-music-live-v3/releases?per_page=20";
    public static final String PACKAGE = "com.maru.musiclive.one";
    private static final String PREFIX =
            "https://github.com/skytins3-png/maru-music-live-v3/releases/download/";

    private MaruOneUpdateChecker() {}

    public static final class Result {
        public final String installedVersion;
        public final String latestVersion;
        public final int versionCode;
        public final String apkUrl;
        public final String apkSha256;
        public final String signerSha256;
        public final String message;
        Result(String installedVersion, String latestVersion, int versionCode, String apkUrl,
                String apkSha256, String signerSha256, String message) {
            this.installedVersion = installedVersion;
            this.latestVersion = latestVersion;
            this.versionCode = versionCode;
            this.apkUrl = apkUrl;
            this.apkSha256 = apkSha256;
            this.signerSha256 = signerSha256;
            this.message = message;
        }
        public boolean available() { return !apkUrl.isEmpty(); }
    }

    public static boolean isHex256(String value) {
        return value != null && value.matches("[0-9a-fA-F]{64}");
    }

    static String metadataValue(String text, String key) {
        if (text == null) return "";
        for (String line : text.split("\\r?\\n")) {
            String prefix = key + "=";
            if (line.startsWith(prefix)) return line.substring(prefix.length()).trim();
        }
        return "";
    }

    static boolean trustedUrl(String version, String url) {
        return version != null && version.matches("\\d+\\.\\d+\\.\\d+")
                && (PREFIX + "v" + version + "-one/MARU-ONE-v" + version + ".apk").equals(url);
    }

    static boolean newerVersion(String remote, String local) {
        return ReleaseVersionLogic.newerThan(remote, local);
    }

    static String signatureSha256(Signature signature) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(signature.toByteArray());
            return shaHex(hash);
        } catch (Exception e) {
            return "";
        }
    }

    public static String shaHex(byte[] raw) {
        StringBuilder hex = new StringBuilder(raw.length * 2);
        for (byte b : raw) hex.append(String.format(Locale.US, "%02x", b & 255));
        return hex.toString();
    }

    public static long versionCode(PackageInfo info) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) return info.getLongVersionCode();
        return info.versionCode;
    }

    public static int signerFlags() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                ? PackageManager.GET_SIGNING_CERTIFICATES
                : PackageManager.GET_SIGNATURES;
    }

    public static Signature[] archiveSigners(PackageInfo info) {
        if (info == null) return null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return info.signingInfo == null ? null : info.signingInfo.getApkContentsSigners();
        }
        return info.signatures;
    }

    public static String installedSigner(Context context) {
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(
                    context.getPackageName(), signerFlags());
            Signature[] signatures = archiveSigners(info);
            return signatures == null || signatures.length != 1
                    ? "" : signatureSha256(signatures[0]);
        } catch (Exception e) {
            return "";
        }
    }

    private static Result no(String installed, String latest, String message) {
        return new Result(installed, latest, 0, "", "", "", message);
    }

    public static Result check(Context context) {
        String installed = "";
        long installedCode = 0;
        try {
            PackageInfo pkg = context.getPackageManager().getPackageInfo(
                    context.getPackageName(), 0);
            installed = pkg.versionName == null ? "" : pkg.versionName;
            installedCode = versionCode(pkg);
        } catch (Exception e) {
            return no("", "", "설치 버전을 확인하지 못했습니다.");
        }
        if (!PACKAGE.equals(context.getPackageName())) {
            return no(installed, "", "MARU 통합 라디오 전용 업데이트 메뉴입니다. 다른 앱의 APK와 섞지 않습니다.");
        }
        final String currentSigner = installedSigner(context);
        if (!isHex256(currentSigner)) return no(installed, "", "설치된 앱의 서명을 검사할 수 없습니다.");
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection)new URL(RELEASES).openConnection();
            conn.setConnectTimeout(12000);
            conn.setReadTimeout(12000);
            conn.setRequestProperty("Accept","application/vnd.github+json");
            conn.setRequestProperty("User-Agent","MARU-ONE-Safe-Update");
            if (conn.getResponseCode() != 200) return no(installed,"","업데이트 서버 응답 오류입니다.");
            StringBuilder json = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    conn.getInputStream(),StandardCharsets.UTF_8))) {
                String line;
                while ((line=reader.readLine()) != null) {
                    json.append(line);
                    if (json.length()>512000) return no(installed,"","배포 정보가 너무 큽니다.");
                }
            }
            JSONArray releases = new JSONArray(json.toString());
            String newest = "";
            for(int i=0;i<releases.length();i++){
                JSONObject release=releases.optJSONObject(i);
                if(release==null||release.optBoolean("draft"))continue;
                String tag=release.optString("tag_name","");
                if(!tag.matches("v\\d+\\.\\d+\\.\\d+-one"))continue;
                String version=tag.substring(1,tag.length()-4);
                if(!newerVersion(version, installed)) continue;
                if(!newest.isEmpty()&&!newerVersion(version,newest))continue;
                newest=version;
                String body=release.optString("body","");
                String pkg=metadataValue(body,"PACKAGE");
                String signer=metadataValue(body,"SIGNER_SHA256");
                String hash=metadataValue(body,"APK_SHA256");
                int code;
                try{code=Integer.parseInt(metadataValue(body,"VERSION_CODE")); }
                catch(Exception ignored){code=0;}
                if(!PACKAGE.equals(pkg)||!isHex256(signer)||!isHex256(hash)
                        ||code<=installedCode)continue;
                JSONArray assets=release.optJSONArray("assets");
                if(assets==null)continue;
                for(int j=0;j<assets.length();j++){
                    JSONObject asset=assets.optJSONObject(j);
                    if(asset==null)continue;
                    String url=asset.optString("browser_download_url","");
                    if(!trustedUrl(version,url))continue;
                    if(!signer.equalsIgnoreCase(currentSigner)){
                        return no(installed,version,"새 APK 서명이 현재 앱과 다릅니다. 삭제하지 마세요. 기존 서명키로 다시 배포해야 합니다.");
                    }
                    return new Result(installed,version,code,url,hash,signer,
                            "설치 가능한 정식 업데이트가 있습니다.");
                }
            }
            return no(installed,newest,newest.isEmpty()?
                    "현재 설치 가능한 새 버전이 없습니다.":
                    "새 버전이 있지만 같은 서명으로 검증된 APK가 없습니다. 기존 앱을 삭제하지 마세요.");
        }catch(Exception e){
            return no(installed,"","인터넷 연결 또는 업데이트 정보 확인에 실패했습니다.");
        }finally{if(conn!=null)conn.disconnect();}
    }
}
