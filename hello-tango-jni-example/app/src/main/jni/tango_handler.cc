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

#include "hello-tango-jni/tango_handler.h"
#include "opencv2/core/core.hpp"
#include <opencv2/highgui/highgui.hpp>
#include <opencv2/imgproc/imgproc.hpp>
#include <stdio.h>
#include <iostream>
#include <fstream>
#include <string>
#include <inttypes.h>
#include <time.h>
#include <unistd.h>
#include <mutex>
#include <thread>
#include <chrono>
#include "synchronized_queue.cc"

#define __STDC_FORMAT_MACROS

namespace hello_tango_jni {


int frame_number =0;
cv::Mat image_array[4000];
double time_stamp_array[4000];

std::mutex queue_mutex;
SynchronizedQueue<cv::Mat> mImageQueue(0);
SynchronizedQueue<double> mTimestampQueue(0);

SynchronizedQueue<std::list<float> > mXYZijQueue(0);
SynchronizedQueue<double> mXYZijTimeQueue(0);

std::thread consumer;

int run_thread = 0;
int run_producer_thread = 0;

int ptClcnt = 1;



static void onPoseAvailable(void*, const TangoPoseData* pose) {
  LOGI("Position: %f, %f, %f. Orientation: %f, %f, %f, %f",
       pose->translation[0], pose->translation[1], pose->translation[2],
       pose->orientation[0], pose->orientation[2], pose->orientation[3],
       pose->orientation[3]);
}



static void onFrameAvailable(void*, TangoCameraId id, const TangoImageBuffer *buffer) {


  if(run_producer_thread != 0){
 // clock_t tStart = clock();

  double timestamp = buffer->timestamp;
  uint32_t height = buffer->height;
  uint32_t width = buffer->width;
  uint32_t stride = buffer->stride;
  int64_t frame_n = buffer->frame_number;

/**
  char timeFilename [100];
  char vidFilename [320];


  sprintf(timeFilename, "/sdcard/Download/timestamps.csv");
  sprintf(vidFilename, "/sdcard/imgs/img_%02d.jpg", frame_number);
  frame_number++;
**/
  cv::Mat m = cv::Mat(height + height / 2, width, CV_8UC1, 0, stride);
  //cv::Mat m2 = cv::Mat(height + height / 2, width, CV_8UC1, 0, stride);
  cv::Mat m2;
  //cv::Mat m = cv::Mat(height+height/2, width, CV_8UC1, 0, stride);
  m.data = buffer->data;
  cv::cvtColor(m, m, CV_YUV2BGR_NV21,3);
  m.copyTo(m2);
  //image_array[frame_number] = m2;
  queue_mutex.lock();
 // mImageQueue.operate("push",NULL,&m2,NULL,NULL);
  mImageQueue.operate("push",NULL,&m2,NULL,NULL);
  queue_mutex.unlock();

/**
  time_stamp_array[frame_number] = timestamp;
  frame_number++;
  **/
   queue_mutex.lock();
   mTimestampQueue.operate("push",NULL,&timestamp,NULL,NULL);
   queue_mutex.unlock();

  /**

**/
  //LOGI("format: %#x",buffer->format);
/**
  std::ofstream fp(vidFilename, std::ios::trunc | std::ios::binary);

  int offset = 0;
  for (int i = 0; i < buffer->height-1; i++) {
    offset += buffer->stride;
    fp.write((char*)(buffer->data + offset), buffer->width);

  }

  fp.close();
  **/

/**
  std::ofstream fp2(timeFilename, std::ios::app | std::ios::binary);
  std::string text;
  sprintf((char*)text.c_str(),"%f,%zu,%zu,%zu\n",timestamp,height,width,stride);
  fp2.write(text.c_str(), strlen(text.c_str()));
  fp2.close();

   cv::imwrite(vidFilename,m);
   **/
 //  LOGI("SAVED FRAME %s", vidFilename);

   //m2.release();
   //m.release();

  //delete buffer;
  // LOGI("time taken:  %.2fs\n", (double)(clock()-tStart)/CLOCKS_PER_SEC);

   }

}


static void onPointCloudAvailableCallback(void* context, const TangoXYZij* point_cloud){

std::list<float> XYZijList;
size_t point_cloud_size = point_cloud->xyz_count * 3;
XYZijList.resize(point_cloud_size);
std::copy(point_cloud->xyz[0], point_cloud->xyz[0] + point_cloud_size,
            XYZijList.begin());

queue_mutex.lock();
mXYZijQueue.operate("push",NULL,&XYZijList,NULL,NULL);
queue_mutex.unlock();


double XYZijTimestamp = point_cloud->timestamp;
queue_mutex.lock();
mXYZijTimeQueue.operate("push",NULL,&XYZijTimestamp,NULL,NULL);
queue_mutex.unlock();

//LOGI("POINT CLOUD RCVD: %d", ptClcnt);
ptClcnt ++;
}

/**
static void writeToFile(){

  int i = 0;
  char timeFilename [100];
  char vidFilename [320];

  sprintf(timeFilename, "/sdcard/Download/timestamps.csv");

  std::ofstream fp2(timeFilename, std::ios::app | std::ios::binary);

  while(i < frame_number){
  std::string text;
  sprintf((char*)text.c_str(),"%f\n",time_stamp_array[i]);
  fp2.write(text.c_str(), strlen(text.c_str()));

  sprintf(vidFilename, "/sdcard/imgs/img_%02d.jpg", i);
  cv::cvtColor(image_array[i], image_array[i], CV_YUV2BGR_NV21,3);
  cv::imwrite(vidFilename,image_array[i]);
  i++;
  LOGI("SAVED FRAME %s", vidFilename);

  }

  fp2.close();


}

**/

static void writePointCloud(std::list<float>* pointCloudList, std::string filename){

    int sz = (*pointCloudList).size();

    std::ofstream fp3(filename.c_str(), std::ios::app | std::ios::binary);

    for(int i = 0; i< sz; i++){

    fp3<< (*pointCloudList).front() << "\n";
    (*pointCloudList).pop_front();


    }

    fp3.close();


}


static void saveFromQueue(std::string filename, SynchronizedQueue<cv::Mat> *imageQueue,SynchronizedQueue<double> *timestampQueue,
SynchronizedQueue<std::list<float>> *depthQueue,SynchronizedQueue<double> *depthTimestampQueue,  int* run){
		int j = 0;
		int k = 0;
		char timeFilename [100];
        char vidFilename [320];
        char depthFilename [100];


        unsigned int sz  =0;
        unsigned int timestampSz = 0;

        unsigned int depthSz = 0;
        unsigned int depthTimeSz = 0;



        sprintf(timeFilename, "/sdcard/Download/timestamps.csv");

        std::ofstream fp2(timeFilename, std::ios::app | std::ios::binary);

	for(;;){

		if((*run) != 0 || sz !=0 || timestampSz != 0 || depthSz != 0 || depthTimeSz != 0){


			queue_mutex.lock();
			(*imageQueue).operate("size",NULL,NULL,&sz,NULL);
			queue_mutex.unlock();

			queue_mutex.lock();
            (*timestampQueue).operate("size",NULL,NULL,&timestampSz,NULL);
            queue_mutex.unlock();

            queue_mutex.lock();
            (*depthQueue).operate("size",NULL,NULL,&depthSz,NULL);
            queue_mutex.unlock();

            queue_mutex.lock();
            (*depthTimestampQueue).operate("size",NULL,NULL,&depthTimeSz,NULL);
            queue_mutex.unlock();


			if(sz != 0){

				queue_mutex.lock();
				 (*imageQueue).operate("size",NULL,NULL,&sz,NULL);
				std::list<cv::Mat> frameList(sz);
				(*imageQueue).operate("drain",NULL,NULL,NULL,&frameList);
				queue_mutex.unlock();

				unsigned int frameListSz = frameList.size();

				for(int i = 0; i< frameListSz; i++){
					char newFileName[300];
					sprintf(newFileName,filename.c_str(),j);
					cv::Mat frame = frameList.front();
                    //LOGI("size is : %d",sz);
                    //cv::Mat frame2;
					//cv::cvtColor(frame, frame2, CV_YUV2BGR_NV21,3);
                    //std::vector<int> compression_params;
                   // compression_params.push_back(CV_IMWRITE_JPEG_QUALITY);
                    //compression_params.push_back(100);
					cv::imwrite(newFileName,frame);
					frameList.pop_front();
					//std::this_thread::sleep_for(std::chrono::microseconds(300));
                    //frame2.release();
                    //
                    //frame.release();

                    j++;
				}

                }

				if(timestampSz != 0){



                queue_mutex.lock();
                (*timestampQueue).operate("size",NULL,NULL,&timestampSz,NULL);
                std::list<double> timeList(timestampSz);
                (*timestampQueue).operate("drain",NULL,NULL,NULL,&timeList);
                queue_mutex.unlock();

                timestampSz = timeList.size();


                for(int i = 0; i< timestampSz; i++){

                std::string text;
                sprintf((char*)text.c_str(),"%f,%f,%f,%f,%f\n",timeList.front(),0.0,0.0,0.0,3.0);
                fp2.write(text.c_str(), strlen(text.c_str()));
                //LOGI("CRASHED AFTER POPPING TIMESTAMPS");
                timeList.pop_front();


                }


				}

				if(depthSz != 0){
                            //update size and drain queue
                            queue_mutex.lock();
                            (*depthQueue).operate("size",NULL,NULL,&depthSz,NULL);
                            std::list<std::list<float>> depthList(depthSz);
                            (*depthQueue).operate("drain",NULL,NULL,NULL,&depthList);
                            queue_mutex.unlock();

                            unsigned int depthSz = depthList.size();

                            //write queue to file
                            for(int i = 0; i< depthSz; i++){
                                std::list<float> tempList = depthList.front();
                                sprintf(depthFilename,"/sdcard/PointCloud/depthFile_%02d.csv",k);

                                writePointCloud(&tempList,depthFilename);
                                k++;
                                 //LOGI("CRASHED AFTER POPPING DEPTH FILE");
                                depthList.pop_front();


                                //std::this_thread::sleep_for(std::chrono::microseconds(300));


                            }

                }


                if(depthTimeSz != 0){



                                queue_mutex.lock();
                                (*depthTimestampQueue).operate("size",NULL,NULL,&depthTimeSz,NULL);
                                std::list<double> depthTimeList(depthTimeSz);
                                (*depthTimestampQueue).operate("drain",NULL,NULL,NULL,&depthTimeList);
                                queue_mutex.unlock();

                                depthTimeSz = depthTimeList.size();


                                for(int i = 0; i< depthTimeSz; i++){

                                std::string text;
                                sprintf((char*)text.c_str(),"%f,%f,%f,%f,%f\n",depthTimeList.front(),0.0,0.0,0.0,4.0);
                                fp2.write(text.c_str(), strlen(text.c_str()));
                                //LOGI("CRASHED AFTER POPPING DEPTH TIME");
                                depthTimeList.pop_front();

                                }



                }


					std::this_thread::sleep_for(std::chrono::microseconds(3000));






		}

		else{break;}



	}

	 fp2.close();
	 //LOGI("finished with ims, dpthsz:  %.2fs\n", (double)(clock()-tStart)/CLOCKS_PER_SEC);


}




TangoHandler::TangoHandler() : tango_config_(nullptr) {}

TangoHandler::~TangoHandler() {
  tango_config_ = nullptr;
};

TangoErrorType TangoHandler::Initialize(JNIEnv* env, jobject activity) {
  return TangoService_initialize(env, activity);
}

TangoErrorType TangoHandler::SetupConfig() {
  // TANGO_CONFIG_DEFAULT is enabling Motion Tracking and disabling Depth
  // Perception.
  tango_config_ = TangoService_getConfig(TANGO_CONFIG_DEFAULT);
  if (tango_config_ == nullptr) {
    return TANGO_ERROR;

  }

  int ret = TangoConfig_setBool(tango_config_, "config_enable_depth", true);
    if (ret != TANGO_SUCCESS) {
      LOGE(
          "JNI_App: config_enable_depth() failed with error"
          "code: %d",
          ret);
      return TANGO_ERROR;
    }

  return TANGO_SUCCESS;
}

TangoErrorType TangoHandler::ConnectPoseCallback() {
  // TangoCoordinateFramePair is used to tell Tango Service about the frame of
  // references that the applicaion would like to listen to.
  TangoCoordinateFramePair pair;
  pair.base = TANGO_COORDINATE_FRAME_START_OF_SERVICE;
  pair.target = TANGO_COORDINATE_FRAME_DEVICE;
  return TangoService_connectOnPoseAvailable(1, &pair, onPoseAvailable);
}


TangoErrorType TangoHandler::ConnectCameraCallback() {
  // TangoCoordinateFramePair is used to tell Tango Service about the frame of
  // references that the applicaion would like to listen to.
  TangoCameraId id = TANGO_CAMERA_FISHEYE;

  int ret = TangoService_connectOnFrameAvailable(id,nullptr, onFrameAvailable);
  if (ret != TANGO_SUCCESS) {
               LOGE(
                   "JNI_App: Failed to connect to fisheye callback with error"
                   "code: %d",
                   ret);
               return TANGO_ERROR;
  }
  ret = TangoService_connectOnXYZijAvailable(onPointCloudAvailableCallback);
           if (ret != TANGO_SUCCESS) {
             LOGE(
                 "JNI_App: Failed to connect to point cloud callback with error"
                 "code: %d",
                 ret);
             return TANGO_ERROR;
           }

  return TANGO_SUCCESS;


}

TangoErrorType TangoHandler::ConnectService() {
  return TangoService_connect(nullptr, tango_config_);
}

void TangoHandler::DisconnectService() {
  TangoConfig_free(tango_config_);
  tango_config_ = nullptr;
  TangoService_disconnect();
}

void TangoHandler::writeToFile(){
int i = 0;
  char timeFilename [100];
  char vidFilename [320];

  sprintf(timeFilename, "/sdcard/Download/timestamps.csv");

  std::ofstream fp2(timeFilename, std::ios::app | std::ios::binary);

  while(i < frame_number){
  std::string text;
  sprintf((char*)text.c_str(),"%f,%f,%f,%f,%f\n",time_stamp_array[i],0.0,0.0,0.0,3.0);
  fp2.write(text.c_str(), strlen(text.c_str()));
/**
  sprintf(vidFilename, "/sdcard/imgs/img_%02d.jpg", i);
  cv::cvtColor(image_array[i], image_array[i], CV_YUV2BGR_NV21,3);
  std::vector<int> compression_params;
  compression_params.push_back(CV_IMWRITE_JPEG_QUALITY);
  compression_params.push_back(100);
  cv::imwrite(vidFilename,image_array[i]);
  i++;
  LOGI("SAVED FRAME %s", vidFilename);
**/

  i++;
  }

  fp2.close();



}

void TangoHandler::startCameraProcess(){

run_producer_thread = 1;
run_thread = 1;
consumer = std::thread(&saveFromQueue, "/sdcard/imgs/img_%02d.jpg",&mImageQueue,&mTimestampQueue,&mXYZijQueue,&mXYZijTimeQueue,&run_thread);

//LOGI("Thread name is :" + consumer.getName());

}

void TangoHandler::stopCameraProcess(){

run_producer_thread = 0;
std::this_thread::sleep_for(std::chrono::seconds(10));
run_thread = 0;
consumer.join();

}

}  // namespace hello_tango_jni
