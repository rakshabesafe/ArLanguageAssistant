package com.example.languageassistant.utils;

import android.content.Context;
import android.util.Log;
import android.widget.EditText;
import android.widget.Toast;
import androidx.lifecycle.MutableLiveData;
import com.vuzix.ultralite.Layout;
import com.vuzix.ultralite.UltraliteSDK;
import com.vuzix.ultralite.sdk.EventListener;
import com.vuzix.ultralite.utils.scroll.TextToImageSlicer; // Assuming this is the correct import
// import com.vuzix.ultralite.utils.scroll.AckWaiter; // Assuming this is the correct import for AckWaiter

import static com.vuzix.ultralite.sdk.LinkStatusListener.LINK_STATUS_DISCONNECTED;

public class UltraliteSDKUtils {

    private static final String TAG = "UltraliteSDKUtils";
    public static final int REQUEST_CONTROL_TIMEOUT_MS = 10000; // 10 seconds

    private static Context context;
    public static UltraliteSDK ultraliteSDK;

    // LiveData for SDK status
    public static MutableLiveData<Boolean> isSdkAvailable = new MutableLiveData<>(false);
    public static MutableLiveData<Boolean> isSdkControlled = new MutableLiveData<>(false);
    
    // Placeholder for actual scrolling text view if needed directly
    // private static UltraliteSDK.ScrollingTextView scrollingTextView; 


    public static void init(Context appContext) {
        context = appContext.getApplicationContext();
        ultraliteSDK = UltraliteSDK.get(context);
        // scrollingTextView = ultraliteSDK.getScrollingTextView(); // Initialize if direct access is planned
        
        // Observe SDK availability
        ultraliteSDK.getAvailable().observeForever(available -> {
            isSdkAvailable.postValue(available);
            if (available) {
                Log.i(TAG, "Ultralite SDK is available.");
                requestSdkControl();
            } else {
                Log.i(TAG, "Ultralite SDK is not available.");
                isSdkControlled.postValue(false);
            }
        });
    }

    public static EventListener getEventListener() {
        return eventListener;
    }

    public static final EventListener eventListener = new EventListener() {
        @Override
        public void onControlGained() {
            Log.i(TAG, "EventListener: Control Gained");
            isSdkControlled.postValue(true);
            handleControlGained();
        }

        @Override
        public void onControlLost() {
            Log.i(TAG, "EventListener: Control Lost");
            isSdkControlled.postValue(false);
            handleControlLost();
        }

        @Override
        public void onConnectionStatusChanged(int status) {
            Log.d(TAG, "EventListener: onConnectionStatusChanged - Status: " + status);
        }

        @Override
        public void onLinkStatusChanged(int status) {
            Log.d(TAG, "EventListener: onLinkStatusChanged - Status: " + status);
            if (status == LINK_STATUS_DISCONNECTED) {
                Log.w(TAG, "EventListener: Link Disconnected");
                isSdkControlled.postValue(false);
                Toast.makeText(context, "Connection to glasses lost or timed out.", Toast.LENGTH_LONG).show();
            }
        }

        @Override
        public void onDisplayOnline(boolean isOnline) {
            Log.d(TAG, "EventListener: onDisplayOnline - Display is " + (isOnline ? "online" : "offline"));
        }
    };

    public static void requestSdkControl() {
        if (ultraliteSDK == null) {
            Log.e(TAG, "UltraliteSDK instance is null. Cannot request control.");
            Toast.makeText(context, "SDK not initialized.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (Boolean.TRUE.equals(isSdkAvailable.getValue()) && (ultraliteSDK.getControlledByMe() == null || !ultraliteSDK.getControlledByMe().getValue())) {
            Log.d(TAG, "Requesting SDK control...");
            try {
                boolean requested = ultraliteSDK.requestControl(REQUEST_CONTROL_TIMEOUT_MS);
                if (requested) {
                    Log.d(TAG, "SDK control request successful. Waiting for onControlGained callback.");
                } else {
                    Log.w(TAG, "SDK control request failed immediately.");
                    isSdkControlled.postValue(false);
                    Toast.makeText(context, "Failed to request control. Ensure Vuzix Connect is active.", Toast.LENGTH_LONG).show();
                }
            } catch (Exception e) {
                Log.e(TAG, "Exception while requesting control: " + e.getMessage(), e);
                Toast.makeText(context, "Error requesting control: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                isSdkControlled.postValue(false);
            }
        } else if (ultraliteSDK.getControlledByMe() != null && ultraliteSDK.getControlledByMe().getValue()) {
            Log.d(TAG, "Already have SDK control.");
            isSdkControlled.postValue(true);
             handleControlGained(); // Call gain handler if already controlled
        }
    }

    public static void handleControlGained() {
        Log.d(TAG, "handleControlGained: SDK control acquired.");
        Toast.makeText(context, "Vuzix Glasses Connected", Toast.LENGTH_SHORT).show();
        // Any other setup needed when control is gained can be added here
        // e.g., initializing Gemini model if that's still relevant here or in MainActivity
    }

    public static void handleControlLost() {
        Log.d(TAG, "handleControlLost: SDK control lost.");
        Toast.makeText(context, "Vuzix Glasses Disconnected", Toast.LENGTH_SHORT).show();
        // Any cleanup when control is lost
        clearGlassesDisplay();
    }
    
    public static void displayTextOnGlasses(String text, EditText editTextScrollingSpeed) {
        if (!Boolean.TRUE.equals(isSdkControlled.getValue())) {
            Log.w(TAG, "Cannot display text on glasses: SDK not controlled.");
            Toast.makeText(context, "Glasses not controlled.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (text == null || text.isEmpty()) {
            Log.w(TAG, "Cannot display empty text on glasses.");
            Toast.makeText(context, "No text to display.", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            float speed = 1.0f; 
            if (editTextScrollingSpeed != null) {
                String speedStr = editTextScrollingSpeed.getText().toString();
                if (!speedStr.isEmpty()) {
                    try {
                        speed = Float.parseFloat(speedStr);
                        if (speed <= 0) {
                            speed = 1.0f; 
                            Toast.makeText(context, "Invalid speed, using default.", Toast.LENGTH_SHORT).show();
                        }
                    } catch (NumberFormatException e) {
                        Log.w(TAG, "Invalid speed format, using default: " + speedStr);
                        Toast.makeText(context, "Invalid speed format, using default.", Toast.LENGTH_SHORT).show();
                    }
                }
            }
            
            final int sliceHeight = 48; 
            final int fontSize = 35;    
            final int lowestLineShowing = 0; // For Z100, this might be 0 for bottom line
            final int maxLinesShowing = 3;   // Max lines on Z100 display

            ultraliteSDK.setLayout(Layout.SCROLL, 0, true, true, 0); // Ensure this is the correct layout for Z100
            UltraliteSDK.ScrollingTextView scrollingTextView = ultraliteSDK.getScrollingTextView();
            
            // Configure scrolling parameters. The speed parameter in scrollLayoutConfig might be an int.
            // The boolean for `true` might mean "start scrolling immediately" or similar.
            // The Z100 SDK documentation should be consulted for exact parameters.
            // Example: scrollLayoutConfig(sliceHeight, lowestLine, maxLines, scrollSpeedInt, autoStart)
            int scrollSpeedInt = (int) (speed * 10); // Example conversion, adjust as needed
            scrollingTextView.scrollLayoutConfig(sliceHeight, lowestLineShowing, maxLinesShowing, scrollSpeedInt, true);
            
            chunkStringsToEngine(text, sliceHeight, fontSize, scrollingTextView, maxLinesShowing);

            Log.i(TAG, "Displaying on glasses: '" + text + "' with speed factor: " + speed);
            Toast.makeText(context, "Sending to glasses...", Toast.LENGTH_SHORT).show();

        } catch (Exception e) {
            Log.e(TAG, "Error displaying text on glasses: " + e.getMessage(), e);
            Toast.makeText(context, "Error sending to glasses.", Toast.LENGTH_SHORT).show();
        }
    }

    public static void clearGlassesDisplay() {
        if (Boolean.TRUE.equals(isSdkControlled.getValue()) && ultraliteSDK != null) {
            try {
                 ultraliteSDK.clearDisplay(); // Use the new clearDisplay method
                Log.i(TAG, "Cleared glasses display.");
                Toast.makeText(context, "Display cleared.", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Log.e(TAG, "Error clearing glasses display: " + e.getMessage(), e);
            }
        }
    }
    
    // Added scrollingTextView and maxLinesShowing as parameters
    private static void chunkStringsToEngine(String content, int sliceHeight, int fontSize, UltraliteSDK.ScrollingTextView scrollingTextView, int maxLinesShowing)  {
        // AckWaiter might not be available or needed for Z100 direct scrolling text view.
        // If it is, ensure it's initialized correctly:
        // AckWaiter ackWaiter = new AckWaiter(ultraliteSDK); 
        
        TextToImageSlicer slicer = new TextToImageSlicer(content, sliceHeight, fontSize);
        int i = 0;
        
        // Fill initial screen without scrolling
        while (slicer.hasMoreSlices() && (i < maxLinesShowing)) {
            final boolean scrollFirst = false;
            // For Z100, sliceIndexNumber might need to be adjusted based on how lines are indexed
            // Assuming 0 is bottom, maxLinesShowing - 1 is top, or vice-versa.
            // Let's assume 0 is the first line to appear at the bottom, then moves up.
            final int sliceIndexNumber = i; // This might need to be (maxLinesShowing - 1 - i) depending on indexing.
            scrollingTextView.sendScrollImage(slicer.getNextSlice(), sliceIndexNumber, scrollFirst);
            // if (ackWaiter != null) ackWaiter.waitForAck("Send line of text as image");
            i++;
        }
        
        // Scroll remaining slices
        while (slicer.hasMoreSlices()) {
            // Pausing might be handled by scroll speed or internally by SDK.
            // If explicit pause is needed: try { Thread.sleep(2000); } catch (InterruptedException e) {}
            final boolean scrollFirst = true; // Enable scrolling for subsequent lines
            final int bottomSliceIndex = 0; // New lines are typically added to the bottom and scroll up
            scrollingTextView.sendScrollImage(slicer.getNextSlice(), bottomSliceIndex, scrollFirst);
            // if (ackWaiter != null) ackWaiter.waitForAck("Send scrolled line");
        }
    }

    public static void releaseControl() {
        if (ultraliteSDK != null && ultraliteSDK.getControlledByMe() != null && ultraliteSDK.getControlledByMe().getValue()) {
            Log.d(TAG, "Releasing SDK control.");
            ultraliteSDK.releaseControl();
        }
    }

    public static void addEventListener() {
        if (ultraliteSDK != null) {
            ultraliteSDK.addEventListener(eventListener);
        }
    }

    public static void removeEventListener() {
         if (ultraliteSDK != null) {
            ultraliteSDK.removeEventListener(eventListener);
        }
    }
}
