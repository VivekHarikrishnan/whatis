package com.research.domain.whatis;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import android.app.Activity;
import android.content.Context;
import android.media.Image;
import android.media.ImageReader;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.provider.DocumentsContract;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.util.DisplayMetrics;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.Toast;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;

import com.googlecode.tesseract.android.TessBaseAPI;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class MainActivity extends Activity {

    private final static String TAG = "Whatis";
    private Size mPreviewSize;
    private File file;
    protected String filePath = "";
    protected String tesseractTestDataPath = "";
    protected String lang = "eng";
    protected String recognizedText = "";
    public String AlchemyAPI_Key = "1e40c0e3fad7162a78edaaa41a89d348ee14560e";

    private TextureView mTextureView;
    private CameraDevice mCameraDevice;
    private CaptureRequest.Builder mPreviewBuilder;
    private CameraCaptureSession mPreviewSession;

    private Button mBtnShot;

    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();

    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_main);

        mTextureView = (TextureView)findViewById(R.id.texture);
        mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);

        mTextureView.setOnClickListener(new OnClickListener(){

            @Override
            public void onClick(View v) {
                Log.e(TAG, "mBtnShot clicked");
                takePicture();
            }

        });

//        mBtnShot = (Button)findViewById(R.id.btn_takepicture);
//        mBtnShot.setOnClickListener(new OnClickListener(){
//
//            @Override
//            public void onClick(View v) {
//                Log.e(TAG, "mBtnShot clicked");
//                takePicture();
//            }
//
//        });
    }

    protected void takePicture() {
        Log.e(TAG, "takePicture");
        if(null == mCameraDevice) {
            Log.e(TAG, "mCameraDevice is null, return");
            return;
        }

        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(mCameraDevice.getId());

            Size[] jpegSizes = null;
            if (characteristics != null) {
                jpegSizes = characteristics
                        .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                        .getOutputSizes(ImageFormat.JPEG);
            }
            int width = 640;
            int height = 480;
            if (jpegSizes != null && 0 < jpegSizes.length) {
                width = jpegSizes[0].getWidth();
                height = jpegSizes[0].getHeight();
            }

            ImageReader reader = ImageReader.newInstance(width, height, ImageFormat.JPEG, 1);
            List<Surface> outputSurfaces = new ArrayList<Surface>(2);
            outputSurfaces.add(reader.getSurface());
            outputSurfaces.add(new Surface(mTextureView.getSurfaceTexture()));

            final CaptureRequest.Builder captureBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureBuilder.addTarget(reader.getSurface());
            captureBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);

            // Orientation
            int rotation = getWindowManager().getDefaultDisplay().getRotation();
            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, ORIENTATIONS.get(rotation));

            filePath = Environment.getExternalStorageDirectory() + File.separator + getResources().getString(R.string.captured);
            file = new File(filePath);

            ImageReader.OnImageAvailableListener readerListener = new ImageReader.OnImageAvailableListener() {

                @Override
                public void onImageAvailable(ImageReader reader) {

                    Image image = null;
                    try {
                        image = reader.acquireLatestImage();
                        ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                        byte[] bytes = new byte[buffer.capacity()];
                        buffer.get(bytes);
                        Log.e(TAG, "Image Captured!!");
                        save(bytes);
                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                    } catch (IOException e) {
                        e.printStackTrace();
                    } finally {
                        if (image != null) {
                            image.close();
                        }
                    }
                }

                private void save(byte[] bytes) throws IOException {
                    OutputStream output = null;
                    try {
                        output = new FileOutputStream(file);
                        output.write(bytes);
                    } finally {
                        if (null != output) {
                            output.close();
                        }
                    }
                }

            };

            HandlerThread thread = new HandlerThread("CameraPicture");
            thread.start();
            final Handler backgroudHandler = new Handler(thread.getLooper());
            reader.setOnImageAvailableListener(readerListener, backgroudHandler);

            final CameraCaptureSession.CaptureCallback captureListener = new CameraCaptureSession.CaptureCallback() {

                @Override
                public void onCaptureCompleted(CameraCaptureSession session,
                                               CaptureRequest request, TotalCaptureResult result) {

                    super.onCaptureCompleted(session, request, result);
                    Toast.makeText(MainActivity.this, "Saved:"+file, Toast.LENGTH_SHORT).show();

                    copyTesseractTestData();
                    ArrayList<Rect> rects = detectText();

                    //SendAlchemyCall("combined");

                    //startPreview();
                }

            };

            mCameraDevice.createCaptureSession(outputSurfaces, new CameraCaptureSession.StateCallback() {

                @Override
                public void onConfigured(CameraCaptureSession session) {

                    try {
                        session.capture(captureBuilder.build(), captureListener, backgroudHandler);
                    } catch (CameraAccessException e) {

                        e.printStackTrace();
                    }
                }

                @Override
                public void onConfigureFailed(CameraCaptureSession session) {

                }
            }, backgroudHandler);

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.e(TAG, "onResume");
    }

    private void openCamera() {

        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        Log.e(TAG, "openCamera E");
        try {
            String cameraId = manager.getCameraIdList()[0];
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            mPreviewSize = map.getOutputSizes(SurfaceTexture.class)[0];

            manager.openCamera(cameraId, mStateCallback, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        catch (SecurityException e) {
            e.printStackTrace();
        }
        Log.e(TAG, "openCamera X");
    }

    private TextureView.SurfaceTextureListener mSurfaceTextureListener = new TextureView.SurfaceTextureListener(){

        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            Log.e(TAG, "onSurfaceTextureAvailable, width="+width+",height="+height);
            openCamera();
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface,
                                                int width, int height) {
            Log.e(TAG, "onSurfaceTextureSizeChanged");
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {
            //Log.e(TAG, "onSurfaceTextureUpdated");
        }

    };

    private CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {

        @Override
        public void onOpened(CameraDevice camera) {

            Log.e(TAG, "onOpened");
            mCameraDevice = camera;
            startPreview();
        }

        @Override
        public void onDisconnected(CameraDevice camera) {

            Log.e(TAG, "onDisconnected");
        }

        @Override
        public void onError(CameraDevice camera, int error) {

            Log.e(TAG, "onError");
        }

    };

    @Override
    protected void onPause() {

        Log.e(TAG, "onPause");
        super.onPause();
        if (null != mCameraDevice) {
            mCameraDevice.close();
            mCameraDevice = null;
        }
    }

    protected void startPreview() {

        if(null == mCameraDevice || !mTextureView.isAvailable() || null == mPreviewSize) {
            Log.e(TAG, "startPreview fail, return");
            return;
        }

        SurfaceTexture texture = mTextureView.getSurfaceTexture();
        if(null == texture) {
            Log.e(TAG,"texture is null, return");
            return;
        }

        texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
        Surface surface = new Surface(texture);

        try {
            mPreviewBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
        } catch (CameraAccessException e) {

            e.printStackTrace();
        }
        mPreviewBuilder.addTarget(surface);

        try {
            mCameraDevice.createCaptureSession(Arrays.asList(surface), new CameraCaptureSession.StateCallback() {

                @Override
                public void onConfigured(CameraCaptureSession session) {

                    mPreviewSession = session;
                    updatePreview();
                }

                @Override
                public void onConfigureFailed(CameraCaptureSession session) {

                    Toast.makeText(MainActivity.this, "onConfigureFailed", Toast.LENGTH_LONG).show();
                }
            }, null);
        } catch (CameraAccessException e) {

            e.printStackTrace();
        }
    }

    protected void updatePreview() {

        if(null == mCameraDevice) {
            Log.e(TAG, "updatePreview error, return");
        }

        mPreviewBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
        HandlerThread thread = new HandlerThread("CameraPreview");
        thread.start();
        Handler backgroundHandler = new Handler(thread.getLooper());

        try {
            mPreviewSession.setRepeatingRequest(mPreviewBuilder.build(), null, backgroundHandler);
        } catch (CameraAccessException e) {

            e.printStackTrace();
        }
    }

    public void copyTesseractTestData() {
        String tessDirPath = Environment.getExternalStorageDirectory() + File.separator + "tessdata" + File.separator;
        final File tessDirFile = new File(tessDirPath);
        tessDirFile.mkdir();

        tesseractTestDataPath = tessDirPath + lang + ".traineddata";
        if (!(new File(tesseractTestDataPath).exists())) {
            try {
                AssetManager assetManager = getAssets();
                InputStream in = assetManager.open(lang + ".traineddata");
                //GZIPInputStream gin = new GZIPInputStream(in);
                OutputStream out = new FileOutputStream(tesseractTestDataPath);

                // Transfer bytes from in to out
                byte[] buf = new byte[1024];
                int len;
                //while ((lenf = gin.read(buff)) > 0) {
                while ((len = in.read(buf)) > 0) {
                    out.write(buf, 0, len);
                }
                in.close();
                //gin.close();
                out.close();

                Log.v(TAG, "Copied " + lang + " traineddata");
            } catch (IOException e) {
                Log.e(TAG, "Was unable to copy " + lang + " traineddata " + e.toString());
            }
        }
    }

    public ArrayList<Rect> detectText() {
        TessBaseAPI baseApi = new TessBaseAPI();

        baseApi.init(Environment.getExternalStorageDirectory().toString(), lang);

        baseApi.setImage(file);
        recognizedText = baseApi.getUTF8Text();


        ArrayList<Rect> rects = baseApi.getWords().getBoxRects();
        baseApi.end();

        Log.e(TAG, recognizedText);

        return rects;
    }

    private void SendAlchemyCall(final String call)
    {
        Thread thread = new Thread(new Runnable(){
            @Override
            public void run() {
                try {
                    SendAlchemyCallInBackground(call);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });

        thread.start();
    }

    private void SendAlchemyCallInBackground(final String call)
    {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Log.e(TAG, "Making " + call);
            }
        });

        Document doc = null;
        AlchemyAPI api = null;
        try
        {
            api = AlchemyAPI.GetInstanceFromString(AlchemyAPI_Key);
        }
        catch( IllegalArgumentException ex )
        {
            Log.e(TAG, "Error loading AlchemyAPI.  Check that you have a valid AlchemyAPI key set in the AlchemyAPI_Key variable.  Keys available at alchemyapi.com.");
            return;
        }


        try{
            if( "concept".equals(call) )
            {
                doc = api.URLGetRankedConcepts(recognizedText);

            }
            else if( "entity".equals(call))
            {
                doc = api.URLGetRankedNamedEntities(recognizedText);

            }
            else if( "keyword".equals(call))
            {
                doc = api.URLGetRankedKeywords(recognizedText);

            }
            else if( "text".equals(call))
            {
                doc = api.URLGetText(recognizedText);

            }
            else if( "sentiment".equals(call))
            {
                AlchemyAPI_NamedEntityParams nep = new AlchemyAPI_NamedEntityParams();
                nep.setSentiment(true);
                doc = api.URLGetRankedNamedEntities(recognizedText, nep);

            }
            else if( "taxonomy".equals(call))
            {
                doc = api.URLGetTaxonomy(recognizedText);

            }
            else if( "image".equals(call))
            {
                doc = api.URLGetImage(recognizedText);

            }
            else if( "combined".equals(call))
            {
                doc = api.TextGetCombined(recognizedText);

            }
            else if( "imageClassify".equals(call))
            {
//                Bitmap bitmap = ((BitmapDrawable)imageview.getDrawable()).getBitmap();
//                ByteArrayOutputStream stream = new ByteArrayOutputStream();
//                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream);
//                byte[] imageByteArray = stream.toByteArray();
//
//                AlchemyAPI_ImageParams imageParams = new AlchemyAPI_ImageParams();
//                imageParams.setImage(imageByteArray);
//                imageParams.setImagePostMode(AlchemyAPI_ImageParams.RAW);
//                doc = api.ImageGetRankedImageKeywords(imageParams);
//                ShowTagInTextView(doc, "text");
            }
        }
        catch(Exception e)
        {
            e.printStackTrace();
            Log.e(TAG, "Error: " + e.getMessage());
        }


        storeKeywords(doc, false);
    }

    private void storeKeywords(final Document doc, final boolean showSentiment)
    {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {

                if( doc == null )
                {
                    return;
                }

                Element root = doc.getDocumentElement();
                NodeList items = root.getElementsByTagName("text");
                if( showSentiment )
                {
                    NodeList sentiments = root.getElementsByTagName("sentiment");
                    for (int i=0;i<items.getLength();i++){
                        Node concept = items.item(i);
                        String astring = concept.getNodeValue();
                        astring = concept.getChildNodes().item(0).getNodeValue();

                        if( i < sentiments.getLength() )
                        {
                            Node sentiment = sentiments.item(i);
                            Node aNode = sentiment.getChildNodes().item(1);
                            Node bNode = aNode.getChildNodes().item(0);
                            String s = bNode.getNodeValue();
                        }
                    }
                }
                else
                {
                    for (int i=0;i<items.getLength();i++) {
                        Node concept = items.item(i);
                        String astring = concept.getNodeValue();
                        astring = concept.getChildNodes().item(0).getNodeValue();
                        Log.d(TAG, astring);
                        Toast.makeText(MainActivity.this, astring, Toast.LENGTH_SHORT).show();
                    }
                }
            }
        });
    }
}