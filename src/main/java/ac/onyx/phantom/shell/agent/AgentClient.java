package ac.onyx.phantom.shell.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;

import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.WinBase;
import com.sun.jna.platform.win32.WinError;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.Native;

public class AgentClient {

    private final ObjectMapper mapper = new ObjectMapper();
    private final String token;

    private static final String PIPE_NAME = "\\\\.\\pipe\\phantom_agent";
    private static final String UNIX_SOCKET = "/tmp/phantom_agent.sock";

    public AgentClient(String token) {
        this.token = token;
    }

    public Map<String, Object> execute(String command, String... args) throws IOException {
        boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
        return isWindows ? executeWindows(command, args) : executeUnix(command, args);
    }

    private WinNT.HANDLE openPipe(String pipeName) throws IOException {
        for (int attempt = 0; attempt < 5; attempt++) {
            WinNT.HANDLE pipe = Kernel32.INSTANCE.CreateFile(
                pipeName,
                WinNT.GENERIC_READ | WinNT.GENERIC_WRITE,
                0, null,
                WinNT.OPEN_EXISTING,
                0, null
            );

            if (!WinBase.INVALID_HANDLE_VALUE.equals(pipe)) {
                return pipe;
            }

            int error = Kernel32.INSTANCE.GetLastError();
            if (error == WinError.ERROR_PIPE_BUSY) {
                // wait for server to become available
                if (!Kernel32.INSTANCE.WaitNamedPipe(pipeName, 2000)) {
                    continue; // retry
                }
            } else if (error == WinError.ERROR_FILE_NOT_FOUND) {
                try { Thread.sleep(500); } catch (InterruptedException ignored) {}
            } else {
                throw new IOException("Failed to open named pipe: error " + error);
            }
        }

        throw new IOException("Timeout waiting for named pipe " + pipeName);
    }

    public Map<String, Object> executeUnix(String command, String... args) throws IOException {

        Path socketPath = Path.of(UNIX_SOCKET);

        var req = new LinkedHashMap<String, Object>();
        req.put("token", token);
        req.put("command", command);
        req.put("args", args);
        req.put("request_id", UUID.randomUUID().toString());

        byte[] reqBytes = mapper.writeValueAsBytes(req);
        byte[] len = ByteBuffer.allocate(4).putInt(reqBytes.length).array();

        // Use UnixDomainSocketChannel (Java 16+) or fallback
        try (SocketChannel channel = SocketChannel.open(StandardProtocolFamily.UNIX)) {
            channel.connect(UnixDomainSocketAddress.of(socketPath));
            channel.write(ByteBuffer.wrap(len));
            channel.write(ByteBuffer.wrap(reqBytes));

            ByteBuffer hdrBuf = ByteBuffer.allocate(4);
            if (channel.read(hdrBuf) != 4)
                throw new IOException("Failed to read response header");
            hdrBuf.flip();
            int respLen = hdrBuf.getInt();

            ByteBuffer respBuf = ByteBuffer.allocate(respLen);
            while (respBuf.hasRemaining()) {
                if (channel.read(respBuf) < 0)
                    throw new EOFException("Unexpected EOF");
            }
            respBuf.flip();
            byte[] respBytes = new byte[respLen];
            respBuf.get(respBytes);

            return mapper.readValue(respBytes, Map.class);
        }
    }

    private Map<String, Object> executeWindows(String command, String... args) throws IOException {
        var req = new LinkedHashMap<String, Object>();
        req.put("token", token);
        req.put("command", command);
        req.put("args", args);
        req.put("request_id", UUID.randomUUID().toString());

        byte[] reqBytes = mapper.writeValueAsBytes(req);

        // Open the named pipe for read/write
        WinNT.HANDLE pipe = openPipe(PIPE_NAME);
        IntByReference written = new IntByReference();
        IntByReference read = new IntByReference();

        try {
            // Write the JSON request (no length prefix for Windows)
            boolean ok = Kernel32.INSTANCE.WriteFile(pipe, reqBytes, reqBytes.length, written, null);
            if (!ok) {
                int err = Kernel32.INSTANCE.GetLastError();
                throw new IOException("WriteFile failed, error code: " + err);
            }

            // Read the full response (until EOF or no bytes read)
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];

            while (true) {
                boolean readOk = Kernel32.INSTANCE.ReadFile(pipe, buf, buf.length, read, null);
                int bytesRead = read.getValue();

                if (!readOk || bytesRead <= 0) {
                    break; // EOF or error
                }
                baos.write(buf, 0, bytesRead);
            }

            byte[] respBytes = baos.toByteArray();
            if (respBytes.length == 0) {
                throw new IOException("Empty response from Phantom Agent");
            }

            return mapper.readValue(respBytes, Map.class);

        } finally {
            Kernel32.INSTANCE.CloseHandle(pipe);
        }
    }
}
