package ac.onyx.phantom.shell.ssh;

import java.util.ArrayList;
import java.util.List;

public class SSHConfig {
    private final List<String> patterns = new ArrayList<>();
    private String hostname;
    private String user;
    private int port = 22; // default SSH port
    private String identityFile;
    private String proxyCommand;

    // Getters and setters
    public List<String> getPatterns() { return patterns; }
    public void addPattern(String pattern) { this.patterns.add(pattern); }

    public String getHostname() { return hostname; }
    public void setHostname(String hostname) { this.hostname = hostname; }

    public String getUser() { return user; }
    public void setUser(String user) { this.user = user; }

    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }

    public String getIdentityFile() { return identityFile; }
    public void setIdentityFile(String identityFile) { this.identityFile = identityFile; }

    public String getProxyCommand() { return proxyCommand; }
    public void setProxyCommand(String proxyCommand) { this.proxyCommand = proxyCommand; }

    /**
     * Generic getter by key name. Returns the value as a String or null if the key is unknown.
     * Supported keys (case-insensitive): hostname, host, user, port, identityfile, proxycommand, patterns
     */
    public String get(String key) {
        if (key == null) return null;
        String k = key.trim().toLowerCase();
        switch (k) {
            case "hostname":
            case "host":
                return this.hostname;
            case "user":
                return this.user;
            case "port":
                return Integer.toString(this.port);
            case "identityfile":
            case "identity-file":
            case "identity_file":
                return this.identityFile;
            case "proxycommand":
            case "proxy-command":
            case "proxy_command":
                return this.proxyCommand;
            case "patterns":
                return String.join(",", this.patterns);
            default:
                return null;
        }
    }

    /**
     * Generic getter returning a String, with a default value when the key is missing or blank.
     */
    public String get(String key, String defaultValue) {
        String val = get(key);
        return (val == null || val.isBlank()) ? defaultValue : val;
    }

    @Override
    public String toString() { 
        String user = (getUser() == null || getUser().isBlank())
            ? System.getProperty("user.name")
            : getUser();
        String result = String.format("%s@%s", user, getHostname());
        return result;
    } 
}
