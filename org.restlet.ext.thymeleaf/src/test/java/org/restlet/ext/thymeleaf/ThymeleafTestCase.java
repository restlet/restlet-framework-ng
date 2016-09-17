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

package org.restlet.ext.thymeleaf;

import org.junit.Test;
import org.restlet.representation.MediaType;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.Assert.assertTrue;

/**
 * Unit test for the Thymeleaf extension.
 *
 * @author Thierry Boileau
 */
public class ThymeleafTestCase {

    @Test
    public void testTemplate() throws Exception {
        ClassLoaderTemplateResolver templateResolver = new ClassLoaderTemplateResolver();
        templateResolver.setPrefix("org/restlet/ext/thymeleaf/");
        templateResolver.setSuffix(".html");
        templateResolver.setCacheTTLMs(3600000L);

        final Map<String, Object> map = new TreeMap<>();
        map.put("welcome", "Hello, world");

        final String result = new TemplateRepresentation("test",
                TemplateRepresentation.createTemplateEngine(templateResolver),
                Locale.getDefault(), map, MediaType.TEXT_PLAIN).getText();

        assertTrue(result.contains("Hello, world"));
    }

}
