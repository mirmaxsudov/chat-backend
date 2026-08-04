package uz.mirmaxsudov.chatclonebackend.annotations.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import uz.mirmaxsudov.chatclonebackend.annotations.validation.validators.UniqueElementsValidator;

import java.lang.annotation.*;

@Documented
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = UniqueElementsValidator.class)
public @interface UniqueElements {
    String message() default "Collection contains duplicate values for property `{property}`";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    String property();
}