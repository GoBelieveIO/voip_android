#include "voip_capture.h"
#include "webrtc/voice_engine/include/voe_base.h"
#include "webrtc/common_types.h"

//#include "webrtc/config.h"
//#include "webrtc/base/thread.h"
//#include "webrtc/base/scoped_ptr.h"
//#include "webrtc/base/asyncinvoker.h"
//#include "webrtc/base/messagehandler.h"
//#include "webrtc/base/bind.h"
//#include "webrtc/base/helpers.h"
//#include "webrtc/base/checks.h"
//#include "webrtc/base/criticalsection.h"
//#include "webrtc/base/safe_conversions.h"
//#include "webrtc/base/thread.h"
//#include "webrtc/base/timeutils.h"
#include "webrtc/common_video/libyuv/include/webrtc_libyuv.h"
#include "webrtc/modules/video_capture/video_capture.h"
#include "webrtc/video/video_receive_stream.h"
#include "webrtc/video/video_send_stream.h"
#include "webrtc/modules/video_coding/codecs/h264/include/h264.h"

#include "webrtc/engine_configurations.h"
#include "webrtc/modules/video_render/video_render_defines.h"
#include "webrtc/modules/video_render/video_render.h"
#include "webrtc/modules/video_capture/video_capture_factory.h"
#include "webrtc/system_wrappers/include/tick_util.h"
#include <string>
#include <vector>
#include "WebRTC.h"
#include "voip_jni.h"

#define ARRAY_SIZE(x) (static_cast<int>(sizeof(x) / sizeof(x[0])))

#define WIDTH 640
#define HEIGHT 480
#define FPS 30

VOIPCapture::VOIPCapture(webrtc::VideoRenderer *render, bool front):
    module_(NULL), captured_frames_(0), 
    front_(front), render_(render), 
    callback_(NULL) {
    
}

VOIPCapture::~VOIPCapture() {

}

void VOIPCapture::startCapture() {
    bool front = front_;
    webrtc::VideoCaptureModule::DeviceInfo* info = webrtc::VideoCaptureFactory::CreateDeviceInfo(0);
    if (!info) {
        return;
    }

    LOG("after create device info");
    int num_cams = info->NumberOfDevices();
    LOG("num cams:%d", num_cams);

    char vcm_id[256];
    bool found = false;
    for (int index = 0; index < num_cams; ++index) {
        char vcm_name[256] = {0};
        if (info->GetDeviceName(index, vcm_name, ARRAY_SIZE(vcm_name),
                                vcm_id, ARRAY_SIZE(vcm_id)) != -1) {
                
            LOG("vcm name:%s", vcm_name);
            //"Facing back" or "Facing front"
            if (front && strstr(vcm_name, "Facing front") != NULL) {
                found = true;
                break;
            } else if (!front && strstr(vcm_name, "Facing back") != NULL) {
                found = true;
                break;
            }
        }
    }
        
    if (!found) {
        LOG("Failed to find capturer");
        delete info;
        return;
    }
        
    webrtc::VideoCaptureCapability best_cap;
    best_cap.width = WIDTH;
    best_cap.height = HEIGHT;
    best_cap.maxFPS = FPS;
    best_cap.rawType = webrtc::kVideoNV21;

    int diff_area = INT_MAX;

    int32_t num_caps = info->NumberOfCapabilities(vcm_id);
    for (int32_t i = 0; i < num_caps; ++i) {
        webrtc::VideoCaptureCapability cap;
        if (info->GetCapability(vcm_id, i, cap) != -1) {
            LOG("cap width:%d height:%d raw type:%d max fps:%d", cap.width, cap.height, cap.rawType, cap.maxFPS);
        }

        int diff = abs(WIDTH*HEIGHT - cap.width*cap.height);
        if (diff < diff_area) {
            diff_area = diff;
            best_cap = cap;
        }
    }
    delete info;
    LOG("best cap width:%d height:%d raw type:%d max fps:%d", 
        best_cap.width, best_cap.height, best_cap.rawType, best_cap.maxFPS);
        
    module_ = webrtc::VideoCaptureFactory::Create(0, vcm_id);
    if (!module_) {
        LOG("Failed to create capturer");
        return;
    }
        
    // It is safe to change member attributes now.
    module_->AddRef();


    module_->RegisterCaptureDataCallback(*this);
    if (module_->StartCapture(best_cap) != 0) {
        module_->DeRegisterCaptureDataCallback();
        return;
    }
}

void VOIPCapture::stopCapture() {
    if (module_ != NULL) {
        module_->DeRegisterCaptureDataCallback();
        module_->StopCapture();
        module_->Release();
        module_ = NULL;
    }
}

void VOIPCapture::switchCamera() {
    stopCapture();
    front_ = !front_;
    startCapture();
}


// Callback when a frame is captured by camera.
void VOIPCapture::OnIncomingCapturedFrame(const int32_t id,
                                         const webrtc::VideoFrame& frame) {
    ++captured_frames_;
    // Log the size and pixel aspect ratio of the first captured frame.
    if (1 == captured_frames_) {
        LOG("frame width:%d heigth:%d rotation:%d", frame.width(), frame.height(), frame.rotation());
    }

    if (render_) {
        render_->RenderFrame(frame, 0);
    }

    if (callback_) {
        callback_->onCapturedFrame(frame);
    }
}

void VOIPCapture::OnCaptureDelayChanged(const int32_t id,
                                       const int32_t delay) {
        
}

