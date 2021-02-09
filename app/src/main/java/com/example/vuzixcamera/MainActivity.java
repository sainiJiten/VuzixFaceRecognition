package com.example.vuzixcamera;

import androidx.appcompat.app.AppCompatActivity;
import id.zelory.compressor.Compressor;
import okhttp3.OkHttpClient;

import android.app.Activity;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.hardware.Camera;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.FrameLayout;

import com.androidnetworking.AndroidNetworking;
import com.androidnetworking.common.Priority;
import com.androidnetworking.error.ANError;
import com.androidnetworking.interfaces.AnalyticsListener;
import com.androidnetworking.interfaces.JSONObjectRequestListener;
import com.androidnetworking.interfaces.UploadProgressListener;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity implements SurfaceHolder.Callback{
    SurfaceView cameraView,transparentView;
    SurfaceHolder holder,holderTransparent;
    Camera camera;
    String person;
    Rect r = new Rect();
    JSONArray numbers;
    int  deviceHeight,deviceWidth;
    String URL = "http://192.168.50.194:5000/"; //URL of the flask server
    final static String TAG = "MainActivity";
    Handler handler = new Handler();
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        cameraView = (SurfaceView)findViewById(R.id.camera);
        holder = cameraView.getHolder();
        holder.addCallback((SurfaceHolder.Callback) this);
        cameraView.setSecure(true);
        transparentView = (SurfaceView)findViewById(R.id.transparent);
        holderTransparent = transparentView.getHolder();
        holderTransparent.addCallback((SurfaceHolder.Callback) this);
        holderTransparent.setFormat(PixelFormat.TRANSLUCENT);
        transparentView.setZOrderMediaOverlay(true);
        deviceWidth=getScreenWidth();
        deviceHeight=getScreenHeight();
        OkHttpClient okHttpClient = new OkHttpClient().newBuilder()
                .connectTimeout(120, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                . writeTimeout(120, TimeUnit.SECONDS)
                .build();
        AndroidNetworking.initialize(getApplicationContext(),okHttpClient);
        camera = Camera.open();
        handler.postDelayed(runnableCode, 3000);
    }

    private Runnable runnableCode = new Runnable() {
        @Override
        public void run() {
            if(camera !=null)
            {
                camera.takePicture(null, null, mPictureCallback);
            }
        }
    };
    @Override
    public void surfaceCreated(SurfaceHolder surfaceHolder) {
        try {
            camera.setPreviewDisplay(holder);
            camera.startPreview();
        }
        catch (Exception e) {
            return;
        }
    }


    @Override
    public void surfaceChanged(SurfaceHolder surfaceHolder, int i, int i1, int i2) {

    }

    @Override
    public void surfaceDestroyed(SurfaceHolder surfaceHolder) {

    }
    public static int getScreenWidth() {
        return Resources.getSystem().getDisplayMetrics().widthPixels;
    }

    public static int getScreenHeight() {
        return Resources.getSystem().getDisplayMetrics().heightPixels;
    }

    private File getOutputMediaFile(String ext) {
        String state = Environment.getExternalStorageState();
        if(!state.equals(Environment.MEDIA_MOUNTED)){
            return null;
        }else{
            File folder_gui = new File(Environment.getExternalStorageDirectory() + File.separator + "GUI");
            if(!folder_gui.exists()){
                folder_gui.mkdirs();
            }
            String name = System.currentTimeMillis() + ext + "image.jpg";
            File outputFile = new File(folder_gui,name);
            return outputFile;
        }
    }
    // Callback function for clicking an image from camera
    Camera.PictureCallback mPictureCallback = new Camera.PictureCallback(){
        @Override
        public void onPictureTaken(byte[] data, Camera camera) {
            File picture_file = getOutputMediaFile("org");
            File comp_file = getOutputMediaFile("comp");
            if(picture_file == null){
                return;
            }
            else{
                try {
                    FileOutputStream fos = new FileOutputStream(picture_file);
                    fos.write(data);
                    fos.close();
                    camera.startPreview();
                    comp_file = new Compressor(MainActivity.this).compressToFile(picture_file);
                    picture_file.delete();
                    uploadImage(comp_file);
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    };

    public void uploadImage(final File file){
        //file Upload
        AndroidNetworking.upload(URL)
                .addMultipartFile("image",file)
                .setTag("uploadTest")
                .setPriority(Priority.HIGH)
                .build()
                .setUploadProgressListener(new UploadProgressListener() {
                    @Override
                    public void onProgress(long bytesUploaded, long totalBytes) {
                        // Can show an animation for bigger files
                    }
                })
                .setAnalyticsListener(new AnalyticsListener() {
                    @Override
                    public void onReceived(long timeTakenInMillis, long bytesSent, long bytesReceived, boolean isFromCache) {
                        Log.d(TAG, " timeTakenInMillis : " + timeTakenInMillis);
                        Log.d(TAG, " bytesSent : " + bytesSent);
                        Log.d(TAG, " bytesReceived : " + bytesReceived);
                        Log.d(TAG, " isFromCache : " + isFromCache);
                    }
                })// Can be removed in production
                .getAsJSONObject(new JSONObjectRequestListener() {
                    @Override
                    public void onResponse(JSONObject response) {
                        try {
                            person = response.getString("name");
                            numbers = response.getJSONArray("rect");
                            r.set(numbers.getInt(0)- 150,numbers.getInt(1)-150,numbers.getInt(0)+numbers.getInt(2)-200,numbers.getInt(1)+numbers.getInt(3)-200);
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }if(person.length()>0) {
                            Draw(person, r);
                        }else{
                            clear();
                        }
                        Log.d(TAG,response.toString());
                        boolean deleted = file.delete();
                        Log.d(TAG, String.valueOf(deleted));
                        if(camera !=null)
                        {
                            camera.takePicture(null, null, mPictureCallback);
                        }
                    }
                    @Override
                    public void onError(ANError error) {
                        Log.d(TAG,error.toString());
                    }
                });
    }
    //function for drawing a rectangle and name of the recognised person in the image on the second surfaceview
    private void Draw(String des,Rect rect)
    {
        Canvas canvas = holderTransparent.lockCanvas(null);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(Color.GREEN);
        paint.setStrokeWidth(3);
        paint.setTextSize(30);
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.MULTIPLY);
        canvas.drawText(des, rect.left, rect.top, paint);
        canvas.drawRect(rect, paint);
        holderTransparent.unlockCanvasAndPost(canvas);
    }
    //function for clearing the surfaceview
    public void clear(){
        Canvas canvas = holderTransparent.lockCanvas(null);
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.MULTIPLY);
        holderTransparent.unlockCanvasAndPost(canvas);
    }

}
