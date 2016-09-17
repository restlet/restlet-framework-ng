/**
 * Copyright 2005-2016 Restlet
 * <p>
 * The contents of this file are subject to the terms of one of the following
 * open source licenses: Apache 2.0 or or EPL 1.0 (the "Licenses"). You can
 * select the license that you prefer but you may not use this file except in
 * compliance with one of these Licenses.
 * <p>
 * You can obtain a copy of the Apache 2.0 license at
 * http://www.opensource.org/licenses/apache-2.0
 * <p>
 * You can obtain a copy of the EPL 1.0 license at
 * http://www.opensource.org/licenses/eclipse-1.0
 * <p>
 * See the Licenses for the specific language governing permissions and
 * limitations under the Licenses.
 * <p>
 * Alternatively, you can obtain a royalty free commercial license with less
 * limitations, transferable or non-transferable, directly at
 * http://restlet.com/products/restlet-framework
 * <p>
 * Restlet is a registered trademark of Restlet S.A.S.
 */

package org.restlet.engine.log;

import org.restlet.engine.util.StringUtils;

/**
 * Logging related utility methods.
 *
 * @author Jerome Louvel
 */
public class LogUtils {

    /**
     * Prevent instantiation of the class.
     */
    private LogUtils() {
    }

    /**
     * Return the best class name. If the class is anonymous, then it returns
     * the super class name.
     *
     * @param clazz
     *            The class to name.
     * @return The class name.
     */
    public static String getBestClassName(Class<?> clazz) {
        String result = clazz.getSimpleName();

        if (StringUtils.isNullOrEmpty(result)) {
            result = getBestClassName(clazz.getSuperclass());
        }

        return result;
    }

    /**
     * Returns a non-null logger name. It is composed by the canonical class
     * name of the owner object suffixed by the owner's hash code.
     *
     * @param baseName
     *            The base logger name to prepend, without a trailing dot.
     * @param owner
     *            The context owner.
     * @return The logger name.
     */
    public static String getLoggerName(String baseName, Object owner) {
        String result = baseName;

        if ((owner != null) && (owner.getClass() != null)) {
            result += "." + getBestClassName(owner.getClass());
        }

        return result;
    }

}
