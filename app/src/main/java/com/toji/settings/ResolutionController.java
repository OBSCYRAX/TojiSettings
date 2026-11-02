package com.toji.settings;

import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;
import rikka.shizuku.Shizuku;

public final class ResolutionController {
    private ResolutionController() {}
    public static final String PREF = "toji_stretch";
    private static final Object LOCK = new Object();
    private static volatile IBinder service;
    private static boolean binding;
    private static final Shizuku.UserServiceArgs SERVICE_ARGS = new Shizuku.UserServiceArgs(
            new ComponentName("com.toji.settings", ShellUserService.class.getName()))
            .processNameSuffix("resolution").daemon(false).version(2);
    private static final ServiceConnection CONNECTION = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder binder) {
            synchronized (LOCK) { service = binder; binding = false; LOCK.notifyAll(); }
        }
        @Override public void onServiceDisconnected(ComponentName name) {
            synchronized (LOCK) { service = null; binding = false; LOCK.notifyAll(); }
        }
    };
    public static final class Result {
        public final boolean ok;
        public final String message;
        public Result(boolean ok, String message) { this.ok = ok; this.message = message; }
    }
    public static Result status() {
        try {
            if (!Shizuku.pingBinder()) return new Result(false, "Binder do Shizuku indisponível. Verifique se o serviço está ativo e reabra o Toji.");
            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED)
                return new Result(false, "Shizuku ativo, mas o Toji está sem permissão. Autorize no Shizuku.");
            return new Result(true, "Shizuku conectado e autorizado.");
        } catch (Throwable t) { return new Result(false, "Erro Shizuku: " + t.getClass().getSimpleName() + ": " + t.getMessage()); }
    }
    public static boolean authorized() { return status().ok; }
    private static IBinder connect() throws Exception {
        synchronized (LOCK) {
            if (service != null && service.pingBinder()) return service;
            if (!binding) {
                binding = true;
                try { Shizuku.bindUserService(SERVICE_ARGS, CONNECTION); }
                catch (Throwable e) { binding = false; LOCK.notifyAll(); throw new Exception("Falha ao vincular UserService", e); }
            }
            long deadline = android.os.SystemClock.elapsedRealtime() + 12000;
            while (binding && (service == null || !service.pingBinder())) {
                long left = deadline - android.os.SystemClock.elapsedRealtime();
                if (left <= 0) break;
                LOCK.wait(left);
            }
            if (service == null || !service.pingBinder()) {
                binding = false;
                throw new Exception("UserService não conectou em 12 segundos. Confira o Logcat.");
            }
            return service;
        }
    }
    public static Result execute(String... args) {
        Result auth = status();
        if (!auth.ok) return auth;
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper())
            return new Result(false, "Execute o comando fora da thread principal.");
        Parcel data = Parcel.obtain(), reply = Parcel.obtain();
        try {
            IBinder binder = connect();
            data.writeStringArray(args);
            if (!binder.transact(1, data, reply, 0)) return new Result(false, "UserService recusou transação.");
            reply.readException();
            boolean ok = reply.readInt() == 1;
            String message = reply.readString();
            return new Result(ok, message == null ? "" : message);
        } catch (Throwable e) {
            synchronized (LOCK) { service = null; binding = false; LOCK.notifyAll(); }
            return new Result(false, "Falha no UserService: " + e.getClass().getSimpleName() + ": " + e.getMessage());
        } finally { data.recycle(); reply.recycle(); }
    }
    public static Result testConnection() { return execute("size"); }
    public static Result apply(Context context, int width, int height) {
        if (width < 600 || width > 3000 || height < 600 || height > 4000)
            return new Result(false, "Resolução fora dos limites.");
        Result before = execute("size");
        if (!before.ok) return before;
        SharedPreferences prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        if (!prefs.getBoolean("active", false)) prefs.edit().putString("before", before.message).apply();
        Result result = execute("size", width + "x" + height);
        if (!result.ok) return result;
        Result verify = execute("size");
        if (!verify.ok || !verify.message.contains(width + "x" + height)) {
            execute("size", "reset");
            return new Result(false, "Não foi possível confirmar a resolução; tentativa de restaurar padrão realizada.");
        }
        prefs.edit().putBoolean("active", true).putInt("width", width).putInt("height", height).apply();
        return new Result(true, "Resolução aplicada: " + width + " × " + height);
    }
    public static Result restore(Context context) {
        Result r = execute("size", "reset");
        if (r.ok) context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
                .putBoolean("active", false).remove("width").remove("height").apply();
        return r.ok ? new Result(true, "Resolução padrão restaurada.") : r;
    }
}
