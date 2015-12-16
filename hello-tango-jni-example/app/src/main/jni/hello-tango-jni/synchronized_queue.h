#include<list>
#include<string>
#include<array>


template <class T>
class SynchronizedQueue{

public:

    SynchronizedQueue(unsigned int size);
    //~SynchronizedQueue();

    void operate(std::string action, T* storage, T* newElement,unsigned int* size,std::list<T> *cpList);

private:
    std::list<T> theQueue;

};







