#voip android sdk

##信令通话的初始化参考[im](https://github.com/GoBelieveIO/im_android)工程

##发起视频通话
        Intent intent = new Intent(this, VOIPVideoActivity.class);
        intent.putExtra("peer_uid", peerUID);
        intent.putExtra("peer_name", "");
        intent.putExtra("current_uid", uid);
        intent.putExtra("channel_id", UUID.randomUUID().toString());
        intent.putExtra("token", token);
        intent.putExtra("is_caller", true);
        startActivity(intent);

##发起语音通话

        Intent intent = new Intent(this, VOIPVoiceActivity.class);
        intent.putExtra("peer_uid", peerUID);
        intent.putExtra("peer_name", "");
        intent.putExtra("current_uid", uid);
        intent.putExtra("channel_id", UUID.randomUUID().toString());
        intent.putExtra("token", token);
        intent.putExtra("is_caller", true);
        startActivity(intent);


##调试jni
1.编译debug版本的libvoip.so
2.cp app/src/main/libs/armeabi-v7a/*.so app/src/main/obj/local/armeabi-v7a/
3.ndk-gdb
