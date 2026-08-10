package dev.minimalaccounting.app;

import dev.minimalaccounting.adapters.api.http.HttpConfiguration;
import dev.minimalaccounting.adapters.api.http.HttpTransactionServerFactory;
import dev.minimalaccounting.infrastructure.JmxMetrics;
import lombok.NonNull;

import java.io.IOException;
import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

final class ServerApplication {
    private static final int DEFAULT_PORT = 8080;
    private static final int MAXIMUM_BODY_BYTES = 1_048_576;

    private final Clock clock;
    private final Map<String, String> environment;

    ServerApplication(@NonNull Clock clock) {
        this(clock, System.getenv());
    }

    ServerApplication(
            @NonNull Clock clock,
            @NonNull Map<String, String> environment) {
        this.clock = clock;
        this.environment = environment;
    }

    int run(String[] arguments, PrintStream out, PrintStream error) {
        Configuration configuration;
        try {
            configuration = Configuration.parse(arguments);
        } catch (IllegalArgumentException invalid) {
            error.println("Invalid server configuration: " + invalid.getMessage());
            printUsage(error);
            return 2;
        }
        if (configuration.help()) {
            printUsage(out);
            return 0;
        }
        if (configuration.accounts() == null) {
            error.println("Invalid server configuration: missing --accounts <file>");
            printUsage(error);
            return 2;
        }
        if (!"memory".equals(configuration.runtime())
                && !"durable".equals(configuration.runtime())) {
            error.println("Invalid server configuration: runtime must be memory or durable");
            return 2;
        }

        DurableRuntimeConfiguration durable = null;
        if ("durable".equals(configuration.runtime())) {
            try {
                durable = DurableRuntimeConfiguration.from(environment);
            } catch (IllegalArgumentException invalid) {
                error.println("Invalid durable configuration: " + invalid.getMessage());
                return 2;
            }
        }

        AccountingRuntime accounting;
        try {
            accounting = durable == null
                    ? InMemoryAccountingFactory.create(clock)
                    : DurableAccountingFactory.create(durable, clock);
            AccountFile.load(configuration.accounts(), accounting.accounts());
        } catch (IOException | RuntimeException failure) {
            error.println("Cannot initialize accounting: " + message(failure));
            return 1;
        }

        var stopped = new CountDownLatch(1);
        Thread shutdown = new Thread(stopped::countDown, "accounting-shutdown");
        Runtime.getRuntime().addShutdownHook(shutdown);
        try (accounting;
             var metrics = JmxMetrics.register(accounting.metrics());
             var workers = accounting.startWorkers();
             var server = HttpTransactionServerFactory.create(
                     new HttpConfiguration(
                             new InetSocketAddress(
                                     configuration.host(), configuration.port()),
                             0,
                             MAXIMUM_BODY_BYTES),
                     accounting.transactions())) {
            server.start();
            out.println("Listening on http://" + configuration.host() + ":"
                    + server.address().getPort() + "/transactions");
            stopped.await();
            return 0;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            error.println("Server interrupted");
            return 1;
        } catch (IOException | RuntimeException failure) {
            error.println("Cannot run server: " + message(failure));
            return 1;
        } finally {
            try {
                Runtime.getRuntime().removeShutdownHook(shutdown);
            } catch (IllegalStateException shuttingDown) {
                // The hook is already running as part of JVM shutdown.
            }
        }
    }

    private static void printUsage(PrintStream output) {
        output.println("Usage: accounting server --accounts <file> [options]");
        output.println();
        output.println("Options:");
        output.println("  --runtime memory|durable  Runtime implementation (default: memory)");
        output.println("  --host <address>     Listen address (default: 127.0.0.1)");
        output.println("  --port <port>        Listen port (default: 8080)");
        output.println();
        output.println("Durable mode environment:");
        output.println("  ACCOUNTING_DATABASE_URL, _USER, _PASSWORD");
        output.println("  ACCOUNTING_BROKER_URL, _USER, _PASSWORD");
        output.println("  ACCOUNTING_DESTINATION_PREFIX (optional)");
    }

    private static String message(Exception error) {
        return error.getMessage() == null
                ? error.getClass().getSimpleName()
                : error.getMessage();
    }

    private record Configuration(
            Path accounts,
            String runtime,
            String host,
            int port,
            boolean help) {
        private static Configuration parse(String[] arguments) {
            Path accounts = null;
            String runtime = "memory";
            String host = "127.0.0.1";
            int port = DEFAULT_PORT;
            boolean help = false;
            for (int index = 0; index < arguments.length; index++) {
                String argument = arguments[index];
                switch (argument) {
                    case "--accounts" -> accounts = Path.of(value(arguments, ++index, argument));
                    case "--runtime" -> runtime = value(arguments, ++index, argument);
                    case "--host" -> host = value(arguments, ++index, argument);
                    case "--port" -> port = port(value(arguments, ++index, argument));
                    case "--help", "-h" -> help = true;
                    default -> throw new IllegalArgumentException(
                            "unknown server option: " + argument);
                }
            }
            return new Configuration(accounts, runtime, host, port, help);
        }

        private static String value(String[] arguments, int index, String option) {
            if (index >= arguments.length || arguments[index].isBlank()) {
                throw new IllegalArgumentException("missing value for " + option);
            }
            return arguments[index];
        }

        private static int port(String value) {
            int port = Integer.parseInt(value);
            if (port < 0 || port > 65_535) {
                throw new IllegalArgumentException("port must be between 0 and 65535");
            }
            return port;
        }
    }
}
