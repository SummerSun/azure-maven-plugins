/**
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for
 * license information.
 */

package com.microsoft.azure.maven.function.bindings;

import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.Locale;

public class BindingFactory {
    private static final String HTTP_OUTPUT_DEFAULT_NAME = "$return";

    public static Binding getBinding(final Annotation annotation) {
        final BindingEnum annotationEnum = Arrays.stream(BindingEnum.values())
                .filter(bindingEnum -> bindingEnum.name().toLowerCase(Locale.ENGLISH)
                        .equals(annotation.annotationType().getSimpleName().toLowerCase(Locale.ENGLISH)))
                .findFirst().orElse(null);
        return annotationEnum == null ? null : new Binding(annotationEnum, annotation);
    }

    public static Binding getHTTPOutBinding() {
        final Binding result = new Binding(BindingEnum.HttpOutput);
        result.setName(HTTP_OUTPUT_DEFAULT_NAME);
        return result;
    }
}
