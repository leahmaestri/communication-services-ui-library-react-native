package com.azurecommunicationuidemoapp;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LayoutDirection;
import android.util.Log;
import android.webkit.URLUtil;

import com.azure.android.communication.common.CommunicationIdentifier;
import com.azure.android.communication.common.CommunicationTokenCredential;
import com.azure.android.communication.common.CommunicationTokenRefreshOptions;
import com.azure.android.communication.ui.calling.CallComposite;
import com.azure.android.communication.ui.calling.CallCompositeBuilder;
import com.azure.android.communication.ui.calling.models.CallCompositeGroupCallLocator;
import com.azure.android.communication.ui.calling.models.CallCompositeJoinLocator;
import com.azure.android.communication.ui.calling.models.CallCompositeLocalOptions;
import com.azure.android.communication.ui.calling.models.CallCompositeLocalizationOptions;
import com.azure.android.communication.ui.calling.models.CallCompositeParticipantViewData;
import com.azure.android.communication.ui.calling.models.CallCompositeSetupScreenViewData;
import com.azure.android.communication.ui.calling.models.CallCompositeRemoteOptions;
import com.azure.android.communication.ui.calling.models.CallCompositeSupportedLocale;
import com.azure.android.communication.ui.calling.models.CallCompositeTeamsMeetingLinkLocator;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableArray;

import org.json.JSONObject;

import java.net.URL;
import java.util.Locale;
import java.util.UUID;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class RNAzureCommunicationUICalling extends ReactContextBaseJavaModule {

    private static final String TAG = "RNAzureCallingUI";
    String mToken = "";

    RNAzureCommunicationUICalling(ReactApplicationContext context) {
        super(context);
    }

    @Override
    public String getName() {
        return "RNAzureCommunicationUICalling";
    }

    @ReactMethod
    public void startCallComposite(ReadableMap localOptions, 
                                   String localAvatarImageResource,
                                   ReadableMap remoteOptions, 
                                   String remoteAvatarImageResource,
                                   ReadableMap localizationOptions, 
                                   Promise promise) {
        // local options
        String displayName = localOptions.getString("displayName");
        String title = localOptions.getString("title");
        String subtitle = localOptions.getString("subtitle");

        Log.d(TAG, "Local Avatar image resource:: " + localAvatarImageResource);
        Log.d(TAG, "Remote Avatar image resource:: " + remoteAvatarImageResource);

        // remote options
        String tokenInput = remoteOptions.getString("token");
        String meetingInput = remoteOptions.getString("meeting");

        // localization options
        String selectedLanguage = localizationOptions.getString("locale");
        boolean isRightToLeft = localizationOptions.getBoolean("layout");
        
        if (URLUtil.isValidUrl(tokenInput.trim())) {
            getCommunicationToken(tokenInput, displayName, meetingInput, localAvatarImageResource, title, subtitle, selectedLanguage, isRightToLeft, remoteAvatarImageResource, promise);
        } else {
            mToken = tokenInput;
            launchComposite(displayName, meetingInput, localAvatarImageResource, title, subtitle, selectedLanguage, isRightToLeft, remoteAvatarImageResource, promise);
        }
    }

    @ReactMethod
    public void getSupportedLocales(Promise promise) {
        WritableArray wArr = Arguments.createArray();
        for (Locale locale : CallCompositeSupportedLocale.getSupportedLocales()) {
            wArr.pushString(locale.getLanguage());
        }
        promise.resolve(wArr);
    }
     
    @ReactMethod
    public void addListener(String eventName) {}
    
    @ReactMethod
    public void removeListeners(Integer count) {}

    public void launchComposite(String displayName, String meetingInput, String localAvatarImageResource, String title, String subtitle, String selectedLanguage, boolean isRightToLeft, String remoteAvatarImageResource, Promise promise) {
        Context context = getCurrentActivity();


        int layoutDirection = isRightToLeft ? LayoutDirection.RTL : LayoutDirection.LTR;

        CallComposite callComposite = new CallCompositeBuilder()
                .localization(new CallCompositeLocalizationOptions(Locale.forLanguageTag(selectedLanguage), layoutDirection)).build();


        try {
            CommunicationTokenRefreshOptions communicationTokenRefreshOptions =
                    new CommunicationTokenRefreshOptions(this::fetchToken, true);

            CommunicationTokenCredential communicationTokenCredential = new CommunicationTokenCredential(communicationTokenRefreshOptions);

            callComposite.addOnErrorEventHandler(eventHandler -> {
                Log.d(TAG, "================= application is logging exception =================");
                Log.d(TAG, eventHandler.getCause().toString());
                Log.d(TAG, eventHandler.getErrorCode().toString());
                Log.d(TAG, "====================================================================");

                promise.reject(eventHandler.getErrorCode() + " " + eventHandler.getCause().getMessage());
            });

            if (remoteAvatarImageResource != null && remoteAvatarImageResource.isEmpty() == false) {
                Bitmap remoteAvatarImageBitmap = getAvatarBitmap(context, remoteAvatarImageResource);

                callComposite.addOnRemoteParticipantJoinedEventHandler((remoteParticipantJoinedEvent) -> {
                    for (CommunicationIdentifier identifier : remoteParticipantJoinedEvent.getIdentifiers()) {
                        callComposite.setRemoteParticipantViewData(identifier,
                                new CallCompositeParticipantViewData()
                                        .setDisplayName(remoteAvatarImageResource)
                                        .setAvatarBitmap(remoteAvatarImageBitmap)
                        );
                    }
                });
            }


            if (URLUtil.isValidUrl(meetingInput.trim())) {
                CallCompositeJoinLocator locator = new CallCompositeTeamsMeetingLinkLocator(meetingInput);

                CallCompositeRemoteOptions remoteOptions = new CallCompositeRemoteOptions(
                        locator,
                        communicationTokenCredential,
                        displayName);


                CallCompositeLocalOptions localOptions = new CallCompositeLocalOptions();

                if (localAvatarImageResource != null && localAvatarImageResource.isEmpty() == false) {
                    Bitmap avatarImageBitmap = getAvatarBitmap(context, localAvatarImageResource);
                    CallCompositeParticipantViewData participantViewData = new CallCompositeParticipantViewData()
                            .setDisplayName(displayName)
                            .setAvatarBitmap(avatarImageBitmap);
                    
                    localOptions.setParticipantViewData(participantViewData);
                }
                
                if (title != null) {
                    CallCompositeSetupScreenViewData setupViewData = new CallCompositeSetupScreenViewData()
                        .setTitle(title)
                        .setSubtitle(subtitle);
                    localOptions.setSetupScreenViewData(setupViewData);
                }
                
                callComposite.launch(context, remoteOptions, localOptions);

            } else {
                CallCompositeJoinLocator locator = new CallCompositeGroupCallLocator(UUID.fromString(meetingInput));

                CallCompositeRemoteOptions remoteOptions = new CallCompositeRemoteOptions(
                        locator,
                        communicationTokenCredential,
                        displayName);
                CallCompositeLocalOptions localOptions = new CallCompositeLocalOptions();

                Bitmap avatarImageBitmap = getAvatarBitmap(context, localAvatarImageResource);
                if (avatarImageBitmap != null) {
                    CallCompositeParticipantViewData participantViewData = new CallCompositeParticipantViewData()
                            .setDisplayName(displayName)
                            .setAvatarBitmap(avatarImageBitmap);
                    
                    
                    localOptions.setParticipantViewData(participantViewData);
                }
                
                if (title != null) {
                    CallCompositeSetupScreenViewData setupViewData = new CallCompositeSetupScreenViewData()
                        .setTitle(title)
                        .setSubtitle(subtitle);
                    localOptions.setSetupScreenViewData(setupViewData);
                }
                
                callComposite.launch(context, remoteOptions, localOptions);
            }

            promise.resolve(null);
        } catch (Exception e) {
            promise.reject("Token is invalid", e);
        }
    }

    private String fetchToken() {
        return mToken;
    }

    private void getCommunicationToken(String tokenInput, String displayName, String meetingInput, String localAvatarImageResource, String title, String subtitle, String selectedLanguage, boolean isRightToLeft, String remoteAvatarImageResource, Promise promise) {
        Thread thread = new Thread(() -> {
            try {
                OkHttpClient client = new OkHttpClient();
                Request request = new Request.Builder()
                        .url(tokenInput.trim())
                        .build();

                Response response = client.newCall(request).execute();
                String responseBody = response.body().string();
                JSONObject json = new JSONObject(responseBody);
                mToken = json.getString("token");

                new Handler(Looper.getMainLooper()).post(() -> {
                    launchComposite(displayName, meetingInput, localAvatarImageResource, title, subtitle, selectedLanguage, isRightToLeft, remoteAvatarImageResource, promise);
                });

            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        thread.start();
    }

    private Bitmap getAvatarBitmap(Context context, String avatarImageResourceName) {
        Bitmap avatarImageBitmap;
        switch (avatarImageResourceName) {
            case "cat":
                avatarImageBitmap = BitmapFactory.decodeResource(context.getResources(), R.drawable.cat);
                break;
            case "fox":
                avatarImageBitmap = BitmapFactory.decodeResource(context.getResources(), R.drawable.fox);
                break;
            case "koala":
                avatarImageBitmap = BitmapFactory.decodeResource(context.getResources(), R.drawable.koala);
                break;
            case "monkey":
                avatarImageBitmap = BitmapFactory.decodeResource(context.getResources(), R.drawable.monkey);
                break;
            case "mouse":
                avatarImageBitmap = BitmapFactory.decodeResource(context.getResources(), R.drawable.mouse);
                break;
            case "octopus":
                avatarImageBitmap = BitmapFactory.decodeResource(context.getResources(), R.drawable.octopus);
                break;
            default:
                avatarImageBitmap = null;
                break;
        }
        return avatarImageBitmap;
    }
}