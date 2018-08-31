/*
 * Copyright (C) 2016 The Android Open Source Project
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

// Modified by Gaurav on Feb 23, 2018

package com.google.android.cameraview.demo.activities;

import android.Manifest;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Point;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.Display;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.GridView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.cameraview.demo.adapters.SimilarImageAdapter;
import com.google.android.cameraview.demo.models.GridViewItem;
import com.google.android.cameraview.demo.utils.BitmapHelper;
import com.google.android.cameraview.demo.utils.FileUtils;
import com.google.android.cameraview.demo.R;
import com.tzutalin.dlib.Constants;
import com.tzutalin.dlib.FaceRec;
import com.tzutalin.dlib.VisionDetRet;

import junit.framework.Assert;

import org.jcodec.api.android.AndroidSequenceEncoder;
import org.jcodec.common.io.FileChannelWrapper;
import org.jcodec.common.io.NIOUtils;
import org.jcodec.common.model.Rational;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jcodec.api.SequenceEncoder;

// This demo app uses dlib face recognition based on resnet
public class MainActivity extends Activity implements
        ActivityCompat.OnRequestPermissionsResultCallback {
    private static final String TAG = "MainActivity";
    private static final int INPUT_SIZE = 500;
    private Handler mBackgroundHandler;
    private final int PICK_IMAGE_CAMERA = 1, PICK_IMAGE_GALLERY = 2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate called");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        if (checkPermissions()) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED) {
                new initRecAsync().execute();
            }
        }
        RelativeLayout rlTakePhoto = (RelativeLayout) findViewById(R.id.rlTakePhoto);
        rlTakePhoto.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selectImage();
            }
        });
    }

    // Select image from camera and gallery
    private void selectImage() {
        try {
            Intent pickPhoto = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            startActivityForResult(pickPhoto, PICK_IMAGE_GALLERY);
        } catch (Exception e) {
            Toast.makeText(this, "Permission error", Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_IMAGE_GALLERY) {
            Uri selectedImage = data.getData();
            try {
                Bitmap bitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), selectedImage);
                Bitmap scaledBitmap = scaleDown(bitmap, MAX_IMAGE_SIZE, true);
               /* ExifInterface exif = null;
                try {
                    if (selectedImage != null) {
                        exif = new ExifInterface(new File(selectedImage.getPath()).getAbsolutePath());
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
                int orientation = 0;
                if (exif != null) {
                    orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION,
                            ExifInterface.ORIENTATION_UNDEFINED);
                }*/
                Bitmap rBitmap = FileUtils.getCorrectlyOrientedImage(MainActivity.this, selectedImage, INPUT_SIZE);

                new recognizeAsync().execute(rBitmap);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }


    public static Bitmap scaleDown(Bitmap realImage, float maxImageSize, boolean filter) {
        float ratio = Math.min(
                (float) maxImageSize / realImage.getWidth(),
                (float) maxImageSize / realImage.getHeight());
        int width = Math.round((float) ratio * realImage.getWidth());
        int height = Math.round((float) ratio * realImage.getHeight());

        Bitmap newBitmap = Bitmap.createScaledBitmap(realImage, width,
                height, filter);
        return newBitmap;
    }

    private FaceRec mFaceRec;

    private void changeProgressDialogMessage(final String msg) {
        Runnable changeMessage = new Runnable() {
            @Override
            public void run() {
                tvProgressTxt.setText(msg);
                progressBar.setProgress(100);
            }
        };
        runOnUiThread(changeMessage);
    }

    int MAX_IMAGE_SIZE = 500;
    int BITMAP_QUALITY = 100;

    private class initRecAsync extends AsyncTask<Void, Integer, Void> {
//        ProgressDialog mdialog = new ProgressDialog(MainActivity.this);

        @Override
        protected void onPreExecute() {
            Log.d(TAG, "initRecAsync onPreExecute called");
            //mdialog.setMessage("Initializing...");
            //mdialog.setCancelable(false);
            //mdialog.show();
            showLoadingDialog();
            progressBar.setProgress(10);
            tvProgressTxt.setText("Initializing...");
            super.onPreExecute();
        }

        @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
        protected Void doInBackground(Void... args) {
            // create dlib_rec_example directory in sd card and copy model files
            File folder = new File(Constants.getDLibDirectoryPath());
            boolean success = false;
            if (!folder.exists()) {
                success = folder.mkdirs();
            }
            if (success) {
                File image_folder = new File(Constants.getDLibImageDirectoryPath());
                image_folder.mkdirs();
                if (!new File(Constants.getFaceShapeModelPath()).exists()) {
                    FileUtils.copyFileFromRawToOthers(MainActivity.this, R.raw.shape_predictor_5_face_landmarks, Constants.getFaceShapeModelPath());
                }
                if (!new File(Constants.getFaceDescriptorModelPath()).exists()) {
                    FileUtils.copyFileFromRawToOthers(MainActivity.this, R.raw.dlib_face_recognition_resnet_model_v1, Constants.getFaceDescriptorModelPath());
                }
            } else {
                //Log.d(TAG, "error in setting dlib_rec_example directory");
            }
            ArrayList<String> allFileList = getAllShownImagesPath();
            if (allFileList.size() > 0) {
                for (int k = 0; k < allFileList.size(); k++) {
                    String filename = allFileList.get(k).substring(allFileList.get(k).lastIndexOf("/") + 1);
                    int last = filename.lastIndexOf(".");
                    filename = last >= 1 ? filename.substring(0, last) : filename;
                    String targetPath = Constants.getDLibImageDirectoryPath() + "/" + filename + ".jpg";
                    File file = new File(targetPath);
                    if (!file.exists()) {
                        ExifInterface exif = null;
                        try {
                            exif = new ExifInterface(allFileList.get(k));
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        int orientation = 0;
                        if (exif != null) {
                            orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION,
                                    ExifInterface.ORIENTATION_UNDEFINED);
                        }
                        final Bitmap bitmap = BitmapFactory.decodeFile(allFileList.get(k));
                        Bitmap rBitmap = FileUtils.rotateBitmap(bitmap, orientation);
                        if (rBitmap != null) {
                            Bitmap scaledBitmap = FileUtils.scaleDown(rBitmap, MAX_IMAGE_SIZE, true);
                            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, BITMAP_QUALITY, bytes);
                            FileOutputStream fo;
                            File destination = new File(Constants.getDLibDirectoryPath() + "/temp.jpg");

                            try {
                                destination.createNewFile();
                                fo = new FileOutputStream(destination);
                                fo.write(bytes.toByteArray());
                                fo.close();
                                FileUtils.copyFile(destination.getAbsolutePath(), targetPath);
                            } catch (FileNotFoundException e) {
                                e.printStackTrace();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }

                    }
                    if (k <= 100)
                        publishProgress(k);
                }
            }

            mFaceRec = new FaceRec(Constants.getDLibDirectoryPath());
            changeProgressDialogMessage("Training the device...");
            mFaceRec.train();
            return null;
        }

        protected void onProgressUpdate(Integer... values) {
            progressBar.setProgress(values[0]);
        }

        protected void onPostExecute(Void result) {
            /*if (mdialog != null && mdialog.isShowing()) {
                mdialog.dismiss();
            }*/
            hideDialog();
        }
    }

    private ProgressBar progressBar;
    private TextView tvProgressTxt;
    private Dialog mdialog;

    private void showLoadingDialog() {
        mdialog = new Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        mdialog.setCancelable(false);
        mdialog.setContentView(R.layout.dialog_loading);
        progressBar = (ProgressBar) mdialog.findViewById(R.id.progressBar);
        tvProgressTxt = (TextView) mdialog.findViewById(R.id.tvProgressTxt);
        progressBar.setMax(100);
        progressBar.setProgress(0);
        mdialog.show();

    }

    void hideDialog() {
        if (mdialog != null && mdialog.isShowing()) {
            mdialog.dismiss();
        }
    }

    String[] permissions = new String[]{
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
    };

    private boolean checkPermissions() {
        int result;
        List<String> listPermissionsNeeded = new ArrayList<>();
        for (String p : permissions) {
            result = ContextCompat.checkSelfPermission(this, p);
            if (result != PackageManager.PERMISSION_GRANTED) {
                listPermissionsNeeded.add(p);
            }
        }
        if (!listPermissionsNeeded.isEmpty()) {
            ActivityCompat.requestPermissions(this, listPermissionsNeeded.toArray(new String[listPermissionsNeeded.size()]), 100);
            return false;
        }
        return true;
    }


    @Override
    protected void onResume() {
        Log.d(TAG, "onResume called");
        super.onResume();


    }

    @Override
    protected void onPause() {
        Log.d(TAG, "onPause called");
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        Log.d(TAG, "onDestroy called");
        super.onDestroy();
        if (mFaceRec != null) {
            mFaceRec.release();
        }
        if (mBackgroundHandler != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                mBackgroundHandler.getLooper().quitSafely();
            } else {
                mBackgroundHandler.getLooper().quit();
            }
            mBackgroundHandler = null;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (requestCode == 100) {
            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // do something

                if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        == PackageManager.PERMISSION_GRANTED) {
                    new initRecAsync().execute();
                }
            }else {
                checkPermissions();
            }
            return;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        return super.onOptionsItemSelected(item);
    }

    private Handler getBackgroundHandler() {
        if (mBackgroundHandler == null) {
            HandlerThread thread = new HandlerThread("background");
            thread.start();
            mBackgroundHandler = new Handler(thread.getLooper());
        }
        return mBackgroundHandler;
    }

    private String getResultMessage(ArrayList<String> names) {
        String msg = new String();
        if (names.isEmpty()) {
            msg = "No face detected or Unknown person";

        } else {
            for (int i = 0; i < names.size(); i++) {
                /*msg += names.get(i).split(Pattern.quote("."))[0];
                if (i != names.size() - 1) msg += ", ";*/
                msg += names.get(i);
            }
            //msg += " found!";
        }
        return msg;
    }

    private class recognizeAsync extends AsyncTask<Bitmap, Integer, ArrayList<String>> {
        //ProgressDialog dialog = new ProgressDialog(MainActivity.this);
        private int mScreenRotation = 0;
        private boolean mIsComputing = false;
        private Bitmap mCroppedBitmap = Bitmap.createBitmap(INPUT_SIZE, INPUT_SIZE, Bitmap.Config.ARGB_8888);
        int count = 1;

        @Override
        protected void onPreExecute() {
            /*dialog.setMessage("Recognizing...");
            dialog.setCancelable(false);
            dialog.show();*/
            showLoadingDialog();
            progressBar.setProgress(10);
            tvProgressTxt.setText("Finding Similar Photos...");
            super.onPreExecute();
        }

        protected ArrayList<String> doInBackground(Bitmap... bp) {

            drawResizedBitmap(bp[0], mCroppedBitmap);
            Log.d(TAG, "byte to bitmap");

            long startTime = System.currentTimeMillis();
            List<VisionDetRet> results;
            results = mFaceRec.recognize(mCroppedBitmap);
            long endTime = System.currentTimeMillis();
            Log.d(TAG, "Time cost: " + String.valueOf((endTime - startTime) / 1000f) + " sec");

            ArrayList<String> names = new ArrayList<>();
            for (VisionDetRet n : results) {
                names.add(n.getLabel());
                publishProgress(count++);
            }
            return names;
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            progressBar.setProgress(values[0]);
        }

        protected void onPostExecute(ArrayList<String> names) {
            progressBar.setProgress(100);
            hideDialog();
           /* AlertDialog.Builder builder1 = new AlertDialog.Builder(MainActivity.this);
            builder1.setMessage(getResultMessage(names));
            builder1.setCancelable(true);
            AlertDialog alert11 = builder1.create();
            alert11.show();*/
            if (names.size() > 0) {
                Log.e("##########: ", getResultMessage(names));
                showSimilarImagesGridDialog(names);
            } else {
                Toast.makeText(MainActivity.this, "No Similar Photos Found", Toast.LENGTH_SHORT).show();
            }
        }

        private void drawResizedBitmap(final Bitmap src, final Bitmap dst) {
            Display getOrient = ((WindowManager) getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
            int orientation = Configuration.ORIENTATION_UNDEFINED;
            Point point = new Point();
            getOrient.getSize(point);
            int screen_width = point.x;
            int screen_height = point.y;
            Log.d(TAG, String.format("screen size (%d,%d)", screen_width, screen_height));
            if (screen_width < screen_height) {
                orientation = Configuration.ORIENTATION_PORTRAIT;
                mScreenRotation = 0;
            } else {
                orientation = Configuration.ORIENTATION_LANDSCAPE;
                mScreenRotation = 0;
            }

            Assert.assertEquals(dst.getWidth(), dst.getHeight());
            final float minDim = Math.min(src.getWidth(), src.getHeight());

            final Matrix matrix = new Matrix();

            // We only want the center square out of the original rectangle.
            final float translateX = -Math.max(0, (src.getWidth() - minDim) / 2);
            final float translateY = -Math.max(0, (src.getHeight() - minDim) / 2);
            matrix.preTranslate(translateX, translateY);

            final float scaleFactor = dst.getHeight() / minDim;
            matrix.postScale(scaleFactor, scaleFactor);

            // Rotate around the center if necessary.
            if (mScreenRotation != 0) {
                matrix.postTranslate(-dst.getWidth() / 2.0f, -dst.getHeight() / 2.0f);
                matrix.postRotate(mScreenRotation);
                matrix.postTranslate(dst.getWidth() / 2.0f, dst.getHeight() / 2.0f);
            }

            final Canvas canvas = new Canvas(dst);
            canvas.drawBitmap(src, matrix, null);
        }
    }

    SimilarImageAdapter adapter;
    private GridView gridView;
    List<GridViewItem> gridItems;

    private void showSimilarImagesGridDialog(final ArrayList<String> fileNamesFound) {
        final Dialog dialog = new Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        dialog.setCancelable(false);
        dialog.setContentView(R.layout.dialog_grid_images);
        gridView = (GridView) dialog.findViewById(R.id.gridView);
        gridItems = new ArrayList<>();
        adapter = new SimilarImageAdapter(this, gridItems);
        gridView.setAdapter(adapter);
        dialog.show();
        new Thread(new Runnable() {
            @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
            @Override
            public void run() {
                checkSimilarFiles(fileNamesFound);
            }
        }).start();
        dialog.findViewById(R.id.btnBack).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });
        dialog.findViewById(R.id.btnMakeVideo).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (gridItems.size() > 0) {
                    showLoadingDialog();
                    tvProgressTxt.setText("Making Video...");
                    progressBar.setProgress(10);
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            makeVideo();
                        }
                    }).start();
                }
            }
        });
    }

    int counter = 10;

    private void makeVideo() {
        counter = 10;
        FileChannelWrapper out = null;
        File dir = new File(Constants.getDLibVideoDirectoryPath());
        if (!dir.exists())
            dir.mkdir();
        File file = new File(dir, "test_" + String.valueOf(System.currentTimeMillis()) + ".mp4");

        try {
            try {
                out = NIOUtils.writableFileChannel(file.getAbsolutePath());
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }

            AndroidSequenceEncoder encoder = new AndroidSequenceEncoder(out, Rational.ONE);
            for (GridViewItem gridViewItem : gridItems) {
                ExifInterface exif = null;
                try {
                    exif = new ExifInterface(gridViewItem.getPath());
                } catch (IOException e) {
                    e.printStackTrace();
                }
                int orientation = 0;
                if (exif != null) {
                    orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION,
                            ExifInterface.ORIENTATION_UNDEFINED);
                }
                final Bitmap bitmap = BitmapFactory.decodeFile(gridViewItem.getPath());
                Bitmap rBitmap = FileUtils.rotateBitmap(bitmap, orientation);
                int nh = 0;
                if (rBitmap != null) {
                    nh = (int) (rBitmap.getHeight() * (512.0 / rBitmap.getWidth()));
                }
                Bitmap scaled = null;
                if (rBitmap != null) {
                    scaled = Bitmap.createScaledBitmap(rBitmap, 512, nh, true);
                }
                ByteArrayOutputStream stream = new ByteArrayOutputStream();
                if (scaled != null) {
                    scaled.compress(Bitmap.CompressFormat.PNG, 70, stream);
                    encoder.encodeImage(scaled);
                }
                if (counter <= 100)
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {

                            progressBar.setProgress(counter++);
                        }
                    });
            }
            encoder.finish();
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    progressBar.setProgress(100);
                    hideDialog();
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    progressBar.setProgress(100);
                    hideDialog();
                }
            });
        } finally {
            NIOUtils.closeQuietly(out);
        }
    }

    public Bitmap getBitmap(String path) {
        try {
            Bitmap bitmap = null;
            File f = new File(path);
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inPreferredConfig = Bitmap.Config.ARGB_8888;

            bitmap = BitmapFactory.decodeStream(new FileInputStream(f), null, options);
            return bitmap;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
    private void checkSimilarFiles(ArrayList<String> fileNamesFound) {
        List<String> fileList = getAllShownImagesPath();
        if (fileList != null && fileList.size() > 0) {
            for (String detectFileName : fileNamesFound) {
                for (String fileName : fileList) {

                    String filename1 = fileName.substring(fileName.lastIndexOf("/") + 1);
                    int last1 = filename1.lastIndexOf(".");
                    filename1 = last1 >= 1 ? filename1.substring(0, last1) : filename1;

                    int last2 = detectFileName.lastIndexOf(".");
                    detectFileName = last2 >= 1 ? detectFileName.substring(0, last2) : detectFileName;

                    if (detectFileName.contains(filename1)) {
                        ExifInterface exif = null;
                        try {
                            exif = new ExifInterface(fileName);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        int orientation = 0;
                        if (exif != null) {
                            orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION,
                                    ExifInterface.ORIENTATION_UNDEFINED);
                        }
                        Bitmap image = BitmapHelper.decodeBitmapFromFile(fileName,
                                70,
                                70);
                        Bitmap rBitmap = FileUtils.rotateBitmap(image, orientation);

                        gridItems.add(new GridViewItem(fileName, rBitmap));

                    }
                }
            }
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    // add elements to al, including duplicates
                    Set<GridViewItem> hs = new HashSet<>(gridItems);
                    gridItems.clear();
                    gridItems.addAll(hs);
                    adapter = new SimilarImageAdapter(MainActivity.this, gridItems);
                    gridView.invalidateViews();
                    gridView.setAdapter(adapter);
                }
            });
        }
    }


    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
    private ArrayList<String> getAllShownImagesPath() {
        Uri uri;
        Cursor cursor;
        int column_index_data, column_index_folder_name;
        ArrayList<String> listOfAllImages = new ArrayList<String>();
        String absolutePathOfImage = null;
        uri = android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI;

        String[] projection = {MediaStore.Images.Media.DATA};
        final String[] selectionArgs = {CAMERA_IMAGE_BUCKET_ID};
        final String selection = MediaStore.Images.Media.BUCKET_ID + " = ?";
        cursor = getContentResolver().query(uri, projection, selection, selectionArgs,
                null, null);

        if (cursor != null) {
            column_index_data = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA);
            while (cursor.moveToNext()) {
                absolutePathOfImage = cursor.getString(column_index_data);

                listOfAllImages.add(absolutePathOfImage);
            }
        }
        return listOfAllImages;
    }

    public static final String CAMERA_IMAGE_BUCKET_NAME =
            Environment.getExternalStorageDirectory().toString()
                    + "/DCIM/Camera";
    public static final String CAMERA_IMAGE_BUCKET_ID =
            getBucketId(CAMERA_IMAGE_BUCKET_NAME);

    /**
     * Matches code in MediaProvider.computeBucketValues. Should be a common
     * function.
     */
    public static String getBucketId(String path) {
        return String.valueOf(path.toLowerCase().hashCode());
    }
}
