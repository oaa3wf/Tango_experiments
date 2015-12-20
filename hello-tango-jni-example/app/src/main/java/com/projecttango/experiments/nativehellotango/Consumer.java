package com.projecttango.experiments.nativehellotango;

/**
 * Created by sastrygrp on 10/16/15.
 */
import android.os.Environment;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.ArrayBlockingQueue;


public class Consumer implements Runnable{


    private ArrayBlockingQueue mQueue;
    private String mFilename;
    private long sleepTime;
    private int runFlag;

    private FileOutputStream mFileStream = null;


    private File mFile;



    public  Consumer(){

        mQueue = new ArrayBlockingQueue(500);
        mFilename = "default.csv";
        sleepTime = 10;

        mFile =  new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS), mFilename);
        runFlag = 0;

    }

    public Consumer(ArrayBlockingQueue yourQueue, String yourFilename,long time){
        mQueue = yourQueue;
        mFilename = yourFilename;
        sleepTime = time;
        mFile =  new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS), mFilename);

        runFlag = 0;


    }

    public void setRunFlag(int flag){

        runFlag = flag;

    }


    @Override
    public void run() {

        while(runFlag == 0) {

            int sz = mQueue.size();

            ArrayList<String> mList = new ArrayList<String>();

            if (sz > 0) {
                mQueue.drainTo(mList);

                int listSz = mList.size();

                try {
                    mFileStream = new FileOutputStream(mFile, true);
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                }

                for (int j = 0; j < listSz; j++) {

                    String res = mList.get(j);

                    try {
                        mFileStream.write(res.getBytes());
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }

                try {
                    mFileStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }

                try {
                    Thread.sleep(sleepTime);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }


            }

        }

    }


}



