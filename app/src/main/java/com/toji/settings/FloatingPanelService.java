
    private void applyResolution(int width, int height) {
        if (busy || testing) {
            setStatus("Confirme ou restaure o teste anterior.");
            return;
        }

        if (width < 600 || width > 3000 ||
            height < 600 || height > 4000) {
            setStatus("Resolução fora dos limites.");
            return;
        }

        hideKeyboard();
        busy = true;
        setStatus("Aplicando via Shizuku...");

        new Thread(() -> {
            ResolutionController.Result result =
                ResolutionController.apply(
                    getApplicationContext(), width, height
                );

            main.post(() -> {
                if (destroyed) {
                    if (result.ok) {
                        new Thread(() ->
                            ResolutionController.restore(
                                getApplicationContext()
                            )
                        ).start();
                    }
                    return;
                }

                busy = false;

                if (!result.ok) {
                    setStatus(result.message);
                    return;
                }

                testing = true;
                setStatus("Teste ativo por 15 segundos.");
                cancelRestoreTimer();

                restoreTimer = () -> {
                    if (testing && !busy) {
                        restoreResolution(false);
                    }
                };

                main.postDelayed(restoreTimer, TEST_MS);
            });
        }, "Toji-Apply").start();
    }

    private void restoreResolution(boolean closeAfter) {
        if (busy) {
            setStatus("Aguarde o comando terminar.");
            return;
        }

        busy = true;
        testing = false;
        cancelRestoreTimer();
        hideKeyboard();
        setStatus("Restaurando resolução...");

        new Thread(() -> {
            ResolutionController.Result result =
                ResolutionController.restore(
                    getApplicationContext()
                );

            main.post(() -> {
                if (destroyed) return;

                busy = false;
                setStatus(result.message);

                if (closeAfter && result.ok) {
                    stopSelf();
                }
            });
        }, "Toji-Restore").start();
    }

    private void closeEverything() {
        if (busy) {
            setStatus("Aguarde o comando terminar.");
            return;
        }

        boolean changed = getSharedPreferences(
            ResolutionController.PREF,
            MODE_PRIVATE
        ).getBoolean("active", false);

        if (!changed) {
            testing = false;
            cancelRestoreTimer();
            stopSelf();
        } else {
            restoreResolution(true);
        }
    }

    private void launchBloodStrike() {
        try {
            Intent intent = getPackageManager()
                .getLaunchIntentForPackage(
                    "com.netease.newspike"
                );

            if (intent == null) {
                setStatus("Blood Strike não encontrado.");
                return;
            }

            intent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
            );

            setExpanded(false);
            startActivity(intent);

        } catch (Exception e) {
            setStatus("Não foi possível abrir Blood Strike.");
        }
    }

    @Override
    public void onConfigurationChanged(
        Configuration newConfig
    ) {
        super.onConfigurationChanged(newConfig);

        if (expanded) {
            root.post(() -> setExpanded(true));
        }
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
        boolean restoreOnExit = testing;

        destroyed = true;
        testing = false;
        cancelRestoreTimer();

        if (restoreOnExit) {
            new Thread(() ->
                ResolutionController.restore(
                    getApplicationContext()
                ),
                "Toji-Exit-Restore"
            ).start();
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
