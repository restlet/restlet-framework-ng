/**
 * Copyright 2005-2014 Restlet
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

package org.restlet.engine;

/**
 * Enumeration of Restlet editions.
 *
 * @author Jerome Louvel
 */
public enum Edition {

    ANDROID("Android", "Android", "Android"), JSE("Java Standard Edition", "Java SE", "JSE"), OSGI("OSGi", "OSGi", "OSGi");

    /** The current engine edition. */
    public static final Edition CURRENT = Edition.JSE;

    private String fullName;
    private String mediumName;
    private String shortName;

    Edition(String fullName, String mediumName, String shortName) {
        this.fullName = fullName;
        this.mediumName = mediumName;
        this.shortName = shortName;
    }

    /**
     * Returns the full size name of the edition.
     *
     * @return The full size of the edition.
     */
    public String getFullName() {
        return fullName;
    }

    /**
     * Returns the medium size name of the edition.
     *
     * @return The medium size name of the edition.
     */
    public String getMediumName() {
        return mediumName;
    }

    /**
     * Returns the short size name of the edition.
     *
     * @return The short size name of the edition.
     */
    public String getShortName() {
        return shortName;
    }
}
