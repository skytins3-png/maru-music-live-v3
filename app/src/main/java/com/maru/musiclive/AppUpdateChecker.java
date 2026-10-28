package com.maru.musiclive;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** Read-only HTTPS release lookup; downloading and installing require user taps. */
public final class AppUpdateChecker {
    static final String RELEASES_API =
            "https://api.github.com/repos/skytins3-png/maru-music-live-v3/releases/latest";
    static final String RELEASES_PAGE =
            "https://github.com/skytins3-png/maru-music-live-v3/releases";

    private AppUpdateChecker() {}

    public static final class Result {
        public final String installedVersion;
        public final String latestVersion;
        public final String downloadUrl;
        public final String message;

        Result(String installedVersion, String latestVersion, String downloadUrl, String message) {
            this.installedVersion = installedVersion;
            this.latestVersion = latestVersion;
            this.downloadUrl = downloadUrl;
            this.message = message;
        }

        public boolean hasUpdate() {
            return downloadUrl != null && !downloadUrl.isEmpty();
        }
    }

    public static Result check(Context context) {
        String installed;
        try {
            PackageInfo info = context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0);
            installed = info.versionName;
            if (installed == null) installed = "";
        } catch (PackageManager.NameNotFoundException e) {
            return new Result("", "", "", "설치된 버전 정보를 확인하지 못했습니다.");
        }

        HttpURLConnection connection = null;
        try {
            URL endpoint = new URL(RELEASES_API);
            connection = (HttpURLConnection) endpoint.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Accept", "application/vnd.github+json");
            connection.setRequestProperty("User-Agent", "MARU-RADIO-Android-Updater");
            connection.setConnectTimeout(8000);
            connection.setReadTimeout(8000);
            int status = connection.getResponseCode();
            if (status == 404) {
                return new Result(installed, "", "", "공개된 업데이트 파일이 아직 없습니다.");
            }
            if (status != 200) {
                return new Result(installed, "", "", "업데이트 서버 응답 오류 (" + status + ")");
            }
            StringBuilder response = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    connection.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                    if (response.length() > 262144) {
                        return new Result(installed, "", "", "업데이트 정보가 너무 큽니다.");
                    }
                }
            }
            JSONObject release = new JSONObject(response.toString());
            if (release.optBoolean("draft") || release.optBoolean("prerelease")) {
                return new Result(installed, "", "", "최신 정식 배포판이 없습니다.");
            }
            String tag = release.optString("tag_name", "");
            if (!tag.startsWith("v")) {
                return new Result(installed, "", "", "업데이트 버전 정보가 올바르지 않습니다.");
            }
            String latest = tag.substring(1);
            if (!ReleaseVersionLogic.newerThan(latest, installed)) {
                return new Result(installed, latest, "", "현재 최신 버전을 사용 중입니다.");
            }
            JSONArray assets = release.optJSONArray("assets");
            if (assets != null) {
                for (int i = 0; i < assets.length(); i++) {
                    JSONObject asset = assets.optJSONObject(i);
                    if (asset == null || !"MARU-RADIO.apk".equals(asset.optString("name"))) {
                        continue;
                    }
                    String url = asset.optString("browser_download_url", "");
                    if (ReleaseVersionLogic.trustedApk(latest, url)) {
                        return new Result(installed, latest, url, "새 버전을 사용할 수 있습니다.");
                    }
                }
            }
            return new Result(installed, latest, "",
                    "새 버전이 있지만 검증된 APK 다운로드 주소가 없습니다.");
        } catch (Exception e) {
            return new Result(installed, "", "", "인터넷 연결을 확인해 주세요.");
        } finally {
            if (connection != null) connection.disconnect();
        }
    }
}
