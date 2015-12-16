
#include "hello-tango-jni/synchronized_queue.h"

template <class T>
SynchronizedQueue<T>::SynchronizedQueue(unsigned int size){


    std::list<T> temp(size);
    theQueue = temp;
}

/**
template <class T>
SynchronizedQueue<T>::~SynchronizedQueue(){
    theQueue.clear();
    delete &theQueue;
   }
   **/

template <class T>
void SynchronizedQueue<T>::operate(std::string action, T* storage, T* newElement,unsigned int * size, std::list<T> *cpList){

       if(action.compare("pop") == 0){

	if(theQueue.size() != 0){
       theQueue.pop_front();
	//std::cout<<theQueue.size()<<std::endl;
	}

       }

       else if(action.compare("push") == 0){

       theQueue.push_back(*(newElement));
	//std::cout<<theQueue.size()<<std::endl;



       }

       else if(action.compare("front") == 0){

	*storage = theQueue.front();
	//std::cout<<theQueue.size()<<std::endl;

	}

	else if(action.compare("size")==0){

	*size = theQueue.size();

	}

	else if(action.compare("drain")==0){

	std::copy(theQueue.begin(),theQueue.end(),(*cpList).begin());
	theQueue.clear();

	}





}

