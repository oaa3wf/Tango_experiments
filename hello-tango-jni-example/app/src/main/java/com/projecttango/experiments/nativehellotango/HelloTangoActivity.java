/*
 * Copyright 2014 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.projecttango.experiments.nativehellotango;

import com.google.tango.hellotangojni.R;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.View;
import android.widget.Toast;
import android.hardware.SensorManager;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.LoaderCallbackInterface;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.concurrent.ArrayBlockingQueue;

/**
 * Main activity controls Tango lifecycle.
 */
public class HelloTangoActivity extends Activity {

  static {
    System.loadLibrary("opencv_java3");
  }
  // The user has not given permission to use Motion Tracking functionality.
  private static final int TANGO_NO_MOTION_TRACKING_PERMISSION = -3;
  // The user has not given permission to use Camera functionality.
  private static final int TANGO_NO_CAMERA_PERMISSION = -5;

  // The input argument is invalid.
  private static final int  TANGO_INVALID = -2;

  // This error code denotes some sort of hard error occurred.
  private static final int  TANGO_ERROR = -1;

  // This code indicates success.
  private static final int  TANGO_SUCCESS = 0;

  // The unique request code for permission intent.
  private static final int PERMISSION_REQUEST_CODE = 0;

  // The flag to check if Tango Service is connect.
  private boolean mIsTangoServiceConnected = false;

  //Declare Sensor Manager and Sensors for IMU readings
  private SensorManager mSensorManager;
  private Sensor accSensor;
  private Sensor gyrSensor;

  //flags to check if sensors are registered
  private boolean regAcc = false;
  private boolean regGyr = false;

/**
  //Files where we want the data stored
  File accFile = new File(Environment.getExternalStoragePublicDirectory(
          Environment.DIRECTORY_DOWNLOADS), "accstamp"+".txt");

  File gyrFile = new File(Environment.getExternalStoragePublicDirectory(
          Environment.DIRECTORY_DOWNLOADS), "gyrstamp" + ".txt");

 **/

  //File streams for accelerometer and gyroscope data
  private FileOutputStream accStream =null;
  private FileOutputStream gyrStream = null;

  // Flag to inform us of when the user wants program started
  private boolean started = false;

 //String for formatting csv text
  private static final String sAccFormat = " %d, %f, %f, %f";
  private static final String sGyrFormat = " %d, %f, %f, %f";

  private ArrayList<String> accMsgList;
  private ArrayList<String> gyrMsgList;


  private Handler mHandler;
  private HandlerThread mHandlerThread;

  private mSensorEventListener theSensorEventListener;

  private ArrayBlockingQueue theQueue;

  private Consumer mConsumer;

  Thread consumerThread;

  int threadSafe = 0;


  //OpenCV Stuff from internet
  private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
    @SuppressLint("LongLogTag")
    @Override
    public void onManagerConnected(int status) {
      switch (status) {
        case LoaderCallbackInterface.SUCCESS:
        {
          Log.i(TAG, "OpenCV loaded successfully");

        } break;
        default:
        {
          super.onManagerConnected(status);
        } break;
      }
    }
  };

  @Override
  protected void onCreate(Bundle savedInstanceState) {

    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);
    setTitle(R.string.app_name);

    // Initialize Tango service.
    // The activity object is used for TangoService to check if the API version
    // is too old for current TangoService.
    int status = TangoJNINative.initialize(this);
    if (status != TANGO_SUCCESS) {
      if (status == TANGO_INVALID) {
        Toast.makeText(this, 
          "Tango Service version mis-match", Toast.LENGTH_SHORT).show();
      } else {
        Toast.makeText(this, 
          "Tango Service initialize internal error", Toast.LENGTH_SHORT).show();
      }
    }
/**
    //define file streams
    try {
      accStream = new FileOutputStream(accFile,true);
    } catch (FileNotFoundException e) {
      e.printStackTrace();

    }


    try {
      gyrStream = new FileOutputStream(gyrFile,true);
    } catch (FileNotFoundException e) {
      e.printStackTrace();

    }
**/
    mHandlerThread = new HandlerThread("mWorkerThread");

    //initialize sensor manager and define sensors
    mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);

    if (mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null){
      accSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
    }

    if (mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE) != null){
      gyrSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
    }

    accMsgList = new ArrayList<String>();
    gyrMsgList = new ArrayList<String>();

    theQueue = new ArrayBlockingQueue(500);

    theSensorEventListener = new mSensorEventListener(regAcc,regGyr,theQueue);

    mConsumer = new Consumer(theQueue,"consumer_imu.csv",(long)10);

    //starts a new thread where the imu stuff is done
    mHandlerThread.start();
    mHandler = new Handler(mHandlerThread.getLooper());

    consumerThread = new Thread(mConsumer);
    consumerThread.start();
  }


  //edited
  @Override
  protected void onResume() {
    super.onResume();

    // Setup Tango configuraturation.
    TangoJNINative.setupConfig();

    // connectCallbacks() returns TANGO_NO_MOTION_TRACKING_PERMISSION error code
    // if the application doesn't have permissions to use Motion Tracking.
    //
    // Permission intent will be called if there is no permission. The intent is
    // used to invoke permission activity.
    //
    // If there is a permission to use Motion Tracking features, the application
    // will connect to TangoService.


    if(started) {

      setCallbacks();
      threadSafe =1;
    }
  }

  @Override
  protected void onPause() {
    // Note that this function will be called when the permission activity is
    // foregrounded.
    super.onPause();
    if (mIsTangoServiceConnected) {
      TangoJNINative.disconnect();
      //unregister sensors
      /**
      if(regAcc||regGyr) {
        mSensorManager.unregisterListener(theSensorEventListener);
        regAcc = false;
        regGyr = false;
      }
       **/
    }
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
  }

  private void callPermissionIntent() {
    // Start permission activity.
    //
    // All permission types can be found from:
    // https://developers.google.com/project-tango/apis/c/c-user-permissions
    Intent intent = new Intent();
    intent.setAction("android.intent.action.REQUEST_TANGO_PERMISSION");
    intent.putExtra("PERMISSIONTYPE", "MOTION_TRACKING_PERMISSION");
    startActivityForResult(intent, PERMISSION_REQUEST_CODE);
  }

  private void callCameraPermissionIntent() {
    // Start permission activity.
    //
    // All permission types can be found from:
    // https://developers.google.com/project-tango/apis/c/c-user-permissions
    Intent intent = new Intent();
    intent.setAction("android.intent.action.REQUEST_TANGO_PERMISSION");
    intent.putExtra("PERMISSIONTYPE", "CAMERA_PERMISSION");
    startActivityForResult(intent, PERMISSION_REQUEST_CODE);
  }

  @Override
  protected void onActivityResult (int requestCode,
                                   int resultCode,
                                   Intent data) {
    // Check if this the request we sent for permission activity.
    //
    // Note that the onResume() will be called after permission activity
    // is dismissed, because the current activity (application) is foregrounded.
    if (requestCode == PERMISSION_REQUEST_CODE) {
      if (resultCode == RESULT_CANCELED) {
        mIsTangoServiceConnected = false;
        finish();
      }
    }
  }


  //store accelerometer and gyro data in a buffer for writing
  /**
  @Override
  public void onSensorChanged(SensorEvent event) {

    if(event.sensor.getType() == Sensor.TYPE_ACCELEROMETER){

      if(regAcc) {


        String msg = String.format(sAccFormat, event.timestamp, (event.values[0]), (event.values[1]), (event.values[2]));


        msg = msg + "\n";
        accMsgList.add(msg);


        //comment here out

        try {
          accStream = new FileOutputStream(accFile,true);
        } catch (FileNotFoundException e) {
          e.printStackTrace();

        }


        String msg = String.format("%d \n", event.timestamp);
        try {
          accStream.write(msg.getBytes());
        } catch (IOException e) {
          e.printStackTrace();
        }

        try {
          accStream.close();
        } catch (IOException e) {
          e.printStackTrace();
        }
       // stop comment here



      }
    }

    if(event.sensor.getType() == Sensor.TYPE_GYROSCOPE){

      if(regGyr) {


        String msg = String.format(sGyrFormat, event.timestamp, (event.values[0]), (event.values[1]), (event.values[2]));


        msg = msg + "\n";
        gyrMsgList.add(msg);

        //comment here out
        try {
          gyrStream = new FileOutputStream(gyrFile,true);
        } catch (FileNotFoundException e) {
          e.printStackTrace();

        }

        String msg = String.format("%d \n", event.timestamp);
        try {
          gyrStream.write(msg.getBytes());
        } catch (IOException e) {
          e.printStackTrace();
        }

        try {
          gyrStream.close();
        } catch (IOException e) {
          e.printStackTrace();
        }

        //stop comment here
      }
    }

  }
**/

  // start recording IMU and Camera data
  public void startRecording(View view){

    started = true;
    setCallbacks();
    TangoJNINative.startCameraProcess();

  }


  // activates callbacks
  private void setCallbacks(){

    int status = 0;

    status = TangoJNINative.connectCallbacks();
    if (status == TANGO_NO_CAMERA_PERMISSION) {
      callCameraPermissionIntent();
    } else if (status == TANGO_SUCCESS) {
      TangoJNINative.connect();
      mIsTangoServiceConnected = true;


      if(threadSafe ==0) {
        //register sensors
        regAcc = mSensorManager.registerListener(theSensorEventListener, accSensor, SensorManager.SENSOR_DELAY_NORMAL, mHandler);
        regGyr = mSensorManager.registerListener(theSensorEventListener, gyrSensor, SensorManager.SENSOR_DELAY_NORMAL, mHandler);
        theSensorEventListener.updater(regAcc, regGyr);

      }



    }

  }
  // stop recording ** to do ** can only be used once, i think we may need to disconnect camera etc
  public void stopRecording(View view) {



    started = false;

   // writeToFile(accMsgList, gyrMsgList);

    if((regAcc || regGyr)) {
      regAcc = false;
      regGyr = false;
      theSensorEventListener.updater(regAcc, regGyr);
      mSensorManager.unregisterListener(theSensorEventListener);


    }
    onPause();


    //writeToFile(theSensorEventListener.accMsgList2, theSensorEventListener.gyrMsgList2);
    //TangoJNINative.writeToFile();

    TangoJNINative.stopCameraProcess();

    mConsumer.setRunFlag(1);

    try {
      consumerThread.join((long)10000);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }

    mHandlerThread.quit();
  }

/**
  @Override
  public void onAccuracyChanged(Sensor sensor, int accuracy) {

  }

**/

  public boolean writeToFile(ArrayList<String> accStrings,ArrayList<String> gyrStrings){

    int accStringSz = accStrings.size();
    int gyrStringSz = gyrStrings.size();

    boolean wrtable = false ;

    if(isExternalStorageWritable()) {
      SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm");
      String date = df.format(Calendar.getInstance().getTime());


      File accFile = new File(Environment.getExternalStoragePublicDirectory(
              Environment.DIRECTORY_DOWNLOADS), "acc" + date +".csv");

      File gyrFile = new File(Environment.getExternalStoragePublicDirectory(
              Environment.DIRECTORY_DOWNLOADS), "gyr" + date + ".csv");

      try {
        accStream = new FileOutputStream(accFile,true);
      } catch (FileNotFoundException e) {
        e.printStackTrace();
        wrtable = false;
      }


      try {
        gyrStream = new FileOutputStream(gyrFile,true);
      } catch (FileNotFoundException e) {
        e.printStackTrace();
        wrtable = false;
      }

      if(accStream != null && gyrStream!= null){

        for(int i = 0; i< accStringSz; i++){

          try {
            accStream.write((accStrings.get(i)).getBytes());
          } catch (IOException e) {
            e.printStackTrace();
          }

        }

        for(int i = 0; i< gyrStringSz; i++){

          try {
            gyrStream.write((gyrStrings.get(i)).getBytes());
          } catch (IOException e) {
            e.printStackTrace();
          }

        }


        try {
          accStream.close();
        } catch (IOException e) {
          e.printStackTrace();
        }

        try {
          gyrStream.close();
        } catch (IOException e) {
          e.printStackTrace();
        }

        wrtable =true;

      }

    }

    return wrtable;
  }



  /* Checks if external storage is available for read and write */
  public boolean isExternalStorageWritable() {
    String state = Environment.getExternalStorageState();
    return Environment.MEDIA_MOUNTED.equals(state);
  }
  


}

/**********************************************************************************************/
/**********************************************************************************************/
/******************New class that implements sensor event listener*****************************/


class mSensorEventListener implements SensorEventListener {

  //private Sensor mSensor;
  private boolean regAcc2;
  private boolean regGyr2;

  private static final String sAccFormat = " %d, %f, %f, %f, %f";
  private static final String sGyrFormat = " %d, %f, %f, %f, %f";

  private ArrayBlockingQueue myQueue;





  public mSensorEventListener( boolean regAcce, boolean regGyre,ArrayBlockingQueue yQueue){

    // mSensor = mSense;
    regAcc2 =regAcce;
    regGyr2 = regGyre;

    myQueue = yQueue;

  }

  public void onAccuracyChanged(Sensor mSenser, int m){

  }

  public void updater(boolean acc, boolean gyr){
    regAcc2 = acc;
    regGyr2 = gyr;
  }

  public void onSensorChanged(SensorEvent event){

    if(event.sensor.getType() == Sensor.TYPE_ACCELEROMETER){

      if(regAcc2) {

        String msg = String.format(sAccFormat, (event.timestamp), (event.values[0]), (event.values[1]), (event.values[2]),1.0);
        //accTextView1.setText(msg);

        msg = msg + "\n";
        myQueue.offer(msg);
      }
    }

    if(event.sensor.getType() == Sensor.TYPE_GYROSCOPE){

      if(regGyr2) {
        String msg = String.format(sGyrFormat, (event.timestamp), (event.values[0]), (event.values[1]), (event.values[2]),2.0);
        //gyrTextView1.setText(msg);

        msg = msg + "\n";
        myQueue.offer(msg);


      }
    }



  }
}


