package ac.onyx.phantom.shell;

import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;

/**
 * A simple command class to demonstrate Spring Shell functionality.
 * This class is a "component" that will be automatically discovered by Spring.
 */
@ShellComponent
public class HelloCommand {

    /**
     * A simple shell command that says hello.
     * The `ShellMethod` annotation maps this method to the `hello` command.
     * The `String name` parameter is automatically prompted for when the command is called.
     *
     * @param name The name of the user to greet.
     * @return A greeting string.
     */
    @ShellMethod(key = "hello", value = "Greets a user.")
    public String sayHello(String name) {
        return "Hello, " + name + "!";
    }
}