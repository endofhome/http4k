package org.http4k.contract.ui.redoc

import org.http4k.contract.ui.RedocConfig
import org.http4k.contract.ui.toFilter
import org.http4k.core.Method.GET
import org.http4k.core.Response
import org.http4k.core.Status.Companion.FOUND
import org.http4k.core.appendToPath
import org.http4k.core.then
import org.http4k.core.with
import org.http4k.lens.Header
import org.http4k.routing.ResourceLoader
import org.http4k.routing.bind
import org.http4k.routing.routes
import org.http4k.routing.static

fun redocWebjar(configFn: RedocConfig.() -> Unit = {}) = routes(
    "" bind GET to { Response(FOUND).with(Header.LOCATION of it.uri.appendToPath("index.html")) },

    RedocConfig()
        .also(configFn)
        .toFilter("redoc.standalone.js")
        .then(static(ResourceLoader.Classpath("org/http4k/contract/ui/redoc/"))),

    static(ResourceLoader.Classpath( "/META-INF/resources/webjars/redoc/2.0.0/"))
)
