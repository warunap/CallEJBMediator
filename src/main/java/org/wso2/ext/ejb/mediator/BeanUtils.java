/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *   * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */

package org.wso2.ext.ejb.mediator;

import org.apache.axiom.om.impl.llom.OMTextImpl;
import org.apache.axiom.om.util.AXIOMUtil;
import org.apache.axis2.databinding.typemapping.SimpleTypeMapper;
import org.apache.synapse.SynapseException;
import org.apache.synapse.config.Entry;
import org.apache.synapse.core.SynapseEnvironment;
import org.apache.synapse.registry.Registry;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.xml.stream.XMLStreamException;
import java.io.IOException;
import java.io.StringReader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Properties;

/**
 * Provides utility methods for method invocation via reflection.
 */
public abstract class BeanUtils {

    /**
     * Invokes the given method on the given object via reflection, handles simple type conversion
     * from String to simple types.
     * @param instance  Instance to invoke the method on.
     * @param method    Method to be invoked.
     * @param args      Arguments for the method invocation.
     * @return          Return value of the method invocation.
     * @throws org.apache.synapse.SynapseException If method invocation fails.
     */
    public static Object invokeInstanceMethod(Object instance, Method method, Object[] args) throws
                                                                                  SynapseException {
        Class[] paramTypes = method.getParameterTypes();

        if (paramTypes.length != args.length) {
            throw new SynapseException("Provided argument count does not match method the " +
                    "parameter count of method '" + method.getName() + "'. Argument count = " +
                    args.length + ", method parameter count = " + paramTypes.length);
        }

        Object[] processedArgs = new Object[paramTypes.length];

        for (int i = 0; i < paramTypes.length; ++i) {

            if (args[i] == null || paramTypes[i].isAssignableFrom(args[i].getClass())) {
                processedArgs[i] = args[i];
            } else if (SimpleTypeMapper.isSimpleType(paramTypes[i])) {
                try {
                    // Workaround for https://issues.apache.org/jira/browse/AXIS2-5212
                    processedArgs[i] = SimpleTypeMapper.getSimpleTypeObject(paramTypes[i],
                            AXIOMUtil.stringToOM("<a>" + args[i].toString() + "</a>"));
                } catch (XMLStreamException ignored) {
                }
            } else {
                throw new SynapseException("Incompatible argument found in " + i + "th argument " +
                        "for '" + method.getName() + "' method.");
            }
        }

        try {
            return method.invoke(instance, processedArgs);
        } catch (IllegalAccessException e) {
            throw new SynapseException("Error while invoking '" + method.getName() + "' method " +
                    "via reflection.", e);
        } catch (InvocationTargetException e) {
            throw new SynapseException("Error while invoking '" + method.getName() + "' method " +
                    "via reflection.", e);
        }
    }

    /**
     * Finds a method in the given class with the given method name and argument count. Fails to
     * resolve the method if two or more overloaded methods are present with the given name and
     * argument count.
     *
     * @param clazz      Class to search for the method in.
     * @param methodName Method name to search for.
     * @param argCount   Length of the argument list.
     * @return           The resolved method, or null if no matching method is found.
     * @throws org.apache.synapse.SynapseException If two or more overloaded methods are found with the given name and
     * argument count.
     */
    public static Method resolveMethod(Class clazz, String methodName, int argCount) throws
                                                                                  SynapseException {
        Method resolvedMethod = null;

        for (Method method : clazz.getMethods()) {

            if (method.getName().equals(methodName) &&
                    method.getParameterTypes().length == argCount) {

                if (resolvedMethod == null) {
                    resolvedMethod = method;
                } else {
                    throw new SynapseException("More than one '" + methodName + "' methods " +
                            "that take " + argCount + " arguments are found in '" +
                            clazz.getName() + "' class.");
                }

            }
        }

        return resolvedMethod;
    }

    public static Context initContext(Hashtable<String, String> env) {
        try {
            return new InitialContext(env);
        } catch (NamingException e) {
            throw new SynapseException("Error occurred while initializing the initial " +
                    "context factory", e);
        }
    }

    public static Object lookupJNDI(String jndiName, Context ctx) {
        try {
            return ctx.lookup(jndiName);
        } catch (NamingException e) {
            throw new SynapseException("Error occurred while looking up object '" + jndiName +
                    " '", e);
        }
    }

    public static Hashtable<String, String> getEnvironmentProperties(SynapseEnvironment synapseEnvironment,
                                                              String jndiConfigRegPath) {

        Hashtable<String, String> env = new Hashtable<String, String>();

        Registry registry = synapseEnvironment.getSynapseConfiguration().getRegistry();

        OMTextImpl omText = (OMTextImpl)registry.getResource(new Entry(jndiConfigRegPath), null);

        Properties properties = new Properties();

        try {
            properties.load(new StringReader(omText.getText()));
        } catch (IOException e) {
            e.printStackTrace();
        }

        Enumeration enumeration = properties.keys();

        while (enumeration.hasMoreElements()) {
            String key = (String) enumeration.nextElement();
            env.put(key, (String) properties.get(key));
        }
        return env;
    }

}
