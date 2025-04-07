package catering.util;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.ConsoleHandler;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * Utility class to provide consistent logging across the application
 */
public class LogManager {

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");

    static {
        // Configure the root logger
        Logger rootLogger = Logger.getLogger("");

        // Remove existing handlers
        for (java.util.logging.Handler handler : rootLogger.getHandlers()) {
            rootLogger.removeHandler(handler);
        }

        // Add a console handler with custom formatter
        ConsoleHandler consoleHandler = new ConsoleHandler();
        consoleHandler.setLevel(Level.ALL);
        consoleHandler.setFormatter(new CustomFormatter());
        rootLogger.addHandler(consoleHandler);

        // Set default level
        rootLogger.setLevel(Level.INFO);
    }

    /**
     * Gets a logger instance for the specified class
     * 
     * @param clazz The class to get a logger for
     * @return A configured Logger instance
     */
    public static Logger getLogger(Class<?> clazz) {
        return Logger.getLogger(clazz.getName());
    }

    /**
     * Custom formatter for readable log output
     */
    private static class CustomFormatter extends Formatter {
        @Override
        public String format(LogRecord record) {
            StringBuilder sb = new StringBuilder();
            sb.append(DATE_FORMAT.format(new Date(record.getMillis())));
            sb.append(" [").append(record.getLevel()).append("] ");
            sb.append(record.getLoggerName()).append(": ");
            sb.append(formatMessage(record));

            if (record.getThrown() != null) {
                sb.append("\n").append(formatException(record.getThrown()));
            }

            sb.append("\n");
            return sb.toString();
        }

        private String formatException(Throwable throwable) {
            StringBuilder sb = new StringBuilder();
            sb.append(throwable.toString()).append("\n");
            for (StackTraceElement element : throwable.getStackTrace()) {
                sb.append("\tat ").append(element.toString()).append("\n");
            }
            return sb.toString();
        }
    }
}