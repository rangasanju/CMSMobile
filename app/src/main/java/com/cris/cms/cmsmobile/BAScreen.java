package com.cris.cms.cmsmobile;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.hardware.Camera;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.FrameLayout;
import com.felhr.utils.HexData;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Set;

public class BAScreen extends AppCompatActivity {

    /*
     * Notifications from UsbService will be received here.
     */
    private final BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            switch (intent.getAction()) {
                case UsbService.ACTION_USB_PERMISSION_GRANTED: // USB PERMISSION GRANTED
                    Toast.makeText(context, "USB Ready", Toast.LENGTH_SHORT).show();

                    break;
                case UsbService.ACTION_USB_PERMISSION_NOT_GRANTED: // USB PERMISSION NOT GRANTED
                    Toast.makeText(context, "USB Permission not granted", Toast.LENGTH_SHORT).show();
                    break;
                case UsbService.ACTION_NO_USB: // NO USB CONNECTED
                    Toast.makeText(context, "No USB connected", Toast.LENGTH_SHORT).show();
                    break;
                case UsbService.ACTION_USB_DISCONNECTED: // USB DISCONNECTED
                    Toast.makeText(context, "USB disconnected", Toast.LENGTH_SHORT).show();
                    break;
                case UsbService.ACTION_USB_NOT_SUPPORTED: // USB NOT SUPPORTED
                    Toast.makeText(context, "USB device not supported", Toast.LENGTH_SHORT).show();
                    break;
            }
        }
    };





    private UsbService usbService;
    private TextView startBAText;
    private MyHandler mHandler;
    private Button mButtonStartBA;
    private Camera mCamera;
    private CameraPreview mPreview;

    private static String encodedImage;

    private final ServiceConnection usbConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName arg0, IBinder arg1) {
            usbService = ((UsbService.UsbBinder) arg1).getService();
            usbService.setHandler(mHandler);

        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            usbService = null;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.balayout);

        mHandler = new MyHandler(this);

        mButtonStartBA = (Button) findViewById(R.id.startBA);
        startBAText = (TextView) findViewById(R.id.baTextView) ;


        // Create an instance of Camera
        mCamera = getCameraInstance();

        // Create our Preview view and set it as the content of our activity.
        mPreview = new CameraPreview(this, mCamera);
        FrameLayout preview = (FrameLayout) findViewById(R.id.camera_preview);
        preview.addView(mPreview);






        mButtonStartBA.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {


                if(mButtonStartBA.getText().equals("Continue"))
                {

                    //startBAText.append(" image64:" + encodedImage);
                    //startBAText.append(" :image64ends");
                    String badata = startBAText.getText().toString();

                    Intent goingBack = new Intent();
                    goingBack.putExtra("badata",badata);
                    setResult(RESULT_OK,goingBack);
                    finish();
                }
                else
                {
                    byte[] d = new byte[] { (byte)0x80,  (byte)0xAA,};
                    usbService.write(d);
                }

            }
        });





    }

    @Override
    public void onResume() {
        super.onResume();
        setFilters();  // Start listening notifications from UsbService
        startService(UsbService.class, usbConnection, null); // Start UsbService(if it was not started before) and Bind it
    }

    @Override
    public void onPause() {
        super.onPause();
        unregisterReceiver(mUsbReceiver);
        unbindService(usbConnection);
    }


    private void startService(Class<?> service, ServiceConnection serviceConnection, Bundle extras) {
        if (!UsbService.SERVICE_CONNECTED) {
            Intent startService = new Intent(this, service);
            if (extras != null && !extras.isEmpty()) {
                Set<String> keys = extras.keySet();
                for (String key : keys) {
                    String extra = extras.getString(key);
                    startService.putExtra(key, extra);
                }
            }
            startService(startService);
        }
        Intent bindingIntent = new Intent(this, service);
        bindService(bindingIntent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    private void setFilters() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(UsbService.ACTION_USB_PERMISSION_GRANTED);
        filter.addAction(UsbService.ACTION_NO_USB);
        filter.addAction(UsbService.ACTION_USB_DISCONNECTED);
        filter.addAction(UsbService.ACTION_USB_NOT_SUPPORTED);
        filter.addAction(UsbService.ACTION_USB_PERMISSION_NOT_GRANTED);
        registerReceiver(mUsbReceiver, filter);
    }

    /*
     * This handler will be passed to UsbService. Data received from serial port is displayed through this handler
     */
    private static class MyHandler extends Handler {
        private final WeakReference<BAScreen> mActivity;
        private boolean writeflag = false;
        private boolean analyze_flag = false;
        private boolean clicked = false;
        public MyHandler(BAScreen activity) {
            mActivity = new WeakReference<>(activity);
        }

        @Override
        public void handleMessage(Message msg) {


            switch (msg.what) {

                case UsbService.MESSAGE_FROM_SERIAL_PORT:
                    byte[] hexdata=  ((String) msg.obj).getBytes();
                    String data = (String) msg.obj;

                    switch(byteArrayToHex(hexdata))
                    {

                        case  "efbfbdefbfbd" :
                            mActivity.get().startBAText.setText("Turning On");
                            break;
                        case "efbfbd01" :
                            if(analyze_flag)
                                mActivity.get().startBAText.setText("Analyzing ... ");
                            else
                                mActivity.get().startBAText.setText("Please Start Blowing");
                            break;
                        case "efbfbd55" :
                            analyze_flag = true;
                            mActivity.get().startBAText.setText("Blowing ... ");
                            if(!clicked)
                            {
                                mActivity.get().mCamera.takePicture(null, null, mActivity.get().mPicture);
                                clicked = true;
                            }
                            break;
                        case "efbfbd00" :
                            analyze_flag = false;
                            mActivity.get().startBAText.setText("Blowing Failure");
                            break;

                    }


                    if(data.contains("TAYAL") || data.contains("KATS"))
                    {
                        writeflag = true;
                        mActivity.get().mButtonStartBA.setText("Continue");
                        mActivity.get().startBAText.setText("");
                    }

                    if(writeflag)
                    {
                        mActivity.get().startBAText.append(data);
                        if(data.contains("Exhale time"))
                        {
                            writeflag = false; //mActivity.get().startBAText.append(" image64:" + encodedImage);
                        }

                    }



                    //mActivity.get().startBAText.append(HexData.hexToString(hexdata));
                    break;
            }
        }
    }

    public static String byteArrayToHex(byte[] a) {
        StringBuilder sb = new StringBuilder(a.length * 2);
        for(byte b: a)
            sb.append(String.format("%02x", b & 0xff));
        return sb.toString();
    }



    /** A safe way to get an instance of the Camera object. */
    public static Camera getCameraInstance(){
        Camera c = null;
        try {
            c = Camera.open(Camera.CameraInfo.CAMERA_FACING_FRONT); // attempt to get a Camera instance

            c.getParameters().setPictureSize(320,240);
        }
        catch (Exception e){
            Log.d("CMS App :  ", "Error creating camera "  + e);
            // Camera is not available (in use or does not exist)
        }
        return c; // returns null if camera is unavailable
    }



    private Camera.PictureCallback mPicture = new Camera.PictureCallback() {

        @Override
        public void onPictureTaken(byte[] data, Camera camera) {

            File pictureFile = getOutputMediaFile(MEDIA_TYPE_IMAGE);
            if (pictureFile == null){
                Log.d("CMS App :  ", "Error creating media file, check storage permissions: " );
                return;
            }

            try {
                FileOutputStream fos = new FileOutputStream(pictureFile);
                fos.write(data);
                fos.close();

                // Convert the image into Base64 String (for transmitting the image back to CMS Central Server)
                encodedImage = Base64.encodeToString(data, Base64.DEFAULT);


            } catch (FileNotFoundException e) {
                Log.d("CMS App :  ", "File not found: " + e.getMessage());
            } catch (IOException e) {
                Log.d("CMS App :  ", "Error accessing file: " + e.getMessage());
            }
        }
    };



    public static final int MEDIA_TYPE_IMAGE = 1;
    public static final int MEDIA_TYPE_VIDEO = 2;

    /** Create a file Uri for saving an image or video */
    private static Uri getOutputMediaFileUri(int type){
        return Uri.fromFile(getOutputMediaFile(type));
    }



    private static File getOutputMediaFile(int type){
        // To be safe, you should check that the SDCard is mounted
        // using Environment.getExternalStorageState() before doing this.

        File mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES), "MyCameraApp");
        // This location works best if you want the created images to be shared
        // between applications and persist after your app has been uninstalled.

        // Create the storage directory if it does not exist
        if (! mediaStorageDir.exists()){
            if (! mediaStorageDir.mkdirs()){
                Log.d("MyCameraApp", "failed to create directory");
                return null;
            }
        }

        // Create a media file name
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        File mediaFile;
        if (type == MEDIA_TYPE_IMAGE){
            mediaFile = new File(mediaStorageDir.getPath() + File.separator +
                    "IMG_"+ timeStamp + ".jpg");
        } else if(type == MEDIA_TYPE_VIDEO) {
            mediaFile = new File(mediaStorageDir.getPath() + File.separator +
                    "VID_"+ timeStamp + ".mp4");
        } else {
            return null;
        }

        return mediaFile;
    }

}