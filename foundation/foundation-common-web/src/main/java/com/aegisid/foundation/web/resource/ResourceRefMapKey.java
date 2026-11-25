package com.aegisid.foundation.web.resource;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ResourceRefMapKey {

  Class<? extends ResourceLoader<?>> loader();

  String domain() default "";

  String projection() default "";

  FallbackStrategy fallback() default FallbackStrategy.DEFAULT;
}
