package com.pivotal.cf.mobile.oauth2client2.app;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.Window;
import android.widget.TextView;

import com.google.api.client.auth.oauth2.AuthorizationCodeFlow;
import com.google.api.client.auth.oauth2.AuthorizationCodeRequestUrl;
import com.google.api.client.auth.oauth2.AuthorizationCodeTokenRequest;
import com.google.api.client.auth.oauth2.BearerToken;
import com.google.api.client.auth.oauth2.ClientParametersAuthentication;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.auth.oauth2.TokenResponse;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;


public class MainActivity extends Activity {

    private static final String TAG = "OAUTH2CLIENT2";
    private static final String STATE_TOKEN = "BLORG";

    /**
     * Global instance of the {@link com.google.api.client.util.store.DataStoreFactory}. The best practice is to make it a single
     * globally shared instance across your application.
     */
    private static FileDataStoreFactory DATA_STORE_FACTORY;

    /** Global instance of the HTTP transport. */
    private static final HttpTransport HTTP_TRANSPORT = new NetHttpTransport();

    /** Global instance of the JSON factory. */
    static final JsonFactory JSON_FACTORY = new JacksonFactory();

    private TextView textView;
    private AuthorizationCodeFlow flow;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().requestFeature(Window.FEATURE_PROGRESS);
        setContentView(R.layout.activity_main);

        if (DATA_STORE_FACTORY == null) {
            final File dataStoreDir = getDir("oauth2", Context.MODE_PRIVATE);
            try {
                DATA_STORE_FACTORY = new FileDataStoreFactory(dataStoreDir);
            } catch (IOException e) {
                logErrorMessage("Could not open file data store: " + e.getLocalizedMessage());
            }
        }

    }

    @Override
    protected void onResume() {
        super.onResume();
        textView = (TextView) findViewById(R.id.textview);

        try {
            flow = getFlow();
        } catch(Exception e) {
            logErrorMessage("Error getting AuthorizationCodeFlow: " + e.getLocalizedMessage());
        }

        // TODO - check if there are valid credentials already saved

        if (intentHasCallbackUrl(getIntent())) {
            final Uri uri = getIntent().getData();
            if (!verifyCallbackState(uri)) {
                logErrorMessage("Received invalid state. Not authorized.");
            } else {
                addLogMessage("Received authorization code: " + getAuthorizationCode(uri));
                doGetTokens(getAuthorizationCode(uri));
            }
        } else {
            addLogMessage("Not authorized yet.");
        }
    }

    private boolean intentHasCallbackUrl(Intent intent) {
        if (!intent.hasCategory(Intent.CATEGORY_BROWSABLE)) {
            return false;
        }
        if (intent.getData() == null) {
            return false;
        }
        return intent.getData().toString().startsWith(Const.REDIRECT_URL);
    }

    private boolean verifyCallbackState(Uri uri) {
        return uri.getQueryParameter("state").equals(STATE_TOKEN);
    }

    private String getAuthorizationCode(Uri uri) {
        return uri.getQueryParameter("code");
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_login) {
            doAuthorization();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void doAuthorization() {
        final AuthorizationCodeRequestUrl authorizationUrl = flow.newAuthorizationUrl();
        authorizationUrl.setRedirectUri(Const.REDIRECT_URL);
        authorizationUrl.setState(STATE_TOKEN);
        final String url = authorizationUrl.build();
        Log.d(TAG, "Loading authorization request URL: " + url);
        final Uri uri = Uri.parse(url);
        final Intent i = new Intent(Intent.ACTION_VIEW, uri);
        startActivity(i); // Launches external browser to do complete authentication
    }

    private void doGetTokens(final String authorizationCode) {
        final AsyncTask<Void, Void, TokenResponse> task = new AsyncTask<Void, Void, TokenResponse>() {

            @Override
            protected TokenResponse doInBackground(Void... params) {
                try {
                    final AuthorizationCodeTokenRequest tokenUrl = flow.newTokenRequest(authorizationCode);
                    tokenUrl.setRedirectUri(Const.REDIRECT_URL);
                    return tokenUrl.execute();
                } catch (Exception e) {
                    logErrorMessage("Could not get tokens: " + e.getLocalizedMessage());
                    return null;
                }
            }

            @Override
            protected void onPostExecute(TokenResponse tokenResponse) {
                if (tokenResponse != null) {
                    addLogMessage("Got access token: " + tokenResponse.getAccessToken());
                    final Credential credentials = storeTokenResponse(tokenResponse);
                    if (credentials != null) {
                        doGetUserInfo(credentials);
                    }
                } else {
                    logErrorMessage("Got null token response.");
                }
            }

        };
        task.execute((Void)null);
    }

    private Credential storeTokenResponse(TokenResponse tokenResponse) {
        try {
            return flow.createAndStoreCredential(tokenResponse, Const.USER_ID);
        } catch (IOException e) {
            logErrorMessage("Could not store token response: " + e.getLocalizedMessage());
            return null;
        }
    }

    // TODO - add method to load Credentials
//    private Credential loadCredentials() {
//
//    }

    private void doGetUserInfo(final Credential credentials) {

        final AsyncTask<Void, Void, String> task = new AsyncTask<Void, Void, String>() {

            @Override
            protected String doInBackground(Void... params) {
                final HttpRequestFactory requestFactory = HTTP_TRANSPORT.createRequestFactory(credentials);
                final GenericUrl url = new GenericUrl(Const.USER_INFO_URL);
                try {
                    final HttpRequest request = requestFactory.buildGetRequest(url);
                    final HttpResponse response = request.execute();
                    final int statusCode = response.getStatusCode();
                    if (statusCode >= 200 && statusCode < 300) {
                        final String contentType = response.getContentType();
                        if (contentType.startsWith("application/json")) {
                            try {
                                final InputStream inputStream = response.getContent();
                                final String responseData = readInput(inputStream);
                                Log.d(TAG, "Read response data: " + responseData);
                                inputStream.close();
                                return responseData;
                            } catch (IOException e) {
                                logErrorMessage("Could not read user info response data: " + e.getLocalizedMessage());
                            }
                        } else {
                            logErrorMessage("Got invalid content type: " + contentType);
                        }
                    } else {
                        logErrorMessage("Got error HTTP status getting user info: " + statusCode);
                    }                } catch (IOException e) {
                    logErrorMessage("Could not get user info: " + e);
                }
                return null;
            }

            @Override
            protected void onPostExecute(String responseData) {
                if (responseData != null) {
                    addLogMessage("Read response data: " + responseData);
                } else {
                    addLogMessage("Got null response data.");
                }

            }
        };
        task.execute((Void)null);
    }

    private static AuthorizationCodeFlow getFlow() throws Exception {
        return new AuthorizationCodeFlow.Builder(
                BearerToken.authorizationHeaderAccessMethod(),
                HTTP_TRANSPORT,
                JSON_FACTORY,
                new GenericUrl(Const.TOKEN_URL),
                new ClientParametersAuthentication(Const.CLIENT_ID, Const.CLIENT_SECRET),
                Const.CLIENT_ID,
                Const.AUTHORIZATION_URL).
                setScopes(Arrays.asList(Const.SCOPES))
                .setDataStoreFactory(DATA_STORE_FACTORY).build();
    }

    private String readInput(InputStream inputStream) throws IOException {
        final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[256];

        while (true) {
            final int numberBytesRead = inputStream.read(buffer);
            if (numberBytesRead < 0) {
                break;
            }
            byteArrayOutputStream.write(buffer, 0, numberBytesRead);
        }

        final String str = new String(byteArrayOutputStream.toByteArray());
        return str;
    }

    private void logErrorMessage(String message) {
        Log.e(TAG, message);
        addLogMessage(message);
    }

    private void addLogMessage(final String message) {
        if (ThreadUtil.isUIThread()) {
            final String currentMessage = (String) textView.getText();
            if (currentMessage == null || currentMessage.isEmpty()) {
                textView.setText(message);
            } else {
                textView.setText(currentMessage + "\n" + message);
            }
        } else {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    addLogMessage(message);
                }
            });
        }
    }
}
