
package com.toji.settings;

import android.app.Service;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class FloatingPanelService extends Service {

    private WindowManager wm;
    private WindowManager.LayoutParams params;

    private LinearLayout root;
    private LinearLayout panel;
    private TextView bubble;

    private EditText widthInput;
    private EditText heightInput;
    private TextView status;

    private boolean expanded = false;
    private boolean busy = false;

    private final Handler handler =
            new Handler(Looper.getMainLooper());

    private int dp(int value) {
        return (int) (value *
                getResources().getDisplayMetrics().density + 0.5f);
    }

    private GradientDrawable shape(
            int color, int radius) {

        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        return drawable;
    }

    private TextView makeButton(
            String text, LinearLayout parent) {

        TextView button = new TextView(this);
        button.setText(text);
        button.setTextColor(Color.WHITE);
        button.setTextSize(13);
        button.setGravity(Gravity.CENTER);
        button.setPadding(
                dp(10), dp(12), dp(10), dp(12));
        button.setBackground(shape(0xff7435ad, 12));

        LinearLayout.LayoutParams lp =
                new LinearLayout.LayoutParams(
                        -1, -2);
        lp.topMargin = dp(8);

        parent.addView(button, lp);
        return button;
    }

    private void toast(String text) {
        handler.post(() ->
                Toast.makeText(
                        this, text, Toast.LENGTH_LONG).show());
    }

    private void setStatus(String text) {
        handler.post(() -> {
            if (status != null) {
                status.setText(text);
            }
        });
    }

    private void hideKeyboard() {
        try {
            InputMethodManager imm =
                    (InputMethodManager)
                            getSystemService(
                                    Context.INPUT_METHOD_SERVICE);

            if (imm != null && root != null) {
                imm.hideSoftInputFromWindow(
                        root.getWindowToken(), 0);
            }

            if (root != null) {
                root.clearFocus();
            }
        } catch (Exception ignored) {
        }
    }

    private void updateWindow() {
        if (root == null || wm == null) {
            return;
        }

        try {
            wm.updateViewLayout(root, params);
        } catch (Exception ignored) {
        }
    }

    private void setExpanded(boolean open) {
        expanded = open;

        if (expanded) {
            bubble.setVisibility(View.GONE);
            panel.setVisibility(View.VISIBLE);

            params.width = dp(270);
            params.height =
                    WindowManager.LayoutParams.WRAP_CONTENT;

            params.flags &=
                    ~WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;

            params.softInputMode =
                    WindowManager.LayoutParams
                            .SOFT_INPUT_ADJUST_RESIZE;

        } else {
            hideKeyboard();

            panel.setVisibility(View.GONE);
            bubble.setVisibility(View.VISIBLE);

            params.width = dp(62);
            params.height = dp(62);

            params.flags |=
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        }

        updateWindow();
    }

    private void runCommand(
            boolean restore, int width, int height) {

        if (busy) {
            toast("Aguarde o comando anterior.");
            return;
        }

        busy = true;

        setStatus("Executando pelo Shizuku...");

        new Thread(() -> {

            ResolutionController.Result result;

            try {
                result = restore
                        ? ResolutionController.restore(this)
                        : ResolutionController.apply(
                                this, width, height);
            } catch (Throwable e) {
                result = new ResolutionController.Result(
                        false,
                        "Erro: " + e.getMessage());
            }

            final ResolutionController.Result response =
                    result;

            handler.post(() -> {
                busy = false;

                String message =
                        (response.ok ? "✓ " : "✕ ")
                                + response.message;

                setStatus(message);
                toast(message);
            });

        }).start();
    }

    private void enableDrag(View target) {
        target.setOnTouchListener(
                new View.OnTouchListener() {

            float downX;
            float downY;
            int startX;
            int startY;
            boolean moved;

            @Override
            public boolean onTouch(
                    View v, MotionEvent event) {

                switch (event.getActionMasked()) {

                    case MotionEvent.ACTION_DOWN:
                        downX = event.getRawX();
                        downY = event.getRawY();

                        startX = params.x;
                        startY = params.y;

                        moved = false;
                        return true;

                    case MotionEvent.ACTION_MOVE:

                        float dx =
                                event.getRawX() - downX;
                        float dy =
                                event.getRawY() - downY;

                        if (Math.abs(dx) > dp(8)
                                || Math.abs(dy) > dp(8)) {
                            moved = true;
                        }

                        if (moved) {
                            params.x = startX + (int) dx;
                            params.y = startY + (int) dy;
                            updateWindow();
                        }

                        return true;

                    case MotionEvent.ACTION_UP:

                        if (!moved) {
                            setExpanded(!expanded);
                        }

                        return true;

                    case MotionEvent.ACTION_CANCEL:
                        return true;
                }

                return false;
            }
        );
    }

    @Override
    public int onStartCommand(
            Intent intent, int flags, int startId) {

        if (!Settings.canDrawOverlays(this)) {
            toast("Autorize a sobreposição de tela.");
            stopSelf();
            return START_NOT_STICKY;
        }

        if (root != null) {
            return START_NOT_STICKY;
        }

        wm = (WindowManager)
                getSystemService(WINDOW_SERVICE);

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        bubble = new TextView(this);
        bubble.setText("TOJI");
        bubble.setTextColor(Color.WHITE);
        bubble.setTextSize(13);
        bubble.setTypeface(
                null, android.graphics.Typeface.BOLD);
        bubble.setGravity(Gravity.CENTER);

        GradientDrawable bubbleBackground =
                shape(0xff261a38, 100);

        bubbleBackground.setStroke(
                dp(2), 0xffb678ff);

        bubble.setBackground(bubbleBackground);

        root.addView(
                bubble,
                new LinearLayout.LayoutParams(
                        dp(62), dp(62)));

        panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(
                dp(14), dp(12), dp(14), dp(14));
        panel.setBackground(shape(0xff211b2c, 16));
        panel.setVisibility(View.GONE);

        root.addView(panel);

        TextView title = new TextView(this);
        title.setText("☷ TOJI • BLOOD STRIKE");
        title.setTextColor(0xffe5b7ff);
        title.setTextSize(14);
        title.setGravity(Gravity.CENTER);
        title.setPadding(
                dp(8), dp(10), dp(8), dp(12));

        panel.addView(title);

        TextView note = new TextView(this);
        note.setText(
                "A resolução altera a tela inteira do Android.");
        note.setTextColor(Color.LTGRAY);
        note.setTextSize(11);
        panel.addView(note);

        widthInput = new EditText(this);
        widthInput.setText("1080");
        widthInput.setHint("Largura");
        widthInput.setSingleLine(true);
        widthInput.setTextColor(Color.WHITE);
        widthInput.setHintTextColor(Color.LTGRAY);
        widthInput.setTextSize(16);
        widthInput.setSelectAllOnFocus(true);
        widthInput.setInputType(
                InputType.TYPE_CLASS_NUMBER);
        panel.addView(widthInput);

        heightInput = new EditText(this);
        heightInput.setText("1920");
        heightInput.setHint("Altura");
        heightInput.setSingleLine(true);
        heightInput.setTextColor(Color.WHITE);
        heightInput.setHintTextColor(Color.LTGRAY);
        heightInput.setTextSize(16);
        heightInput.setSelectAllOnFocus(true);
        heightInput.setInputType(
                InputType.TYPE_CLASS_NUMBER);
        panel.addView(heightInput);

        TextView apply =
                makeButton("APLICAR RESOLUÇÃO", panel);

        TextView restore =
                makeButton("RESTAURAR TELA", panel);

        TextView game =
                makeButton("ABRIR BLOOD STRIKE", panel);

        TextView minimize =
                makeButton("MINIMIZAR", panel);

        TextView close =
                makeButton("FECHAR PAINEL", panel);

        status = new TextView(this);
        status.setText(
                "Pronto para aplicar resolução.");
        status.setTextColor(0xffd7c5e9);
        status.setTextSize(12);
        status.setPadding(
                0, dp(12), 0, dp(4));

        panel.addView(status);

        apply.setOnClickListener(v -> {
            try {
                int width = Integer.parseInt(
                        widthInput.getText()
                                .toString().trim());

                int height = Integer.parseInt(
                        heightInput.getText()
                                .toString().trim());

                if (width < 600 || width > 3000
                        || height < 600 || height > 4000) {
                    toast("Resolução fora dos limites.");
                    return;
                }

                hideKeyboard();
                runCommand(false, width, height);

            } catch (NumberFormatException e) {
                toast("Digite números válidos.");
            }
        });

        restore.setOnClickListener(v -> {
            hideKeyboard();
            runCommand(true, 0, 0);
        });

        game.setOnClickListener(v -> {
            Intent launch = getPackageManager()
                    .getLaunchIntentForPackage(
                            "com.netease.newspike");

            if (launch != null) {
                launch.addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK);

                try {
                    startActivity(launch);
                    setExpanded(false);
                } catch (Exception e) {
                    toast("Abra o Blood Strike manualmente.");
                }
            } else {
                toast("Blood Strike não encontrado.");
            }
        });

        minimize.setOnClickListener(v ->
                setExpanded(false));

        close.setOnClickListener(v ->
                stopSelf());

        enableDrag(bubble);
        enableDrag(title);

        params = new WindowManager.LayoutParams(
                dp(62),
                dp(62),
                WindowManager.LayoutParams
                        .TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams
                        .FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );

        params.gravity =
                Gravity.TOP | Gravity.START;

        params.x = dp(20);
        params.y = dp(110);

        try {
            wm.addView(root, params);
        } catch (Exception e) {
            root = null;
            stopSelf();
        }

        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        hideKeyboard();

        if (root != null && wm != null) {
            try {
                wm.removeView(root);
            } catch (Exception ignored) {
            }
            root = null;
        }

        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
