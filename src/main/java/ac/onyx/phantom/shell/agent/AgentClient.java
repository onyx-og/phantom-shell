package ac.onyx.phantom.shell.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.file.Path;
import java.util.*;

public class AgentClient {

    private final ObjectMapper mapper = new ObjectMapper();
    private final Path socketPath;
    private final String token;

    public AgentClient(String socketPath, String token) {
        this.socketPath = Path.of(socketPath);
        this.token = token;
    }

    public Map<String, Object> execute(String command, String... args) throws IOException {
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
}
