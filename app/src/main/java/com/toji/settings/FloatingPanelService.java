
package com.toji.settings;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

public class FloatingPanelService extends Service {

    private WindowManager wm;
    private WindowManager.LayoutParams params;

    private LinearLayout root;
    private LinearLayout panel;
    private TextView title;
    private TextView status;
    private EditText widthInput;
    private EditText heightInput;

    private final Handler handler =
            new Handler(Looper.getMainLooper());

    private Runnable autoRestore;

    private boolean expanded = false;
    private boolean pending = false;
    private boolean busy = false;

    private static final int TEST_TIME = 15000;

    private int dp(int value) {
        return (int) (value *
            getResources().getDisplayMetrics().density + 0.5f);
    }

    private GradientDrawable bg(int color, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        return d;
    }

    private TextView text(String value, int size) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextColor(Color.WHITE);
        t.setTextSize(size);
        return t;
    }

    private TextView button(String label, Runnable action) {
        TextView b = text(label, 14);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(12), dp(13), dp(12), dp(13));
        b.setBackground(bg(0xFF263244, 12));
        b.setOnClickListener(v -> action.run());

        LinearLayout.LayoutParams lp =
            new LinearLayout.LayoutParams(
                -1, -2
            );
        lp.bottomMargin = dp(8);
        panel.addView(b, lp);

        return b;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        if (!Settings.canDrawOverlays(this)) {
            stopSelf();
            return;
        }

        wm = (WindowManager)
                getSystemService(WINDOW_SERVICE);

        int type = Build.VERSION.SDK_INT >= 26
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        params = new WindowManager.LayoutParams(
                -2,
                -2,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                    | WindowManager.LayoutParams.FLAG_SPLIT_TOUCH,
                PixelFormat.TRANSLUCENT
        );

        params.gravity = Gravity.TOP | Gravity.START;
        params.x = dp(12);
        params.y = dp(180);

        createInterface();

        try {
            wm.addView(root, params);
        } catch (Exception e) {
            stopSelf();
        }
    }

    private void createInterface() {

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setBackground(bg(0xFF141826, 22));
        header.setPadding(dp(4), dp(4), dp(10), dp(4));

        TextView bubble = text("T", 20);
        bubble.setGravity(Gravity.CENTER);
        bubble.setTypeface(null, 1);
        bubble.setBackground(bg(0xFF7545ED, 40));

        header.addView(bubble,
            new LinearLayout.LayoutParams(dp(48), dp(48)));

        title = text("  TOJI  |  STRETCH", 14);
        title.setTypeface(null, 1);
        title.setVisibility(View.GONE);
        header.addView(title);

        root.addView(header);
        configureDrag(header);

        panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(14), dp(14), dp(14), dp(10));
        panel.setBackground(bg(0xFF111827, 16));
        panel.setVisibility(View.GONE);

        root.addView(panel,
            new LinearLayout.LayoutParams(dp(290), -2));

        TextView heading = text("RESOLUCAO", 17);
        heading.setTypeface(null, 1);
        heading.setPadding(0, 0, 0, dp(12));
        panel.addView(heading);

        LinearLayout inputs = new LinearLayout(this);
        inputs.setOrientation(LinearLayout.HORIZONTAL);

        widthInput = makeInput("1080");
        heightInput = makeInput("2400");

        LinearLayout.LayoutParams ip =
            new LinearLayout.LayoutParams(0, dp(50), 1);
        ip.rightMargin = dp(6);
        inputs.addView(widthInput, ip);

        inputs.addView(heightInput,
            new LinearLayout.LayoutParams(0, dp(50), 1));

        panel.addView(inputs);

        TextView hint = text(
            "Largura                 Altura", 12
        );
        hint.setTextColor(0xFF9CA3AF);
        hint.setPadding(0, dp(4), 0, dp(12));
        panel.addView(hint);

        TextView preset = button(
            "TESTAR 1080 x 2400",
            () -> applyResolution(1080, 2400)
        );
        preset.setBackground(bg(0xFF7545ED, 12));

        button("TESTAR PERSONALIZADA", () -> {
            try {
                int w = Integer.parseInt(
                    widthInput.getText().toString().trim()
                );
                int h = Integer.parseInt(
                    heightInput.getText().toString().trim()
                );

                applyResolution(w, h);

            } catch (NumberFormatException e) {
                updateStatus("Digite largura e altura validas.");
            }
        });

        button("MANTER RESOLUCAO", () -> {
            if (busy || !pending) {
                updateStatus("Nenhum teste pendente.");
                return;
            }

            pending = false;
            cancelTimer();
            updateStatus("Resolucao mantida. Restaure quando quiser.");
        });

        button("RESTAURAR TELA", () -> {
            restoreResolution(false);
        });

        button("FECHAR PAINEL", () -> {
            if (busy) {
                updateStatus("Aguarde o comando terminar.");
                return;
            }

            if (pending) {
                restoreResolution(true);
            } else {
                stopSelf();
            }
        });

        status = text("Pronto para testar.", 12);
        status.setTextColor(0xFFA5B4FC);
        status.setPadding(0, dp(8), 0, dp(6));
        panel.addView(status);
    }

    private EditText makeInput(String initial) {
        EditText e = new EditText(this);

        e.setSingleLine(true);
        e.setText(initial);
        e.setTextColor(Color.WHITE);
        e.setTextSize(15);
        e.setSelectAllOnFocus(true);
        e.setGravity(Gravity.CENTER);
        e.setInputType(InputType.TYPE_CLASS_NUMBER);
        e.setBackground(bg(0xFF263244, 10));

        e.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                setOverlayFocusable(true);
                e.requestFocus();

                e.postDelayed(() -> {
                    InputMethodManager imm =
                        (InputMethodManager)
                        getSystemService(INPUT_METHOD_SERVICE);

                    if (imm != null) {
                        imm.showSoftInput(
                            e,
                            InputMethodManager.SHOW_IMPLICIT
                        );
                    }
                }, 150);
            }
            return false;
        });

        return e;
    }

    private void setOverlayFocusable(boolean focusable) {
        if (params == null || wm == null || root == null) {
            return;
        }

        if (focusable) {
            params.flags &=
                ~WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        } else {
            params.flags |=
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        }

        params.flags |=
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;

        try {
            wm.updateViewLayout(root, params);
        } catch (Exception ignored) {
        }
    }

    private void hideKeyboard() {
        if (root == null) return;

        InputMethodManager imm =
            (InputMethodManager)
            getSystemService(INPUT_METHOD_SERVICE);

        if (imm != null) {
            imm.hideSoftInputFromWindow(
                root.getWindowToken(), 0
            );
        }

        root.clearFocus();
        setOverlayFocusable(false);
    }

    private void configureDrag(View handle) {

        final int slop =
            ViewConfiguration.get(this).getScaledTouchSlop();

        handle.setOnTouchListener(new View.OnTouchListener() {

            private float downX;
            private float downY;
            private int initialX;
            private int initialY;
            private boolean moved;

            @Override
            public boolean onTouch(View v, MotionEvent e) {
                switch (e.getActionMasked()) {

                    case MotionEvent.ACTION_DOWN:
                        downX = e.getRawX();
                        downY = e.getRawY();
                        initialX = params.x;
                        initialY = params.y;
                        moved = false;
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        float dx = e.getRawX() - downX;
                        float dy = e.getRawY() - downY;

                        if (Math.abs(dx) > slop ||
                            Math.abs(dy) > slop) {
                            moved = true;
                        }

                        if (moved) {
                            params.x = initialX + (int) dx;
                            params.y = initialY + (int) dy;

                            try {
                                wm.updateViewLayout(root, params);
                            } catch (Exception ignored) {
                            }
                        }
                        return true;

                    case MotionEvent.ACTION_UP:
                        if (!moved) {
                            togglePanel();
                        }
                        return true;

                    case MotionEvent.ACTION_CANCEL:
                        return true;
                }

                return false;
            }
        });
    }

    private void togglePanel() {
        expanded = !expanded;

        hideKeyboard();

        panel.setVisibility(
            expanded ? View.VISIBLE : View.GONE
        );

        title.setVisibility(
            expanded ? View.VISIBLE : View.GONE
        );

        try {
            wm.updateViewLayout(root, params);
        } catch (Exception ignored) {
        }
    }

    private void updateStatus(String message) {
        if (status != null) {
            status.setText(message);
        }
    }

    private void cancelTimer() {
        if (autoRestore != null) {
            handler.removeCallbacks(autoRestore);
            autoRestore = null;
        }
    }

    private void applyResolution(int w, int h) {

        if (busy) {
            updateStatus("Comando em andamento.");
            return;
        }

        if (pending) {
            updateStatus("Confirme ou restaure o teste anterior.");
            return;
        }

        if (w < 600 || w > 3000 ||
            h < 600 || h > 4000) {
            updateStatus("Resolucao fora dos limites.");
            return;
        }

        hideKeyboard();

        busy = true;
        updateStatus("Aplicando via Shizuku...");

        new Thread(() -> {

            ResolutionController.Result result =
                ResolutionController.apply(
                    getApplicationContext(), w, h
                );

            handler.post(() -> {
                busy = false;

                if (!result.ok) {
                    updateStatus(result.message);
                    return;
                }

                pending = true;

                updateStatus(
                    "Teste ativo por 15 segundos. " +
                    "Toque MANTER para confirmar."
                );

                cancelTimer();

                autoRestore = () -> {
                    if (pending) {
                        restoreResolution(false);
                    }
                };

                handler.postDelayed(autoRestore, TEST_TIME);
            });

        }, "Toji-Apply").start();
    }

    private void restoreResolution(boolean closeAfter) {

        if (busy) {
            updateStatus("Aguarde o comando terminar.");
            return;
        }

        busy = true;
        pending = false;

        cancelTimer();
        hideKeyboard();

        updateStatus("Restaurando resolucao...");

        new Thread(() -> {

            ResolutionController.Result result =
                ResolutionController.restore(
                    getApplicationContext()
                );

            handler.post(() -> {
                busy = false;
                updateStatus(result.message);

                if (closeAfter && result.ok) {
                    stopSelf();
                }
            });

        }, "Toji-Restore").start();
    }

    @Override
    public int onStartCommand(
        Intent intent, int flags, int startId
    ) {
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        boolean needsRestore = pending;

        pending = false;
        cancelTimer();

        if (needsRestore) {
            new Thread(() ->
                ResolutionController.restore(
                    getApplicationContext()
                ), "Toji-Exit-Restore").start();
        }

        if (wm != null && root != null) {
            try {
                wm.removeView(root);
            } catch (Exception ignored) {
            }
        }

        super.onDestroy();
    }
}
