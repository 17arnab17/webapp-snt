import com.fasterxml.jackson.databind.ObjectMapper
import io.ktor.application.ApplicationCall
import io.ktor.features.ContentConverter
import io.ktor.http.ContentType
import io.ktor.http.content.TextContent
import io.ktor.request.ApplicationReceiveRequest
import io.ktor.util.pipeline.PipelineContext
import kotlinx.coroutines.io.ByteReadChannel
import kotlinx.coroutines.io.readRemaining

/**
 * Class to allow for automatic serialization and deserialization of XML
 * Based on https://github.com/spothero/herolab-spotnow/blob/master/src/com/spothero/lab/spotnow/api/parkonect/ParkonectXmlConverter.kt
 */
class XmlConverter(val mapper : ObjectMapper) : ContentConverter{
    override suspend fun convertForReceive(context: PipelineContext<ApplicationReceiveRequest, ApplicationCall>): Any? {
        val xmlStream = context.subject.value as ByteReadChannel?
        val xmlString = xmlStream?.readRemaining()?.readText()
        return mapper.readValue(xmlString ?: "", context.subject.type.javaObjectType)
    }

    override suspend fun convertForSend(
        context: PipelineContext<Any, ApplicationCall>,
        contentType: ContentType,
        value: Any
    ): Any? {
        return TextContent(mapper.writeValueAsString(value), contentType)
    }
}