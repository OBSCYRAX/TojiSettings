package com.toji.settings;

import android.os.Binder;
import android.os.Parcel;
import android.os.RemoteException;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;

public class ShellUserService extends Binder {
    public ShellUserService() { super(); }
    @Override protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
        if (code != 1) return super.onTransact(code, data, reply, flags);
        String[] args = data.createStringArray();
        if (args == null || args.length < 1 || args.length > 2 || !"size".equals(args[0]) ||
                (args.length == 2 && !"reset".equals(args[1]) && !args[1].matches("[0-9]{3,4}x[0-9]{3,4}"))) {
            reply.writeNoException(); reply.writeInt(0); reply.writeString("Comando não permitido."); return true;
        }
        Process process = null;
        try {
            String[] cmd = args.length == 1 ? new String[]{"wm", "size"} : new String[]{"wm", "size", args[1]};
            process = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            if (!process.waitFor(8, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                reply.writeNoException(); reply.writeInt(0); reply.writeString("Tempo limite do comando excedido."); return true;
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (InputStream in = process.getInputStream()) {
                byte[] buffer = new byte[1024]; int n;
                while ((n = in.read(buffer)) != -1 && out.size() < 16384) out.write(buffer, 0, n);
            }
            boolean ok = process.exitValue() == 0;
            String msg = out.toString("UTF-8").trim();
            reply.writeNoException(); reply.writeInt(ok ? 1 : 0);
            reply.writeString(msg.isEmpty() ? (ok ? "Comando executado." : "Falha no comando wm.") : msg);
        } catch (Exception e) {
            reply.writeNoException(); reply.writeInt(0);
            reply.writeString("Erro: " + e.getClass().getSimpleName() + ": " + e.getMessage());
        } finally { if (process != null) process.destroy(); }
        return true;
    }
}
