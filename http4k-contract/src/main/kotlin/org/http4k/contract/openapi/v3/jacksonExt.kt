package org.http4k.contract.openapi.v3

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonPropertyDescription
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.PropertyNamingStrategy
import com.fasterxml.jackson.databind.annotation.JsonNaming
import org.http4k.contract.openapi.ApiInfo
import org.http4k.contract.openapi.ApiRenderer
import org.http4k.contract.openapi.OpenAPIJackson
import org.http4k.contract.openapi.OpenApiExtension
import org.http4k.contract.openapi.OpenApiVersion
import org.http4k.format.ConfigurableJackson
import org.http4k.format.Jackson
import kotlin.reflect.KParameter
import kotlin.reflect.full.createInstance

/**
 * Defaults for configuring OpenApi3 with Jackson
 */
fun OpenApi3(
    apiInfo: ApiInfo,
    json: ConfigurableJackson = OpenAPIJackson,
    extensions: List<OpenApiExtension> = emptyList(),
    servers: List<ApiServer> = emptyList(),
    version: OpenApiVersion = OpenApiVersion._3_0_0
) =
    OpenApi3(apiInfo, json, extensions, ApiRenderer.Auto(json, AutoJsonToJsonSchema(json)), servers = servers, version = version)

fun AutoJsonToJsonSchema(json: ConfigurableJackson) = AutoJsonToJsonSchema(
    json,
    FieldRetrieval.compose(
        SimpleLookup(metadataRetrievalStrategy =
        JacksonFieldMetadataRetrievalStrategy.then(PrimitivesFieldMetadataRetrievalStrategy)),
        FieldRetrieval.compose(JacksonJsonPropertyAnnotated, JacksonJsonNamingAnnotated(json))
    )
)

object JacksonJsonPropertyAnnotated : FieldRetrieval {
    override fun invoke(target: Any, name: String) =
        SimpleLookup(
            metadataRetrievalStrategy = JacksonFieldMetadataRetrievalStrategy
        )(target, target.javaClass.findName(name) ?: throw NoFieldFound(name, this))

    private fun Class<Any>.findName(name: String): String? =
        kotlin.constructors.first().parameters.firstNotNullOfOrNull { f ->
            f.annotations.filterIsInstance<JsonProperty>().find { it.value == name }
                ?.let { f.name }
        }
            ?: try {
                superclass?.findName(name)
            } catch (e: IllegalStateException) {
                throw NoFieldFound(name, this, e)
            }
}

class JacksonJsonNamingAnnotated(private val json: ConfigurableJackson = Jackson) : FieldRetrieval {
    override fun invoke(target: Any, name: String) = SimpleLookup(
        renamingStrategyIfRequired(target::class.java),
        JacksonFieldMetadataRetrievalStrategy
    )(target, name)

    private fun renamingStrategyIfRequired(clazz: Class<*>): (String) -> String {
        val namingStrategy = clazz.annotations
            .filterIsInstance<JsonNaming>()
            .map { it.value }.getOrNull(0)
            ?.let { it.createInstance() as PropertyNamingStrategy }
            ?: json.mapper.propertyNamingStrategy

        return if (namingStrategy is PropertyNamingStrategies.NamingBase) {
            { name: String -> namingStrategy.translate(name) }
        } else {
            { name -> name }
        }
    }
}

object JacksonFieldMetadataRetrievalStrategy : FieldMetadataRetrievalStrategy {
    override fun invoke(target: Any, fieldName: String): FieldMetadata =
        FieldMetadata(target.javaClass.findPropertyDescription(fieldName)?.let { mapOf("description" to it) }
            ?: emptyMap())

    private fun Class<Any>.findPropertyDescription(name: String): String? =
        kotlin.constructors.firstOrNull()?.let {
            it.parameters
                .firstOrNull { p -> p.kind == KParameter.Kind.VALUE && p.name == name }
                ?.let { p ->
                    p.annotations.filterIsInstance<JsonPropertyDescription>().firstOrNull()?.value
                } ?: superclass?.findPropertyDescription(name)
        } ?: superclass?.findPropertyDescription(name)
}
