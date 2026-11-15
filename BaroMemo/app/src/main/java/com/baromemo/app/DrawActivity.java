package com.baromemo.app;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/** 손가락으로 간단히 그려서 메모에 붙이는 화면. */
public class DrawActivity extends Activity {
    public static final String EXTRA_TEXT = "text";

    private static final int[] COLORS = {0xFF222222, 0xFFE5484D, 0xFF3D7DF5, 0xFF2BB39A, 0xFFF5A623};
    private static final float[] WIDTHS = {4f, 9f, 16f};

    private DrawView canvas;
    private final List<TextView> colorButtons = new ArrayList<>();
    private TextView widthButton;
    private int widthIndex = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Ui.applyPalette(this);
        super.onCreate(savedInstanceState);

        FrameLayout root = new FrameLayout(this);
        root.setFitsSystemWindows(true);
        root.setBackgroundColor(getColor(R.color.bg));

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(12), dp(8), dp(12), dp(12));
        root.addView(box, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // 위 줄: 취소 / 되돌리기 / 모두 지우기 / 저장
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(textButton("취소", v -> finish()), weight());
        top.addView(textButton("되돌리기", v -> canvas.undo()), weight());
        top.addView(textButton("모두 지우기", v -> canvas.clear()), weight());
        TextView save = textButton("저장", v -> saveAndClose());
        save.setBackgroundResource(R.drawable.bg_btn_accent);
        save.setTextColor(Ui.onAccent(this));
        top.addView(save, weight());
        box.addView(top, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)));

        // 그리는 곳
        canvas = new DrawView(this);
        LinearLayout.LayoutParams canvasLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        canvasLp.topMargin = dp(10);
        canvasLp.bottomMargin = dp(10);
        box.addView(canvas, canvasLp);

        // 아래 줄: 색 / 굵기
        LinearLayout bottom = new LinearLayout(this);
        bottom.setGravity(Gravity.CENTER_VERTICAL);
        for (final int color : COLORS) {
            TextView dot = new TextView(this);
            dot.setText("●");
            dot.setTextSize(26);
            dot.setTextColor(color);
            dot.setGravity(Gravity.CENTER);
            dot.setOnClickListener(v -> pickColor(color));
            colorButtons.add(dot);
            bottom.addView(dot, weight());
        }
        widthButton = textButton("굵기", v -> {
            widthIndex = (widthIndex + 1) % WIDTHS.length;
            applyWidth();
        });
        bottom.addView(widthButton, weight());
        box.addView(bottom, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));

        setContentView(root);
        pickColor(COLORS[0]);
        applyWidth();
    }

    private void pickColor(int color) {
        canvas.color = color;
        for (int i = 0; i < colorButtons.size(); i++) {
            colorButtons.get(i).setAlpha(COLORS[i] == color ? 1f : 0.35f);
        }
    }

    private void applyWidth() {
        canvas.strokeWidth = WIDTHS[widthIndex] * getResources().getDisplayMetrics().density;
        widthButton.setText(widthIndex == 0 ? "굵기: 가늘게" : widthIndex == 1 ? "굵기: 보통" : "굵기: 굵게");
    }

    private void saveAndClose() {
        if (canvas.isEmpty()) {
            Toast.makeText(this, "그린 게 없어요", Toast.LENGTH_SHORT).show();
            return;
        }
        String name = NoteStore.saveBitmap(this, canvas.toBitmap());
        if (name == null) {
            Toast.makeText(this, "그림을 저장하지 못했어요", Toast.LENGTH_SHORT).show();
            return;
        }
        NoteStore.add(this, getIntent().getStringExtra(EXTRA_TEXT), name);
        setResult(RESULT_OK);
        finish();
    }

    private TextView textButton(String label, View.OnClickListener onClick) {
        TextView t = new TextView(this);
        t.setText(label);
        t.setTextSize(14);
        t.setGravity(Gravity.CENTER);
        t.setTextColor(getColor(R.color.text));
        t.setOnClickListener(onClick);
        return t;
    }

    private LinearLayout.LayoutParams weight() {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    /** 흰 종이에 선을 긋는 뷰. */
    private static class DrawView extends View {
        int color = 0xFF222222;
        float strokeWidth = 9f;

        private final List<Path> paths = new ArrayList<>();
        private final List<Paint> paints = new ArrayList<>();
        private Path current;

        DrawView(Context ctx) {
            super(ctx);
            setBackgroundColor(Color.WHITE);
        }

        boolean isEmpty() {
            return paths.isEmpty();
        }

        void undo() {
            if (paths.isEmpty()) return;
            paths.remove(paths.size() - 1);
            paints.remove(paints.size() - 1);
            invalidate();
        }

        void clear() {
            paths.clear();
            paints.clear();
            invalidate();
        }

        /** 그린 부분만 남기고 빈 여백은 잘라 낸 그림. */
        Bitmap toBitmap() {
            int w = Math.max(1, getWidth());
            int h = Math.max(1, getHeight());

            // 선이 지나간 영역 구하기
            RectF all = new RectF();
            RectF one = new RectF();
            float maxStroke = 0f;
            boolean first = true;
            for (int i = 0; i < paths.size(); i++) {
                paths.get(i).computeBounds(one, true);
                if (first) {
                    all.set(one);
                    first = false;
                } else {
                    all.union(one);
                }
                maxStroke = Math.max(maxStroke, paints.get(i).getStrokeWidth());
            }
            float pad = maxStroke + 24f * getResources().getDisplayMetrics().density;
            int left = Math.max(0, (int) Math.floor(all.left - pad));
            int top = Math.max(0, (int) Math.floor(all.top - pad));
            int right = Math.min(w, (int) Math.ceil(all.right + pad));
            int bottom = Math.min(h, (int) Math.ceil(all.bottom + pad));
            if (first || right - left < 1 || bottom - top < 1) {
                left = 0;
                top = 0;
                right = w;
                bottom = h;
            }

            Bitmap bmp = Bitmap.createBitmap(right - left, bottom - top, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(bmp);
            c.drawColor(Color.WHITE);
            c.translate(-left, -top);
            drawPaths(c);
            return bmp;
        }

        private void drawPaths(Canvas c) {
            for (int i = 0; i < paths.size(); i++) c.drawPath(paths.get(i), paints.get(i));
        }

        @Override
        protected void onDraw(Canvas c) {
            super.onDraw(c);
            drawPaths(c);
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            float x = e.getX();
            float y = e.getY();
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
                    p.setStyle(Paint.Style.STROKE);
                    p.setStrokeCap(Paint.Cap.ROUND);
                    p.setStrokeJoin(Paint.Join.ROUND);
                    p.setColor(color);
                    p.setStrokeWidth(strokeWidth);
                    current = new Path();
                    current.moveTo(x, y);
                    current.lineTo(x + 0.1f, y + 0.1f); // 점만 찍어도 보이게
                    paths.add(current);
                    paints.add(p);
                    invalidate();
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (current != null) {
                        current.lineTo(x, y);
                        invalidate();
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    current = null;
                    return true;
                default:
                    return super.onTouchEvent(e);
            }
        }
    }
}
