package uz.mirmaxsudov.chatclonebackend.annotations.validation.validators;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import uz.mirmaxsudov.chatclonebackend.annotations.validation.UniqueElements;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class UniqueElementsValidator implements ConstraintValidator<UniqueElements, Collection<?>> {
    private String propertyName;
    private String messageTemplate;

    @Override
    public void initialize(UniqueElements annotation) {
        propertyName = annotation.property();
        messageTemplate = annotation.message();
    }

    @Override
    public boolean isValid(Collection<?> value, ConstraintValidatorContext context) {
        if (value == null || value.isEmpty()) return true;

        Set<Object> seen = new HashSet<>();

        for (Object element : value) {
            if (element == null) continue;

            Object propValue = readProperty(element, propertyName);
            if (!seen.add(propValue)) {
                context.disableDefaultConstraintViolation();
                context.buildConstraintViolationWithTemplate(messageTemplate.replace("{property}", propertyName)).addConstraintViolation();
                return false;
            }
        }

        return true;
    }

    private Object readProperty(Object bean, String prop) {
        try {
            String capitalized = prop.substring(0, 1).toUpperCase() + prop.substring(1);
            Method getter;
            try {
                getter = bean.getClass().getMethod("get" + capitalized);
            } catch (NoSuchMethodException ex) {
                getter = bean.getClass().getMethod("is" + capitalized);
            }
            return getter.invoke(bean);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to read property `" + prop + "` on " + bean.getClass(), e);
        }
    }
}