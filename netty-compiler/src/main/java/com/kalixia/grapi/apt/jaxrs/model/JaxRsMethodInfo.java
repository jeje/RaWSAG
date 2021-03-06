package com.kalixia.grapi.apt.jaxrs.model;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import javax.ws.rs.QueryParam;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@SuppressWarnings("PMD.DataflowAnomalyAnalysis")
public class JaxRsMethodInfo {
    private final Element resourceElement;
    private final Element element;
    private final String verb;
    private final String uriTemplate;
    private final String methodName;
    private final TypeMirror returnType;
    private final List<JaxRsParamInfo> parameters;
    private final String[] produces;
    private final List<Annotation> shiroAnnotations;

    public JaxRsMethodInfo(Element resourceElement, Element element, String verb, String uriTemplate, String methodName, TypeMirror returnType,
                    List<JaxRsParamInfo> parameters, String[] produces, List<Annotation> shiroAnnotations) {
        this.resourceElement = resourceElement;
        this.element = element;
        this.verb = verb;
        this.uriTemplate = uriTemplate;
        this.methodName = methodName;
        this.returnType = returnType;
        this.parameters = new ArrayList<>(parameters);
        this.produces = Arrays.copyOf(produces, produces.length);
        this.shiroAnnotations = new ArrayList<>(shiroAnnotations);
    }

    public Element getResourceElement() {
        return resourceElement;
    }

    public Element getElement() {
        return element;
    }

    public String getVerb() {
        return verb;
    }

    public String getUriTemplate() {
        return uriTemplate;
    }

    public String getMethodName() {
        return methodName;
    }

    public TypeMirror getReturnType() {
        return returnType;
    }

    public boolean hasReturnType() {
        return !"void".equals(returnType.toString());
    }

    public List<JaxRsParamInfo> getParameters() {
        return new ArrayList<>(parameters);
    }

    public boolean hasParameters() {
        return parameters.size() > 0;
    }

    @SuppressWarnings("PMD.OnlyOneReturn")
    public boolean hasParametersToValidate() {
        if (parameters.isEmpty()) {
            return false;
        }
        boolean validationRequired = false;
        for (JaxRsParamInfo param : parameters) {
            VariableElement element = param.getElement();
            List<? extends AnnotationMirror> annotationMirrors = element.getAnnotationMirrors();
            for (AnnotationMirror annotationMirror : annotationMirrors) {
                if (annotationMirror.toString().startsWith("@javax.validation")) {
                    validationRequired = true;
                }
            }
        }
        return validationRequired;
    }

    public boolean hasQueryParameters() {
        List<JaxRsParamInfo> methodInfoParameters = getParameters();
        boolean hasQueryParam = false;
        for (JaxRsParamInfo paramInfo : methodInfoParameters) {
            QueryParam queryParam = paramInfo.getElement().getAnnotation(QueryParam.class);
            if (queryParam != null) {
                hasQueryParam = true;
            }
        }
        return hasQueryParam;
    }

    public String[] getProduces() {
        return Arrays.copyOf(produces, produces.length);
    }

    public List<Annotation> getShiroAnnotations() {
        return new ArrayList<>(shiroAnnotations);
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("JaxRsMethodInfo{");
        sb.append("element=").append(element);
        sb.append(", verb='").append(verb).append('\'');
        sb.append(", uriTemplate='").append(uriTemplate).append('\'');
        sb.append(", methodName='").append(methodName).append('\'');
        sb.append(", returnType='").append(returnType).append('\'');
        sb.append(", parameters=").append(parameters);
        sb.append('}');
        return sb.toString();
    }
}
