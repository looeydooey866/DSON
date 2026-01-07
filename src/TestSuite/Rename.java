package TestSuite;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * An annotation to be added to object fields, to change its associated name for DSON.DSON parsing.
 * To use, simply add the annotation as follows:
 * <br><br>
 * {@code @DSON.Rename("number")}
 * <br>
 * {@code public int N;}
 * <br><br>
 * When the field is parsed, it will be reflected as {@code number: ...} instead of {@code n (fields are lowercased): ...}.
 * <br><br>
 * When the field is serialized and data is read from JSON, the parser will look for the name {@code number} instead of {@code n}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Rename {
    String value();
}