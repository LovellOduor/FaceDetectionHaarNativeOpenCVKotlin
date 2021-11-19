#include <opencv2/opencv.hpp>
#include <cstdlib>
#include <jni.h>
#include <iostream>
#include <vector>

using namespace cv;
using namespace std;

CascadeClassifier cc;

extern "C"
JNIEXPORT void JNICALL
Java_com_example_cpproj_MainActivity_loadClassifier(JNIEnv *env, jobject thiz, jstring path) {
    cc.load(env->GetStringUTFChars(path,0));
}

void rotateMat(Mat &matImage, int rotation)
{
    if (rotation == 90) {
        transpose(matImage, matImage);
        flip(matImage, matImage, 1); //transpose+flip(1)=CW
    } else if (rotation == 270) {
        transpose(matImage, matImage);
        flip(matImage, matImage, 0); //transpose+flip(0)=CCW
    } else if (rotation == 180) {
        flip(matImage, matImage, -1);    //flip(-1)=180
    }

}

extern "C"
JNIEXPORT jstring JNICALL Java_com_example_cpproj_MainActivity_stringFromJNI(JNIEnv *env, jobject thiz) {
    // TODO: implement stringFromJNI()
    return env->NewStringUTF("Hello from c");
}

static jfloat* jres;
static jfloatArray output;

extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_example_cpproj_MainActivity_detect(JNIEnv *env, jobject thiz, jbyteArray src, jint width,
                                            jint height, jint rotation) {
    jbyte *_yuv = env->GetByteArrayElements(src, 0);

    Mat myyuv(height + height / 2, width, CV_8UC1, _yuv);
    Mat frame(height, width, CV_8UC4);

    cvtColor(myyuv, frame, COLOR_YUV2BGRA_NV21);
    rotateMat(frame, rotation);
    flip(frame, frame, 1);

    env->ReleaseByteArrayElements(src, _yuv, 0);
    vector<Rect> v;
    cc.detectMultiScale(frame, v, 1.1, 2, 0 | CASCADE_SCALE_IMAGE,Size(30, 30));

    int arrlen = 4;
    jres = new jfloat[arrlen];
    jres[0] = 1;
    jres[1] = 0;
    jres[2] = 1;
    jres[3] = 0;

    if(v.size()>0){
        Rect m = v[0];
        jres[0] = m.x;
        jres[1] = m.y;
        jres[2] = m.width;
        jres[3] = m.height;
    }

    output = env->NewFloatArray(arrlen);
    env->SetFloatArrayRegion(output, 0, arrlen, jres);
    return output;
}