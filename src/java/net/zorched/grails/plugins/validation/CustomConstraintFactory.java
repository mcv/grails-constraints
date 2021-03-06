/* Copyright 2009 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.zorched.grails.plugins.validation;

import groovy.lang.Closure;
import org.codehaus.groovy.grails.validation.AbstractConstraint;
import org.codehaus.groovy.grails.validation.Constraint;
import org.codehaus.groovy.grails.validation.ConstraintFactory;
import org.springframework.validation.Errors;

import java.util.ArrayList;

import org.springframework.orm.hibernate3.HibernateTemplate;
import org.springframework.context.ApplicationContext;
import org.hibernate.SessionFactory;

/**
 * Factory for creating wrapped custom Constraints
 */
public class CustomConstraintFactory implements ConstraintFactory {

    private final DefaultGrailsConstraintClass constraint;
    private final ApplicationContext applicationContext;

    public CustomConstraintFactory(DefaultGrailsConstraintClass c) {
        this(null, c);
    }
    
    public CustomConstraintFactory(ApplicationContext applicationContext, DefaultGrailsConstraintClass c) {
        this.constraint = c;
        this.applicationContext = applicationContext;
    }

    /**
     * Create a new instance of a constraint.
     * If the application is using Hibernate and the Constraint is marked persistent, then a CustomPersistentConstraint
     * will be used. Otherwise a CustomConstraint (non-persistent will be used).
     */
    public Constraint newInstance() {
        if (constraint.isPersistent()) {
            if (null == applicationContext) 
                throw new IllegalStateException("applicationContext is null. Are you using the hibernate plugin? Persistent constraints are only supported with hibernate persistence.");
            return new CustomPersistentConstraint();
        }
            
        return new CustomConstraint();
    }

    /**
     * Wrapper class that extends the AbstractConstraint so the user supplied constraint
     * conforms to the proper API.
     */
    class CustomConstraint extends AbstractConstraint {
        
        private Closure validateClosure;
        private Closure supportsClosure;
        private int validationParamCount;
        
        public CustomConstraint() {
            Closure temp = constraint.getValidationMethod();
            if (null != temp) {
                validateClosure = (Closure) temp.clone();
                validateClosure.setDelegate(this);
            }
            
            temp = constraint.getSupportsMethod();
            if (null != temp) {
                supportsClosure = (Closure) temp.clone();
            }
            
            validationParamCount =  validateClosure.getMaximumNumberOfParameters();
        }
        
        public Object getParams() {
            return constraintParameter;
        }
        
        public boolean supports(Class aClass) {
            if (null == supportsClosure) {
                return true;
            }
            return (Boolean) supportsClosure.call(aClass);
        }

        public String getName() {
            return constraint.getName();
        }

        public void setParameter(Object constraintParameter) {
            constraint.validateParams(constraintParameter, constraintPropertyName, constraintOwningClass);
            super.setParameter(constraintParameter);
        }

        @Override
        protected String getDefaultMessage(String code) {            
            String m = super.getDefaultMessage(code);
            if (null == m) {
                m = constraint.getDefaultMessage();
            }
            return m;
        }

        @Override
        protected void processValidate(Object target, Object propertyValue, Errors errors) {
            ArrayList<Object> params = new ArrayList<Object>();
            if (validationParamCount > 0)
                params.add(propertyValue);
            if (validationParamCount > 1)
                params.add(target);
            if (validationParamCount > 2)
                params.add(errors);

            boolean isValid = (Boolean) validateClosure.call(params.toArray());
            if (! isValid) {
                Object[] args = new Object[] { constraintPropertyName, constraintOwningClass, propertyValue, constraintParameter };
                super.rejectValue(target, errors, constraint.getDefaultMessageCode(), constraint.getFailureCode(), args);
            }
        }
    }
    
    /**
     * Wrapper class that extends the CustomConstraint to support Constraints that
     * need access to the database for their validation
     */
    class CustomPersistentConstraint extends CustomConstraint {
        
        public ApplicationContext getApplicationContext() {
            return applicationContext;
        }
        
        /**
         * Get a HibernateTemplate from the Spring ApplicationContext.
         */
        public HibernateTemplate getHibernateTemplate() {
            if (applicationContext == null) throw new IllegalStateException("Persistent Constraint requires an instance of ApplicationContext, but it was null. Did you set 'static persistent=true' ?");

            if (applicationContext.containsBean("sessionFactory")) {
                return new HibernateTemplate((SessionFactory) applicationContext.getBean("sessionFactory"),true);
            }
            return null;
        }
    }
}
