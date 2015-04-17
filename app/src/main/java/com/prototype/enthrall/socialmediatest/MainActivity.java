package com.prototype.enthrall.socialmediatest;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.support.v7.app.ActionBarActivity;
import android.util.Base64;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.facebook.AccessToken;
import com.facebook.CallbackManager;
import com.facebook.FacebookAuthorizationException;
import com.facebook.FacebookCallback;
import com.facebook.FacebookException;
import com.facebook.FacebookSdk;
import com.facebook.GraphRequest;
import com.facebook.GraphResponse;
import com.facebook.Profile;
import com.facebook.ProfileTracker;
import com.facebook.appevents.AppEventsLogger;
import com.facebook.login.LoginManager;
import com.facebook.login.LoginResult;
import com.facebook.login.widget.LoginButton;
import com.facebook.login.widget.ProfilePictureView;
import com.facebook.share.ShareApi;
import com.facebook.share.Sharer;
import com.facebook.share.model.ShareLinkContent;
import com.facebook.share.model.SharePhotoContent;
import com.facebook.share.widget.ShareDialog;
import com.loopj.android.http.AsyncHttpClient;
import com.loopj.android.http.AsyncHttpRequest;
import com.loopj.android.http.AsyncHttpResponseHandler;
import com.loopj.android.http.RequestParams;

import org.apache.http.Header;
import org.apache.http.entity.StringEntity;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


public class MainActivity extends ActionBarActivity {

    public static final String TAG = "MainActivity";

    CallbackManager mCallbackManager;
    LoginButton loginButton;
    Profile mProfile;
    TextView mText;
    ProfilePictureView mProfilePicture;

    private final String PENDING_ACTION_BUNDLE_KEY =
            "com.prototype.enthrall.socialmediatest:PendingAction";

    private Button postStatusUpdateButton;
    private TextView greeting;
    private boolean canPresentShareDialog;
    private ProfileTracker profileTracker;
    private ShareDialog shareDialog;
    private PendingAction pendingAction = PendingAction.NONE;

    private JSONObject userData;

    //List of permissions that we're asking for!
    private List<String> permissions = Arrays.asList("public_profile, email, user_birthday, user_friends");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        FacebookSdk.sdkInitialize(getApplicationContext());
        mCallbackManager = CallbackManager.Factory.create();

        LoginManager.getInstance().registerCallback(mCallbackManager,
                new FacebookCallback<LoginResult>() {
                    @Override
                    public void onSuccess(LoginResult loginResult) {

                        handlePendingAction();
                        updateUI();
                    }

                    @Override
                    public void onCancel() {
                        if (pendingAction != PendingAction.NONE) {
                            showAlert();
                            pendingAction = PendingAction.NONE;
                        }
                        updateUI();
                    }

                    @Override
                    public void onError(FacebookException exception) {
                        if (pendingAction != PendingAction.NONE
                                && exception instanceof FacebookAuthorizationException) {
                            showAlert();
                            pendingAction = PendingAction.NONE;
                        }
                        updateUI();
                    }

                    private void showAlert() {
                        new AlertDialog.Builder(getBaseContext())
                                .setTitle(R.string.cancelled)
                                .setMessage(R.string.permission_not_granted)
                                .setPositiveButton(R.string.ok, null)
                                .show();
                    }
                });

        shareDialog = new ShareDialog(this);
        shareDialog.registerCallback(
                mCallbackManager,
                shareCallback);

        if (savedInstanceState != null) {
            String name = savedInstanceState.getString(PENDING_ACTION_BUNDLE_KEY);
            pendingAction = PendingAction.valueOf(name);
        }

        setContentView(R.layout.activity_main);

        profileTracker = new ProfileTracker() {
            @Override
            protected void onCurrentProfileChanged(Profile oldProfile, Profile currentProfile) {
                updateUI();
                // It's possible that we were waiting for Profile to be populated in order to
                // post a status update.
                handlePendingAction();
            }
        };

        mProfilePicture = (ProfilePictureView) findViewById(R.id.profile_picture);
        greeting = (TextView) findViewById(R.id.greeting);

        postStatusUpdateButton = (Button) findViewById(R.id.postStatusUpdateButton);
        postStatusUpdateButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                onClickPostStatusUpdate();
            }
        });

        GraphRequest request = GraphRequest.newMeRequest(
                AccessToken.getCurrentAccessToken(),
                new GraphRequest.GraphJSONObjectCallback() {
                    @Override
                    public void onCompleted(
                            JSONObject object,
                            GraphResponse response) {
                        if(object != null) {
                            Log.d(TAG, object.toString());
                            userData = object;
                            String userID;
                            try {
                                userID = userData.getString("id");
                                Log.d(TAG, "userID is: " + userID);
                                Log.d(TAG, "email is: " + userData.getString("email"));

                                Log.d(TAG, "Posting Now!");

                                AsyncHttpClient client = new AsyncHttpClient(true, 9000, 9443);
                                AsyncHttpResponseHandler responseHandler = new AsyncHttpResponseHandler() {

                                    @Override
                                    public void onStart() {
                                        // called before request is started
                                    }

                                    @Override
                                    public void onSuccess(int statusCode, Header[] headers, byte[] response) {
                                        // called when response HTTP status is "200 OK"
                                    }

                                    @Override
                                    public void onFailure(int statusCode, Header[] headers, byte[] errorResponse, Throwable e) {
                                        // called when response HTTP status is "4XX" (eg. 401, 403, 404)
                                    }

                                    @Override
                                    public void onRetry(int retryNo) {
                                        // called when request is retried
                                    }
                                }; //this doesn't really need to do anything
                                JSONObject jsonParams = new JSONObject();
                                jsonParams.put("email", userData.getString("email"));
                                jsonParams.put("id", userData.getString("id"));
                                StringEntity entity = null;
                                try {
                                    entity = new StringEntity(jsonParams.toString());
                                } catch (UnsupportedEncodingException e) {
                                    e.printStackTrace();
                                }

                                client.post(getBaseContext(), "https://db.enthrallsports.com:9443/api/facebook", entity,
                                        "application/json", responseHandler);
                            } catch (JSONException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                });
        Bundle parameters = new Bundle();
        //this just contains whatever fields we want
        parameters.putString("fields", "email, id, name, link, gender, birthday, first_name, work");
        request.setParameters(parameters);
        request.executeAsync();

        // Can we present the share dialog for regular links?
        canPresentShareDialog = ShareDialog.canShow(ShareLinkContent.class);

        showHashKey(getBaseContext());
//        mProfilePicture = (ProfilePictureView) findViewById(R.id.profile_picture);
//        LoginManager.getInstance().logInWithPublishPermissions(this, Arrays.asList("publish_actions"));
        loginButton = (LoginButton) findViewById(R.id.login_button);

        loginButton.registerCallback(mCallbackManager, new FacebookCallback<LoginResult>() {
            @Override
            public void onSuccess(LoginResult loginResult) {
                // App code
                Log.d(TAG, "Successful button press");
                mProfile = Profile.getCurrentProfile();
                Log.d(TAG, mProfile.getName() + " " + mProfile.getId());
                mProfilePicture.setProfileId(mProfile.getId());

                GraphRequest request = GraphRequest.newMeRequest(
                        AccessToken.getCurrentAccessToken(),
                        new GraphRequest.GraphJSONObjectCallback() {
                            @Override
                            public void onCompleted(
                                    JSONObject object,
                                    GraphResponse response) {
                                if (object != null) {
                                    Log.d(TAG, object.toString());
                                    userData = object;
                                    String userID;
                                    try {
                                        userID = userData.getString("id");
                                        Log.d(TAG, "userID is: " + userID);
                                        Log.d(TAG, "email is now: " + userData.getString("email"));
                                    } catch (JSONException e) {
                                        e.printStackTrace();
                                    }
                                }
                            }
                        });
                Bundle parameters = new Bundle();
                //this just contains whatever fields we want
                parameters.putString("fields", "email, id, name, link, gender, birthday, first_name, work");
                request.setParameters(parameters);
                request.executeAsync();

            }

            @Override
            public void onCancel() {
                // App code
                Log.d(TAG, "Canceled login button press");
            }

            @Override
            public void onError(FacebookException exception) {
                // App code
                Log.d(TAG, "Error on login button press");
            }
        });
        loginButton.setReadPermissions(permissions);

//        AsyncHttpClient client = new AsyncHttpClient();
//        JSONObject jsonParams = new JSONObject();
//        try {
//            jsonParams.put("email", userData.getString("email"));
//            jsonParams.put("id", userData.getString("id"));
//            StringEntity entity = new StringEntity(jsonParams.toString());
//            client.post(getBaseContext(), "http://db.enthrallsports.com:9443/facebook", entity,
//                    "application/json", null);
//            Log.d(TAG, "Posting Now!");
//        }
//        catch (Exception e) {
//            Log.d(TAG, "Unable to Post");
//            e.printStackTrace();
//        }

    }

    private FacebookCallback<Sharer.Result> shareCallback = new FacebookCallback<Sharer.Result>() {
        @Override
        public void onCancel() {
            Log.d("HelloFacebook", "Canceled");
        }

        @Override
        public void onError(FacebookException error) {
            Log.d("HelloFacebook", String.format("Error: %s", error.toString()));
            String title = getString(R.string.error);
            String alertMessage = error.getMessage();
            showResult(title, alertMessage);
        }

        @Override
        public void onSuccess(Sharer.Result result) {
            Log.d("HelloFacebook", "Success!");
            if (result.getPostId() != null) {
                String title = getString(R.string.success);
                String id = result.getPostId();
                String alertMessage = getString(R.string.successfully_posted_post, id);
                showResult(title, alertMessage);
            }
        }

        private void showResult(String title, String alertMessage) {
            new AlertDialog.Builder(MainActivity.this)
                    .setTitle(title)
                    .setMessage(alertMessage)
                    .setPositiveButton("Ok", null)
                    .show();
        }
    };

    private enum PendingAction {
        NONE,
        POST_PHOTO,
        POST_STATUS_UPDATE
    }

    private void onClickPostStatusUpdate() {
        performPublish(PendingAction.POST_STATUS_UPDATE, canPresentShareDialog);
    }

    private void postStatusUpdate() {
        Profile profile = Profile.getCurrentProfile();
        ShareLinkContent linkContent = new ShareLinkContent.Builder()
                .setContentTitle("Enthrall Sports")
                .setContentDescription(
                        "I just posted something because of something from Enthrall!")
                .setContentUrl(Uri.parse("http://enthrallsports.com/"))
                .build();
        if (canPresentShareDialog) {
            shareDialog.show(linkContent);
        } else if (profile != null && hasPublishPermission()) {
            ShareApi.share(linkContent, shareCallback);
        } else {
            pendingAction = PendingAction.POST_STATUS_UPDATE;
        }
    }

    private void updateUI() {
        boolean enableButtons = AccessToken.getCurrentAccessToken() != null;

        postStatusUpdateButton.setEnabled(enableButtons || canPresentShareDialog);

        Profile profile = Profile.getCurrentProfile();
        if (enableButtons && profile != null) {
            mProfilePicture.setProfileId(profile.getId());
            greeting.setText(getString(R.string.hello_user, profile.getFirstName()));
        } else {
            mProfilePicture.setProfileId(null);
            greeting.setText(null);
        }
    }

    private void handlePendingAction() {
        PendingAction previouslyPendingAction = pendingAction;
        // These actions may re-set pendingAction if they are still pending, but we assume they
        // will succeed.
        pendingAction = PendingAction.NONE;

        switch (previouslyPendingAction) {
            case NONE:
                break;
            case POST_PHOTO:
                break;
            case POST_STATUS_UPDATE:
                postStatusUpdate();
                break;
        }
    }

    private boolean hasPublishPermission() {
        AccessToken accessToken = AccessToken.getCurrentAccessToken();
        return accessToken != null && accessToken.getPermissions().contains("publish_actions");
    }

    private void performPublish(PendingAction action, boolean allowNoToken) {
        AccessToken accessToken = AccessToken.getCurrentAccessToken();
        if (accessToken != null) {
            pendingAction = action;
            if (hasPublishPermission()) {
                // We can do the action right away.
                handlePendingAction();
                return;
            } else {
                // We need to get new permissions, then complete the action when we get called back.
                LoginManager.getInstance().logInWithPublishPermissions(
                        this,
                        Arrays.asList("publish_actions"));
                return;
            }
        }

        if (allowNoToken) {
            pendingAction = action;
            handlePendingAction();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Call the 'activateApp' method to log an app event for use in analytics and advertising
        // reporting.  Do so in the onResume methods of the primary Activities that an app may be
        // launched into.
        AppEventsLogger.activateApp(this);

        updateUI();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        outState.putString(PENDING_ACTION_BUNDLE_KEY, pendingAction.name());
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        mCallbackManager.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onPause() {
        super.onPause();

        // Call the 'deactivateApp' method to log an app event for use in analytics and advertising
        // reporting.  Do so in the onPause methods of the primary Activities that an app may be
        // launched into.
        AppEventsLogger.deactivateApp(this);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    public static void showHashKey(Context context) {
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(
                    "com.prototype.enthrall.socialmediatest", PackageManager.GET_SIGNATURES);
            for (Signature signature : info.signatures) {
                MessageDigest md = MessageDigest.getInstance("SHA");
                md.update(signature.toByteArray());
                Log.i("KeyHash:", Base64.encodeToString(md.digest(), Base64.DEFAULT));
            }
        } catch (PackageManager.NameNotFoundException e) {
        } catch (NoSuchAlgorithmException e) {
        }
    }

    public static String executePost(String targetURL, String urlParameters) {
        URL url;
        HttpURLConnection connection = null;
        try {
            //Create connection
            url = new URL(targetURL);
            connection = (HttpURLConnection)url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            connection.setRequestProperty("Content-Length", "" + Integer.toString(urlParameters.getBytes().length));
            connection.setRequestProperty("Content-Language", "en-US");
            connection.setUseCaches (false);
            connection.setDoInput(true);
            connection.setDoOutput(true);

            //Send request
            DataOutputStream wr = new DataOutputStream (
                    connection.getOutputStream ());
            wr.writeBytes (urlParameters);
            wr.flush ();
            wr.close ();

            //Get Response
            InputStream is = connection.getInputStream();
            BufferedReader rd = new BufferedReader(new InputStreamReader(is));
            String line;
            StringBuffer response = new StringBuffer();
            while((line = rd.readLine()) != null) {
                response.append(line);
                response.append('\r');
            }
            rd.close();
            return response.toString();

        } catch (Exception e) {

            e.printStackTrace();
            return null;

        } finally {

            if(connection != null) {
                connection.disconnect();
            }
        }
    }

}
