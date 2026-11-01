
package com.toji.settings;

import android.app.Service;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.IBinder;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.text.InputType;

public class FloatingPanelService extends Service {
    private WindowManager wm;
    private LinearLayout root;
    private LinearLayout controls;
    private WindowManager.LayoutParams params;
    private boolean minimized = false;

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private GradientDrawable background(int color) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(14));
        return drawable;
    }

    private TextView button(String label, LinearLayout parent) {
        TextView view = new TextView(this);
        view.setText(label);
        view.setTextColor(Color.WHITE);
        view.setTextSize(13);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(12), dp(12), dp(12), dp(12));
        view.setBackground(background(0xff7532b7));

        LinearLayout.LayoutParams lp =
            new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(7);
        parent.addView(view, lp);
        return view;
    }

    private void message(String text) {
        new Handler(getMainLooper()).post(() ->
            Toast.makeText(this, text, Toast.LENGTH_LONG).show()
        );
    }

    private void runCommand(boolean restore, int width, int height) {
        new Thread(() -> {
            ResolutionController.Result result = restore
                ? ResolutionController.restore(this)
                : ResolutionController.apply(this, width, height);

            message(result.message);
        }).start();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!Settings.canDrawOverlays(this)) {
            stopSelf();
            return START_NOT_STICKY;
        }

        if (root != null) return START_NOT_STICKY;

        wm = (WindowManager) getSystemService(WINDOW_SERVICE);

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(12), dp(10), dp(12), dp(12));
        root.setBackground(background(0xff211b2c));

        TextView title = new TextView(this);
        title.setText("☷ TOJI  •  BLOOD STRIKE");
        title.setTextColor(0xffe5b7ff);
        title.setTextSize(13);
        title.setGravity(Gravity.CENTER);
        title.setPadding(dp(8), dp(10), dp(8), dp(10));
        root.addView(title);

        controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.VERTICAL);
        root.addView(controls);

        TextView minimize = button("MINIMIZAR / EXPANDIR", controls);
        TextView note = new TextView(this);
        note.setText("A resolução altera todo o Android.");
        note.setTextColor(Color.LTGRAY);
        note.setTextSize(11);
        controls.addView(note);

        EditText width = new EditText(this);
        width.setHint("Largura: 1080");
        width.setText("1080");
        width.setSingleLine(true);
        width.setTextColor(Color.WHITE);
        width.setHintTextColor(Color.LTGRAY);
        width.setInputType(InputType.TYPE_CLASS_NUMBER);
        controls.addView(width);

        EditText height = new EditText(this);
        height.setHint("Altura: 1920");
        height.setText("1920");
        height.setSingleLine(true);
        height.setTextColor(Color.WHITE);
        height.setHintTextColor(Color.LTGRAY);
        height.setInputType(InputType.TYPE_CLASS_NUMBER);
        controls.addView(height);

        TextView apply = button("APLICAR RESOLUÇÃO", controls);
        TextView restore = button("RESTAURAR TELA", controls);
        TextView game = button("ABRIR BLOOD STRIKE", controls);
        TextView close = button("FECHAR PAINEL", controls);

        minimize.setOnClickListener(v -> toggle());

        title.setOnClickListener(v -> toggle());

        apply.setOnClickListener(v -> {
            try {
                int w = Integer.parseInt(width.getText().toString());
                int h = Integer.parseInt(height.getText().toString());

                if (w < 600 || w > 3000 || h < 600 || h > 4000) {
                    message("Resolução fora dos limites permitidos.");
                    return;
                }

                runCommand(false, w, h);
            } catch (NumberFormatException e) {
                message("Digite largura e altura válidas.");
            }
        });

        restore.setOnClickListener(v -> runCommand(true, 0, 0));

        game.setOnClickListener(v -> {
            Intent launch = getPackageManager()
                .getLaunchIntentForPackage("com.netease.newspike");

            if (launch != null) {
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try {
                    startActivity(launch);
                } catch (Exception e) {
                    message("Abra o Blood Strike manualmente.");
                }
            } else {
                message("Blood Strike não encontrado.");
            }
        });

        close.setOnClickListener(v -> stopSelf());

        params = new WindowManager.LayoutParams(
            dp(240),
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        );

        params.gravity = Gravity.TOP | Gravity.START;
        params.x = dp(20);
        params.y = dp(100);

        title.setOnTouchListener(new View.OnTouchListener() {
            float downX, downY;
            int startX, startY;
            boolean moved;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        downX = event.getRawX();
                        downY = event.getRawY();
                        startX = params.x;
                        startY = params.y;
                        moved = false;
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        if (Math.abs(event.getRawX() - downX) > dp(6)
                            || Math.abs(event.getRawY() - downY) > dp(6)) {
                            moved = true;
                        }

                        if (moved) {
                            params.x = startX + (int) (event.getRawX() - downX);
                            params.y = startY + (int) (event.getRawY() - downY);
                            wm.updateViewLayout(root, params);
                        }
                        return true;

                    case MotionEvent.ACTION_UP:
                        if (!moved) toggle();
                        return true;
                }
                return true;
            }
        });

        try {
            wm.addView(root, params);
        } catch (Exception e) {
            root = null;
            stopSelf();
        }

        return START_NOT_STICKY;
    }

    private void toggle() {
        minimized = !minimized;
        controls.setVisibility(minimized ? View.GONE : View.VISIBLE);
        if (root != null && wm != null) {
            wm.updateViewLayout(root, params);
        }
    }

    @Override
    public void onDestroy() {
        if (root != null && wm != null) {
            wm.removeView(root);
            root = null;
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
