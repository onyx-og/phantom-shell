package ac.onyx.phantom.shell;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.jline.terminal.Attributes;
import org.jline.terminal.Terminal;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStyle;
import org.springframework.shell.command.annotation.Command;
import org.springframework.shell.command.annotation.Option;
import org.springframework.shell.component.PathInput;
import org.springframework.shell.component.PathInput.PathInputContext;
import org.springframework.shell.component.SingleItemSelector;
import org.springframework.shell.component.StringInput;
import org.springframework.shell.component.SingleItemSelector.SingleItemSelectorContext;
import org.springframework.shell.component.StringInput.StringInputContext;
import org.springframework.shell.component.support.SelectorItem;
import org.springframework.shell.standard.AbstractShellComponent;

import ac.onyx.phantom.shell.ssh.SSHConfigParser;
import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.connection.channel.direct.Session;
import net.schmizz.sshj.transport.verification.PromiscuousVerifier;
import ac.onyx.phantom.shell.ssh.SSHConfig;

@Command(group = "Remotes")
public class RemotesCommand extends AbstractShellComponent {

    @Command(command = "remotes", alias = "--remotes",
          description = "List and select remote servers")
    public String remotes(
        @Option(longNames = "config", shortNames = 'c', required = false, description = "Path to SSH config file")
        String configPath
    ) throws IOException, InterruptedException {

        // Use provided path, or fallback to default ~/.ssh/config
        File configFile;
        if (configPath != null && !configPath.isBlank()) {
            configFile = new File(configPath);
        } else {
            configFile = new File(System.getProperty("user.home"), ".ssh/config");
        }

        if (!configFile.exists() || !configFile.isFile()) {
            getTerminal().writer().println("⚠️  SSH config not found at: " + configFile.getAbsolutePath());
            getTerminal().writer().println("Please enter a valid SSH config path:");

            // Use Spring Shell PathInput for interactive selection
            PathInput component = new PathInput(getTerminal(), "SSH config file path");
            component.setResourceLoader(getResourceLoader());
            component.setTemplateExecutor(getTemplateExecutor());

            PathInputContext context = component.run(PathInputContext.empty());
            Path inputPath = context.getResultValue();

            if (inputPath == null || inputPath.toString().isBlank()) {
                return "❌ No valid path entered. Aborting.";
            }

            configFile = new File(inputPath.toString().replaceFirst("^~", System.getProperty("user.home")));

            if (!configFile.exists()) {
                return "❌ File not found: " + inputPath;
            }

        }

        // Continue with parsing & host selection
        List<SSHConfig> hosts = SSHConfigParser.parse(configFile);

        // Filter entries without a hostname
        List<SSHConfig> validHosts = hosts.stream()
                .filter(h -> h.getHostname() != null && !h.getHostname().isBlank())
                .collect(Collectors.toList());

        if (validHosts.isEmpty()) {
            return "No valid SSH hosts found in ~/.ssh/config";
        }

        List<SelectorItem<SSHConfig>> items = validHosts.stream()
            .filter(h -> h.getHostname() != null && !h.getHostname().isBlank())
            .map(h -> SelectorItem.of(
                String.join(",", h.getPatterns()),
                h
            ))
            .collect(Collectors.toList());

        SingleItemSelector<SSHConfig, SelectorItem<SSHConfig>> remoteSelector = new SingleItemSelector<>(getTerminal(),
            items, "Select a remote", null);

		remoteSelector.setResourceLoader(getResourceLoader());
		remoteSelector.setTemplateExecutor(getTemplateExecutor());

		SingleItemSelectorContext<SSHConfig, SelectorItem<SSHConfig>> context = remoteSelector
            .run(SingleItemSelectorContext.empty());

		SSHConfig selectedHost = context.getResultItem()
            .map(SelectorItem<SSHConfig>::getItem)
            .orElse(null);

        if (selectedHost == null) return "No host selected";

        String user = (selectedHost.getUser() == null || selectedHost.getUser().isBlank())
            ? System.getProperty("user.name")
            : selectedHost.getUser();

        // --- 3. Shell selection ---
        List<String> availableShells = new ArrayList<>();
        availableShells.add("Integrated");

        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            availableShells.add("PowerShell");
        } else {
            availableShells.add("Bash");
        }

        List<SelectorItem<String>> shellItems = availableShells.stream()
            .map(s -> SelectorItem.of(s, s))
            .collect(Collectors.toList());

        SingleItemSelector<String, SelectorItem<String>> shellSelector =
            new SingleItemSelector<>(getTerminal(), shellItems, "Select shell", null);
        shellSelector.setResourceLoader(getResourceLoader());
        shellSelector.setTemplateExecutor(getTemplateExecutor());

        SingleItemSelectorContext<String, SelectorItem<String>> shellContext =
            shellSelector.run(SingleItemSelectorContext.empty());

        String selectedShell = shellContext.getResultItem()
            .map(SelectorItem::getItem)
            .orElse("Integrated");

        // --- 4. Launch the chosen shell ---
        if ("Integrated".equals(selectedShell)) {
            Terminal terminal = getTerminal();

            // --- 2. Ask for password using Spring Shell StringInput ---
            StringInput passwordInput = new StringInput(getTerminal(), "Password for " + user + ":", "");
            passwordInput.setResourceLoader(getResourceLoader());
            passwordInput.setTemplateExecutor(getTemplateExecutor());
            passwordInput.setMaskCharacter('*');  // Mask input

            StringInputContext passwordCtx = passwordInput.run(StringInputContext.empty());
            String password = passwordCtx.getResultValue();

            // Optional: If the user presses Enter without typing anything,
            // you can handle that as "no password" (maybe try key-based auth)
            if (password == null || password.isBlank()) {
                password = "";
                getTerminal().writer().println("⚠️  No password entered, attempting key-based authentication...");
            }
            
            Attributes original = terminal.enterRawMode();
            try ( SSHClient client = new SSHClient()) {
                client.addHostKeyVerifier(new PromiscuousVerifier());
                client.connect(selectedHost.getHostname(), selectedHost.getPort());

                try {
                    if (password.isEmpty()) {
                        // Try key-based authentication first
                        try {
                            client.authPublickey(user);
                        } catch (Exception e) {
                            getTerminal().writer().println("⚠️  Key-based authentication failed, retrying with password...");
                            client.authPassword(user, password);
                        }
                    } else {
                        client.authPassword(user, password);
                    }
                } catch (Exception authEx) {
                    AttributedString serr = new AttributedString("Authentication failed: " + authEx.getMessage(),
                        AttributedStyle.DEFAULT.foreground(AttributedStyle.RED));
                    return serr.toAnsi();
                }
                
                // We need two threads: one for input, one for output.
                // Use a try-with-resources to ensure the executor is shut down.
                try {
                    ExecutorService executor = Executors.newFixedThreadPool(2);
                    // Start the SSH session and shell
                    try (Session session = client.startSession()) {
                        session.allocateDefaultPTY();
                        try (Session.Shell shell = session.startShell()) {
                            // --- 1. Remote Output -> Local Terminal Thread ---
                            executor.submit(() -> {
                                try (InputStream remoteOut = shell.getInputStream()) {
                                    byte[] buffer = new byte[1024];
                                    int bytesRead;
                                    // Loop until the remote stream closes
                                    while (shell.isOpen() && (bytesRead = remoteOut.read(buffer)) != -1) {
                                        terminal.output().write(buffer, 0, bytesRead);
                                        terminal.output().flush(); // CRITICAL: Flush to see output immediately
                                    }
                                } catch (IOException e) {
                                    // This often happens when the shell closes, which is normal
                                }
                            });

                            // --- 2. Local Input -> Remote Input Thread ---
                            executor.submit(() -> {
                                try (OutputStream remoteIn = shell.getOutputStream()) {
                                    Reader localReader = terminal.reader();
                                    int c;
                                    // Loop until the local reader closes (or we're interrupted)
                                    while (shell.isOpen() && (c = localReader.read()) != -1) {
                                        remoteIn.write(c);
                                        remoteIn.flush(); // CRITICAL: Flush to send input immediately
                                    }
                                } catch (IOException e) {
                                    // This often happens when the shell closes
                                }
                            });

                            // --- 3. Main Thread: Wait for completion ---
                            // Wait for the shell to close (e.g., user types 'exit')
                            session.join(0, TimeUnit.SECONDS); // 0 means wait indefinitely
                            terminal.writer().println("\nSSH session closed.");

                        } // shell.close() is called here
                    } // session.close() is called here

                } catch (org.jline.reader.UserInterruptException e) {
                    // User pressed Ctrl+C
                    terminal.writer().println("\n--- Session Interrupted (Ctrl+C) ---");
                } catch (Exception e) {
                    terminal.writer().println("An error occurred: " + e.getMessage());
                } finally {
                    terminal.writer().flush();
                }
            } catch (Exception e) {
                AttributedString serr = new AttributedString("Failed: " + e.getMessage(),
                    AttributedStyle.DEFAULT.foreground(AttributedStyle.RED)
                );
                return serr.toAnsi();
            } finally {
                terminal.setAttributes(original);
            }
        } else {
            // External shell link
            String command;
            String sshCommand = String.format("ssh %s@%s -p %d", user, selectedHost.getHostname(), selectedHost.getPort());
            
            if ("PowerShell".equals(selectedShell)) {
                // command = "powershell.exe";
                new ProcessBuilder("cmd.exe", "/c", "start", "powershell.exe", "-NoExit", "-Command", sshCommand)
                    .inheritIO()
                    .start();
            } else {
                command = "bash";

                new ProcessBuilder(command, "-c", sshCommand)
                    .inheritIO()
                    .start()
                    .waitFor();
            }

        }

        return "Connection closed";
    }
}