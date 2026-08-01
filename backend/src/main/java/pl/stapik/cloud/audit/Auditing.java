package pl.stapik.cloud.audit;

import pl.stapik.cloud.audit.data.AuditAction;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Auditing {
    AuditAction action();
    String extensionId() default "";
    String actor() default "";
    String details() default "";
}
