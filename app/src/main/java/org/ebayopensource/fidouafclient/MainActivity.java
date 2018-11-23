/*
 * Copyright 2015 eBay Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.ebayopensource.fidouafclient;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.google.gson.Gson;

import org.ebayopensource.fido.uaf.msg.RegistrationRequest;
import org.ebayopensource.fido.uaf.msg.client.UAFIntentType;
import org.ebayopensource.fido.uaf.msg.asm.obj.OIDCTokens;
import org.ebayopensource.fidouafclient.curl.Curl;
import org.ebayopensource.fidouafclient.op.Auth;
import org.ebayopensource.fidouafclient.op.Dereg;
import org.ebayopensource.fidouafclient.op.OpUtils;
import org.ebayopensource.fidouafclient.op.Reg;
import org.ebayopensource.fidouafclient.util.Endpoints;
import org.ebayopensource.fidouafclient.util.Preferences;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.security.MessageDigest;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import static android.R.id.message;
import static android.webkit.ConsoleMessage.MessageLevel.LOG;

public class MainActivity extends Activity {

    private static final int REG_ACTIVITY_RES_3 = 3;
    private static final int AUTH_ACTIVITY_RES_5 = 5;
    private static final int DEREG_ACTIVITY_RES_4 = 4;

    // XXX unify loggers
    private static final String TAG = MainActivity.class.getSimpleName();

    private static final Logger logger = Logger.getLogger(MainActivity.class.getName());

    private Gson gson = new Gson();
    private TextView facetID;
    private TextView msg;
    private TextView title;
    private TextView username;

    private Reg reg = new Reg();
    private Dereg dereg = new Dereg();
    private Auth auth = new Auth();
    private int authenticatorIndex = 1;
    private OIDCTokens tokens = null;

    //push notification manager
    private notificationTokenManager notifier = new notificationTokenManager();
    private boolean processingNotification=false;
    private String authenticationSession ="";
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Preferences.getSettingsParam("keyID").equals("")) {
            setContentView(R.layout.activity_main);
            findFields();
        } else {
            setContentView(R.layout.activity_registered);
            findFields();
            username.setText(Preferences.getSettingsParam("username"));
        }
        Intent intent = getIntent();
        Log.i("onCreate: ", "Got Intent");
        // check if this intent is started via custom scheme link
        if (Intent.ACTION_VIEW.equals(intent.getAction())) {
            if (intent.getExtras().containsKey(notificationListenerService.AUTHENTICATION_EXTRA))
            {
                Log.d(TAG, "Started using push notification");
                authenticationSession = intent.getStringExtra(notificationListenerService.AUTHENTICATION_EXTRA);
                Log.d(TAG, "Authentication Session is " + authenticationSession);
                processNotification(authenticationSession);
            }
            else {
                Log.d(TAG, "Started using URI");
                Uri uri = intent.getData();
                processUri(uri);
            }
        }
    }

    private void processUri(Uri uri)
    {
        Log.i("processUri: ", "Launch URL is " + uri.toString());
        // may be some test here with your custom uri
        String authCode = uri.getQueryParameter("code"); // "str" is set
        String authState = uri.getQueryParameter("state"); // "string" is set
        Log.i("processUri: ", "Authorization code is " + authCode);
        Log.i("processUri: ", "State is " + authState);
        msg.setText("State is " + authState);//get the tokens
        //String postData = "grant_type=authorization_code&client_id=832a7164-93f7-4f23-9c77-4a2205227fab&redirect_uri=mrbapp://android.mr-b.click/authResponse&code=" + authCode;
        String postData = "grant_type=authorization_code&code_verifier=11234567890123456789012345678901234567890123&redirect_uri=mrbapp://android.mr-b.click/authResponse&code=" + authCode;
        String postHeader = "Content-type:application/x-www-form-urlencoded Authorization:Basic&nbsp;bmhzLW9ubGluZTpzZWNyZXQ=";
        String responseTokens = Curl.postInSeparateThread(Preferences.getSettingsParam("oidcServerEndpoint") + "/token", postHeader, postData);
        Log.i("processUri: ", "Tokens are " + responseTokens);
        tokens = gson.fromJson(responseTokens, OIDCTokens.class);
        Log.i("processUri: ", "Cast responseTokens into object ");
        Log.i("processUri: ", "Access Token is " + tokens.access_token);
        msg.setText(msg.getText() + "\nIdentity Token is " + tokens.id_token);
        if (authState.equals("startRegistration"))
        {
            //kick off the registration process
            msg.setText(msg.getText() + "\nStarting Registration Process");
            regRequest();
        }
    }

    private void getTokens(String uri)
    {
        Log.i("processUri: ", "Launch URL is " + uri);
        // may be some test here with your custom uri
        String authCode = uri.substring(uri.indexOf("code=")+5); // "str" is set
        authCode = authCode.substring(0, authCode.length()-3);
        //String authState = uri.getQueryParameter("state"); // "string" is set
        Log.i("processUri: ", "Authorization code is " + authCode);
        //Log.i("processUri: ", "State is " + authState);
        //msg.setText("State is " + authState);//get the tokens
        //String postData = "grant_type=authorization_code&client_id=832a7164-93f7-4f23-9c77-4a2205227fab&redirect_uri=mrbapp://android.mr-b.click/authResponse&code=" + authCode;
        String postData = "grant_type=authorization_code&code_verifier=11234567890123456789012345678901234567890123&redirect_uri=mrbapp://android.mr-b.click/authResponse&code=" + authCode;
        String postHeader = "Content-type:application/x-www-form-urlencoded Authorization:Basic&nbsp;bmhzLW9ubGluZTpzZWNyZXQ=";
        String responseTokens = Curl.postInSeparateThread(Preferences.getSettingsParam("oidcServerEndpoint") + "/token", postHeader, postData);
        Log.i("processUri: ", "Tokens are " + responseTokens);
        tokens = gson.fromJson(responseTokens, OIDCTokens.class);
        Log.i("processUri: ", "Cast responseTokens into object ");
        Log.i("processUri: ", "Access Token is " + tokens.access_token);
        Log.i("processUri: ", "ID Token is " + tokens.id_token);
        msg.setText(msg.getText() + "\nIdentity Token is " + tokens.id_token);
    }


    private void processNotification(String authenticationSession)
    {
        processingNotification = true;
        authRequest(null);
    }
    @Override
    public void onStart() {
        super.onStart();


    }

    private void findFields (){
        msg = (TextView) findViewById(R.id.textViewMsg);
        title = (TextView) findViewById(R.id.textViewTitle);
        username = (TextView) findViewById(R.id.textUsername);
    }
	
    public void facetIDRequest(View view) {
        String facetIDval = "";
        try {
            facetIDval = getFacetID(this.getPackageManager().getPackageInfo(this.getPackageName(), this.getPackageManager().GET_SIGNATURES));
            Log.d("facetID: ", facetIDval);
        } catch (NameNotFoundException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        facetID = (TextView) findViewById(R.id.textViewFacetID);
        facetID.setText(facetIDval);
    }

    public void info(View view) {

        title.setText("Discovery info");
        String asmRequest = "{\"asmVersion\":{\"major\":1,\"minor\":0},\"requestType\":\"GetInfo\"}";
        Intent i = new Intent("org.fidoalliance.intent.FIDO_OPERATION");
        i.addCategory("android.intent.category.DEFAULT");
        i.setType("application/fido.uaf_client+json");

        List<ResolveInfo> queryIntentActivities = this.getPackageManager().queryIntentActivities(i, PackageManager.GET_META_DATA);

//		i = new Intent ("com.sec.android.fido.org.ebayopensource.fido.uaf.asm.AsmActivity");
//		i.setType("application/fido.uaf_asm+json");

        Bundle data = new Bundle();
        data.putString("message", OpUtils.getEmptyUafMsgRegRequest());
        data.putString("UAFIntentType", UAFIntentType.DISCOVER.name());
        i.putExtras(data);
//		i.setComponent(new ComponentName(queryIntentActivities.get(0).activityInfo.packageName, queryIntentActivities.get(0).activityInfo.name));
        startActivityForResult(i, 1);
        return;
    }

    public void regRequest(View view) {
        regRequest();
    }
    public void regRequest(){
//        String username = Preferences.getSettingsParam("username");
        String username = ((EditText) findViewById(R.id.editTextName)).getText().toString();
        if (username.equals ("") && tokens == null) {
            msg.setText("Retrieving tokens to initiate FIDO registration.");
            Log.d(TAG, Preferences.getSettingsParam("oidcServerEndpoint"));
            //String urlString = Preferences.getSettingsParam("oidcServerEndpoint") + "/authorize?response_type=code&client_id=832a7164-93f7-4f23-9c77-4a2205227fab&state=startRegistration&scope=openid+profile+email+phone&redirect_uri=mrbapp://android.mr-b.click/authResponse";
            String urlString = Preferences.getSettingsParam("oidcServerEndpoint") + "/authorize?scope=openid&state=startRegistration&client_id=nhs-online&response_type=code&code_challenge=znWZj1iRORuIZi3ivOXFG5Ttk75O8e_uqhySIgR8MPk&code_challenge_method=S256&redirect_uri=mrbapp://android.mr-b.click/authResponse";
            //String urlString = Preferences.getSettingsParam("oidcServerEndpoint") + "/authorize?scope=openid&client_id=nhs-online&redirect_uri=mrbapp://android.mr-b.click/authResponse&response_type=code&code_challenge=znWZj1iRORuIZi3ivOXFG5Ttk75O8e_uqhySIgR8MPk&code_challenge_method=S256
            Log.d(TAG, "URL to launch is: " + urlString);
            Uri uri = Uri.parse("googlechrome://navigate?url=" + urlString);
            Intent intent = new Intent(Intent.ACTION_VIEW, uri);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
//            intent.setPackage("com.android.chrome");
            try {
                startActivity(intent);
            } catch (ActivityNotFoundException ex) {
                Log.d(TAG, ex.getMessage());
                // Chrome browser presumably not installed so allow user to choose instead
                intent.setPackage(null);
                startActivity(intent);
            }



            return;
        }
        Preferences.setSettingsParam("username", username);


        title.setText("Registration operation executed, Username = " + username);

        Intent i = new Intent("org.fidoalliance.intent.FIDO_OPERATION");
        i.addCategory("android.intent.category.DEFAULT");

        i.setType("application/fido.uaf_client+json");

        List<ResolveInfo> queryIntentActivities = this.getPackageManager().queryIntentActivities(i, PackageManager.MATCH_DEFAULT_ONLY);
        String facetID = "";
        try {
            facetID = getFacetID(this.getPackageManager().getPackageInfo(this.getPackageName(), this.getPackageManager().GET_SIGNATURES));
            title.setText("facetID=" + facetID);
        } catch (NameNotFoundException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        String regRequest;
        if (tokens != null)
        {
            regRequest = reg.getUafMsgRegRequest(tokens, facetID, this);
            Log.d(TAG, "Got UAF Message from server response " + regRequest);
            try {
                JSONObject json = new JSONObject(regRequest);
                String serverResponseArray = json.get("uafProtocolMessage").toString();
                Log.d(TAG, "Server array response (string) is " + serverResponseArray);
                RegistrationRequest rr_response = gson.fromJson(serverResponseArray, RegistrationRequest[].class)[0];
                username = rr_response.username;
                Log.d(TAG, "Have set username to " + username);
                ((EditText) findViewById(R.id.editTextName)).setText(username);
                Preferences.setSettingsParam("username", username);
            }
            catch (Exception ex) {
                Log.e(TAG, "Caught error processing server response to get username " + ex.getMessage());
            }
        }
        else {
            regRequest = reg.getUafMsgRegRequest(username, facetID, this);
        }
        //parse reg request and set username
        Log.d(TAG, "UAF reg request: " + regRequest);
        title.setText("{regRequest}" + regRequest);

        Bundle data = new Bundle();
        data.putString("message", regRequest);
        data.putString("UAFIntentType", UAFIntentType.UAF_OPERATION.name());
        data.putString("channelBindings", regRequest);
        i.putExtras(data);

//		i.setComponent(new ComponentName(queryIntentActivities.get(0).activityInfo.packageName, queryIntentActivities.get(0).activityInfo.name));
        startActivityForResult(i, REG_ACTIVITY_RES_3);
    }

    private String getFacetID(PackageInfo paramPackageInfo) {
        try {
            ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(paramPackageInfo.signatures[0].toByteArray());
            Certificate certificate = CertificateFactory.getInstance("X509").generateCertificate(byteArrayInputStream);
            MessageDigest messageDigest = MessageDigest.getInstance("SHA1");
            String facetID = "android:apk-key-hash:" + Base64.encodeToString(((MessageDigest) messageDigest).digest(certificate.getEncoded()), 3);
            Log.d(TAG, facetID);
            return facetID;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public void dereg(View view) {

        title.setText("Deregistration operation executed");
        String uafMessage = dereg.getUafMsgRequest();
        Intent i = new Intent("org.fidoalliance.intent.FIDO_OPERATION");
        i.addCategory("android.intent.category.DEFAULT");
        i.setType("application/fido.uaf_client+json");

        List<ResolveInfo> queryIntentActivities = this.getPackageManager().queryIntentActivities(i, PackageManager.MATCH_DEFAULT_ONLY);

        Bundle data = new Bundle();
        data.putString("message", uafMessage);
        data.putString("UAFIntentType", "UAF_OPERATION");
        data.putString("channelBindings", uafMessage);
        i.putExtras(data);
        startActivityForResult(i, DEREG_ACTIVITY_RES_4);
    }

    public void authRequest(View view) {
        title.setText("Authentication operation executed");
        String facetID = "";
        try {
            facetID = getFacetID(this.getPackageManager().getPackageInfo(this.getPackageName(), this.getPackageManager().GET_SIGNATURES));
        } catch (NameNotFoundException e) {
            e.printStackTrace();
        }
        String authRequest = auth.getUafMsgRequest(facetID,this,false);
        Intent i = new Intent("org.fidoalliance.intent.FIDO_OPERATION");
        i.addCategory("android.intent.category.DEFAULT");
        i.setType("application/fido.uaf_client+json");
        Bundle data = new Bundle();
        data.putString("message", authRequest);
        data.putString("UAFIntentType", "UAF_OPERATION");
        data.putString("channelBindings", authRequest);
        i.putExtras(data);
        startActivityForResult(i, AUTH_ACTIVITY_RES_5);
    }

    public void trxRequest(View view) {
        title.setText("Authentication operation executed");
        String facetID = "";
        try {
            facetID = getFacetID(this.getPackageManager().getPackageInfo(this.getPackageName(), this.getPackageManager().GET_SIGNATURES));
        } catch (NameNotFoundException e) {
            e.printStackTrace();
        }
        String authRequest = auth.getUafMsgRequest(facetID,this,true);
        Intent i = new Intent("org.fidoalliance.intent.FIDO_OPERATION");
        i.addCategory("android.intent.category.DEFAULT");
        i.setType("application/fido.uaf_client+json");
        Bundle data = new Bundle();
        data.putString("message", authRequest);
        data.putString("UAFIntentType", "UAF_OPERATION");
        data.putString("channelBindings", authRequest);
        i.putExtras(data);
        startActivityForResult(i, AUTH_ACTIVITY_RES_5);
    }

    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        Log.d(TAG, String.format("onActivityResult: requestCode=%d, resultCode=%d, data=%s",
                requestCode, resultCode, new ArrayList<>(data.getExtras().keySet())));

        if (data == null){
            msg.setText("UAF Client didn't return any data. resultCode="+resultCode);
            return;
        }

        Object[] array = data.getExtras().keySet().toArray();
        StringBuffer extras = new StringBuffer();
        extras.append("[resultCode="+resultCode+"]");
        for (int i = 0; i < array.length; i++) {
            extras.append("[" + array[i] + "=");
//            if ("message".equals(array[i])) {
//                continue;
//            }
            extras.append(""+data.getExtras().get((String) array[i]) + "]");
        }
        title.setText("extras=" + extras.toString());

        if (requestCode == 1) {
            if (resultCode == RESULT_OK) {
                String asmResponse = data.getStringExtra("message");
                Log.d(TAG, "UAF message: " + asmResponse);

                String discoveryData = data.getStringExtra("discoveryData");
                msg.setText("{message}" + asmResponse + "{discoveryData}" + discoveryData);
                //Prepare ReqResponse
                //post to server
            }
            if (resultCode == RESULT_CANCELED) {
                userCancelled();
            }
        }
        if (requestCode == 2) {
            if (resultCode == RESULT_OK) {
                String asmResponse = data.getStringExtra("message");
                Log.d(TAG, "UAF message: " + asmResponse);
                msg.setText(asmResponse);
                dereg.recordKeyId(asmResponse);
                //Prepare ReqResponse
                //post to server
            }
            if (resultCode == RESULT_CANCELED) {
                userCancelled();
            }
        } else if (requestCode == REG_ACTIVITY_RES_3) {
            if (resultCode == RESULT_OK) {
                try {
                    String uafMessage = data.getStringExtra("message");
                    Log.d(TAG, "UAF message: " + message);
                    msg.setText(uafMessage);
                    //Prepare ReqResponse
                    //post to server
                    //	            String res = reg.sendRegResponse(asmResponse);
                    String res = reg.clientSendRegResponse(uafMessage);
                    Log.d(TAG, "UAF message response: " + res);
                    setContentView(R.layout.activity_registered);
                    findFields();
                    title.setText("extras=" + extras.toString());
                    msg.setText(res);
                    username.setText(Preferences.getSettingsParam("username"));
                    //get the notification token and send to server
                    //commented out for PoC with NHS  Logon
                    //String registerResponse = Curl.putInSeparateThread(Preferences.getSettingsParam("oidcServerEndpoint") + "/clientnotificationkeys/" + Preferences.getSettingsParam("username"), "", notificationTokenManager.notificationToken);
                } catch (Exception e){
                    msg.setText("Registration operation failed.\n"+e);
                }
            }
            if (resultCode == RESULT_CANCELED) {
                userCancelled();
            }
        } else if (requestCode == DEREG_ACTIVITY_RES_4) {
            if (resultCode == RESULT_OK) {
                //commented out for NHS  Logon PoC
                //String deregisterResponse = Curl.deleteInSeparateThread(Preferences.getSettingsParam("oidcServerEndpoint") + "/clientnotificationkeys/" + Preferences.getSettingsParam("username"));
                Preferences.setSettingsParam("keyID", "");
                Preferences.setSettingsParam("username", "");
                setContentView(R.layout.activity_main);
                findFields();
                title.setText("extras=" + extras.toString());
                String message = data.getStringExtra("message");
                Log.d(TAG, String.format("UAF message: [%s]", message));
                if (message != null) {
                    String out = "Dereg done. Client msg=" + message;
                    out = out + ". Sent=" + dereg.clientSendDeregResponse(message);
                    msg.setText(out);
                } else {
                    String deregMsg = Preferences.getSettingsParam("deregMsg");
                    String out = "Dereg done. Client msg was empty. Dereg msg = " + deregMsg;
                    out = out + ". Response=" + dereg.post(deregMsg);
                    msg.setText(out);

                }

            }
            if (resultCode == RESULT_CANCELED) {
                userCancelled();
            }
        } else if (requestCode == AUTH_ACTIVITY_RES_5) {
            // Got authentication response message from UAF activity
            if (resultCode == RESULT_OK) {
                String uafMessage = data.getStringExtra("message");
                Log.d(TAG, "UAF message: " + uafMessage);
                if (uafMessage != null) {
                    msg.setText(uafMessage);
                    //Prepare ReqResponse
                    //post to server
//	            String res = auth.sendAuthResponse(asmResponse);
                    if (processingNotification)
                    {
                        //processing logon for external RP
                        try {
                            processingNotification = false;
                            JSONObject json = new JSONObject(uafMessage);
                            String AuthResponse = json.get("uafProtocolMessage").toString();
                            Log.d(TAG, "AuthResponse message is: " + AuthResponse);
                            msg.setText("\nAuthResponse is\n" + AuthResponse);
                            String AuthResponseB64 = Base64.encodeToString(AuthResponse.getBytes(), Base64.NO_WRAP);
                            Log.d(TAG, "Base64 encoded AuthResponse message is: " + AuthResponseB64);
                            Log.d(TAG, "Authentication Session is " + authenticationSession);
                            String registerResponse = Curl.postInSeparateThread(Preferences.getSettingsParam("oidcServerEndpoint") + "/fidoauthresponse/" + authenticationSession, "", AuthResponseB64);
                        }
                        catch (Exception ex) {
                            Log.d(TAG, "Failed to process notification");
                        }
                        Log.d(TAG, "Notification processing finished - closing activity");
                        this.finish();

                    }
                    else {
                        //NEW CODE - invoke OIDC URL
                        //app-based logon
                        try {
                            JSONObject json = new JSONObject(uafMessage);
                            String AuthResponse = json.get("uafProtocolMessage").toString();
                            Log.d(TAG, "AuthResponse message is: " + AuthResponse);
                            msg.setText("\nAuthResponse is\n" + AuthResponse);
                            String AuthResponseB64 = Base64.encodeToString(AuthResponse.getBytes(), Base64.NO_WRAP);
                            Log.d(TAG, "Base64 encoded AuthResponse message is: " + AuthResponseB64);
                            String redirect_uri = "mrbapp://android.mr-b.click/authResponse";
                            //String urlString = Preferences.getSettingsParam("oidcServerEndpoint") + "/authorize?response_type=code&client_id=832a7164-93f7-4f23-9c77-4a2205227fab&state=authenticated&scope=openid+profile+email+phone&redirect_uri=" + redirect_uri + "&fidoAuthResponse=" + AuthResponseB64;
                            String urlString = Preferences.getSettingsParam("oidcServerEndpoint") + "/authorize?scope=openid&client_id=nhs-online&response_type=code&code_challenge=znWZj1iRORuIZi3ivOXFG5Ttk75O8e_uqhySIgR8MPk&code_challenge_method=S256&state=authenticated&redirect_uri=" + redirect_uri + "&fido_auth_response=" + AuthResponseB64;
                            //String urlString = Preferences.getSettingsParam("oidcServerEndpoint") + "/authorize?response_type=code&client_id=832a7164-93f7-4f23-9c77-4a2205227fab&state=authenticated&scope=openid+profile+email+phone&redirect_uri=" + redirect_uri + "&fidoAuthResponse=" + AuthResponseB64;
                            Log.d(TAG, "URL to launch is: " + urlString);
                            //section to launch OIDC via device browser
                        /*
                        Intent intent=new Intent(Intent.ACTION_VIEW,Uri.parse(urlString));
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        intent.setPackage("com.android.chrome");
                        try {
                            startActivity(intent);
                        } catch (ActivityNotFoundException ex) {
                            // Chrome browser presumably not installed so allow user to choose instead
                            intent.setPackage(null);
                            startActivity(intent);
                        }
                        */
                            //section to launch OIDC via direct calls
                            String targetUrl = Curl.getInSeparateThread(urlString, "", redirect_uri);
                            Log.d(TAG, "targetUrl is: " + targetUrl);
                            getTokens(targetUrl);
                            //Uri uri = Uri.parse(targetUrl);
                            //processUri(uri);
                        } catch (Exception ex) {
                            //OLD Code - invoke FIDO Server directly to validate auth response
                            String res = auth.clientSendResponse(uafMessage);
                            msg.setText("\n" + res);
                            Log.d(TAG, "UAF message response: " + res);
                        }
                    }

                }
            }
            if (resultCode == RESULT_CANCELED) {
                userCancelled();
            }
        }

    }

    private void userCancelled() {
        String warnMsg = "User cancelled";
        Log.w(TAG, warnMsg);
        Toast.makeText(this, warnMsg, Toast.LENGTH_SHORT).show();
    }

    public RegistrationRequest getRegistrationRequest(String username) {
        logger.info("  [UAF][1]Reg - getRegRequest  ");
        String regReq = Curl.getInSeparateThread(Endpoints.getRegRequestEndpoint() + username);
        logger.info("  [UAF][1]Reg - getRegRequest  : " + regReq);
        return gson.fromJson(regReq, RegistrationRequest[].class)[0];
    }


    public void deregRequest(View view) {
        startActivity(new Intent("info.gazers.log.FidoActivity"));
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            startActivity(new Intent(
                    "org.ebayopensource.fidouafclient.SettingsActivity"));
        }
        if (id == R.id.action_discover) {
            info(this.getWindow().getCurrentFocus());
        }
        if (id == R.id.action_save_message) {
            SaveMessageDialog.show(this, msg);
        }
        return super.onOptionsItemSelected(item);
    }

}
