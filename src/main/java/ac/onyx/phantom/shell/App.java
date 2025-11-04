package ac.onyx.phantom.shell;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.shell.command.annotation.CommandScan;

@SpringBootApplication
@CommandScan
public class App {
    public static void main(String[] args) {
        SpringApplication.run(App.class, args);
    }
}