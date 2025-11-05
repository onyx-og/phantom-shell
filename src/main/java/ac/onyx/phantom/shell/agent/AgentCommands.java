package ac.onyx.phantom.shell.agent;

import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStyle;
import org.springframework.shell.command.annotation.Command;
import org.springframework.shell.command.annotation.Option;
import org.springframework.shell.standard.*;

import java.util.*;

@Command(group = "Agent")
public class AgentCommands extends AbstractShellComponent {

    private final AgentClient client =
        new AgentClient("devtoken123");

    @Command(command = "agent", alias = "--agent",
        description = "Run command on phantom-agent")
    public String runCommand(
        @Option(description = "Command name") String command,
        @Option(description = "Arguments (optional)", defaultValue = ShellOption.NULL) String... args
    ) {
        try {
            Map<String, Object> resp = client.execute(command, args);
            if (resp.containsKey("error")) {
                return "❌ Error: " + resp.get("error") +
                       (resp.containsKey("message") ? " - " + resp.get("message") : "");
            }
            String out = (String) resp.getOrDefault("stdout", "");
            String err = (String) resp.getOrDefault("stderr", "");
            int code = (int) resp.getOrDefault("returncode", -1);

            // Combine streams as a normal shell would
            StringBuilder sb = new StringBuilder();

            if (!out.isBlank()) sb.append(out);
            if (!err.isBlank()) {
                AttributedString serr = new AttributedString(err,
                    AttributedStyle.DEFAULT.foreground(AttributedStyle.RED)
                );
                sb.append("\n")
                  .append(serr.toAnsi())
                  .append("\n");
            }
            
            return sb.toString().stripTrailing();
        } catch (Exception e) {
            AttributedString serr = new AttributedString("⚠️ Failed: " + e.getMessage(),
                AttributedStyle.DEFAULT.foreground(AttributedStyle.RED)
            );
            return serr.toAnsi();
        }
    }
}
