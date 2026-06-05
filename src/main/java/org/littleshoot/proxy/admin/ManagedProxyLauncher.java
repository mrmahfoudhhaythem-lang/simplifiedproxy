package org.littleshoot.proxy.admin;

import org.apache.commons.cli.*;
import org.apache.log4j.xml.DOMConfigurator;
import org.littleshoot.proxy.HttpProxyServer;
import org.littleshoot.proxy.impl.DefaultHttpProxyServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

/**
 * Enhanced launcher that starts LittleProxy with user management and admin interface.
 * 
 * Usage:
 *   java -jar littleproxy.jar --port 8080 --admin-port 9090 --users-file users.csv
 */
public class ManagedProxyLauncher {
    private static final Logger LOG = LoggerFactory.getLogger(ManagedProxyLauncher.class);

    private static final String OPTION_PORT = "port";
    private static final String OPTION_ADMIN_PORT = "admin-port";
    private static final String OPTION_USERS_FILE = "users-file";
    private static final String OPTION_ADMIN_PASSWORD = "admin-password";
    private static final String OPTION_HELP = "help";

    public static void main(String[] args) {
        pollLog4JConfigurationFileIfAvailable();
        
        Options options = new Options();
        options.addOption(null, OPTION_PORT, true, "Proxy port (default: 8080)");
        options.addOption(null, OPTION_ADMIN_PORT, true, "Admin interface port (default: 9090)");
        options.addOption(null, OPTION_USERS_FILE, true, "Path to users CSV file (default: users.csv)");
        options.addOption(null, OPTION_ADMIN_PASSWORD, true, "Admin password for web interface (default: admin123)");
        options.addOption(null, OPTION_HELP, false, "Display help");

        CommandLineParser parser = new DefaultParser();
        
        try {
            CommandLine cmd = parser.parse(options, args);
            
            if (cmd.hasOption(OPTION_HELP)) {
                printHelp(options);
                return;
            }

            int proxyPort = Integer.parseInt(cmd.getOptionValue(OPTION_PORT, "8080"));
            int adminPort = Integer.parseInt(cmd.getOptionValue(OPTION_ADMIN_PORT, "9090"));
            String usersFile = cmd.getOptionValue(OPTION_USERS_FILE, "users.csv");
            String adminPassword = cmd.getOptionValue(OPTION_ADMIN_PASSWORD, "admin123");

            startManagedProxy(proxyPort, adminPort, usersFile, adminPassword);
            
        } catch (ParseException e) {
            LOG.error("Failed to parse command line options", e);
            printHelp(options);
            System.exit(1);
        } catch (Exception e) {
            LOG.error("Failed to start proxy", e);
            System.exit(1);
        }
    }

    private static void startManagedProxy(int proxyPort, int adminPort, String usersFile, String adminPassword) {
        LOG.info("==========================================");
        LOG.info("   LittleProxy with User Management");
        LOG.info("==========================================");
        LOG.info("Proxy Port: {}", proxyPort);
        LOG.info("Admin Port: {}", adminPort);
        LOG.info("Users File: {}", usersFile);
        LOG.info("==========================================");

        // Initialize user store
        CSVUserStore userStore = new CSVUserStore(usersFile);
        LOG.info("Loaded {} users from {}", userStore.getAllUsers().size(), usersFile);

        // Create authenticator and filters
        UserStoreProxyAuthenticator authenticator = new UserStoreProxyAuthenticator(userStore);
        UserFiltersSource filtersSource = new UserFiltersSource(userStore);

        // Start the main proxy server
        LOG.info("Starting proxy server on port {}...", proxyPort);
        HttpProxyServer proxyServer = DefaultHttpProxyServer.bootstrap()
                .withPort(proxyPort)
                .withAllowLocalOnly(false)
                .withProxyAuthenticator(authenticator)
                .withFiltersSource(filtersSource)
                .withName("LittleProxy-Managed")
                .start();

        LOG.info("Proxy server started successfully on port {}", proxyPort);

        // Start admin web interface
        LOG.info("Starting admin web interface on port {}...", adminPort);
        AdminWebServer adminServer = new AdminWebServer(userStore, adminPort, adminPassword);
        adminServer.start();

        LOG.info("");
        LOG.info("==========================================");
        LOG.info("   System Ready!");
        LOG.info("==========================================");
        LOG.info("Proxy URL: http://localhost:{}", proxyPort);
        LOG.info("Admin Panel: http://localhost:{}", adminPort);
        LOG.info("Admin Login: admin / {}", adminPassword);
        LOG.info("==========================================");
        LOG.info("");
        LOG.info("Configure your browser or application to use:");
        LOG.info("  HTTP Proxy: localhost:{}", proxyPort);
        LOG.info("  Authentication: Required (use credentials from CSV)");
        LOG.info("");
        LOG.info("Press Ctrl+C to stop the proxy");

        // Add shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            LOG.info("Shutting down...");
            try {
                adminServer.stop();
                proxyServer.stop();
            } catch (Exception e) {
                LOG.error("Error during shutdown", e);
            }
            LOG.info("Shutdown complete");
        }));

        // Keep the main thread alive
        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            LOG.info("Main thread interrupted, shutting down");
        }
    }

    private static void printHelp(Options options) {
        HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp("ManagedProxyLauncher", options);
        
        System.out.println("\nExamples:");
        System.out.println("  # Start with default settings:");
        System.out.println("  java -jar littleproxy.jar");
        System.out.println("");
        System.out.println("  # Start with custom ports:");
        System.out.println("  java -jar littleproxy.jar --port 8888 --admin-port 8889");
        System.out.println("");
        System.out.println("  # Start with custom users file:");
        System.out.println("  java -jar littleproxy.jar --users-file /path/to/users.csv");
    }

    private static void pollLog4JConfigurationFileIfAvailable() {
        File log4jConfigurationFile = new File("src/test/resources/log4j.xml");
        if (log4jConfigurationFile.exists()) {
            DOMConfigurator.configureAndWatch(
                    log4jConfigurationFile.getAbsolutePath(), 15);
        }
    }
}
