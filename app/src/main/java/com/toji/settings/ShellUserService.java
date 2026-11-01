
package com.toji.settings;

import android.os.Binder;
import android.os.Parcel;
import android.os.RemoteException;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

public class ShellUserService extends Binder {

    public ShellUserService() {
        super();
    }

    @Override
    protected boolean onTransact(
            int code,
            Parcel data,
            Parcel reply,
            int flags
    ) throws RemoteException {

        if (code != 1) {
            return super.onTransact(code, data, reply, flags);
        }

        String[] args = data.createStringArray();

        if (args == null || args.length < 1 ||
                !"size".equals(args[0]) ||
                args.length > 2) {
            reply.writeNoException();
            reply.writeInt(0);
            reply.writeString("Comando não permitido.");
            return true;
        }

        if (args.length == 2 &&
                !"reset".equals(args[1]) &&
                !args[1].matches("[0-9]{3,4}x[0-9]{3,4}")) {
            reply.writeNoException();
            reply.writeInt(0);
            reply.writeString("Resolução inválida.");
            return true;
        }

        Process process = null;

        try {
            String[] command = new String[args.length + 1];
            command[0] = "wm";
            System.arraycopy(args, 0, command, 1, args.length);

            ProcessBuilder builder = new ProcessBuilder(command);
            builder.redirectErrorStream(true);
            process = builder.start();

            boolean finished = process.waitFor(
                    8, TimeUnit.SECONDS);

            if (!finished) {
                process.destroyForcibly();
                reply.writeNoException();
                reply.writeInt(0);
                reply.writeString("Tempo limite excedido.");
                return true;
            }

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()));

            StringBuilder output = new StringBuilder();
            String line;

            while ((line = reader.readLine()) != null) {
                output.append(line).append('\n');
            }

            boolean success = process.exitValue() == 0;

            reply.writeNoException();
            reply.writeInt(success ? 1 : 0);
            reply.writeString(
                    output.length() > 0
                            ? output.toString().trim()
                            : success
                                    ? "Comando executado."
                                    : "Falha ao executar comando."
            );

        } catch (Exception e) {
            reply.writeNoException();
            reply.writeInt(0);
            reply.writeString(
                    "Erro: " + e.getClass().getSimpleName()
                            + ": " + e.getMessage()
            );
        } finally {
            if (process != null) {
                process.destroy();
            }
        }

        return true;
    }
}
