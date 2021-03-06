/*-
 * Copyright (c) 2020 Sygic a.s.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package intl.who.covid19;

import android.annotation.SuppressLint;
import android.content.Context;
import android.net.Uri;
import android.os.AsyncTask;

import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.List;

import sk.turn.http.Http;

public class Api {
    public interface Listener {
        void onResponse(int status, String response);
    }
    private static class Response {
        private int status;
        private String body;
        Response(int status, String body) {
            this.status = status;
            this.body = body;
        }
    }
    public static class ProfileRequest {
        private String deviceId;
        private String pushToken;
        private String phoneNumber;
        private String locale;
        public ProfileRequest(String deviceUid, String pushToken, String phoneNumber) {
            deviceId = deviceUid;
            this.pushToken = pushToken;
            this.phoneNumber = phoneNumber;
            locale = java.util.Locale.getDefault().toString();
        }
    }
    public static class ProfileResponse {
        public long profileId;
        public String deviceId;
    }
    public static class ContactRequest {
        private final String sourceDeviceId;
        private final long sourceProfileId;
        public final ArrayList<Encounter> connections = new ArrayList<>();
        public ContactRequest(String sourceDeviceId, long sourceProfileId) {
            this.sourceDeviceId = sourceDeviceId;
            this.sourceProfileId = sourceProfileId;
        }
    }
    private static class AuthTokenRequest {
        private final String deviceId;
        private final long profileId;
        private String mfaToken;
        private String duration;
        public AuthTokenRequest(String deviceId, long profileId) {
            this.deviceId = deviceId;
            this.profileId = profileId;
        }
        public AuthTokenRequest setMfaToken(String mfaToken) {
            this.mfaToken = mfaToken;
            return this;
        }
        public AuthTokenRequest setDuration(int duration) {
            this.duration = String.valueOf(duration);
            return this;
        }
    }
    public static class LocationRequest {
        private final String deviceId;
        private final long profileId;
        public final List<Location> locations = new ArrayList<>();
        public LocationRequest(String deviceId, long profileId) {
            this.deviceId = deviceId;
            this.profileId = profileId;
        }
    }
    public static class QuarantineLeftRequest {
        private final String deviceId;
        private final long profileId;
        private final double latitude;
        private final double longitude;
        private final int accuracy;
        private final long recordTimestamp;
        public QuarantineLeftRequest(String deviceId, long profileId, android.location.Location location) {
            this.deviceId = deviceId;
            this.profileId = profileId;
            latitude = location.getLatitude();
            longitude = location.getLongitude();
            accuracy = (int) location.getAccuracy();
            recordTimestamp = location.getTime() / 1000;
        }
    }

    private final App app;
    public Api(Context context) {
        app = App.get(context);
    }

    public void createProfile(ProfileRequest request, Listener listener) {
        send("profile", Http.PUT, request, listener);
    }
    public void sendContacts(ContactRequest request, Listener listener) {
        send("profile/contacts", Http.POST, request, listener);
    }
    public void requestAuthToken(Listener listener) {
        send("profile/mfatoken", Http.POST, new AuthTokenRequest(app.prefs().getString(Prefs.DEVICE_UID, null),
                app.prefs().getLong(Prefs.DEVICE_ID, 0L)), listener);
    }
    public void confirmAuthToken(String mfaToken, Listener listener) {
        send("profile/mfatoken", Http.PUT, new AuthTokenRequest(app.prefs().getString(Prefs.DEVICE_UID, null),
                app.prefs().getLong(Prefs.DEVICE_ID, 0L)).setMfaToken(mfaToken), listener);
    }
    public void confirmQuarantine(String mfaToken, int duration, Listener listener) {
        send("profile/quarantine", Http.POST, new AuthTokenRequest(app.prefs().getString(Prefs.DEVICE_UID, null),
                app.prefs().getLong(Prefs.DEVICE_ID, 0L)).setMfaToken(mfaToken).setDuration(duration), listener);
    }
    public void sendLocations(LocationRequest request, Listener listener) {
        send("profile/location", Http.POST, request, listener);
    }
    public void quarantineLeft(QuarantineLeftRequest request, Listener listener) {
        send("profile/areaexit", Http.POST, request, listener);
    }

    @SuppressLint("StaticFieldLeak")
    private void send(String action, String method, Object request, final Listener listener) {
        new AsyncTask<Void, Void, Response>() {
            @Override
            protected Response doInBackground(Void... voids) {
                try {
                    String data = new Gson().toJson(request);
                    App.log("API > " + method + " " + action + " " + data);
                    Uri uri = Uri.withAppendedPath(app.apiUri(), action);
                    Http http = new Http(uri.toString(), method)
                            .addHeader("Content-Type", "application/json")
                            .setData(data)
                            .send();
                    int code = http.getResponseCode();
                    String response = http.getResponseString();
                    App.log("API < " + code + " " + response + (code == 200 ? "" : " " + http.getResponseMessage()));
                    return new Response(http.getResponseCode(), response);
                } catch (Exception e) {
                    App.log("API failed " + e);
                    return new Response(-1, e.getMessage());
                }
            }
            @Override
            protected void onPostExecute(Response response) {
                if (listener != null) {
                    listener.onResponse(response.status, response.body);
                }
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }
}
