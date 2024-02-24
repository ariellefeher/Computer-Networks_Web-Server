import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

public class Config {
    private int port;
    private String rootDirectory;
    private String defaultPage;
    private int maxThreads;

    public Config(String configFilePath) throws IOException {
        Properties prop = new Properties();
        prop.load(new FileInputStream(configFilePath));

        this.port = Integer.parseInt(prop.getProperty("port", "8080"));
        this.rootDirectory = prop.getProperty("root", "~/www/lab/html/");
        this.defaultPage = prop.getProperty("defaultPage", "index.html");
        this.maxThreads = Integer.parseInt(prop.getProperty("maxThreads", "10"));
    }

    // Getters
    public int getPort() {
        return port;
    }

    public String getRootDirectory() {
        return rootDirectory;
    }

    public String getDefaultPage() {
        return defaultPage;
    }

    public int getMaxThreads() {
        return maxThreads;
    }
}
