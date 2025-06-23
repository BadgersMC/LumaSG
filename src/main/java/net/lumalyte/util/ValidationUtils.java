package net.lumalyte.util;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Map;

/**
 * Utility class for parameter validation and null safety checks.
 * 
 * <p>This class provides common validation methods that throw runtime exceptions
 * when validation fails. All methods are static and the class cannot be instantiated.</p>
 * 
 * <p>The validation methods follow a consistent pattern of checking a condition
 * and throwing an exception with a descriptive error message if the condition fails.
 * The error messages include the parameter name and operation context for better debugging.</p>
 * 
 * @author LumaSG Team
 * @since 1.0.0
 */
public final class ValidationUtils {
    
    /**
     * Private constructor to prevent instantiation.
     */
    private ValidationUtils() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }
    
    /**
     * Validates that an object is not null.
     * 
     * @param object The object to validate
     * @param parameterName The name of the parameter for error messages
     * @param operation The operation being performed
     * @throws IllegalArgumentException if the object is null
     */
    public static void requireNonNull(@Nullable Object object, @NotNull String parameterName, @NotNull String operation) {
        if (object == null) {
            throw new IllegalArgumentException(
                "Parameter '" + parameterName + "' cannot be null during " + operation
            );
        }
    }
    
    /**
     * Validates that an object is not null with a custom error message.
     * 
     * @param object The object to validate
     * @param errorMessage The custom error message
     * @param operation The operation being performed
     * @throws IllegalArgumentException if the object is null
     */
    public static void requireNonNullWithMessage(@Nullable Object object, @NotNull String errorMessage, @NotNull String operation) {
        if (object == null) {
            throw new IllegalArgumentException(errorMessage + " during " + operation);
        }
    }
    
    /**
     * Validates that a string is not null or empty.
     * 
     * @param string The string to validate
     * @param parameterName The name of the parameter for error messages
     * @param operation The operation being performed
     * @throws IllegalArgumentException if the string is null or empty
     */
    public static void requireNonEmpty(@Nullable String string, @NotNull String parameterName, @NotNull String operation) {
        if (string == null || string.trim().isEmpty()) {
            throw new IllegalArgumentException(
                "Parameter '" + parameterName + "' cannot be null or empty during " + operation
            );
        }
    }
    
    /**
     * Validates that a string is not null or empty with a custom error message.
     * 
     * @param string The string to validate
     * @param errorMessage The custom error message
     * @param operation The operation being performed
     * @throws IllegalArgumentException if the string is null or empty
     */
    public static void requireNonEmptyWithMessage(@Nullable String string, @NotNull String errorMessage, @NotNull String operation) {
        if (string == null || string.trim().isEmpty()) {
            throw new IllegalArgumentException(errorMessage + " during " + operation);
        }
    }
    
    /**
     * Validates that a collection is not null or empty.
     * 
     * @param collection The collection to validate
     * @param parameterName The name of the parameter for error messages
     * @param operation The operation being performed
     * @throws IllegalArgumentException if the collection is null or empty
     */
    public static void requireNonEmpty(@Nullable Collection<?> collection, @NotNull String parameterName, @NotNull String operation) {
        if (collection == null || collection.isEmpty()) {
            throw new IllegalArgumentException(
                "Parameter '" + parameterName + "' cannot be null or empty during " + operation
            );
        }
    }
    
    /**
     * Validates that an array is not null or empty.
     * 
     * @param array The array to validate
     * @param parameterName The name of the parameter for error messages
     * @param operation The operation being performed
     * @throws IllegalArgumentException if the array is null or empty
     */
    public static void requireNonEmpty(@Nullable Object[] array, @NotNull String parameterName, @NotNull String operation) {
        if (array == null || array.length == 0) {
            throw new IllegalArgumentException(
                "Parameter '" + parameterName + "' cannot be null or empty during " + operation
            );
        }
    }
    
    /**
     * Validates that a map is not null or empty.
     * 
     * @param map The map to validate
     * @param parameterName The name of the parameter for error messages
     * @param operation The operation being performed
     * @throws IllegalArgumentException if the map is null or empty
     */
    public static void requireNonEmpty(@Nullable Map<?, ?> map, @NotNull String parameterName, @NotNull String operation) {
        if (map == null || map.isEmpty()) {
            throw new IllegalArgumentException(
                "Parameter '" + parameterName + "' cannot be null or empty during " + operation
            );
        }
    }
    
    /**
     * Validates that a number is positive (greater than 0).
     * 
     * @param number The number to validate
     * @param parameterName The name of the parameter for error messages
     * @param operation The operation being performed
     * @throws IllegalArgumentException if the number is not positive
     */
    public static void requirePositive(int number, @NotNull String parameterName, @NotNull String operation) {
        if (number <= 0) {
            throw new IllegalArgumentException(
                "Parameter '" + parameterName + "' must be positive, got: " + number + " during " + operation
            );
        }
    }
    
    /**
     * Validates that a number is positive (greater than 0).
     * 
     * @param number The number to validate
     * @param parameterName The name of the parameter for error messages
     * @param operation The operation being performed
     * @throws IllegalArgumentException if the number is not positive
     */
    public static void requirePositive(long number, @NotNull String parameterName, @NotNull String operation) {
        if (number <= 0) {
            throw new IllegalArgumentException(
                "Parameter '" + parameterName + "' must be positive, got: " + number + " during " + operation
            );
        }
    }
    
    /**
     * Validates that a number is positive (greater than 0).
     * 
     * @param number The number to validate
     * @param parameterName The name of the parameter for error messages
     * @param operation The operation being performed
     * @throws IllegalArgumentException if the number is not positive
     */
    public static void requirePositive(double number, @NotNull String parameterName, @NotNull String operation) {
        if (number <= 0.0) {
            throw new IllegalArgumentException(
                "Parameter '" + parameterName + "' must be positive, got: " + number + " during " + operation
            );
        }
    }
    
    /**
     * Validates that a number is non-negative (greater than or equal to 0).
     * 
     * @param number The number to validate
     * @param parameterName The name of the parameter for error messages
     * @param operation The operation being performed
     * @throws IllegalArgumentException if the number is negative
     */
    public static void requireNonNegative(int number, @NotNull String parameterName, @NotNull String operation) {
        if (number < 0) {
            throw new IllegalArgumentException(
                "Parameter '" + parameterName + "' must be non-negative, got: " + number + " during " + operation
            );
        }
    }
    
    /**
     * Validates that a number is non-negative (greater than or equal to 0).
     * 
     * @param number The number to validate
     * @param parameterName The name of the parameter for error messages
     * @param operation The operation being performed
     * @throws IllegalArgumentException if the number is negative
     */
    public static void requireNonNegative(long number, @NotNull String parameterName, @NotNull String operation) {
        if (number < 0) {
            throw new IllegalArgumentException(
                "Parameter '" + parameterName + "' must be non-negative, got: " + number + " during " + operation
            );
        }
    }
    
    /**
     * Validates that a number is non-negative (greater than or equal to 0).
     * 
     * @param number The number to validate
     * @param parameterName The name of the parameter for error messages
     * @param operation The operation being performed
     * @throws IllegalArgumentException if the number is negative
     */
    public static void requireNonNegative(double number, @NotNull String parameterName, @NotNull String operation) {
        if (number < 0.0) {
            throw new IllegalArgumentException(
                "Parameter '" + parameterName + "' must be non-negative, got: " + number + " during " + operation
            );
        }
    }
    
    /**
     * Validates that a condition is true.
     * 
     * @param condition The condition to validate
     * @param errorMessage The error message if the condition is false
     * @param operation The operation being performed
     * @throws IllegalArgumentException if the condition is false
     */
    public static void requireTrue(boolean condition, @NotNull String errorMessage, @NotNull String operation) {
        if (!condition) {
            throw new IllegalArgumentException(errorMessage + " during " + operation);
        }
    }
    
    /**
     * Validates that a condition is false.
     * 
     * @param condition The condition to validate
     * @param errorMessage The error message if the condition is true
     * @param operation The operation being performed
     * @throws IllegalArgumentException if the condition is true
     */
    public static void requireFalse(boolean condition, @NotNull String errorMessage, @NotNull String operation) {
        if (condition) {
            throw new IllegalArgumentException(errorMessage + " during " + operation);
        }
    }
    
    /**
     * Validates that a number is within a specified range (inclusive).
     * 
     * @param number The number to validate
     * @param min The minimum value (inclusive)
     * @param max The maximum value (inclusive)
     * @param parameterName The name of the parameter for error messages
     * @param operation The operation being performed
     * @throws IllegalArgumentException if the number is outside the range
     */
    public static void requireInRange(int number, int min, int max, @NotNull String parameterName, @NotNull String operation) {
        if (number < min || number > max) {
            throw new IllegalArgumentException(
                "Parameter '" + parameterName + "' must be between " + min + " and " + max + 
                ", got: " + number + " during " + operation
            );
        }
    }
    
    /**
     * Validates that a number is within a specified range (inclusive).
     * 
     * @param number The number to validate
     * @param min The minimum value (inclusive)
     * @param max The maximum value (inclusive)
     * @param parameterName The name of the parameter for error messages
     * @param operation The operation being performed
     * @throws IllegalArgumentException if the number is outside the range
     */
    public static void requireInRange(double number, double min, double max, @NotNull String parameterName, @NotNull String operation) {
        if (number < min || number > max) {
            throw new IllegalArgumentException(
                "Parameter '" + parameterName + "' must be between " + min + " and " + max + 
                ", got: " + number + " during " + operation
            );
        }
    }
    
    /**
     * Returns a default value if the input is null.
     * 
     * @param value The value to check
     * @param defaultValue The default value to return if input is null
     * @param <T> The type of the value
     * @return The input value if not null, otherwise the default value
     */
    public static <T> @NotNull T nullSafe(@Nullable T value, @NotNull T defaultValue) {
        return value != null ? value : defaultValue;
    }
    
    /**
     * Returns a default string if the input is null or empty.
     * 
     * @param value The string to check
     * @param defaultValue The default string to return if input is null or empty
     * @return The input string if not null or empty, otherwise the default string
     */
    public static @NotNull String nullSafeString(@Nullable String value, @NotNull String defaultValue) {
        return (value != null && !value.trim().isEmpty()) ? value : defaultValue;
    }
    
    /**
     * Returns a default integer if the input is null or zero.
     * 
     * @param value The integer to check
     * @param defaultValue The default integer to return if input is null or zero
     * @return The input integer if not null or zero, otherwise the default integer
     */
    public static int nullSafeInt(@Nullable Integer value, int defaultValue) {
        return (value != null && value != 0) ? value : defaultValue;
    }
    
    /**
     * Returns a default boolean if the input is null.
     * 
     * @param value The boolean to check
     * @param defaultValue The default boolean to return if input is null
     * @return The input boolean if not null, otherwise the default boolean
     */
    public static boolean nullSafeBoolean(@Nullable Boolean value, boolean defaultValue) {
        return value != null ? value : defaultValue;
    }
} 