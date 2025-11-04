package ac.onyx.phantom.shell;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.shell.command.annotation.Command;
import org.springframework.shell.command.annotation.Option;
import org.springframework.shell.component.PathInput;
import org.springframework.shell.component.PathInput.PathInputContext;
import org.springframework.shell.component.SingleItemSelector;
import org.springframework.shell.component.SingleItemSelector.SingleItemSelectorContext;
import org.springframework.shell.component.support.SelectorItem;
import org.springframework.shell.standard.AbstractShellComponent;

import ac.onyx.phantom.shell.ssh.SSHConfigParser;
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
        // availableShells.add("Integrated");

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
            // Integrated SSHJ shell
            
        } else {
            // External shell
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