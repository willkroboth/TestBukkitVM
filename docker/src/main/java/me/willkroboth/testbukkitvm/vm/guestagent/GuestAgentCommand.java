package me.willkroboth.testbukkitvm.vm.guestagent;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.libvirt.Domain;
import org.libvirt.Error.ErrorNumber;
import org.libvirt.LibvirtException;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Base64;

@FunctionalInterface
public interface GuestAgentCommand<T> {
    T run(Domain domain, boolean log) throws LibvirtException;

    // Helper methods
    private static String base64EncodeBytes(byte[] bytes) {
        return Base64.getEncoder().encodeToString(bytes);
    }

    private static String base64Encode(String plain) {
        return plain == null ? null : base64EncodeBytes(plain.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] base64DecodeBytes(String base64) {
        return Base64.getDecoder().decode(base64);
    }

    private static String base64Decode(String base64) {
        return base64 == null ? null : new String(base64DecodeBytes(base64), StandardCharsets.UTF_8);
    }

    // Command builders
    // PING
    // `guest-ping` https://qemu-project.gitlab.io/qemu/interop/qemu-ga-ref.html#qapidoc-10
    static GuestAgentCommand<Boolean> ping() {
        GuestAgentCommand<Void> ping = new SimpleCommand<>("guest-ping", SimpleCommand.EMPTY);

        return (domain, log) -> {
            try {
                ping.run(domain, log);
            } catch (LibvirtException exception) {
                ErrorNumber errorNumber = exception.getError().getCode();
                if (errorNumber == ErrorNumber.VIR_ERR_AGENT_UNRESPONSIVE ||
                    errorNumber == ErrorNumber.VIR_ERR_OPERATION_INVALID) {
                    // Agent is not connected, not a problem
                    return false;
                }
                // Not sure what the problem was, but it may be important
                throw exception;
            }
            return true;
        };
    }

    // EXECUTE SHELL COMMAND
    // `guest-exec` https://qemu-project.gitlab.io/qemu/interop/qemu-ga-ref.html#qapidoc-238
    static GuestAgentCommand<Integer> executeCommand(String path, String[] arg, String[] env, String inputData, boolean captureOutput) {
        return new SimpleCommand<>("guest-exec", returnElement -> returnElement.getAsJsonObject().get("pid").getAsInt())
            // Required properties
            .addProperty("path", path)
            // Optional properties
            .argumentsNowOptional()
            .addArray("arg", arg)
            .addArray("env", env)
            .addProperty("inputData", base64Encode(inputData))
            .addPropertyIfNotDefault("capture-output", captureOutput, false);
    }

    // `guest-exec-status` https://qemu-project.gitlab.io/qemu/interop/qemu-ga-ref.html#qapidoc-225
    static GuestAgentCommand<GuestExecStatus> getExecutionStatus(int pid) {
        return new SimpleCommand<>("guest-exec-status", returnElement -> {
            JsonObject response = returnElement.getAsJsonObject();

            boolean exited = response.get("exited").getAsBoolean();

            String outData = null;
            String errData = null;
            if (exited) {
                JsonElement outDataJson = response.get("out-data");
                if (outDataJson != null) outData = base64Decode(outDataJson.getAsString());

                JsonElement errDataJson = response.get("err-data");
                if (errDataJson != null) errData = base64Decode(errDataJson.getAsString());
            }

            return new GuestExecStatus(exited, outData, errData);
        }
        ).addProperty("pid", pid);
    }

    // FILE IO
    // `guest-file-open` https://qemu-project.gitlab.io/qemu/interop/qemu-ga-ref.html#qapidoc-32
    static GuestAgentCommand<Integer> openFile(Path path, FileOpenMode openMode) {
        return new SimpleCommand<>("guest-file-open", JsonElement::getAsInt)
            .addProperty("path", path.toString())
            .argumentsNowOptional()
            .addProperty("mode", openMode);
    }

    // `guest-file-close` https://qemu-project.gitlab.io/qemu/interop/qemu-ga-ref.html#qapidoc-35
    static GuestAgentCommand<Void> closeFile(int fileHandle) {
        return new SimpleCommand<>("guest-file-close", SimpleCommand.EMPTY)
            .addProperty("handle", fileHandle);
    }

    // `guest-file-read` https://qemu-project.gitlab.io/qemu/interop/qemu-ga-ref.html#qapidoc-42
    static GuestAgentCommand<byte[]> readFile(int fileHandle) {
        return new SimpleCommand<>("guest-file-read", returnElement ->
            base64DecodeBytes(returnElement.getAsJsonObject().get("buf-b64").getAsString())
        ).addProperty("handle", fileHandle);
    }

    // `guest-file-write` https://qemu-project.gitlab.io/qemu/interop/qemu-ga-ref.html#qapidoc-49
    static GuestAgentCommand<Void> writeFile(int fileHandle, byte[] bytes) {
        // Libvirt limits the maximum string length we can encode,
        //  so we may need to write the file in multiple calls if it's too big
        //  https://lists.libvirt.org/archives/list/users@lists.libvirt.org/thread/DG6ULSKQJ5NCO374FCQ77GI6FAX3D2TL/
        //  The maximum libvirt string length is 4 MiB, but putting MAX_BYTES at 2 MiB seemed to time out the
        //  guest agent. Putting it at a lower value (found by just messing around) seems more successful.
        // NOTE: MAX_BYTES should probably at least be a multiple of 4 to ensure when we split a file we don't
        //  send incomplete bytes. Base 64 turns every 3 bytes into 4 characters.
//        final int MAX_BYTES = 2097152; // 2^21
        final int MAX_BYTES = 131072; // 2^17
        String encoded = base64EncodeBytes(bytes);
        int length = encoded.length();

        if (length < MAX_BYTES) {
            // File can go in one write
            return writeFileSmall(fileHandle, encoded);
        }

        int subStrings = (int) Math.ceil((double) length / MAX_BYTES);
        System.out.println("Splitting file into " + subStrings + " segments");

        return (domain, log) -> {
            for (int i = 0; i < subStrings; i++) {
                System.out.println("Sending file segment " + (i + 1) + "/" + subStrings);
                int start = i * MAX_BYTES;
                int end = Math.min((i + 1) * MAX_BYTES, length);

                // A sub String creates a copy of the underlying byte array, so
                //  we really only want to create these substrings once necessary
                String subEncoded = encoded.substring(start, end);

                writeFileSmall(fileHandle, subEncoded).run(domain, log);
            }

            return null;
        };
    }

    private static GuestAgentCommand<Void> writeFileSmall(int fileHandle, String encoded) {
        return new SimpleCommand<>("guest-file-write", SimpleCommand.EMPTY)
            .addProperty("handle", fileHandle)
            .addProperty("buf-b64", encoded);
    }
}
