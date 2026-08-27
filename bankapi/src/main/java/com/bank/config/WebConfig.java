package com.bank.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/*
 * Grants cross-origin access to the API.
 *
 * A new package - config - because this is neither a layer nor domain logic. It configures
 * the framework rather than participating in the request path, and mixing it into controller/
 * would blur what that package means.
 *
 * WebMvcConfigurer is a callback interface: Spring finds every bean implementing it and calls
 * the methods you override, leaving the rest at their defaults. Overriding addCorsMappings
 * adds CORS without replacing any of Boot's other MVC configuration - which is why this
 * class does NOT carry @EnableWebMvc. That annotation switches Boot's auto-configuration off
 * entirely and you would silently lose Jackson setup, static resource handling and the
 * springdoc UI along with it.
 *
 * Note what this is NOT: it is not authorisation. CORS is a browser-enforced rule about which
 * ORIGINS may read a response. curl, Postman and any server-side client ignore it completely.
 * It stops another website's JavaScript from using a user's session against this API; it does
 * not stop anyone from calling the API. That distinction is Phase 10's subject.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    // TODO 1 - inject the permitted origins from configuration rather than hardcoding them.
    @Value("${bankapi.cors.allowed-origins}")
    private String[] allowedOrigins;
    
    //   Hardcoding a production hostname in source has the same problem as hardcoding the
    //   Mongo URI: the value is environment-specific, and source is not. Dev is
    //   http://localhost:5173, production is something that does not exist yet.
    //
    //   Your call, and it mirrors one you have already made once: give it a default
    //   (${...:http://localhost:5173}) so the app runs out of the box, or leave it required
    //   so a missing value fails at startup. You chose fail-fast for MONGODB_URI on the
    //   grounds that a server which starts wrong is harder to diagnose than one that refuses
    //   to start. Whether that reasoning transfers here is a real question - a missing CORS
    //   origin breaks the browser client only, and only in production.
    //
    //   Whichever you pick, add the property to application.properties.

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "PUT", "DELETE")
                .allowedHeaders("Content-Type")
                .allowCredentials(false)
                .maxAge(3600);
        //
        //   .allowedOrigins(...)   the injected array. NOT "*". An origin list is the whole
        //                          point of the mechanism, and "*" is also forbidden outright
        //                          once credentials are involved - the spec refuses the
        //                          combination, so a wildcard now becomes a rewrite in
        //                          Phase 10 rather than a tightening.
        //
        //   .allowedMethods(...)   name them. This API has five endpoints using four verbs;
        //                          "*" would advertise PATCH and TRACE as well. You do not
        //                          need to list OPTIONS - Spring answers the preflight itself.
        //
        //   .allowedHeaders(...)   which REQUEST headers the browser may send. Content-Type
        //                          is the one your fetch calls set, and the one that makes
        //                          those requests preflight in the first place.
        //
        //   .allowCredentials(...) whether cookies and Authorization headers ride along.
        //                          false today - nothing authenticates yet. Phase 10 changes
        //                          the answer, and that is the moment the "*" ban bites.
        //
        //   .maxAge(...)           seconds the browser may cache this preflight. Without it
        //                          every POST, PUT and DELETE costs two round trips instead
        //                          of one, forever.
    }
}
