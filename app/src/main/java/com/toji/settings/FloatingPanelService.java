
package com.toji.settings;

import android.app.Service;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
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
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

public class FloatingPanelService extends Service {

    private WindowManager wm;
    private WindowManager.LayoutParams params;
    private LinearLayout root, header, controls;
    private ScrollView scroll;
    private TextView headerText, status, barText;
    private EditText inputW, inputH;
    private SeekBar stretchBar;

    private final Handler handler =
        new Handler(Looper.getMainLooper());

    private Runnable timer;
    private boolean expanded = false;
    private boolean busy = false;
    private boolean testPending = false;

    private int savedX, savedY;
    private int panelWidth;

    private static final int TEST_MS = 15000;

    private int dp(int n) {
        return (int) (
            n * getResources().getDisplayMetrics().density
            + 0.5f
        );
    }

    private GradientDrawable background(
        int color, int radius
    ) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        return d;
    }

    private TextView label(
        String s, int size, int color
    ) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER_VERTICAL);
        return t;
    }

    private TextView button(
        String text, int color, Runnable click
    ) {
        TextView b = label(text, 13, Color.WHITE);
        b.setGravity(Gravity.CENTER);
        b.setBackground(background(color, 11));
        b.setPadding(
            dp(8), dp(11), dp(8), dp(11)
        );

        LinearLayout.LayoutParams lp =
            new LinearLayout.LayoutParams(-1, -2);

        lp.bottomMargin = dp(7);
        controls.addView(b, lp);

        b.setOnClickListener(v -> click.run());
        return b;
    }

    private EditText number(String value) {
        EditText e = new EditText(this);

        e.setSingleLine(true);
        e.setText(value);
        e.setSelectAllOnFocus(true);
        e.setTextColor(Color.WHITE);
        e.setTextSize(15);
        e.setGravity(Gravity.CENTER);
        e.setInputType(InputType.TYPE_CLASS_NUMBER);
        e.setBackground(
            background(0xFF273347, 11)
        );

        e.setOnTouchListener((v, event) -> {
            if (
                event.getActionMasked()
                == MotionEvent.ACTION_DOWN
            ) {
                setFocusable(true);
            }
            return false;
        });

        return e;
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
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        );

        params.gravity = Gravity.TOP | Gravity.START;
        params.x = dp(12);
        params.y = dp(110);

        savedX = params.x;
        savedY = params.y;

        buildPanel();

        try {
            wm.addView(root, params);
        } catch (Exception e) {
            stopSelf();
        }
    }

    private void buildPanel() {
        panelWidth = Math.min(
            dp(280),
            Math.max(
                dp(210),
                getResources()
                    .getDisplayMetrics().widthPixels
                    - dp(28)
            )
        );

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(
            dp(4), dp(4), dp(9), dp(4)
        );
        header.setBackground(
            background(0xFF131724, 22)
        );

        TextView bubble =
            label("T", 19, Color.WHITE);

        bubble.setGravity(Gravity.CENTER);
        bubble.setTypeface(null, 1);
        bubble.setBackground(
            background(0xFF7844ED, 30)
        );

        header.addView(
            bubble,
            new LinearLayout.LayoutParams(
                dp(45), dp(45)
            )
        );

        headerText = label(
            "  ━━  TOJI STRETCH",
            12,
            Color.WHITE
        );
        headerText.setTypeface(null, 1);
        header.addView(headerText);

        root.addView(header);
        setupDrag(header);

        scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        scroll.setVerticalScrollBarEnabled(true);
        scroll.setBackground(
            background(0xFF101726, 15)
        );

        controls = new LinearLayout(this);
        controls.setOrientation(
            LinearLayout.VERTICAL
        );
        controls.setPadding(
            dp(12), dp(12), dp(12), dp(8)
        );

        scroll.addView(controls);
        root.addView(scroll);

        TextView heading = label(
            "TOJI  •  RESOLUÇÃO",
            16,
            Color.WHITE
        );
        heading.setTypeface(null, 1);
        heading.setPadding(
            0, 0, 0, dp(9)
        );
        controls.addView(heading);

        LinearLayout inputs =
            new LinearLayout(this);

        inputW = number("1080");
        inputH = number("1920");

        LinearLayout.LayoutParams il =
            new LinearLayout.LayoutParams(
                0, dp(44), 1
            );
        il.rightMargin = dp(7);

        inputs.addView(inputW, il);
        inputs.addView(
            inputH,
            new LinearLayout.LayoutParams(
                0, dp(44), 1
            )
        );

        controls.addView(inputs);

        TextView hint = label(
            "LARGURA                              ALTURA",
            10,
            0xFFAAB3C5
        );
        hint.setPadding(
            0, dp(3), 0, dp(12)
        );
        controls.addView(hint);

        // BARRINHA ROSA DE AJUSTE

        barText = label(
            "BARRINHA  •  ALTURA 1920",
            12,
            0xFFFF91CE
        );
        controls.addView(barText);

        stretchBar = new SeekBar(this);
        stretchBar.setMax(80);
        stretchBar.setProgress(16);

        stretchBar.setProgressTintList(
            ColorStateList.valueOf(0xFFFF5ABA)
        );
        stretchBar.setThumbTintList(
            ColorStateList.valueOf(0xFFFF5ABA)
        );
        controls.addView(stretchBar);

        TextView barHint = label(
            "1600                                       3200",
            11,
            0xFFAAB3C5
        );
        barHint.setPadding(
            0, 0, 0, dp(9)
        );
        controls.addView(barHint);

        stretchBar.setOnSeekBarChangeListener(
            new SeekBar.OnSeekBarChangeListener() {

                @Override
                public void onProgressChanged(
                    SeekBar s,
                    int progress,
                    boolean fromUser
                ) {
                    int h = 1600 + progress * 20;

                    barText.setText(
                        "BARRINHA  •  ALTURA " + h
                    );

                    if (fromUser) {
                        inputH.setText(
                            String.valueOf(h)
                        );
                    }
                }

                @Override
                public void onStartTrackingTouch(
                    SeekBar s
                ) {}

                @Override
                public void onStopTrackingTouch(
                    SeekBar s
                ) {}
            }
        );

        // CONTROLES

        button(
            "APLICAR / TESTAR RESOLUÇÃO",
            0xFF7742E7,
            () -> {
                try {
                    int w = Integer.parseInt(
                        inputW.getText()
                            .toString().trim()
                    );

                    int h = Integer.parseInt(
                        inputH.getText()
                            .toString().trim()
                    );

                    apply(w, h);

                } catch (NumberFormatException e) {
                    showStatus(
                        "Digite dois números válidos."
                    );
                }
            }
        );

        button(
            "MANTER RESOLUÇÃO",
            0xFF273347,
            () -> {
                if (busy || !testPending) {
                    showStatus(
                        "Nenhum teste aguardando confirmação."
                    );
                    return;
                }

                cancelTimer();
                testPending = false;

                showStatus(
                    "Resolução mantida. Use Restaurar para voltar."
                );
            }
        );

        button(
            "RESTAURAR TELA",
            0xFF273347,
            () -> restore(false)
        );

        button(
            "ABRIR BLOOD STRIKE",
            0xFF273347,
            () -> openBloodStrike()
        );

        button(
            "MINIMIZAR PARA BOLINHA",
            0xFF273347,
            () -> setExpanded(false)
        );

        button(
            "FECHAR TUDO E RESTAURAR",
            0xFF572A39,
            () -> restore(true)
        );

        status = label(
            "Pronto para testar.",
            11,
            0xFFB4A9FA
        );

        status.setPadding(
            0, dp(5), 0, dp(3)
        );
        controls.addView(status);

        setExpanded(false);
    }

    private void setupDrag(View handle) {
        final int slop =
            ViewConfiguration.get(this)
                .getScaledTouchSlop();

        handle.setOnTouchListener(
            new View.OnTouchListener() {
                float downX, downY;
                int originX, originY;
                boolean moving;

                @Override
                public boolean onTouch(
                    View v, MotionEvent event
                ) {
                    switch (
                        event.getActionMasked()
                    ) {
                        case MotionEvent.ACTION_DOWN:
                            downX = event.getRawX();
                            downY = event.getRawY();

                            originX = params.x;
                            originY = params.y;

                            moving = false;
                            return true;

                        case MotionEvent.ACTION_MOVE:
                            float dx =
                                event.getRawX() - downX;
                            float dy =
                                event.getRawY() - downY;

                            if (
                                Math.abs(dx) > slop ||
                                Math.abs(dy) > slop
                            ) {
                                moving = true;
                            }

                            if (moving) {
                                params.x = Math.max(
                                    0,
                                    originX + Math.round(dx)
                                );
                                params.y = Math.max(
                                    0,
                                    originY + Math.round(dy)
                                );
                                updateLayout();
                            }
                            return true;

                        case MotionEvent.ACTION_UP:
                            if (!moving) {
                                setExpanded(!expanded);
                            }
                            return true;

                        case MotionEvent.ACTION_CANCEL:
                            return true;
                    }
                    return false;
                }
            }
        );
    }

    private void setFocusable(boolean enabled) {
        if (params == null) return;

        if (enabled) {
            params.flags &=
                ~WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        } else {
            params.flags |=
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        }

        params.flags |=
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;

        updateLayout();
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
        setFocusable(false);
    }

    private void setExpanded(boolean open) {
        if (
            root == null ||
            header == null ||
            scroll == null
        ) {
            return;
        }

        hideKeyboard();

        if (open && !expanded) {
            savedX = params.x;
            savedY = params.y;
        }

        expanded = open;

        headerText.setVisibility(
            open ? View.VISIBLE : View.GONE
        );
        scroll.setVisibility(
            open ? View.VISIBLE : View.GONE
        );

        if (open) {
            int screenW =
                getResources()
                    .getDisplayMetrics().widthPixels;

            int screenH =
                getResources()
                    .getDisplayMetrics().heightPixels;

            panelWidth = Math.max(
                dp(190),
                Math.min(
                    dp(280),
                    screenW - dp(24)
                )
            );

            int scrollH = Math.max(
                dp(110),
                Math.min(
                    dp(340),
                    screenH - dp(95)
                )
            );

            scroll.setLayoutParams(
                new LinearLayout.LayoutParams(
                    panelWidth, scrollH
                )
            );

            params.x = Math.min(
                params.x,
                Math.max(
                    0,
                    screenW - panelWidth - dp(8)
                )
            );

            params.y = dp(8);
        } else {
            params.x = savedX;
            params.y = savedY;
        }

        updateLayout();
    }

    private void updateLayout() {
        if (
            wm != null &&
            root != null &&
            root.getWindowToken() != null
        ) {
            try {
                wm.updateViewLayout(
                    root, params
                );
            } catch (Exception ignored) {}
        }
    }

    private void showStatus(String text) {
        if (status != null) {
            status.setText(text);
        }
    }

    private void cancelTimer() {
        if (timer != null) {
            handler.removeCallbacks(timer);
        }
        timer = null;
    }

    private void openBloodStrike() {
        try {
            Intent launch =
                getPackageManager()
                    .getLaunchIntentForPackage(
                        "com.netease.newspike"
                    );

            if (launch == null) {
                showStatus(
                    "Blood Strike não encontrado no aparelho."
                );
                return;
            }

            launch.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
            );

            setExpanded(false);
            startActivity(launch);

        } catch (Exception e) {
            showStatus(
                "Não foi possível abrir Blood Strike."
            );
        }
    }

    private void apply(int w, int h) {
        if (busy || testPending) {
            showStatus(
                "Confirme ou restaure o teste anterior."
            );
            return;
        }

        if (
            w < 600 || w > 3000 ||
            h < 600 || h > 4000
        ) {
            showStatus(
                "Resolução fora dos limites."
            );
            return;
        }

        hideKeyboard();
        busy = true;

        showStatus(
            "Aplicando com Shizuku..."
        );

        new Thread(() -> {
            ResolutionController.Result r =
                ResolutionController.apply(
                    getApplicationContext(),
                    w,
                    h
                );

            handler.post(() -> {
                busy = false;

                if (!r.ok) {
                    showStatus(r.message);
                    return;
                }

                testPending = true;

                showStatus(
                    "Teste ativo: 15 s para manter ou restaurar."
                );

                cancelTimer();

                timer = () -> {
                    if (testPending) {
                        restore(false);
                    }
                };

                handler.postDelayed(
                    timer, TEST_MS
                );
            });

        }, "Toji-Stretch-Apply").start();
    }

    private void restore(boolean closeAfter) {
        if (busy) {
            showStatus(
                "Aguarde o comando terminar."
            );
            return;
        }

        busy = true;
        testPending = false;

        cancelTimer();
        hideKeyboard();

        showStatus(
            "Restaurando resolução..."
        );

        new Thread(() -> {
            ResolutionController.Result r =
                ResolutionController.restore(
                    getApplicationContext()
                );

            handler.post(() -> {
                busy = false;
                showStatus(r.message);

                if (closeAfter && r.ok) {
                    stopSelf();
                }
            });

        }, "Toji-Stretch-Restore").start();
    }

    @Override
    public void onConfigurationChanged(
        Configuration config
    ) {
        super.onConfigurationChanged(config);
        if (expanded) {
            setExpanded(true);
        }
    }

    @Override
    public int onStartCommand(
        Intent intent,
        int flags,
        int startId
    ) {
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        boolean restoreOnExit = testPending;
        testPending = false;

        cancelTimer();

        if (restoreOnExit) {
            new Thread(() -> {
                ResolutionController.restore(
                    getApplicationContext()
                );
            }, "T
