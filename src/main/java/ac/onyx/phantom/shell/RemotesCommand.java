package ac.onyx.phantom.shell;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
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
    public String remotes() throws IOException {
        List<SSHConfig> hosts = SSHConfigParser.parse(new File(System.getProperty("user.home"), ".ssh/config"));
        List<SelectorItem<String>> items = hosts.stream()
                .map(h -> SelectorItem.of(
                        String.join(",", h.getPatterns()),
                        h.getUser() + "@" + h.getHostname() + ":" + h.getPort()
                ))
                .collect(Collectors.toList());
                SingleItemSelector<String, SelectorItem<String>> component = new SingleItemSelector<>(getTerminal(),
				items, "Select a remote", null);
		component.setResourceLoader(getResourceLoader());
		component.setTemplateExecutor(getTemplateExecutor());
		SingleItemSelectorContext<String, SelectorItem<String>> context = component
				.run(SingleItemSelectorContext.empty());
		String result = context.getResultItem()
                .map(SelectorItem::getItem)
                .orElse("No selection");

        return "Selected remote: " + result;
    }
}