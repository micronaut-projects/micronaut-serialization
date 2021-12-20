package io.micronaut.serde.bson;

import io.micronaut.context.BeanContext;
import io.micronaut.core.annotation.AnnotatedElement;
import io.micronaut.serde.annotation.Serdeable;
import io.micronaut.serde.config.naming.KebabCaseStrategy;
import io.micronaut.serde.config.naming.PropertyNamingStrategy;
import jakarta.inject.Singleton;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Serdeable
public class NamingStrategiesEntity {

    @Serdeable.Serializable(naming = KebabCaseStrategy.class)
    private String renameCompileTime;

    @MyAnn
    @Serdeable.Serializable(naming = RunTimePropertyNamingStrategy.class)
    private String renameRunTime;

    private String notRenamedProperty;

    public String getRenameCompileTime() {
        return renameCompileTime;
    }

    public void setRenameCompileTime(String renameCompileTime) {
        this.renameCompileTime = renameCompileTime;
    }

    public String getRenameRunTime() {
        return renameRunTime;
    }

    public void setRenameRunTime(String renameRunTime) {
        this.renameRunTime = renameRunTime;
    }

    public String getNotRenamedProperty() {
        return notRenamedProperty;
    }

    public void setNotRenamedProperty(String notRenamedProperty) {
        this.notRenamedProperty = notRenamedProperty;
    }

    @Singleton
    public static class RunTimePropertyNamingStrategy implements PropertyNamingStrategy {

        private final BeanContext beanContext;

        public RunTimePropertyNamingStrategy(BeanContext beanContext) {
            this.beanContext = beanContext;
        }

        @Override
        public String translate(AnnotatedElement element) {
            return beanContext == null ? "fail" : "bar " + (element.getAnnotationMetadata().hasAnnotation(MyAnn.class) ? "yes" : "no");
        }
    }

    @Documented
    @Retention(RUNTIME)
    public @interface MyAnn {
    }

}
