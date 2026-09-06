package xyz.deep.nagram.updater;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BuildConfig;
import org.telegram.messenger.FileLog;
import org.telegram.tgnet.TLRPC;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

import tw.nekomimi.nekogram.helpers.remote.BaseRemoteHelper;

/**
 * GitHubUpdateChecker queries the DeepBlue9789/Nagram GitHub repository releases API
 * to verify if a new release of the forked app is available.
 */
public class GitHubUpdateChecker {

    public static final String GITHUB_API_LATEST_RELEASE =
            "https://api.github.com/repos/DeepBlue9789/Nagram/releases/latest";

    public interface CheckCallback {
        void onUpdateResult(TLRPC.TL_help_appUpdate update, String error);
    }

    public static void checkForUpdates(final CheckCallback callback) {
        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(GITHUB_API_LATEST_RELEASE);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Accept", "application/vnd.github.v3+json");
                conn.setRequestProperty("User-Agent", "DeepNagramApp/" + BuildConfig.BUILD_VERSION_STRING);
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(15000);

                int responseCode = conn.getResponseCode();
                if (responseCode != 200) {
                    if (callback != null) {
                        AndroidUtilities.runOnUIThread(() -> callback.onUpdateResult(null, "HTTP " + responseCode));
                    }
                    return;
                }

                BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String inputLine;
                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
                in.close();

                JSONObject releaseJson = new JSONObject(response.toString());
                String tagName = releaseJson.optString("tag_name", "");
                String htmlUrl = releaseJson.optString("html_url", "https://github.com/DeepBlue9789/Nagram/releases");
                String body = releaseJson.optString("body", "");

                // Find APK asset download URL if available
                String apkDownloadUrl = htmlUrl;
                JSONArray assets = releaseJson.optJSONArray("assets");
                if (assets != null) {
                    for (int i = 0; i < assets.length(); i++) {
                        JSONObject asset = assets.getJSONObject(i);
                        String name = asset.optString("name", "");
                        if (name.endsWith(".apk")) {
                            apkDownloadUrl = asset.optString("browser_download_url", htmlUrl);
                            break;
                        }
                    }
                }

                String currentVer = BuildConfig.BUILD_VERSION_STRING.replace("v", "").trim();
                String remoteVer = tagName.replace("v", "").trim();

                boolean isNewer = isVersionNewer(remoteVer, currentVer);

                if (isNewer) {
                    TLRPC.TL_help_appUpdate appUpdate = new TLRPC.TL_help_appUpdate();
                    appUpdate.version = tagName;
                    appUpdate.url = apkDownloadUrl;
                    appUpdate.text = body;
                    appUpdate.flags |= 4; // URL flag
                    if (callback != null) {
                        AndroidUtilities.runOnUIThread(() -> callback.onUpdateResult(appUpdate, null));
                    }
                } else {
                    if (callback != null) {
                        AndroidUtilities.runOnUIThread(() -> callback.onUpdateResult(null, null));
                    }
                }
            } catch (Exception e) {
                FileLog.e("GitHubUpdateChecker error", e);
                if (callback != null) {
                    AndroidUtilities.runOnUIThread(() -> callback.onUpdateResult(null, e.getMessage()));
                }
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }
        }).start();
    }

    private static boolean isVersionNewer(String remote, String current) {
        try {
            String[] rParts = remote.split("\\.");
            String[] cParts = current.split("\\.");
            int length = Math.max(rParts.length, cParts.length);
            for (int i = 0; i < length; i++) {
                int r = i < rParts.length ? Integer.parseInt(rParts[i].replaceAll("[^0-9]", "")) : 0;
                int c = i < cParts.length ? Integer.parseInt(cParts[i].replaceAll("[^0-9]", "")) : 0;
                if (r > c) return true;
                if (r < c) return false;
            }
        } catch (Exception ignore) {
            return !remote.equals(current);
        }
        return false;
    }
}
