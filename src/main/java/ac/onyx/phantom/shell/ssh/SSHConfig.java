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
}
