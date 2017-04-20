package com.cris.cms.cmsmobile;

import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.Toast;
import com.datamini.tpos.usb.api.Switch;

public class MainActivity extends AppCompatActivity {


    WebView myWebView;
    Button myButton;
    Switch s;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        s = new Switch();
        //s.modulePowerSwitch(s.getModulePowerOff());
        s.modulePowerSwitch(s.getModulePowerOn());

        myWebView = (WebView) findViewById(R.id.webview);
        myWebView.setWebViewClient(new WebViewClient());

        WebSettings myWebSettings = myWebView.getSettings();
        myWebSettings.setJavaScriptEnabled(true);
        myWebView.addJavascriptInterface(this, "android");
        //myWebView.loadUrl("http://192.168.1.100:8080/mystruts1-helloworld-final");
        myWebView.loadUrl("http://172.16.25.18:9080/CMS");
        //myWebView.loadUrl("http://10.60.201.120/CMSTEST");
        //myWebView.loadUrl("http://cms.indianrail.gov.in/CMSTEST/");


/*
        // FOR TESTING THE BA DEVICE STAND ALONE
        myButton = (Button) findViewById(R.id.startBA);

        myButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

              startBA(v);

            }
        });
*/


    }




    @android.webkit.JavascriptInterface
    public void startBio(String crewid) {



        //Toast.makeText(this, crewid, Toast.LENGTH_SHORT).show();

        Intent getNameScreenIntent = new Intent(this, BioScreen.class);

        final int res = 1;
        getNameScreenIntent.putExtra("callingactivity","MainActivity");
        getNameScreenIntent.putExtra("crewid",crewid);
        startActivityForResult(getNameScreenIntent,res);


    }



    @android.webkit.JavascriptInterface
    public void startBA(View view) {

        Log.d("LOG : " , "INSIDE Start BA");

        //Toast.makeText(this, crewid, Toast.LENGTH_SHORT).show();

        Intent getNameScreenIntent = new Intent(this, BAScreen.class);

        final int res = 2;
        getNameScreenIntent.putExtra("callingactivity","MainActivity");
        getNameScreenIntent.putExtra("crewid","RTM1001");
        startActivityForResult(getNameScreenIntent,res);


    }



    @android.webkit.JavascriptInterface
    public void showToast(String toast) {
        Toast.makeText(this, toast, Toast.LENGTH_SHORT).show();
    }



    @android.webkit.JavascriptInterface
    public void emptyDB(){

        Log.d("LOG : " , "INSIDE");
        DatabaseHandler db = new DatabaseHandler(this);
        SQLiteDatabase dbs = db.getWritableDatabase();

        // DELETE THE EXISTING FINGER PRINT
        dbs.delete("FP_Data",null,null);


        dbs.close();
        db.close();
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if(resultCode == RESULT_OK)
        {
            switch(requestCode)
            {
                case 1:
                    String msg = data.getStringExtra("match");
                    //Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
                    myWebView.loadUrl("javascript:returnFromBioVeri('" + msg + "')");
                    //myWebView.loadUrl("javascript:returnFromBioVeri('" + msg + "')");
                    break;
                case 2:
                    s.modulePowerSwitch(s.getModulePowerOff());
                    msg = data.getStringExtra("badata");
                    //Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
                    myWebView.loadUrl("javascript:receiveBAResponse('" + msg + "')");
                    //myWebView.loadUrl("javascript:returnFromBioVeri('" + msg + "')");
                    break;

            }
        }


    }



    public void insertUser(){
        DatabaseHandler db = new DatabaseHandler(this);
        SQLiteDatabase dbs = db.getWritableDatabase();

        String sql                      =   "INSERT INTO FP_Data (crewid,finger,) VALUES(?,?)";
        SQLiteStatement insertStmt      =   dbs.compileStatement(sql);
        insertStmt.clearBindings();
        insertStmt.bindString(1, "RTM1001");
        insertStmt.bindString(2,"L");
        //insertStmt.bindBlob(3, template);
        insertStmt.executeInsert();
        db.close();
    }

    public void getUser(){
        DatabaseHandler db = new DatabaseHandler(this);
        SQLiteDatabase dbs = db.getWritableDatabase();

        String selectQuery = "SELECT  * FROM FP_Data";

        Cursor cursor = dbs.rawQuery(selectQuery, null);

        // looping through all rows and adding to list
        if (cursor.moveToFirst()) {
            do {
                Log.d("ROW",cursor.getString(0));
            } while (cursor.moveToNext());
        }
        db.close();
    }


/*
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        TextView username = (TextView) findViewById(R.id.textView);

        String nameback = data.getStringExtra("name");

        username.append(" " + nameback);


    } */







}
