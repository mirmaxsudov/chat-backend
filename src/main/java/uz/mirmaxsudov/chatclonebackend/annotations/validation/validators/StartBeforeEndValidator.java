package uz.mirmaxsudov.chatclonebackend.annotations.validation.validators;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;
import uz.mirmaxsudov.chatclonebackend.annotations.validation.StartBeforeEnd;

import java.time.LocalDateTime;

public class StartBeforeEndValidator implements ConstraintValidator<StartBeforeEnd, Object> {
    private String startField;
    private String endField;

    @Override
    public void initialize(StartBeforeEnd a) {
        this.startField = a.start();
        this.endField = a.end();
    }

    @Override
    public boolean isValid(Object bean, ConstraintValidatorContext ctx) {
        if (bean == null) return true;
        BeanWrapper beanWrapper = new BeanWrapperImpl(bean);
        LocalDateTime startDate = (LocalDateTime) beanWrapper.getPropertyValue(startField);
        LocalDateTime endDate = (LocalDateTime) beanWrapper.getPropertyValue(endField);

        if (startDate == null || endDate == null) return true;

        boolean isValid = startDate.isBefore(endDate);

        if (!isValid) {
            ctx.disableDefaultConstraintViolation();
            ctx.buildConstraintViolationWithTemplate(
                            ctx.getDefaultConstraintMessageTemplate()
                    ).addPropertyNode(endField)
                    .addConstraintViolation();
        }

        return isValid;
    }
}
