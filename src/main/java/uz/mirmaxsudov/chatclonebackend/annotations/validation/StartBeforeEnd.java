package uz.mirmaxsudov.chatclonebackend.annotations.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import uz.mirmaxsudov.chatclonebackend.annotations.validation.validators.StartBeforeEndValidator;

import java.lang.annotation.*;

@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = StartBeforeEndValidator.class)
public @interface StartBeforeEnd {
    String message() default "Start date must be before end date";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    String start();

    String end();
}
