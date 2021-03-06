/*
 * Copyright 2004-2005 the original author or authors.
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
package org.codehaus.groovy.grails.plugins.web.mimes

import grails.util.GrailsUtil
import javax.servlet.http.HttpServletRequest
import org.apache.commons.collections.map.ListOrderedMap
import org.codehaus.groovy.grails.web.mime.DefaultAcceptHeaderParser
import org.codehaus.groovy.grails.web.mime.MimeType
import org.codehaus.groovy.grails.web.servlet.GrailsApplicationAttributes
import org.codehaus.groovy.grails.web.servlet.HttpHeaders
import org.springframework.context.ApplicationContext
import org.springframework.web.context.request.RequestContextHolder
import org.codehaus.groovy.grails.web.mime.DefaultMimeUtility

/**
 * Provides content negotiation capabilities to Grails via a new withFormat method on controllers
 * as well as a format property on the HttpServletRequest instance.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
class MimeTypesGrailsPlugin {

    def version = GrailsUtil.getGrailsVersion()
    def dependsOn = [core:version, servlets:version, controllers:version]
    def observe = ['controllers']

    def doWithSpring = {
        "${MimeType.BEAN_NAME}"(MimeTypesFactoryBean)
        grailsMimeUtility(DefaultMimeUtility, ref(MimeType.BEAN_NAME))
    }

    def doWithDynamicMethods = { ApplicationContext ctx ->

        def config = application.config.grails.mime
        MimeType[] mimeTypes = ctx.getBean("mimeTypes", MimeType[].class)
        boolean useAcceptHeader = config.use.accept.header ? true : false

        // Reads the request format by parsing request headers. Will check for the existance of a format parameter first as an override
        HttpServletRequest.metaClass.getFormat = { ->
            def result = delegate.getAttribute(GrailsApplicationAttributes.CONTENT_FORMAT)
            if (!result) {

                def formatOverride = RequestContextHolder.currentRequestAttributes().params.format
                if (formatOverride) {
                    def allMimes = mimeTypes
                    def mime = allMimes.find { it.extension == formatOverride }
                    result = mime ? mime.extension : mimeTypes[0].extension

                    // Save the evaluated format as a request attribute.
                    // This is a blatant hack because we should to this
                    // on the first call. Unfortunately, doing so breaks
                    // integration tests:
                    //   - Test uses "c.params.format = ..."
                    //   - "c.params" creates parameter map
                    //   - which triggers the parameter parsing listeners
                    //   - which call "request.format"
                    //   - which initialises the CONTENT_FORMAT attribute
                    //   - *before* the "format" parameter is added to the map
                    //   - so the saved format is wrong
                    delegate.setAttribute(GrailsApplicationAttributes.CONTENT_FORMAT, result)
                }
                else {
                    result = delegate.mimeTypes[0].extension
                }
            }
            result
        }

        HttpServletRequest.metaClass.getMimeTypes = {->
            def result = delegate.getAttribute(GrailsApplicationAttributes.REQUEST_FORMATS)
            if (!result) {

                def userAgent = delegate.getHeader(HttpHeaders.USER_AGENT)
                def msie = userAgent && userAgent ==~ /msie(?i)/ ?: false

                def parser = new DefaultAcceptHeaderParser(application)
                parser.configuredMimeTypes = mimeTypes
                def header = delegate.contentType
                if (!header) header = delegate.getHeader(HttpHeaders.CONTENT_TYPE)
                if (msie) header = "*/*"
                if (!header && useAcceptHeader) header = delegate.getHeader(HttpHeaders.ACCEPT)
                result = parser.parse(header)

                delegate.setAttribute(GrailsApplicationAttributes.REQUEST_FORMATS, result)
            }
            result
        }
    }

}

class FormatInterceptor {
    def formatOptions = new ListOrderedMap()
    Object invokeMethod(String name,args) {
        if (args.size() > 0 && (args[0] instanceof Closure || args[0] instanceof Map)) {
            formatOptions[name] = args[0]
        }
        else {
            formatOptions[name] = null
        }
    }
}
