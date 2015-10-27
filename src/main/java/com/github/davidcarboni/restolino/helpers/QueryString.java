package com.github.davidcarboni.restolino.helpers;

import com.google.common.net.UrlEscapers;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.jetty.util.UrlEncoded;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.List;

/**
 * This class enables you to work with a URL query string.
 * <p/>
 * Whilst technically a query string can contain multiple values for a single
 * parameter name, in practice this rarely happens so, for expedience, this
 * class is a Map.
 *
 * @author david
 */
public class QueryString extends HashMap<String, String> {

    /**
     * Generated by Eclipse.
     */
    private static final long serialVersionUID = -3070809403310976231L;

    public QueryString() {
        // Default constructor.
    }

    public QueryString(URI uri) {
        String rawQuery = uri.getRawQuery();
        String[] parameters = StringUtils.split(rawQuery, "&");
        if (parameters != null) {
            for (String parameter : parameters) {
                String[] pair = StringUtils.split(parameter, "=");
                if (pair != null && pair.length == 2) {
                    String key = pair[0];
                    String value = pair[1];
                    if (StringUtils.isNotBlank(key) && StringUtils.isNotBlank(value)) {

                        String decodedKey;
                        String decodedValue;
                        try {
                             decodedKey = URLDecoder.decode(key, StandardCharsets.UTF_8.name());
                             decodedValue = URLDecoder.decode(value, StandardCharsets.UTF_8.name());
                        } catch (UnsupportedEncodingException e) {
                            throw new IllegalArgumentException("URL does not appear to be UTF8 encoded", e);
                        }

                        // These are Jetty classes, but they *appear* to need Java 8?
                        //String decodedKey = UrlEncoded.decodeString(key, 0, key.length(), Charset.forName("UTF8"));
                        //String decodedValue = UrlEncoded.decodeString(value, 0, value.length(), Charset.forName("UTF8"));

                        put(decodedKey, decodedValue);
                    }
                }
            }
        }
    }

    /**
     * A value suitable for constructinng URIs with. This means that if there
     * are no parameters, null will be returned.
     *
     * @return If the map is empty, null, otherwise an escaped query string.
     */
    public String toQueryString() {

        String result = null;

        List<String> parameters = new ArrayList<>();
        for (Map.Entry<String, String> entry : entrySet()) {
            // We don't encode the key because it could legitimately contain
            // things like underscores, e.g. "_escaped_fragment_" would become:
            // "%5Fescaped%5Ffragment%5F"
            String key = entry.getKey();
            String value;
            try {
                value = URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8.name());
            } catch (UnsupportedEncodingException e) {
                throw new IllegalArgumentException("Error encoding URL", e);
            }
            // Jetty class *appears* to need Java 8
            //String value = UrlEncoded.encodeString(entry.getValue());
            parameters.add(key + "=" + value);
        }
        if (parameters.size() > 0) {
            result = StringUtils.join(parameters, '&');
        }

        return result;
    }

    /**
     * A value that meets the requirements of toString. This means that if there
     * are no parameters, an empty String will be returned.
     *
     * @return If the map is empty, {@link StringUtils#EMPTY}, otherwise an
     * escaped query string.
     */
    @Override
    public String toString() {
        String result = toQueryString();
        if (result == null) {
            result = StringUtils.EMPTY;
        }
        return result;
    }
}
