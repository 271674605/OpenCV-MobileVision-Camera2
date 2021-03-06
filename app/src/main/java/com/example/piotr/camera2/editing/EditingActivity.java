package com.example.piotr.camera2.editing;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.PointF;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import com.example.piotr.camera2.R;
import com.example.piotr.camera2.scanning.ScanningActivity;
import com.example.piotr.camera2.utils.DrawingUtils;
import com.example.piotr.camera2.utils.GlobalVars;
import com.example.piotr.camera2.utils.OpenCVInitializer;
import org.opencv.android.Utils;
import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;

public class EditingActivity extends AppCompatActivity {

    private static final String TAG = "EditingActivity";

    public static final String EXTRA_CONTOURS = "com.example.piotr.camera2.editing.CONTOURS";

    private EditingView editingView;

    private Mat rgba;
    private ArrayList<PointF> quadF;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_editing);

        Intent intent = getIntent();
        if(intent == null) {
            finish();
            return;
        }

        editingView = findViewById(R.id.editing_view);

        rgba = GlobalVars.mat;
        quadF = intent.getParcelableArrayListExtra(ScanningActivity.EXTRA_CONTOURS);
        if(quadF == null)
            return;

        final boolean rotate90fix = intent.getBooleanExtra(ScanningActivity.EXTRA_ROTATE90FIX, false);

        editingView.setRotate90Fix(rotate90fix);
        editingView.setNewImageWithContours(rgba, quadF);
    }

    @Override
    protected void onResume() {
        super.onResume();
        OpenCVInitializer.init();
    }

    private ArrayList<Point> getQuadPointsInClockwiseOrder() {
        ArrayList<Point> points = new ArrayList<>(4);
        PointF topleft = quadF.stream().min((o1, o2) -> Float.compare(o1.x + o1.y, o2.x + o2.y)).orElseGet(PointF::new);
        points.add(new Point(topleft.x, topleft.y));

        PointF topright = quadF.stream().max((o1, o2) -> Float.compare(o1.x - o1.y, o2.x - o2.y)).orElseGet(PointF::new);
        points.add(new Point(topright.x, topright.y));

        PointF bottomright = quadF.stream().max((o1, o2) -> Float.compare(o1.x + o1.y, o2.x + o2.y)).orElseGet(PointF::new);
        points.add(new Point(bottomright.x, bottomright.y));

        PointF bottomleft = quadF.stream().min((o1, o2) -> Float.compare(o1.x - o1.y, o2.x - o2.y)).orElseGet(PointF::new);
        points.add(new Point(bottomleft.x, bottomleft.y));

        return points;
    }

    private ArrayList<Point> getRectPointsInClockwiseOrder(int width, int height) {
        ArrayList<Point> points = new ArrayList<>(4);
        points.add(new Point(0.0, 0.0));
        points.add(new Point(width - 1, 0.0));
        points.add(new Point(width - 1, height - 1));
        points.add(new Point(0.0, height - 1));

        return points;
    }

    private void transformBitmap() {

        MatOfPoint2f sourcePointsMat = new MatOfPoint2f();
        ArrayList<Point> sourcePoints = getQuadPointsInClockwiseOrder();
        sourcePointsMat.fromList(sourcePoints);

        RotatedRect boundingBox = Imgproc.minAreaRect(sourcePointsMat);
        int width = (int)boundingBox.size.width;
        int height = (int)boundingBox.size.height;

        MatOfPoint2f targetPointsMat = new MatOfPoint2f();
        ArrayList<Point> targetPoints = getRectPointsInClockwiseOrder(width, height);
        targetPointsMat.fromList(targetPoints);

        Mat transformation = Imgproc.getPerspectiveTransform(sourcePointsMat, targetPointsMat);


        Mat sourceMat = rgba;

        Mat targetMat = new Mat(width, height, CvType.CV_8UC4);

        Imgproc.warpPerspective(sourceMat, targetMat, transformation, new Size(width, height));

        quadF = new ArrayList<>(4);
        for(Point p : targetPointsMat.toList()) {
            quadF.add(new PointF((float)p.x, (float)p.y));
        }

        editingView.setScale(DrawingUtils.Scale.FIT);
        editingView.setNewImageWithContours(targetMat, quadF);
    }

    public void btnDone(View view) {
        transformBitmap();
    }
}
