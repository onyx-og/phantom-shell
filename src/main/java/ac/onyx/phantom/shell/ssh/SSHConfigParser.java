package ac.onyx.phantom.shell.ssh;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class SSHConfigParser {

    public static List<SSHConfig> parse(File configFile) throws IOException {
        List<SSHConfig> hosts = new ArrayList<>();
        if (!configFile.exists()) return hosts;

        try (BufferedReader reader = new BufferedReader(new FileReader(configFile))) {
            SSHConfig current = null;

            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;

                String[] parts = line.split("\\s+", 2);
                if (parts.length < 2) continue;
                String key = parts[0].toLowerCase();
                String value = parts[1];

                switch (key) {
                    case "host":
                        current = new SSHConfig();
                        for (String pattern : value.split("\\s+")) {
                            current.addPattern(pattern);
                        }
                        hosts.add(current);
                        break;
                    case "hostname":
                        if (current != null) current.setHostname(value);
                        break;
                    case "user":
                        if (current != null) current.setUser(value);
                        break;
                    case "port":
                        if (current != null) current.setPort(Integer.parseInt(value));
                        break;
                    case "identityfile":
                        if (current != null) current.setIdentityFile(value);
                        break;
                    case "proxycommand":
                        if (current != null) current.setProxyCommand(value);
                        break;
                    default:
                        // ignore unknown keys
                        break;
                }
            }
        }

        return hosts;
    }
}
