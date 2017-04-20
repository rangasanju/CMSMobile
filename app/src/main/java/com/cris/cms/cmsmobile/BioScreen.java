package com.cris.cms.cmsmobile;


import java.io.*;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.BroadcastReceiver;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.NotificationCompatSideChannelService;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.widget.Spinner;
import android.widget.Toast;

import SecuGen.FDxSDKPro.*;

public class BioScreen extends Activity
        implements View.OnClickListener, Runnable, SGFingerPresentEvent {

    private static final String TAG = "SecuGen USB";

    private String crewid;
    private String registerflag="false";
    private String match = "nomatch";
    private Button mButtonRegister;
    private Button mButtonMatch;
    //private Button mButtonOK;
    private Spinner fingers;
    private String selectedFinger;
    private String doneFinger="NONE";
    private int verify_count = 0;

    private String registered_finger_1;
    private String registered_finger_2;

    //private android.widget.TextView mTextViewResult;
    //private android.widget.CheckBox mCheckBoxMatched;
    private EditText mEditLog;
    private PendingIntent mPermissionIntent;
    private ImageView mImageViewFinger1;
    private ImageView mImageViewFinger2;
    private ImageView mImageViewLeft;
    private ImageView mImageViewRight;


    //private ImageView mImageViewVerify;
    private byte[] mRegisterImage;
    private byte[] mVerifyImage;
    private byte[] mRegisterTemplate;
    private byte[] mRegisterTemplate2;  // TO STORE THE SECOND FINGER FROM DB
    private byte[] mVerifyTemplate;
    private int[] mMaxTemplateSize;
    private int mImageWidth;
    private int mImageHeight;
    private int[] grayBuffer;
    private Bitmap grayBitmap;
    private IntentFilter filter; //2014-04-11
    private SGAutoOnEventNotifier autoOn;
    private boolean mLed;
    private boolean mAutoOnEnabled;
    private JSGFPLib sgfplib;

    private void debugMessage(String message) {
        //this.mEditLog.append(message);
        //this.mEditLog.invalidate(); //TODO trying to get Edit log to update after each line written
        Log.d("INFO : " , message);
    }


    //RILEY
    //This broadcast receiver is necessary to get user permissions to access the attached USB device
    private static final String ACTION_USB_PERMISSION = "com.android.example.USB_PERMISSION";
    private final BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            //DEBUG Log.d(TAG,"Enter mUsbReceiver.onReceive()");
            if (ACTION_USB_PERMISSION.equals(action)) {
                synchronized (this) {
                    UsbDevice device = (UsbDevice)intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        if(device != null){
                            //DEBUG Log.d(TAG, "Vendor ID : " + device.getVendorId() + "\n");
                            //DEBUG Log.d(TAG, "Product ID: " + device.getProductId() + "\n");
                            debugMessage("Vendor ID : " + device.getVendorId() + "\n");
                            debugMessage("Product ID: " + device.getProductId() + "\n");
                        }
                        else
                            Log.e(TAG, "mUsbReceiver.onReceive() Device is null");
                    }
                    else
                        Log.e(TAG, "mUsbReceiver.onReceive() permission denied for device " + device);
                }
            }
        }
    };




    //RILEY
    //This message handler is used to access local resources not
    //accessible by SGFingerPresentCallback() because it is called by
    //a separate thread.
    public Handler fingerDetectedHandler = new Handler(){
        // @Override
        public void handleMessage(Message msg) {
            //Handle the message

            if(registerflag.equals("true"))
                CaptureFingerPrintForRegistration();
            else
                CaptureFingerPrintForVerification();


        }
    };



    public void SGFingerPresentCallback (){
        autoOn.stop();
        //EnableControls();
        sgfplib.SetLedOn(false);
        fingerDetectedHandler.sendMessage(new Message());


    }

    public long CaptureFingerPrintForRegistration(){


        if (mRegisterImage != null)
            mRegisterImage = null;
        mRegisterImage = new byte[mImageWidth*mImageHeight];

        long timeout = 10000;
        long qual = 60;
        // Capture Image For Registration
        long code = sgfplib.GetImageEx(mRegisterImage,timeout,qual);

        // Set image in the Big Box


        //mImageViewFinger1.setImageBitmap(grayBitmap);
        //mImageViewFinger2.setImageBitmap(grayBitmap);

        if(doneFinger.equals("NONE"))
        {
            mImageViewFinger1.setImageBitmap(this.toGrayscale(mRegisterImage));
            mImageViewFinger1.setScaleType(ImageView.ScaleType.FIT_CENTER);
            doneFinger = selectedFinger;
            //mTextViewResult.setText("One Finger Registered Successfully");
            ShowAlert("One Finger Registered Successfully. Please select another finger and click on Register");
        }
        else
        {
            mImageViewFinger2.setImageBitmap(this.toGrayscale(mRegisterImage));
            mImageViewFinger2.setScaleType(ImageView.ScaleType.FIT_CENTER);
            mButtonRegister.setText("OK");
        }







        // CONVERT TO TEMPLATE
        long result = sgfplib.SetTemplateFormat(SGFDxTemplateFormat.TEMPLATE_FORMAT_SG400);
        SGFingerInfo fpInfo = new SGFingerInfo();
        for (int i=0; i< mRegisterTemplate.length; ++i)
            mRegisterTemplate[i] = 0;

        long templateresult = sgfplib.CreateTemplate(fpInfo, mRegisterImage, mRegisterTemplate);

        // Insert into DB
        insertUser(selectedFinger,mRegisterTemplate);


        //mTextViewResult.setText("getImageEx(10000,60) ret: " + code + " [ " + doneFinger + "] \n");


        return result;
    }


    public long CaptureFingerPrintForVerification(){


        verify_count++;

        if (mVerifyImage != null)
            mVerifyImage = null;
        mVerifyImage = new byte[mImageWidth*mImageHeight];

        long timeout = 10000;
        long qual = 60;
        // Capture Image For Registration
        long code = sgfplib.GetImageEx(mVerifyImage,timeout,qual);

        // Set image in the ImageView

        mImageViewFinger1.setImageBitmap(this.toGrayscale(mVerifyImage));
        mImageViewFinger1.setScaleType(ImageView.ScaleType.FIT_CENTER);


        // CONVERT TO TEMPLATE

        long result = sgfplib.SetTemplateFormat(SGFDxTemplateFormat.TEMPLATE_FORMAT_SG400);

        SGFingerInfo fpInfo = new SGFingerInfo();
        for (int i=0; i< mVerifyTemplate.length; ++i)
            mVerifyTemplate[i] = 0;

        result = sgfplib.CreateTemplate(fpInfo, mVerifyImage, mVerifyTemplate);



        // MATCHING PROCESS
        boolean[] matched = new boolean[1];

        result = sgfplib.MatchTemplate(mRegisterTemplate, mVerifyTemplate, SGFDxSecurityLevel.SL_NORMAL, matched);

        if (matched[0]) {
            //mTextViewResult.setText("MATCHED!!\n");
            //this.mCheckBoxMatched.setChecked(true);
            debugMessage("MATCHED!!\n");
            match = "<<MATCH>>";
            ShowAlert("Verification Successfull");
            mButtonMatch.setText("Continue");



        }
        else {

            result = sgfplib.MatchTemplate(mRegisterTemplate2, mVerifyTemplate, SGFDxSecurityLevel.SL_NORMAL, matched);
            if (matched[0]) {
                //mTextViewResult.setText("MATCHED!!\n");
                //this.mCheckBoxMatched.setChecked(true);
                debugMessage("MATCHED!!\n");
                match = "<<MATCH>>";
                ShowAlert("Verification Successfull");
                mButtonMatch.setText("Continue");

            }
            else {
                //mTextViewResult.setText("NOT MATCHED!!");
                //this.mCheckBoxMatched.setChecked(false);
                debugMessage("NOT MATCHED!!\n");
                match = "<<NO MATCH>>";
                ShowAlert("Verification Failed");

            }

        }

        mVerifyImage = null;
        fpInfo = null;
        matched = null;




        return result;
    }







    //RILEY
    @Override
    public void onCreate(Bundle savedInstanceState) {



            super.onCreate(savedInstanceState);


        AlertDialog.Builder dlgAlert = new AlertDialog.Builder(this);
        dlgAlert.setMessage("onCreate1");
            Intent mainactivity = getIntent();
            crewid = mainactivity.getExtras().getString("crewid");

            verify_count =  0;
            //mRegisterTemplate = getUser();
            getUser();



            // Toast.makeText(this, register, Toast.LENGTH_SHORT).show();

            // set the layout to biolayout or biolayoutregister accordingly
            if(registerflag.equals("true"))
                setContentView(R.layout.registrationlayout);
            else
                setContentView(R.layout.verificationlayout);


            // HANDS VIEW
            mImageViewLeft = (ImageView) findViewById(R.id.imageViewLeft);
            mImageViewRight = (ImageView) findViewById(R.id.imageViewRight);
            mImageViewLeft.setImageResource(R.drawable.l);
            mImageViewRight.setImageResource(R.drawable.r);

            mImageViewFinger1 = (ImageView)findViewById(R.id.imageViewFinger1);

            if(registerflag.equals("true"))
            {
                mButtonRegister = (Button)findViewById(R.id.buttonRegister);
                mButtonRegister.setOnClickListener(this);



                mImageViewFinger2 = (ImageView)findViewById(R.id.imageViewFinger2);
                addItemsToSpinner();
                addListenerToFingerSpinner();
                ShowAlert("This process will register two fingers. Please select a finger and click on Register button ");

            }else
            {
                mButtonMatch = (Button)findViewById(R.id.buttonMatch);

                mButtonMatch.setOnClickListener(this);


                switch (registered_finger_1)
                {
                    case "L1" :
                        mImageViewLeft.setImageResource(R.drawable.l1);
                        break;
                    case "L2" :
                        mImageViewLeft.setImageResource(R.drawable.l2);
                        break;
                    case "L3" :
                        mImageViewLeft.setImageResource(R.drawable.l3);
                        break;
                    case "L4" :
                        mImageViewLeft.setImageResource(R.drawable.l4);
                        break;
                    case "L5" :
                        mImageViewLeft.setImageResource(R.drawable.l5);
                        break;


                }

                switch (registered_finger_2)
                {

                    case "R1" :
                        mImageViewRight.setImageResource(R.drawable.r1);
                        break;
                    case "R2" :
                        mImageViewRight.setImageResource(R.drawable.r2);
                        break;
                    case "R3" :
                        mImageViewRight.setImageResource(R.drawable.r3);
                        break;
                    case "R4" :
                        mImageViewRight.setImageResource(R.drawable.r4);
                        break;
                    case "R5" :
                        mImageViewRight.setImageResource(R.drawable.r5);
                        break;

                }


            }

            //mTextViewResult = (android.widget.TextView)findViewById(R.id.textViewResult);

            //mImageViewVerify = (ImageView)findViewById(R.id.imageViewVerify);               // small verify box

            grayBuffer = new int[JSGFPLib.MAX_IMAGE_WIDTH_ALL_DEVICES*JSGFPLib.MAX_IMAGE_HEIGHT_ALL_DEVICES];
            for (int i=0; i<grayBuffer.length; ++i)
                grayBuffer[i] = Color.GRAY;
            grayBitmap = Bitmap.createBitmap(JSGFPLib.MAX_IMAGE_WIDTH_ALL_DEVICES, JSGFPLib.MAX_IMAGE_HEIGHT_ALL_DEVICES, Bitmap.Config.ARGB_8888);
            grayBitmap.setPixels(grayBuffer, 0, JSGFPLib.MAX_IMAGE_WIDTH_ALL_DEVICES, 0, 0, JSGFPLib.MAX_IMAGE_WIDTH_ALL_DEVICES, JSGFPLib.MAX_IMAGE_HEIGHT_ALL_DEVICES);


            int[] sintbuffer = new int[(JSGFPLib.MAX_IMAGE_WIDTH_ALL_DEVICES/2)*(JSGFPLib.MAX_IMAGE_HEIGHT_ALL_DEVICES/2)];
            for (int i=0; i<sintbuffer.length; ++i)
                sintbuffer[i] = Color.GRAY;
            Bitmap sb = Bitmap.createBitmap(JSGFPLib.MAX_IMAGE_WIDTH_ALL_DEVICES/2, JSGFPLib.MAX_IMAGE_HEIGHT_ALL_DEVICES/2, Bitmap.Config.ARGB_8888);
            sb.setPixels(sintbuffer, 0, JSGFPLib.MAX_IMAGE_WIDTH_ALL_DEVICES/2, 0, 0, JSGFPLib.MAX_IMAGE_WIDTH_ALL_DEVICES/2, JSGFPLib.MAX_IMAGE_HEIGHT_ALL_DEVICES/2);



            //mImageViewVerify.setImageBitmap(grayBitmap);

            mMaxTemplateSize = new int[1];

        dlgAlert.setMessage("onCreate2");
            //USB Permissions
            mPermissionIntent = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION), 0);
            filter = new IntentFilter(ACTION_USB_PERMISSION);
        dlgAlert.setMessage("onCreate3");
            registerReceiver(mUsbReceiver, filter);
            sgfplib = new JSGFPLib((UsbManager)getSystemService(Context.USB_SERVICE));
        dlgAlert.setMessage("onCreate4");

            debugMessage("jnisgfplib version: " + sgfplib.Version() + "\n");
            mLed = true;
            mAutoOnEnabled = true;
            autoOn = new SGAutoOnEventNotifier (sgfplib, this);







    }





    public void onClick(View v) {
        //long dwTimeStart = 0, dwTimeEnd = 0, dwTimeElapsed = 0;
        //mTextViewResult.setText("Clicked");

        if (v == this.mButtonRegister) {

            if(mButtonRegister.getText().equals("OK"))
            {

                mRegisterImage = null;
                match = "<<MATCH>>";
                Intent goingBack = new Intent();
                goingBack.putExtra("match",match);
                setResult(RESULT_OK,goingBack);
                finish();
            }
            else
            {
                if(doneFinger == selectedFinger)
                {
                    //mTextViewResult.setText(selectedFinger + " finger is already registered. Please selecet another finger");
                    ShowAlert(selectedFinger + " finger is already registered. Please select another finger");
                }
                else
                {
                    //DEBUG Log.d(TAG, "Clicked REGISTER");
                    Toast.makeText(this, "here", Toast.LENGTH_SHORT).show();
                    debugMessage("Clicked REGISTER\n");


                    sgfplib.SetLedOn(true);
                    autoOn.start();



                }

            }




        }
        if (v == this.mButtonMatch) {


            if(mButtonMatch.getText().equals("Continue"))
            {
                Intent goingBack = new Intent();
                goingBack.putExtra("match",match);
                setResult(RESULT_OK,goingBack);
                finish();
            }
            else if(verify_count > 2)
            {
                ShowAlertFinish("You have made 3 unsuccessfull attempt. Please Login again");
            }
            else
            {
                sgfplib.SetLedOn(true);
                autoOn.start();
            }

        }

/*
        if (v == this.mButtonOK) {
            //DEBUG Log.d(TAG, "Clicked MATCH");
            debugMessage("Clicked OK\n");


            Intent goingBack = new Intent();
            goingBack.putExtra("match",match);
            setResult(RESULT_OK,goingBack);
            finish();

        }*/


    }



    public void ShowAlert(String msg)
    {
        AlertDialog.Builder dlgAlert = new AlertDialog.Builder(this);
        dlgAlert.setMessage(msg);
        dlgAlert.setTitle("CMS App");
        dlgAlert.setPositiveButton("OK",
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog,int whichButton){
                        //finish();
                        return;
                    }
                }
        );
        dlgAlert.setCancelable(false);
        dlgAlert.create().show();
    }


    public void ShowAlertFinish(String msg)
    {
        AlertDialog.Builder dlgAlert = new AlertDialog.Builder(this);
        dlgAlert.setMessage(msg);
        dlgAlert.setTitle("CMS App");
        dlgAlert.setPositiveButton("OK",
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog,int whichButton){
                        Intent goingBack = new Intent();
                        goingBack.putExtra("match",match);
                        setResult(RESULT_OK,goingBack);
                        finish();
                        return;
                    }
                }
        );
        dlgAlert.setCancelable(false);
        dlgAlert.create().show();
    }

    public void EnableControls(){

        if(registerflag.equals("true"))
        {
            this.mButtonRegister.setClickable(true);
            this.mButtonRegister.setTextColor(getResources().getColor(android.R.color.white));
        }
        else
        {

            this.mButtonMatch.setClickable(true);
            this.mButtonMatch.setTextColor(getResources().getColor(android.R.color.white));
        }



    }

    public void DisableControls(){

        if(registerflag.equals("true"))
        {
            this.mButtonRegister.setClickable(false);
            this.mButtonRegister.setTextColor(getResources().getColor(android.R.color.black));

        }
        else
        {

            this.mButtonMatch.setClickable(false);
            this.mButtonMatch.setTextColor(getResources().getColor(android.R.color.black));
        }



    }



    @Override
    public void onPause() {
        Log.d(TAG, "onPause()");
        autoOn.stop();
        sgfplib.SetLedOn(false);
        EnableControls();
        sgfplib.CloseDevice();
        unregisterReceiver(mUsbReceiver);
        mRegisterImage = null;
        mVerifyImage = null;
        mRegisterTemplate = null;
        mRegisterTemplate2 = null;
        mVerifyTemplate = null;
       //
        //
        //
        // mImageViewFingerprint.setImageBitmap(grayBitmap);

        if(registerflag.equals("true"))
        {
            mImageViewFinger1.setImageBitmap(grayBitmap);
            mImageViewFinger2.setImageBitmap(grayBitmap);
        }

        //mImageViewVerify.setImageBitmap(grayBitmap);
        super.onPause();
    }

    @Override
    public void onResume(){

        super.onResume();

        Log.d(TAG, "onResume()");
        AlertDialog.Builder dlgAlert = new AlertDialog.Builder(this);
        dlgAlert.setMessage("onResume1");

       registerReceiver(mUsbReceiver, filter);
        dlgAlert.setMessage("onResume2");
        long error = sgfplib.Init( SGFDxDeviceName.SG_DEV_AUTO);
        dlgAlert.setMessage("onResume3");
        if (error != SGFDxErrorCode.SGFDX_ERROR_NONE){

            if (error == SGFDxErrorCode.SGFDX_ERROR_DEVICE_NOT_FOUND)
                dlgAlert.setMessage("The attached fingerprint device is not supported on Android");
            else
                dlgAlert.setMessage("Fingerprint device initialization failed!");
            dlgAlert.setTitle("SecuGen Fingerprint SDK");
            dlgAlert.setPositiveButton("OK",
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog,int whichButton){
                            finish();
                            return;
                        }
                    }
            );
            dlgAlert.setCancelable(false);
            dlgAlert.create().show();
        }
        else {
            UsbDevice usbDevice = sgfplib.GetUsbDevice();
            if (usbDevice == null){

                dlgAlert.setMessage("SDU04P or SDU03P fingerprint sensor not found!");
                dlgAlert.setTitle("SecuGen Fingerprint SDK");
                dlgAlert.setPositiveButton("OK",
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog,int whichButton){
                                finish();
                                return;
                            }
                        }
                );
                dlgAlert.setCancelable(false);
                dlgAlert.create().show();
            }
            else {
                dlgAlert.setMessage("onResume4");
                sgfplib.GetUsbManager().requestPermission(usbDevice, mPermissionIntent);
                dlgAlert.setMessage("onResume4");
                error = sgfplib.OpenDevice(0);
                debugMessage("OpenDevice() ret: " + error + "\n");
                SGDeviceInfoParam deviceInfo = new SGDeviceInfoParam();
                error = sgfplib.GetDeviceInfo(deviceInfo);
                debugMessage("GetDeviceInfo() ret: " + error + "\n");
                mImageWidth = deviceInfo.imageWidth;
                mImageHeight= deviceInfo.imageHeight;
                debugMessage("Image width: " + mImageWidth + "\n");
                debugMessage("Image height: " + mImageHeight + "\n");
                debugMessage("Serial Number: " + new String(deviceInfo.deviceSN()) + "\n");
                sgfplib.SetTemplateFormat(SGFDxTemplateFormat.TEMPLATE_FORMAT_SG400);
                sgfplib.GetMaxTemplateSize(mMaxTemplateSize);
                debugMessage("TEMPLATE_FORMAT_SG400 SIZE: " + mMaxTemplateSize[0] + "\n");

                if(registerflag.equals("true"))
                {
                    mRegisterTemplate = new byte[mMaxTemplateSize[0]];
                }


                mVerifyTemplate = new byte[mMaxTemplateSize[0]];
                if (mAutoOnEnabled){
                    autoOn.start();
                    //DisableControls();
                }
                //Thread thread = new Thread(this);
                //thread.start();
            }
        }

    }



    public void addItemsToSpinner()
    {
        fingers = (Spinner) findViewById(R.id.spinner);

        ArrayAdapter<CharSequence> fingerSpinnerAdapter = ArrayAdapter.createFromResource(this,R.array.fingers,android.R.layout.simple_spinner_item);
        fingerSpinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_item);
        fingers.setAdapter(fingerSpinnerAdapter);

    }

    public void addListenerToFingerSpinner()
    {

        fingers = (Spinner) findViewById(R.id.spinner);
        fingers.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedFinger = parent.getItemAtPosition(position).toString();

                mImageViewLeft.setImageResource(R.drawable.l);
                mImageViewRight.setImageResource(R.drawable.r);

                switch (selectedFinger)
                {
                    case "L1" :
                        mImageViewLeft.setImageResource(R.drawable.l1);
                        break;
                    case "L2" :
                        mImageViewLeft.setImageResource(R.drawable.l2);
                        break;
                    case "L3" :
                        mImageViewLeft.setImageResource(R.drawable.l3);
                        break;
                    case "L4" :
                        mImageViewLeft.setImageResource(R.drawable.l4);
                        break;
                    case "L5" :
                        mImageViewLeft.setImageResource(R.drawable.l5);
                        break;
                    case "R1" :
                        mImageViewRight.setImageResource(R.drawable.r1);
                        break;
                    case "R2" :
                        mImageViewRight.setImageResource(R.drawable.r2);
                        break;
                    case "R3" :
                        mImageViewRight.setImageResource(R.drawable.r3);
                        break;
                    case "R4" :
                        mImageViewRight.setImageResource(R.drawable.r4);
                        break;
                    case "R5" :
                        mImageViewRight.setImageResource(R.drawable.r5);
                        break;

                }


            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });

    }





    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy()");
        sgfplib.CloseDevice();
        mRegisterImage = null;
        mVerifyImage = null;
        mRegisterTemplate = null;
        mRegisterTemplate2 = null;
        mVerifyTemplate = null;

        sgfplib.Close();
        super.onDestroy();
    }

    //Converts image to grayscale (NEW)
    public Bitmap toGrayscale(byte[] mImageBuffer, int width, int height)
    {
        byte[] Bits = new byte[mImageBuffer.length * 4];
        for (int i = 0; i < mImageBuffer.length; i++) {
            Bits[i * 4] = Bits[i * 4 + 1] = Bits[i * 4 + 2] = mImageBuffer[i]; // Invert the source bits
            Bits[i * 4 + 3] = -1;// 0xff, that's the alpha.
        }

        Bitmap bmpGrayscale = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        //Bitmap bm contains the fingerprint img
        bmpGrayscale.copyPixelsFromBuffer(ByteBuffer.wrap(Bits));
        return bmpGrayscale;
    }


    //Converts image to grayscale (NEW)
    public Bitmap toGrayscale(byte[] mImageBuffer)
    {
        byte[] Bits = new byte[mImageBuffer.length * 4];
        for (int i = 0; i < mImageBuffer.length; i++) {
            Bits[i * 4] = Bits[i * 4 + 1] = Bits[i * 4 + 2] = mImageBuffer[i]; // Invert the source bits
            Bits[i * 4 + 3] = -1;// 0xff, that's the alpha.
        }

        Bitmap bmpGrayscale = Bitmap.createBitmap(mImageWidth, mImageHeight, Bitmap.Config.ARGB_8888);
        //Bitmap bm contains the fingerprint img
        bmpGrayscale.copyPixelsFromBuffer(ByteBuffer.wrap(Bits));
        return bmpGrayscale;
    }


    //Converts image to grayscale (NEW)
    public Bitmap toGrayscale(Bitmap bmpOriginal)
    {
        int width, height;
        height = bmpOriginal.getHeight();
        width = bmpOriginal.getWidth();
        Bitmap bmpGrayscale = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        for (int y=0; y< height; ++y) {
            for (int x=0; x< width; ++x){
                int color = bmpOriginal.getPixel(x, y);
                int r = (color >> 16) & 0xFF;
                int g = (color >> 8) & 0xFF;
                int b = color & 0xFF;
                int gray = (r+g+b)/3;
                color = Color.rgb(gray, gray, gray);
                //color = Color.rgb(r/3, g/3, b/3);
                bmpGrayscale.setPixel(x, y, color);
            }
        }
        return bmpGrayscale;
    }

    //Converts image to binary (OLD)
    public Bitmap toBinary(Bitmap bmpOriginal)
    {
        int width, height;
        height = bmpOriginal.getHeight();
        width = bmpOriginal.getWidth();
        Bitmap bmpGrayscale = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);
        Canvas c = new Canvas(bmpGrayscale);
        Paint paint = new Paint();
        ColorMatrix cm = new ColorMatrix();
        cm.setSaturation(0);
        ColorMatrixColorFilter f = new ColorMatrixColorFilter(cm);
        paint.setColorFilter(f);
        c.drawBitmap(bmpOriginal, 0, 0, paint);
        return bmpGrayscale;
    }


    public void DumpFile(String fileName, byte[] buffer)
    {
        //Uncomment section below to dump images and templates to SD card
    	/*
        try {
            File myFile = new File("/sdcard/Download/" + fileName);
            myFile.createNewFile();
            FileOutputStream fOut = new FileOutputStream(myFile);
            fOut.write(buffer,0,buffer.length);
            fOut.close();
        } catch (Exception e) {
            debugMessage("Exception when writing file" + fileName);
        }
       */
    }

public void match()
{

    long dwTimeStart = 0, dwTimeEnd = 0, dwTimeElapsed = 0;

    debugMessage("Clicked MATCH\n");
    if (mVerifyImage != null)
        mVerifyImage = null;
    mVerifyImage = new byte[mImageWidth*mImageHeight];
    ByteBuffer byteBuf = ByteBuffer.allocate(mImageWidth*mImageHeight);
    dwTimeStart = System.currentTimeMillis();
    long result = sgfplib.GetImage(mVerifyImage);
    //DumpFile("verify.raw", mVerifyImage);
    dwTimeEnd = System.currentTimeMillis();
    dwTimeElapsed = dwTimeEnd-dwTimeStart;
    debugMessage("GetImage() ret:" + result + " [" + dwTimeElapsed + "ms]\n");
    //mImageViewFingerprint.setImageBitmap(this.toGrayscale(mVerifyImage));
    //mImageViewVerify.setImageBitmap(this.toGrayscale(mVerifyImage));
    dwTimeStart = System.currentTimeMillis();
    result = sgfplib.SetTemplateFormat(SGFDxTemplateFormat.TEMPLATE_FORMAT_SG400);
    dwTimeEnd = System.currentTimeMillis();
    dwTimeElapsed = dwTimeEnd-dwTimeStart;
    debugMessage("SetTemplateFormat(SG400) ret:" +  result + " [" + dwTimeElapsed + "ms]\n");
    SGFingerInfo fpInfo = new SGFingerInfo();
    for (int i=0; i< mVerifyTemplate.length; ++i)
        mVerifyTemplate[i] = 0;
    dwTimeStart = System.currentTimeMillis();
    result = sgfplib.CreateTemplate(fpInfo, mVerifyImage, mVerifyTemplate);
    //DumpFile("verify.min", mVerifyTemplate);
    dwTimeEnd = System.currentTimeMillis();
    dwTimeElapsed = dwTimeEnd-dwTimeStart;
    debugMessage("CreateTemplate() ret:" + result+ " [" + dwTimeElapsed + "ms]\n");
    boolean[] matched = new boolean[1];
    dwTimeStart = System.currentTimeMillis();

    mRegisterTemplate = getUser();
    result = sgfplib.MatchTemplate(mRegisterTemplate, mVerifyTemplate, SGFDxSecurityLevel.SL_NORMAL, matched);
    dwTimeEnd = System.currentTimeMillis();
    dwTimeElapsed = dwTimeEnd-dwTimeStart;
    debugMessage("MatchTemplate() ret:" + result+ " [" + dwTimeElapsed + "ms]\n");
    if (matched[0]) {
        //mTextViewResult.setText("MATCHED!!\n");
        //this.mCheckBoxMatched.setChecked(true);
        debugMessage("MATCHED!!\n");
        match = "<<MATCH>>";

    }
    else {
        //mTextViewResult.setText("NOT MATCHED!!");
        //this.mCheckBoxMatched.setChecked(false);
        debugMessage("NOT MATCHED!!\n");
        match = "<<NO MATCH>>";

    }
    byteBuf = null;
    mVerifyImage = null;
    fpInfo = null;
    matched = null;

}

    public void insertUser(String finger, byte[] template){
        DatabaseHandler db = new DatabaseHandler(this);
        SQLiteDatabase dbs = db.getWritableDatabase();

        // DELETE THE EXISTING FINGER PRINT
        //dbs.delete("FP_Data","crewid=",new String[]{crewid});

        String sql  =   "INSERT INTO FP_Data (crewid,finger,image) VALUES(?,?,?)";
        SQLiteStatement insertStmt      =   dbs.compileStatement(sql);
        insertStmt.clearBindings();
        insertStmt.bindString(1, crewid);
        insertStmt.bindString(2,finger);
        insertStmt.bindBlob(3, template);
        insertStmt.executeInsert();

        dbs.close();
        db.close();
    }

    public byte[] getUser(){

        byte[] storedTemplate = null;
        DatabaseHandler db = new DatabaseHandler(this);
        SQLiteDatabase dbs = db.getWritableDatabase();
        //Toast.makeText(this, crewid, Toast.LENGTH_SHORT).show();

        Log.d("crewid : ", "crewid");
        Log.d("crewid : ", crewid);
        String selectQuery = "SELECT  * FROM FP_Data WHERE crewid='" + crewid + "'";
        Log.d("selectQuery : ", selectQuery);
        Log.d("registerflag : ", registerflag);
        Cursor cursor = dbs.rawQuery(selectQuery, null);

        // looping through all rows and adding to list
        if (cursor.moveToFirst()) {

            registerflag = "false";
            Log.d("registerflag FALSE: ", registerflag);
            //Log.d("ROW",cursor.getString(0));
            mRegisterTemplate =  cursor.getBlob(2);
            registered_finger_1 = cursor.getString(1);

            cursor.moveToNext();
            mRegisterTemplate2 = cursor.getBlob(2);
            registered_finger_2 = cursor.getString(1);
        }
        else
        {
            Log.d("registerflag TRUE: ", registerflag);
            registerflag = "true";
        }
        db.close();
        return storedTemplate;

    }




    private void SDKTest(){
        //mTextViewResult.setText("");
        debugMessage("\n###############\n");
        debugMessage("### SDK Test  ###\n");
        debugMessage("###############\n");

        int X_SIZE = 248;
        int Y_SIZE = 292;

        long error = 0;
        byte[] sgTemplate1;
        byte[] sgTemplate2;
        byte[] sgTemplate3;
        byte[] ansiTemplate1;
        byte[] ansiTemplate2;
        byte[] isoTemplate1;
        byte[] isoTemplate2;
        byte[] ansiTemplate1Windows;
        byte[] ansiTemplate2Windows;
        byte[] ansiTemplate3Windows;

        int[] size = new int[1];
        int[] score = new int[1];
        int[] quality1 = new int[1];
        int[] quality2 = new int[1];
        int[] quality3 = new int[1];
        long nfiq1;
        long nfiq2;
        long nfiq3;
        boolean[] matched = new boolean[1];

        byte[] finger1 = new byte[X_SIZE*Y_SIZE];
        byte[] finger2 = new byte[X_SIZE*Y_SIZE];
        byte[] finger3 = new byte[X_SIZE*Y_SIZE];

        long dwTimeStart = 0, dwTimeEnd = 0, dwTimeElapsed = 0;

        try {
            InputStream fileInputStream =getResources().openRawResource(R.raw.finger_0_10_3);
            error = fileInputStream.read(finger1);
            fileInputStream.close();
        } catch (IOException ex){
            debugMessage("Error: Unable to find fingerprint image R.raw.finger_0_10_3.\n");
            return;
        }
        try {
            InputStream fileInputStream =getResources().openRawResource(R.raw.finger_1_10_3);
            error = fileInputStream.read(finger2);
            fileInputStream.close();
        } catch (IOException ex){
            debugMessage("Error: Unable to find fingerprint image R.raw.finger_1_10_3.\n");
            return;
        }
        try {
            InputStream fileInputStream =getResources().openRawResource(R.raw.finger_2_10_3);
            error = fileInputStream.read(finger3);
            fileInputStream.close();
        } catch (IOException ex){
            debugMessage("Error: Unable to find fingerprint image R.raw.finger_2_10_3.\n");
            return;
        }

        try {
            InputStream fileInputStream =getResources().openRawResource(R.raw.ansi378_0_10_3_windows);
            int length = fileInputStream.available();
            debugMessage("ansi378_0_10_3_windows.ansi378 \n\ttemplate length is: " + length + "\n");
            ansiTemplate1Windows = new byte[length];
            error = fileInputStream.read(ansiTemplate1Windows);
            debugMessage("\tRead: " + error + "bytes\n");
            fileInputStream.close();
        } catch (IOException ex){
            debugMessage("Error: Unable to find fingerprint image R.raw.ansi378_0_10_3_windows.ansi378.\n");
            return;
        }
        try {
            InputStream fileInputStream =getResources().openRawResource(R.raw.ansi378_1_10_3_windows);
            int length = fileInputStream.available();
            debugMessage("ansi378_1_10_3_windows.ansi378 \n\ttemplate length is: " + length + "\n");
            ansiTemplate2Windows = new byte[length];
            error = fileInputStream.read(ansiTemplate2Windows);
            debugMessage("\tRead: " + error + "bytes\n");
            fileInputStream.close();
        } catch (IOException ex){
            debugMessage("Error: Unable to find fingerprint image R.raw.ansi378_1_10_3_windows.ansi378.\n");
            return;
        }
        try {
            InputStream fileInputStream =getResources().openRawResource(R.raw.ansi378_2_10_3_windows);
            int length = fileInputStream.available();
            debugMessage("ansi378_2_10_3_windows.ansi378 \n\ttemplate length is: " + length + "\n");
            ansiTemplate3Windows = new byte[length];
            error = fileInputStream.read(ansiTemplate3Windows);
            debugMessage("\tRead: " + error + "bytes\n");
            fileInputStream.close();
        } catch (IOException ex){
            debugMessage("Error: Unable to find fingerprint image R.raw.ansi378_2_10_3_windows.ansi378.\n");
            return;
        }

        JSGFPLib sgFplibSDKTest = new JSGFPLib((UsbManager)getSystemService(Context.USB_SERVICE));

        error = sgFplibSDKTest.InitEx( X_SIZE, Y_SIZE, 500);
        debugMessage("InitEx("+ X_SIZE + "," + Y_SIZE + ",500) ret:" +  error + "\n");
        if (error != SGFDxErrorCode.SGFDX_ERROR_NONE)
            return;

        SGFingerInfo fpInfo1 = new SGFingerInfo();
        SGFingerInfo fpInfo2 = new SGFingerInfo();
        SGFingerInfo fpInfo3 = new SGFingerInfo();

        error = sgFplibSDKTest.GetImageQuality((long)X_SIZE, (long)Y_SIZE, finger1, quality1);

        debugMessage("GetImageQuality(R.raw.finger_0_10_3) ret:" +  error + "\n\tFinger quality=" +  quality1[0] + "\n");
        error = sgFplibSDKTest.GetImageQuality((long)X_SIZE, (long)Y_SIZE, finger2, quality2);
        debugMessage("GetImageQuality(R.raw.finger_1_10_3) ret:" +  error + "\n\tFinger quality=" +  quality2[0] + "\n");
        error = sgFplibSDKTest.GetImageQuality((long)X_SIZE, (long)Y_SIZE, finger3, quality3);
        debugMessage("GetImageQuality(R.raw.finger_2_10_3) ret:" +  error + "\n\tFinger quality=" +  quality3[0] + "\n");

        dwTimeStart = System.currentTimeMillis();
        nfiq1 = sgFplibSDKTest.ComputeNFIQ(finger1, X_SIZE, Y_SIZE);
        dwTimeEnd = System.currentTimeMillis();
        dwTimeElapsed = dwTimeEnd-dwTimeStart;
        debugMessage("ComputeNFIQ(R.raw.finger_0_10_3)\n\tNFIQ=" +  nfiq1 + "\n");
        if (nfiq1 == 2)
            debugMessage("\t+++PASS\n");
        else
            debugMessage("\t+++FAIL!!!!!!!!!!!!!!!!!!\n");
        debugMessage("\t" + dwTimeElapsed +  " milliseconds\n");

        dwTimeStart = System.currentTimeMillis();
        nfiq2 = sgFplibSDKTest.ComputeNFIQ(finger2, X_SIZE, Y_SIZE);
        dwTimeEnd = System.currentTimeMillis();
        dwTimeElapsed = dwTimeEnd-dwTimeStart;
        debugMessage("ComputeNFIQ(R.raw.finger_1_10_3)\n\tNFIQ=" +  nfiq2 + "\n");
        if (nfiq2 == 3)
            debugMessage("\t+++PASS\n");
        else
            debugMessage("\t+++FAIL!!!!!!!!!!!!!!!!!!\n");
        debugMessage("\t" + dwTimeElapsed +  " milliseconds\n");

        dwTimeStart = System.currentTimeMillis();
        nfiq3 = sgFplibSDKTest.ComputeNFIQ(finger3, X_SIZE, Y_SIZE);
        dwTimeEnd = System.currentTimeMillis();
        dwTimeElapsed = dwTimeEnd-dwTimeStart;
        debugMessage("ComputeNFIQ(R.raw.finger_2_10_3)\n\tNFIQ=" +  nfiq3 + "\n");
        if (nfiq3 == 2)
            debugMessage("\t+++PASS\n");
        else
            debugMessage("\t+++FAIL!!!!!!!!!!!!!!!!!!\n");
        debugMessage("\t" + dwTimeElapsed +  " milliseconds\n");

        fpInfo1.FingerNumber = 1;
        fpInfo1.ImageQuality = quality1[0];
        fpInfo1.ImpressionType = SGImpressionType.SG_IMPTYPE_LP;
        fpInfo1.ViewNumber = 1;

        fpInfo2.FingerNumber = 1;
        fpInfo2.ImageQuality = quality2[0];
        fpInfo2.ImpressionType = SGImpressionType.SG_IMPTYPE_LP;
        fpInfo2.ViewNumber = 2;

        fpInfo3.FingerNumber = 1;
        fpInfo3.ImageQuality = quality3[0];
        fpInfo3.ImpressionType = SGImpressionType.SG_IMPTYPE_LP;
        fpInfo3.ViewNumber = 3;



        ///////////////////////////////////////////////////////////////////////////////////////////////
        ///////////////////////////////////////////////////////////////////////////////////////////////
        //TEST SG400
        debugMessage("#######################\n");
        debugMessage("TEST SG400\n");
        debugMessage("###\n###\n");
        error = sgFplibSDKTest.SetTemplateFormat(SGFDxTemplateFormat.TEMPLATE_FORMAT_SG400);
        debugMessage("SetTemplateFormat(SG400) ret:" +  error + "\n");
        error = sgFplibSDKTest.GetMaxTemplateSize(size);
        debugMessage("GetMaxTemplateSize() ret:" +  error + " SG400_MAX_SIZE=" +  size[0] + "\n");

        sgTemplate1  = new byte[size[0]];
        sgTemplate2 = new byte[size[0]];
        sgTemplate3 = new byte[size[0]];

        //TEST DeviceInfo

        error = sgFplibSDKTest.CreateTemplate(null, finger1, sgTemplate1);
        debugMessage("CreateTemplate(finger3) ret:" + error + "\n");
        error = sgFplibSDKTest.GetTemplateSize(sgTemplate1, size);
        debugMessage("GetTemplateSize() ret:" +  error + " size=" +  size[0] + "\n");

        error = sgFplibSDKTest.CreateTemplate(null, finger2, sgTemplate2);
        debugMessage("CreateTemplate(finger2) ret:" + error + "\n");
        error = sgFplibSDKTest.GetTemplateSize(sgTemplate2, size);
        debugMessage("GetTemplateSize() ret:" +  error + " size=" +  size[0] + "\n");

        error = sgFplibSDKTest.CreateTemplate(null, finger3, sgTemplate3);
        debugMessage("CreateTemplate(finger3) ret:" + error + "\n");
        error = sgFplibSDKTest.GetTemplateSize(sgTemplate3, size);
        debugMessage("GetTemplateSize() ret:" +  error + " size=" +  size[0] + "\n");

        ///////////////////////////////////////////////////////////////////////////////////////////////
        error = sgFplibSDKTest.MatchTemplate(sgTemplate1, sgTemplate2, SGFDxSecurityLevel.SL_NORMAL, matched);
        debugMessage("MatchTemplate(sgTemplate1,sgTemplate2) \n\tret:" + error + "\n");
        if (matched[0])
            debugMessage("\tMATCHED!!\n");
        else
            debugMessage("\tNOT MATCHED!!\n");

        error = sgFplibSDKTest.GetMatchingScore(sgTemplate1, sgTemplate2,  score);
        debugMessage("GetMatchingScore(sgTemplate1, sgTemplate2) \n\tret:" + error + ". \n\tScore:" + score[0] + "\n");


        ///////////////////////////////////////////////////////////////////////////////////////////////
        error = sgFplibSDKTest.MatchTemplate(sgTemplate1, sgTemplate3, SGFDxSecurityLevel.SL_NORMAL, matched);
        debugMessage("MatchTemplate(sgTemplate1,sgTemplate3) \n\tret:" + error + "\n");
        if (matched[0])
            debugMessage("\tMATCHED!!\n");
        else
            debugMessage("\tNOT MATCHED!!\n");

        error = sgFplibSDKTest.GetMatchingScore(sgTemplate1, sgTemplate3,  score);
        debugMessage("GetMatchingScore(sgTemplate1, sgTemplate3) \n\tret:" + error + ". \n\tScore:" + score[0] + "\n");


        ///////////////////////////////////////////////////////////////////////////////////////////////
        error = sgFplibSDKTest.MatchTemplate(sgTemplate2, sgTemplate3, SGFDxSecurityLevel.SL_NORMAL, matched);
        debugMessage("MatchTemplate(sgTemplate2,sgTemplate3) \n\tret:" + error + "\n");
        if (matched[0])
            debugMessage("\tMATCHED!!\n");
        else
            debugMessage("\tNOT MATCHED!!\n");

        error = sgFplibSDKTest.GetMatchingScore(sgTemplate2, sgTemplate3,  score);
        debugMessage("GetMatchingScore(sgTemplate2, sgTemplate3) \n\tret:" + error + ". \n\tScore:" + score[0] + "\n");


        ///////////////////////////////////////////////////////////////////////////////////////////////
        ///////////////////////////////////////////////////////////////////////////////////////////////
        //TEST ANSI378
        debugMessage("#######################\n");
        debugMessage("TEST ANSI378\n");
        debugMessage("###\n###\n");
        error = sgFplibSDKTest.SetTemplateFormat(SGFDxTemplateFormat.TEMPLATE_FORMAT_ANSI378);
        debugMessage("SetTemplateFormat(ANSI378) ret:" +  error + "\n");
        error = sgFplibSDKTest.GetMaxTemplateSize(size);
        debugMessage("GetMaxTemplateSize() ret:" +  error + "\n\tANSI378_MAX_SIZE=" +  size[0] + "\n");

        ansiTemplate1  = new byte[size[0]];
        ansiTemplate2 = new byte[size[0]];

        error = sgFplibSDKTest.CreateTemplate(fpInfo1, finger1, ansiTemplate1);
        debugMessage("CreateTemplate(finger1) ret:" + error + "\n");
        error = sgFplibSDKTest.GetTemplateSize(ansiTemplate1, size);
        debugMessage("GetTemplateSize(ansi) ret:" +  error + " size=" +  size[0] + "\n");

        error = sgFplibSDKTest.CreateTemplate(fpInfo2, finger2, ansiTemplate2);
        debugMessage("CreateTemplate(finger2) ret:" + error + "\n");
        error = sgFplibSDKTest.GetTemplateSize(ansiTemplate2, size);
        debugMessage("GetTemplateSize(ansi) ret:" +  error + " size=" +  size[0] + "\n");

        error = sgFplibSDKTest.MatchTemplate(ansiTemplate1, ansiTemplate2, SGFDxSecurityLevel.SL_NORMAL, matched);
        debugMessage("MatchTemplate(ansi) ret:" + error + "\n");
        if (matched[0])
            debugMessage("\tMATCHED!!\n");
        else
            debugMessage("\tNOT MATCHED!!\n");

        error = sgFplibSDKTest.GetMatchingScore(ansiTemplate1, ansiTemplate2,  score);
        debugMessage("GetMatchingScore(ansi) ret:" + error + ". \n\tScore:" + score[0] + "\n");

        error = sgFplibSDKTest.GetTemplateSizeAfterMerge(ansiTemplate1, ansiTemplate2, size);
        debugMessage("GetTemplateSizeAfterMerge(ansi) ret:" + error + ". \n\tSize:" + size[0] + "\n");

        byte[] mergedAnsiTemplate1 = new byte[size[0]];
        error = sgFplibSDKTest.MergeAnsiTemplate(ansiTemplate1, ansiTemplate2, mergedAnsiTemplate1);
        debugMessage("MergeAnsiTemplate() ret:" + error + "\n");

        error = sgFplibSDKTest.MatchAnsiTemplate(ansiTemplate1, 0, mergedAnsiTemplate1, 0, SGFDxSecurityLevel.SL_NORMAL, matched);
        debugMessage("MatchAnsiTemplate(0,0) ret:" + error + "\n");
        if (matched[0])
            debugMessage("\tMATCHED!!\n");
        else
            debugMessage("\tNOT MATCHED!!\n");

        error = sgFplibSDKTest.MatchAnsiTemplate(ansiTemplate1, 0, mergedAnsiTemplate1, 1, SGFDxSecurityLevel.SL_NORMAL, matched);
        debugMessage("MatchAnsiTemplate(0,1) ret:" + error + "\n");
        if (matched[0])
            debugMessage("\tMATCHED!!\n");
        else
            debugMessage("\tNOT MATCHED!!\n");

        error = sgFplibSDKTest.GetAnsiMatchingScore(ansiTemplate1, 0, mergedAnsiTemplate1, 0, score);
        debugMessage("GetAnsiMatchingScore(0,0) ret:" + error + ". \n\tScore:" + score[0] + "\n");

        error = sgFplibSDKTest.GetAnsiMatchingScore(ansiTemplate1, 0, mergedAnsiTemplate1, 1, score);
        debugMessage("GetAnsiMatchingScore(0,1) ret:" + error + ". \n\tScore:" + score[0] + "\n");

        SGANSITemplateInfo ansiTemplateInfo = new SGANSITemplateInfo();
        error = sgFplibSDKTest.GetAnsiTemplateInfo(ansiTemplate1, ansiTemplateInfo);
        debugMessage("GetAnsiTemplateInfo(ansiTemplate1) ret:" + error + "\n");
        debugMessage("   TotalSamples=" + ansiTemplateInfo.TotalSamples + "\n");
        for (int i=0; i<ansiTemplateInfo.TotalSamples; ++i){
            debugMessage("   Sample[" + i + "].FingerNumber=" + ansiTemplateInfo.SampleInfo[i].FingerNumber + "\n");
            debugMessage("   Sample[" + i + "].ImageQuality=" + ansiTemplateInfo.SampleInfo[i].ImageQuality + "\n");
            debugMessage("   Sample[" + i + "].ImpressionType=" + ansiTemplateInfo.SampleInfo[i].ImpressionType + "\n");
            debugMessage("   Sample[" + i + "].ViewNumber=" + ansiTemplateInfo.SampleInfo[i].ViewNumber + "\n");
        }

        error = sgFplibSDKTest.GetAnsiTemplateInfo(mergedAnsiTemplate1, ansiTemplateInfo);
        debugMessage("GetAnsiTemplateInfo(mergedAnsiTemplate1) ret:" + error + "\n");
        debugMessage("   TotalSamples=" + ansiTemplateInfo.TotalSamples + "\n");

        for (int i=0; i<ansiTemplateInfo.TotalSamples; ++i){
            debugMessage("   Sample[" + i + "].FingerNumber=" + ansiTemplateInfo.SampleInfo[i].FingerNumber + "\n");
            debugMessage("   Sample[" + i + "].ImageQuality=" + ansiTemplateInfo.SampleInfo[i].ImageQuality + "\n");
            debugMessage("   Sample[" + i + "].ImpressionType=" + ansiTemplateInfo.SampleInfo[i].ImpressionType + "\n");
            debugMessage("   Sample[" + i + "].ViewNumber=" + ansiTemplateInfo.SampleInfo[i].ViewNumber + "\n");
        }


        ///////////////////////////////////////////////////////////////////////////////////////////////
        ///////////////////////////////////////////////////////////////////////////////////////////////
        //ALGORITHM COMPATIBILITY TEST
        boolean compatible;
        debugMessage("#######################\n");
        debugMessage("TEST ANSI378 Compatibility\n");
        debugMessage("###\n###\n");
        ///
        error = sgFplibSDKTest.GetMatchingScore(ansiTemplate1, ansiTemplate1Windows,  score);

        debugMessage("0_10_3.raw <> 0_10_3.ansiw:" + score[0] + "\n");
        if (score[0] == 199)
            debugMessage("\t+++PASS\n");
        else
            debugMessage("\t+++FAIL!!!!!!!!!!!!!!!!!!\n");

        ///
        error = sgFplibSDKTest.GetMatchingScore(ansiTemplate1, ansiTemplate2Windows,  score);
        debugMessage("0_10_3.raw <> 1_10_3.ansiw:" + score[0] + "\n");
        if (score[0] == 199)
            debugMessage("\t+++PASS\n");
        else
            debugMessage("\t+++FAIL!!!!!!!!!!!!!!!!!!\n");
        ///
        error = sgFplibSDKTest.GetMatchingScore(ansiTemplate1, ansiTemplate3Windows,  score);
        debugMessage("0_10_3.raw <> 2_10_3.ansiw:" + score[0] + "\n");
        if (score[0] == 176)
            debugMessage("\t+++PASS\n");
        else
            debugMessage("\t+++FAIL!!!!!!!!!!!!!!!!!!\n");
        ///
        error = sgFplibSDKTest.GetMatchingScore(ansiTemplate2, ansiTemplate3Windows,  score);
        if (score[0] == 192)
            compatible = true;
        else
            compatible = false;
        debugMessage("1_10_3.raw <> 2_10_3.ansiw:" + score[0] + "\n\tCompatible:" + compatible + "\n");

        ///////////////////////////////////////////////////////////////////////////////////////////////
        ///////////////////////////////////////////////////////////////////////////////////////////////
        //TEST ISO19794-2
        debugMessage("#######################\n");
        debugMessage("TEST ISO19794-2\n");
        debugMessage("###\n###\n");
        error = sgFplibSDKTest.SetTemplateFormat(SGFDxTemplateFormat.TEMPLATE_FORMAT_ISO19794);
        debugMessage("SetTemplateFormat(ISO19794) ret:" +  error + "\n");
        error = sgFplibSDKTest.GetMaxTemplateSize(size);
        debugMessage("GetMaxTemplateSize() ret:" +  error + " ISO19794_MAX_SIZE=" +  size[0] + "\n");

        isoTemplate1  = new byte[size[0]];
        isoTemplate2 = new byte[size[0]];

        error = sgFplibSDKTest.CreateTemplate(fpInfo1, finger1, isoTemplate1);
        debugMessage("CreateTemplate(finger1) ret:" + error + "\n");
        error = sgFplibSDKTest.GetTemplateSize(isoTemplate1, size);
        debugMessage("GetTemplateSize(iso) ret:" +  error + " \n\tsize=" +  size[0] + "\n");

        error = sgFplibSDKTest.CreateTemplate(fpInfo2, finger2, isoTemplate2);
        debugMessage("CreateTemplate(finger2) ret:" + error + "\n");
        error = sgFplibSDKTest.GetTemplateSize(isoTemplate2, size);
        debugMessage("GetTemplateSize(iso) ret:" +  error + " \n\tsize=" +  size[0] + "\n");

        error = sgFplibSDKTest.MatchTemplate(isoTemplate1, isoTemplate2, SGFDxSecurityLevel.SL_NORMAL, matched);
        debugMessage("MatchTemplate(iso) ret:" + error + "\n");
        if (matched[0])
            debugMessage("\tMATCHED!!\n");
        else
            debugMessage("\tNOT MATCHED!!\n");

        error = sgFplibSDKTest.GetMatchingScore(isoTemplate1, isoTemplate2,  score);
        debugMessage("GetMatchingScore(iso) ret:" + error + ". \n\tScore:" + score[0] + "\n");

        error = sgFplibSDKTest.GetIsoTemplateSizeAfterMerge(isoTemplate1, isoTemplate2, size);
        debugMessage("GetIsoTemplateSizeAfterMerge() ret:" + error + ". \n\tSize:" + size[0] + "\n");


        byte[] mergedIsoTemplate1 = new byte[size[0]];
        error = sgFplibSDKTest.MergeIsoTemplate(isoTemplate1, isoTemplate2, mergedIsoTemplate1);
        debugMessage("MergeIsoTemplate() ret:" + error + "\n");

        error = sgFplibSDKTest.MatchIsoTemplate(isoTemplate1, 0, mergedIsoTemplate1, 0, SGFDxSecurityLevel.SL_NORMAL, matched);
        debugMessage("MatchIsoTemplate(0,0) ret:" + error + "\n");
        if (matched[0])
            debugMessage("\tMATCHED!!\n");
        else
            debugMessage("\tNOT MATCHED!!\n");

        error = sgFplibSDKTest.MatchIsoTemplate(isoTemplate1, 0, mergedIsoTemplate1, 1, SGFDxSecurityLevel.SL_NORMAL, matched);
        debugMessage("MatchIsoTemplate(0,1) ret:" + error + "\n");
        if (matched[0])
            debugMessage("\tMATCHED!!\n");
        else
            debugMessage("\tNOT MATCHED!!\n");

        error = sgFplibSDKTest.GetIsoMatchingScore(isoTemplate1, 0, mergedIsoTemplate1, 0, score);
        debugMessage("GetIsoMatchingScore(0,0) ret:" + error + ". \n\tScore:" + score[0] + "\n");

        error = sgFplibSDKTest.GetIsoMatchingScore(isoTemplate1, 0, mergedIsoTemplate1, 1, score);
        debugMessage("GetIsoMatchingScore(0,1) ret:" + error + ". \n\tScore:" + score[0] + "\n");

        SGISOTemplateInfo isoTemplateInfo = new SGISOTemplateInfo();
        error = sgFplibSDKTest.GetIsoTemplateInfo(isoTemplate1, isoTemplateInfo);
        debugMessage("GetIsoTemplateInfo(isoTemplate1) \n\tret:" + error + "\n");
        debugMessage("\tTotalSamples=" + isoTemplateInfo.TotalSamples + "\n");
        for (int i=0; i<isoTemplateInfo.TotalSamples; ++i){
            debugMessage("\tSample[" + i + "].FingerNumber=" + isoTemplateInfo.SampleInfo[i].FingerNumber + "\n");
            debugMessage("\tSample[" + i + "].ImageQuality=" + isoTemplateInfo.SampleInfo[i].ImageQuality + "\n");
            debugMessage("\tSample[" + i + "].ImpressionType=" + isoTemplateInfo.SampleInfo[i].ImpressionType + "\n");
            debugMessage("\tSample[" + i + "].ViewNumber=" + isoTemplateInfo.SampleInfo[i].ViewNumber + "\n");
        }

        error = sgFplibSDKTest.GetIsoTemplateInfo(mergedIsoTemplate1, isoTemplateInfo);
        debugMessage("GetIsoTemplateInfo(mergedIsoTemplate1) \n\tret:" + error + "\n");
        debugMessage("\tTotalSamples=" + isoTemplateInfo.TotalSamples + "\n");
        for (int i=0; i<isoTemplateInfo.TotalSamples; ++i){
            debugMessage("\tSample[" + i + "].FingerNumber=" + isoTemplateInfo.SampleInfo[i].FingerNumber + "\n");
            debugMessage("\tSample[" + i + "].ImageQuality=" + isoTemplateInfo.SampleInfo[i].ImageQuality + "\n");
            debugMessage("\tSample[" + i + "].ImpressionType=" + isoTemplateInfo.SampleInfo[i].ImpressionType + "\n");
            debugMessage("\tSample[" + i + "].ViewNumber=" + isoTemplateInfo.SampleInfo[i].ViewNumber + "\n");
        }

        //Reset extractor/matcher for attached device opened in resume() method
        error = sgFplibSDKTest.InitEx( mImageWidth, mImageHeight, 500);
        debugMessage("InitEx("+ mImageWidth + "," + mImageHeight + ",500) ret:" +  error + "\n");

        ///////////////////////////////////////////////////////////////////////////////////////////////
        ///////////////////////////////////////////////////////////////////////////////////////////////
        //Test WSQ Processing
        debugMessage("#######################\n");
        debugMessage("TEST WSQ COMPRESSION\n");
        debugMessage("###\n###\n");
        byte[] wsqfinger1;
        int wsqLen;
        try {
            InputStream fileInputStream =getResources().openRawResource(R.raw.wsq2raw_finger);
            wsqLen = fileInputStream.available();
            debugMessage("WSQ file length is: " + wsqLen + "\n");
            wsqfinger1 = new byte[wsqLen];
            error = fileInputStream.read(wsqfinger1);
            debugMessage("Read: " + error + "bytes\n");
            fileInputStream.close();
        } catch (IOException ex){
            debugMessage("Error: Unable to find fingerprint image R.raw.wsq2raw_finger.\n");
            return;
        }


        int[] fingerImageOutSize = new int[1];
        dwTimeStart = System.currentTimeMillis();
        error = sgFplibSDKTest.WSQGetDecodedImageSize(fingerImageOutSize, wsqfinger1, wsqLen);
        dwTimeEnd = System.currentTimeMillis();
        dwTimeElapsed = dwTimeEnd-dwTimeStart;
        debugMessage("WSQGetDecodedImageSize() ret:" +  error + "\n");
        debugMessage("\tRAW Image size is: " + fingerImageOutSize[0] + "\n");
        debugMessage("\t" + dwTimeElapsed +  " milliseconds\n");
//      debugMessage("Byte 0:"+ String.format("%02X",wsqfinger1[0]) + "\n");
//      debugMessage("Byte 1:"+ String.format("%02X",wsqfinger1[1]) + "\n");
//      debugMessage("Byte 201:"+ String.format("%02X",wsqfinger1[201]) + "\n");
//      debugMessage("Byte 1566:"+ String.format("%02X",wsqfinger1[1566]) + "\n");
//      debugMessage("Byte 7001:"+ String.format("%02X",wsqfinger1[7001]) + "\n");
//      debugMessage("Byte 7291:"+ String.format("%02X",wsqfinger1[7291]) + "\n");

        byte[] rawfinger1ImageOut = new byte[fingerImageOutSize[0]];
        int[] decodeWidth = new int[1];
        int[] decodeHeight = new int[1];
        int[] decodePixelDepth = new int[1];
        int[] decodePPI = new int[1];
        int[] decodeLossyFlag = new int[1];
        debugMessage("Decode WSQ File\n");
        dwTimeStart = System.currentTimeMillis();
        error = sgFplibSDKTest.WSQDecode(rawfinger1ImageOut, decodeWidth, decodeHeight, decodePixelDepth, decodePPI, decodeLossyFlag, wsqfinger1, wsqLen);
        dwTimeEnd = System.currentTimeMillis();
        dwTimeElapsed = dwTimeEnd-dwTimeStart;
        debugMessage("\tret:\t\t\t"+ error + "\n");
        debugMessage("\twidth:\t\t"+ decodeWidth[0] + "\n");
        debugMessage("\theight:\t\t"+ decodeHeight[0] + "\n");
        debugMessage("\tdepth:\t\t"+ decodePixelDepth[0] + "\n");
        debugMessage("\tPPI:\t\t\t"+ decodePPI[0] + "\n");
        debugMessage("\tLossy Flag\t"+ decodeLossyFlag[0] + "\n");
        if ((decodeWidth[0] == 258) && (decodeHeight[0] == 336))
            debugMessage("\t+++PASS\n");
        else
            debugMessage("\t+++FAIL!!!!!!!!!!!!!!!!!!\n");
        debugMessage("\t" + dwTimeElapsed +  " milliseconds\n");

        //mImageViewFingerprint.setImageBitmap(this.toGrayscale(rawfinger1ImageOut, decodeWidth[0], decodeHeight[0]));


        byte[] rawfinger1;
        int encodeWidth=258;
        int encodeHeight=336;
        int encodePixelDepth=8;
        int encodePPI=500;

        int rawLen;
        try {
            InputStream fileInputStream =getResources().openRawResource(R.raw.raw2wsq_finger);
            rawLen = fileInputStream.available();
            debugMessage("RAW file length is: " + rawLen + "\n");
            rawfinger1 = new byte[rawLen];
            error = fileInputStream.read(rawfinger1);
            debugMessage("Read: " + error + "bytes\n");
            fileInputStream.close();
        } catch (IOException ex){
            debugMessage("Error: Unable to find fingerprint image R.raw.raw2wsq_finger.\n");
            return;
        }

        int[] wsqImageOutSize = new int[1];
        dwTimeStart = System.currentTimeMillis();
        error = sgFplibSDKTest.WSQGetEncodedImageSize(wsqImageOutSize, SGWSQLib.BITRATE_5_TO_1, rawfinger1, encodeWidth, encodeHeight, encodePixelDepth, encodePPI);
        dwTimeEnd = System.currentTimeMillis();
        dwTimeElapsed = dwTimeEnd-dwTimeStart;
        debugMessage("WSQGetEncodedImageSize() ret:" +  error + "\n");
        debugMessage("WSQ Image size is: " + wsqImageOutSize[0] + "\n");
        if (wsqImageOutSize[0] == 20200)
            debugMessage("\t+++PASS\n");
        else
            debugMessage("\t+++FAIL!!!!!!!!!!!!!!!!!!\n");
        debugMessage("\t" + dwTimeElapsed +  " milliseconds\n");

        byte[] wsqfinger1ImageOut = new byte[wsqImageOutSize[0]];
        dwTimeStart = System.currentTimeMillis();
        error = sgFplibSDKTest.WSQEncode(wsqfinger1ImageOut, SGWSQLib.BITRATE_5_TO_1, rawfinger1, encodeWidth, encodeHeight, encodePixelDepth, encodePPI);
        dwTimeEnd = System.currentTimeMillis();
        dwTimeElapsed = dwTimeEnd-dwTimeStart;
        debugMessage("WSQEncode() ret:" +  error + "\n");
        debugMessage("\t" + dwTimeElapsed +  " milliseconds\n");

        dwTimeStart = System.currentTimeMillis();
        error = sgFplibSDKTest.WSQGetDecodedImageSize(fingerImageOutSize, wsqfinger1ImageOut, wsqImageOutSize[0]);
        dwTimeEnd = System.currentTimeMillis();
        dwTimeElapsed = dwTimeEnd-dwTimeStart;
        debugMessage("WSQGetDecodedImageSize() ret:" +  error + "\n");
        debugMessage("RAW Image size is: " + fingerImageOutSize[0] + "\n");
        debugMessage("\t" + dwTimeElapsed +  " milliseconds\n");

        byte[] rawfinger2ImageOut = new byte[fingerImageOutSize[0]];
        dwTimeStart = System.currentTimeMillis();
        error = sgFplibSDKTest.WSQDecode(rawfinger2ImageOut, decodeWidth, decodeHeight, decodePixelDepth, decodePPI, decodeLossyFlag, wsqfinger1, wsqLen);
        dwTimeEnd = System.currentTimeMillis();
        dwTimeElapsed = dwTimeEnd-dwTimeStart;
        debugMessage("WSQDecode() ret:" +  error + "\n");
        debugMessage("\tret:\t\t\t"+ error + "\n");
        debugMessage("\twidth:\t\t"+ decodeWidth[0] + "\n");
        debugMessage("\theight:\t\t"+ decodeHeight[0] + "\n");
        debugMessage("\tdepth:\t\t"+ decodePixelDepth[0] + "\n");
        debugMessage("\tPPI:\t\t\t"+ decodePPI[0] + "\n");
        debugMessage("\tLossy Flag\t"+ decodeLossyFlag[0] + "\n");
        if ((decodeWidth[0] == 258) && (decodeHeight[0] == 336))
            debugMessage("\t+++PASS\n");
        else
            debugMessage("\t+++FAIL!!!!!!!!!!!!!!!!!!\n");
        //mImageViewFingerprint.setImageBitmap(this.toGrayscale(rawfinger2ImageOut, decodeWidth[0], decodeHeight[0]));
        debugMessage("\t" + dwTimeElapsed +  " milliseconds\n");

        debugMessage("\n## END SDK TEST ##\n");
    }

    public void run() {

        Log.d(TAG, "Enter run()");
        //ByteBuffer buffer = ByteBuffer.allocate(1);
        //UsbRequest request = new UsbRequest();
        //request.initialize(mSGUsbInterface.getConnection(), mEndpointBulk);
        //byte status = -1;
        while (true) {

            try {
                Thread.sleep(10000);
            } catch (InterruptedException e) {
            }
            // queue a request on the interrupt endpoint
            //request.queue(buffer, 1);
            // send poll status command
            //  sendCommand(COMMAND_STATUS);
            // wait for status event
            /*
            if (mSGUsbInterface.getConnection().requestWait() == request) {
                byte newStatus = buffer.get(0);
                if (newStatus != status) {
                    Log.d(TAG, "got status " + newStatus);
                    status = newStatus;
                    if ((status & COMMAND_FIRE) != 0) {
                        // stop firing
                        sendCommand(COMMAND_STOP);
                    }
                }
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                }
            } else {
                Log.e(TAG, "requestWait failed, exiting");
                break;
            }
            */
        }
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


        mediaFile = new File(mediaStorageDir.getPath() + File.separator +
                "LOG_"+ timeStamp + ".txt");





        return mediaFile;
    }






}