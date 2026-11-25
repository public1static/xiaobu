package com.aegisid.foundation.web.resource;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(ResourceRefs.class)
public @interface ResourceRef {

  Class<? extends ResourceLoader<?>> value();

  String domain() default "";

  OnNull onNull() default OnNull.SKIP;

  String projection() default "";

  FallbackStrategy fallback() default FallbackStrategy.DEFAULT;
}
