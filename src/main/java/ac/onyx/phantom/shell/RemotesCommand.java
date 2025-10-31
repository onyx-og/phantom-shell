package ac.onyx.phantom.shell;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.shell.component.SingleItemSelector;
import org.springframework.shell.component.SingleItemSelector.SingleItemSelectorContext;
import org.springframework.shell.component.support.SelectorItem;
import org.springframework.shell.standard.AbstractShellComponent;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;

import ac.onyx.phantom.shell.ssh.SSHConfigParser;
import ac.onyx.phantom.shell.ssh.SSHConfig;

@ShellComponent
public class RemotesCommand extends AbstractShellComponent {

    @ShellMethod(key = {"remotes", "--remotes"}, value = "List and select remote servers")
    public String remotes() throws IOException, InterruptedException {
        List<SSHConfig> hosts = SSHConfigParser.parse(new File(System.getProperty("user.home"), ".ssh/config"));

        // Filter entries without a hostname
        List<SSHConfig> validHosts = hosts.stream()
                .filter(h -> h.getHostname() != null && !h.getHostname().isBlank())
                .collect(Collectors.toList());

        if (validHosts.isEmpty()) {
            return "No valid SSH hosts found in ~/.ssh/config";
        }

        // List<SelectorItem<SSHConfig>> items = validHosts.stream()
        //     .filter(h -> h.getHostname() != null && !h.getHostname().isBlank())
        //     .map(h -> SelectorItem.of(
        //             String.join(",", h.getPatterns()),
        //             (
        //                 ( h.getUser() == null || h.getUser().isBlank() )
        //                 ? System.getProperty("user.name")
        //                 : h.getUser()
        //             ) + "@" + h.getHostname() + ":" + h.getPort()
        //     ))
        //     .collect(Collectors.toList());
        
        List<SelectorItem<SSHConfig>> items = validHosts.stream()
            .filter(h -> h.getHostname() != null && !h.getHostname().isBlank())
            .map(h -> SelectorItem.of(
                    String.join(",", h.getPatterns()),
                    h
            ))
            .collect(Collectors.toList());

        SingleItemSelector<SSHConfig, SelectorItem<SSHConfig>> component = new SingleItemSelector<>(getTerminal(),
            items, "Select a remote", null);

		component.setResourceLoader(getResourceLoader());
		component.setTemplateExecutor(getTemplateExecutor());

		SingleItemSelectorContext<SSHConfig, SelectorItem<SSHConfig>> context = component
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
            if ("PowerShell".equals(selectedShell)) {
                command = "powershell.exe"; // or "pwsh" for PowerShell Core
            } else {
                command = "bash";
            }

            String sshCommand = String.format("ssh %s@%s -p %d", user, selectedHost.getHostname(), selectedHost.getPort());

            new ProcessBuilder(command, "-c", sshCommand)
                .inheritIO()
                .start()
                .waitFor();
        }

        return "Connection closed";
    }
}