package io.gobelieve.voip_demo;


import android.app.ProgressDialog;
import android.content.Intent;
import android.os.AsyncTask;
import android.provider.Settings;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.beetle.im.IMService;
import com.beetle.im.RTMessage;
import com.beetle.im.RTMessageObserver;
import com.beetle.voip.MediaPipeActivity;
import com.beetle.voip.VOIPActivity;
import com.beetle.voip.VOIPCommand;
import com.beetle.voip.VOIPVideoActivity;
import com.beetle.voip.VOIPVoiceActivity;


import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.UUID;

public class MainActivity extends AppCompatActivity implements RTMessageObserver {

    private final int REQUEST_VOIP = 1;

    private EditText myEditText;
    private EditText peerEditText;


    private long myUID;
    private long peerUID;
    private String token;

    private ArrayList<String> channelIDs = new ArrayList<>();

    private boolean calling = false;

    ProgressDialog dialog;
    AsyncTask mLoginTask;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        myEditText = (EditText)findViewById(R.id.editText);
        peerEditText = (EditText)findViewById(R.id.editText2);


        String androidID = Settings.Secure.getString(this.getContentResolver(),
                Settings.Secure.ANDROID_ID);

        //app可以单独部署服务器，给予第三方应用更多的灵活性
        //在开发阶段也可以配置成测试环境的地址 "sandbox.voipnode.gobelieve.io", "sandbox.imnode.gobelieve.io"
        String sdkHost = "imnode2.gobelieve.io";
        IMService.getInstance().setHost(sdkHost);
        IMService.getInstance().setIsSync(false);
        IMService.getInstance().registerConnectivityChangeReceiver(getApplicationContext());
        IMService.getInstance().setDeviceID(androidID);
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    public void dialVideo(View v) {
        try {
            final long myUID = Long.parseLong(myEditText.getText().toString());
            final long peerUID = Long.parseLong(peerEditText.getText().toString());

            if (myUID == 0 || peerUID == 0) {
                return;
            }

            if (mLoginTask != null) {
                return;
            }

            final ProgressDialog dialog = ProgressDialog.show(this, null, "登录中...");

            mLoginTask = new AsyncTask<Void, Integer, String>() {
                @Override
                protected String doInBackground(Void... urls) {
                    return MainActivity.this.login(myUID);
                }

                @Override
                protected void onPostExecute(String result) {
                    mLoginTask = null;
                    dialog.dismiss();
                    if (result != null && result.length() > 0) {
                        //设置用户id,进入MainActivity
                        String token = result;
                        MainActivity.this.token = token;
                        IMService.getInstance().setToken(token);
                        IMService.getInstance().start();

                        calling = true;

                        Intent intent = new Intent(MainActivity.this, VOIPVideoActivity.class);
                        intent.putExtra("peer_uid", peerUID);
                        intent.putExtra("peer_name", "测试");
                        intent.putExtra("current_uid", myUID);
                        intent.putExtra("channel_id", UUID.randomUUID().toString());
                        intent.putExtra("token", token);
                        intent.putExtra("is_caller", true);
                        startActivityForResult(intent, REQUEST_VOIP);

                    } else {
                        Toast.makeText(MainActivity.this, "登陆失败", Toast.LENGTH_SHORT).show();
                    }
                }
            }.execute();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void dial(View v) {
        try {
            final long myUID = Long.parseLong(myEditText.getText().toString());
            final long peerUID = Long.parseLong(peerEditText.getText().toString());

            if (myUID == 0 || peerUID == 0) {
                return;
            }


            if (mLoginTask != null) {
                return;
            }

            final ProgressDialog dialog = ProgressDialog.show(this, null, "登录中...");

            mLoginTask = new AsyncTask<Void, Integer, String>() {
                @Override
                protected String doInBackground(Void... urls) {
                    return MainActivity.this.login(myUID);
                }

                @Override
                protected void onPostExecute(String result) {
                    mLoginTask = null;
                    dialog.dismiss();
                    if (result != null && result.length() > 0) {
                        //设置用户id,进入MainActivity
                        String token = result;
                        MainActivity.this.token = token;
                        IMService.getInstance().setToken(token);
                        IMService.getInstance().start();

                        calling = true;
                        Intent intent = new Intent(MainActivity.this, MediaPipeActivity.class);
                        intent.putExtra("peer_uid", peerUID);
                        intent.putExtra("peer_name", "测试");
                        intent.putExtra("current_uid", myUID);
                        intent.putExtra("channel_id", UUID.randomUUID().toString());
                        intent.putExtra("token", token);
                        intent.putExtra("is_caller", true);
                        startActivityForResult(intent, REQUEST_VOIP);

                    } else {
                        Toast.makeText(MainActivity.this, "登陆失败", Toast.LENGTH_SHORT).show();
                    }
                }
            }.execute();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    public void receiveCall(View v) {
        try {
            final long myUID = Long.parseLong(myEditText.getText().toString());
            final long peerUID = Long.parseLong(peerEditText.getText().toString());

            if (myUID == 0 || peerUID == 0) {
                return;
            }


            if (mLoginTask != null) {
                return;
            }

            final ProgressDialog dialog = ProgressDialog.show(this, null, "登录中...");

            mLoginTask = new AsyncTask<Void, Integer, String>() {
                @Override
                protected String doInBackground(Void... urls) {
                    return MainActivity.this.login(myUID);
                }

                @Override
                protected void onPostExecute(String result) {
                    dialog.dismiss();
                    mLoginTask = null;
                    if (result != null && result.length() > 0) {
                        //设置用户id,进入MainActivity
                        String token = result;
                        MainActivity.this.token = token;
                        IMService.getInstance().setToken(token);
                        IMService.getInstance().start();
                        IMService.getInstance().addRTObserver(MainActivity.this);

                        ProgressDialog dialog = ProgressDialog.show(MainActivity.this, null, "等待中...");

                        dialog.setTitle("等待中...");

                        MainActivity.this.dialog = dialog;
                        MainActivity.this.myUID = myUID;
                        MainActivity.this.peerUID = peerUID;

                    } else {
                        dialog.dismiss();
                        Toast.makeText(MainActivity.this, "登陆失败", Toast.LENGTH_SHORT).show();
                    }
                }
            }.execute();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String login(long uid) {
        //调用app自身的登陆接口获取im服务必须的access token
        String URL = "http://demo.gobelieve.io";
        String uri = String.format("%s/auth/token", URL);

        try {
            java.net.URL url = new URL(uri);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setDoInput(true);
            connection.setUseCaches(false);
            connection.setRequestProperty("Content-type", "application/json");
            connection.connect();

            JSONObject json = new JSONObject();
            json.put("uid", uid);
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(connection.getOutputStream(), "UTF-8"));
            writer.write(json.toString());
            writer.close();

            int responseCode = connection.getResponseCode();
            if(responseCode != HttpURLConnection.HTTP_OK) {
                System.out.println("login failure code is:" + responseCode);
                return null;
            }

            InputStream inputStream = connection.getInputStream();

            //inputstream -> string
            ByteArrayOutputStream result = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int length;
            while ((length = inputStream.read(buffer)) != -1) {
                result.write(buffer, 0, length);
            }
            String str = result.toString(StandardCharsets.UTF_8.name());


            JSONObject jsonObject = new JSONObject(str);
            String accessToken = jsonObject.getString("token");
            return accessToken;
        } catch (Exception e) {
            e.printStackTrace();
        }

        return "";
    }



    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        IMService.getInstance().stop();
        calling = false;
    }

    @Override
    public void onRTMessage(RTMessage rt) {
        if (calling) {
            return;
        }

        if (VOIPActivity.activityCount > 0) {
            return;
        }

        JSONObject obj = null;
        try {
            JSONObject json = new JSONObject(rt.content);
            obj = json.getJSONObject("voip");
        } catch (JSONException e) {
            e.printStackTrace();
            return;
        }

        if (rt.sender != peerUID) {
            return;
        }

        VOIPCommand command = new VOIPCommand(obj);

        if (channelIDs.contains(command.channelID)) {
            return;
        }
        if (command.cmd == VOIPCommand.VOIP_COMMAND_DIAL) {
            dialog.dismiss();

            calling = true;
            channelIDs.add(command.channelID);

            Intent intent = new Intent(MainActivity.this, VOIPVoiceActivity.class);
            intent.putExtra("peer_uid", peerUID);
            intent.putExtra("peer_name", "测试");
            intent.putExtra("current_uid", myUID);
            intent.putExtra("token", token);
            intent.putExtra("is_caller", false);
            intent.putExtra("channel_id", command.channelID);
            startActivityForResult(intent, REQUEST_VOIP);


        } else if (command.cmd == VOIPCommand.VOIP_COMMAND_DIAL_VIDEO) {
            dialog.dismiss();

            calling = true;
            channelIDs.add(command.channelID);

            Intent intent = new Intent(MainActivity.this, VOIPVideoActivity.class);
            intent.putExtra("peer_uid", peerUID);
            intent.putExtra("peer_name", "测试");
            intent.putExtra("current_uid", myUID);
            intent.putExtra("token", token);
            intent.putExtra("is_caller", false);
            intent.putExtra("channel_id", command.channelID);
            startActivityForResult(intent, REQUEST_VOIP);

        }
    }
}
