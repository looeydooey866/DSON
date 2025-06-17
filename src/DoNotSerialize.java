import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * An annotation to be added to object fields. If added to an object field, it will not be serialized into JSON strings by DamnSON.
 * Fields annotated with DoNotSerialize will also not accept data from JSON.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface DoNotSerialize{}