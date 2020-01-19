package nl.utwente.softwaresecurity

import Comment
import Image
import MetaData
import XmlConverter
import io.ktor.application.*
import io.ktor.response.*
import io.ktor.request.*
import io.ktor.routing.*
import io.ktor.http.*
import io.ktor.thymeleaf.Thymeleaf
import io.ktor.thymeleaf.ThymeleafContent
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver
import io.ktor.http.content.*
import io.ktor.features.*
import com.fasterxml.jackson.databind.*
import io.ktor.jackson.*
import java.io.File
import java.io.FileNotFoundException
import java.io.InputStream
import java.math.BigInteger
import java.security.MessageDigest
import java.sql.Connection
import java.sql.DriverManager
import javax.imageio.ImageIO
import com.fasterxml.jackson.dataformat.xml.JacksonXmlModule
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.apache.http.HttpRequestInterceptor
import java.io.IOException
import java.lang.Exception
import java.sql.Timestamp
import java.nio.file.Files
import java.security.Security
import java.util.regex.Matcher

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)

}

// These are create statements to initialise the database. They will not do anything if the database already exists.
val tables = listOf(
    "CREATE TABLE IF NOT EXISTS images (" +
            "id SERIAL UNIQUE, " +
            "path VARCHAR(150) NOT NULL, " +
            "width INTEGER NOT NULL, " +
            "height INTEGER NOT NULL, " +
            "title VARCHAR(200) NOT NULL, " +
            "private BOOLEAN DEFAULT FALSE" +
    ")",
    "CREATE INDEX IF NOT EXISTS images_search_index ON images USING gin(to_tsvector('simple', title))",
    "CREATE TABLE IF NOT EXISTS comments (" +
            "image_id INTEGER, " +
            "user_name VARCHAR(150), " +
            "comment VARCHAR(300), " +
            "CONSTRAINT fk_comment_image FOREIGN KEY (image_id) REFERENCES images(id) " +
    ")",
    "CREATE TABLE IF NOT EXISTS metadata (" +
            "image_id INTEGER, " +
            "creationTime TIMESTAMP, " +
            "camera_make VARCHAR(10000), " +
            "camera_model VARCHAR (10000), " +
            "orientation INTEGER, " +
            "horizontal_ppi INTEGER, " +
            "vertical_ppi INTEGER, " +
            "shutter_speed FLOAT, " +
            "color_space VARCHAR(20)," +
            "CONSTRAINT fk_metadata_images FOREIGN KEY (image_id) REFERENCES images(id)" +
    ")"
)

val storageDir = System.getenv("IMAGE_DIR") ?: "/var/tmp/images"

/**
 * Connects to the database, initialises it if necessary, and returns a connection to the database for use in the
 * application.
 */
fun initDb() : Connection? {
    // Extract parameters from the environment variables or use sane-ish defaults.
    val database = System.getenv("POSTGRES_DB") ?: "production"
    val username = System.getenv("POSTGRES_USER") ?: "user"
    val password = System.getenv("POSTGRES_PASSWORD") ?: "testing-password"
    val host = System.getenv("POSTGRES_HOST") ?: "production-postgres"

    // Set up the connection
    val con = try {
        DriverManager.getConnection("jdbc:postgresql://${host}/${database}", username, password)
    } catch (e: Exception) {
        e.printStackTrace()
        error("Could not connect to postgres database! Check your environment variables and try again!")
    }

    // Execute all database initialisation queries
    tables.forEach {
        con.prepareStatement(it).execute()
    }

    // Return the ready connection
    return con
}

@Suppress("unused") // Referenced in application.conf
@kotlin.jvm.JvmOverloads


fun Application.module(testing: Boolean = false) {
    // Set up a connection to the database
    val conn = if (testing) null else initDb()
    
    Files.createDirectories((File(storageDir)).toPath())


    // Set up templating
    install(Thymeleaf) {
        setTemplateResolver(ClassLoaderTemplateResolver().apply {
            prefix = "templates/thymeleaf/"
            suffix = ".html"
            characterEncoding = "utf-8"
        })
    }

    // Set up compressing large responses to make them faster
    install(Compression) {
        gzip {
            priority = 1.0
        }
        deflate {
            priority = 10.0
            minimumSize(1024) // condition
        }
    }
    // Adding headers to make the site more secure
    install(DefaultHeaders) {
        header("X-Content-Type-Options", "nosniff")
        header("X-Developer", "Arnab Chattopadhyay")
        header("X-XSS-Protection", "1")
        header("X-Frame-Options", "sameorigin")
    }


    // Set up XML wrapping for metadata format
    val xmlMapper = XmlMapper(JacksonXmlModule().apply {
        setDefaultUseWrapper(false)
    }).registerKotlinModule()
        .configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true)
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        .registerModule(JavaTimeModule())

    // Allow for automatically turning raw data classes into JSON
    install(ContentNegotiation) {
        register(ContentType.Application.Json, JacksonConverter())
        register(ContentType.Application.Xml, XmlConverter(xmlMapper))
    }


    // Here all paths the web server will listen to are defined
    routing {
        trace { application.log.trace(it.buildText()) }

        // This is the index of the web app. See resources/templates.thymeleaf/index.html
        get("/") {

            val images = if (conn != null) {
                // List all images that are publicly viewable
                Image.allFromQuery(conn.prepareStatement("SELECT * FROM images WHERE NOT private").executeQuery())
            } else {
                listOf()
            }

            // Render the home page
            call.respond(ThymeleafContent("index", mapOf("images" to images)))
        }

        // This is the upload page. See resources/templates.thymeleaf/upload.html
        get("/upload") {
            call.respond(ThymeleafContent("upload", mapOf()))
        }


        // This is the details page for a single image. See resources/templates.thymeleaf/image.html
        get("/image/{imageId}") {
            // Get the image ID from the URL
            /*fun anImageFile() = object: Matcher<File> {
                override fun test(value:File): Result{

                }
            }

            val anImageFile = object :Matcher{
                private val suffixes = setOf("jpeg","jpg","gif")
                override fun test(value:File) : Result {
                    val fileExists = value.exists()
                    val hasImageSuffix = suffixes.any {
                        value.name.toLowerCase().endsWith(it)
                    }
                    if(!hasImageSuffix){
                        return Result(false)
                    }
                    return Result(true)
                }
            }
*/

            val imageId = call.parameters["imageId"]?.toInt()

            if (imageId == null) {
                call.respond(HttpStatusCode.NotFound)
            } else {
                // Query the database for the image
                val image =
                    Image.fromQuery(conn!!.prepareStatement("SELECT * FROM images WHERE id = $imageId").executeQuery())
                if (image.title == null) {
                    call.respond(HttpStatusCode.NotFound)
                }
                //val pathOfImage = "/image/{imageId}"

                // Query the database for a list of comments
                val comments =
                    Comment.fromQuery(conn.prepareStatement("SELECT * FROM comments WHERE image_id = $imageId").executeQuery())

                // Query the database for image metadata that might have been uploaded
                val meta =
                    MetaData.fromQuery(conn.prepareStatement("SELECT * FROM metadata WHERE image_id = $imageId").executeQuery())

                // Store all data into a map
                val context = mutableMapOf("image" to image, "comments" to comments)

                // Metadata is optional, so only include that if it exists
                if (meta != null) {
                    context["metadata"] = meta
                }

                // Render the result
                call.respond(ThymeleafContent("image", context))
            }

        }

        // This is the handler for posting a new comment
        post("/image/{imageId}/comments/post") {
            // Get the POST query
            val postParams = call.receiveParameters()
            val name =  postParams["userName"]
            val comment = postParams["comment"]

            // Get the image ID from the URL
            val imageId = call.parameters["imageId"]?.toInt()

            if (imageId == null) {
                call.respond(HttpStatusCode.NotFound)
            } else {
                // Insert the comment into the database
                conn!!
                    .prepareStatement("INSERT INTO comments (image_id, user_name, comment) VALUES ($imageId, '$name', '$comment')")
                    .execute()

                // Redirect back to the image page
                call.respondRedirect("/image/$imageId")
            }
        }

        // This is the handler for getting a raw image file
        get("/img/{filename}") {
            // Get the file name from the URL
            val filename : String? = call.parameters["filename"]

            // Return a 404 when the file does not exist
            if (filename == null || filename.endsWith(".txt")) {
                call.respond(HttpStatusCode.NotFound)
            }

            // Try to return the contents of the file requested, or return a 404 if something goes wrong
            try {
                val fileContents = File("${storageDir}/${filename}").readBytes()
                call.respond(fileContents)
            } catch (e: FileNotFoundException) {
                call.respond(HttpStatusCode.NotFound)
            }
        }

        // This is the handler for uploading an image file
        post("/upload") {
            // Uploads are handled through a multipart POST request
            val multipart = call.receiveMultipart()

            // We cannot easily read out all data in one go, so we set up writable variables which we update later
            var title = ""
            var private = false
            var filename = ""
            var imageStream : InputStream?
            var imageBytes = ByteArray(0)
            var metaData : MetaData? = null

            // Map through all parts of the POST data
            multipart.forEachPart {
                // If the part is a regular form item, either update the title variable or the private variable
                if (it is PartData.FormItem) {
                    when (it.name) {
                        "title" -> title = it.value
                        "private" -> private = it.value == "on"
                        else -> {print("Unknown parameter passed: ${it.name}")}
                    }
                }
                // If the part is a file upload and it is the file, get the file name or store it based on the MD5 hash of the file contents
                if (it is PartData.FileItem) {
                    if (it.name == "file") {
                        imageStream = it.streamProvider()
                        imageBytes = imageStream!!.readBytes()

                        // Fallback to md5 if there is no file name
                        val md = MessageDigest.getInstance("MD5")
                        val md5Name = BigInteger(1, md.digest(imageBytes)).toString(16).padStart(32, '0')

                        filename = it.originalFileName ?: md5Name
                    } else if (it.name == "metadata") {
                        try {
                            val text = it.streamProvider().bufferedReader().readText()
                            metaData = xmlMapper.readValue(text)
                        } catch (e : IOException) {
                            // No metadata uploaded, not a problem
                        }
                    } else {
                        print("Unknown parameter passed: ${it.name}")
                    }
                }
                if (it is PartData.BinaryItem) {
                    print("Unknown parameter passed: ${it.name}")
                }
            }

            // Store the image file
            val imageFile = File("${storageDir}/${filename}")
            imageFile.writeBytes(imageBytes)

            // Read back the image to extract metadata
            val imageFromFile = ImageIO.read(imageFile)

            // Insert the image into the database
            val insertResult = conn!!
                .prepareStatement("INSERT INTO" +
                        " images (title, path, width, height, private)" +
                        " VALUES ('${title}', '${filename}', ${imageFromFile.width}, ${imageFromFile.height}, ${private})" +
                        " RETURNING id")
                .executeQuery()

            // Get the image ID for the uploaded image
            insertResult.next()
            val newImageId = insertResult.getInt(1)

            if (newImageId > 0 && metaData != null) {
                val stmt = conn
                    .prepareStatement("INSERT INTO metadata (image_id, creationtime, camera_make, camera_model, orientation, horizontal_ppi, vertical_ppi, shutter_speed, color_space) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")
                with(stmt) {
                    setInt(1, newImageId)
                    setTimestamp(2, Timestamp.from(metaData!!.creationTime))
                    setString(3, metaData!!.cameraMake)
                    setString(4, metaData!!.cameraModel)
                    setInt(5, metaData!!.orientation ?: 0)
                    setInt(6, metaData!!.horizontalPpi ?: 0)
                    setInt(7, metaData!!.verticalPpi ?: 0)
                    setFloat(8, metaData!!.shutterSpeed ?: 0.0f)
                    setString(9, metaData!!.colorSpace)

                    execute()
                }
            }

            // Redirect to the image page
            call.respondRedirect("/image/${newImageId}")
        }

        get("/image/{imageId}/metadata") {
            val imageId = call.parameters["imageId"]?.toInt()
            if (imageId == null) {
                call.respond(HttpStatusCode.NotFound)
            } else {
                val metaData = MetaData.fromQuery(conn!!
                    .prepareStatement("SELECT * FROM metadata WHERE image_id = $imageId")
                    .executeQuery())

                if (metaData == null) {
                    call.respond(HttpStatusCode.NotFound)
                } else {
                    call.respond(metaData)
                }
            }
        }

        // This is the search page handler
        get("/search") {
            // Extract the search query from the query parameters
            val query = call.request.queryParameters["q"] ?: ""

            // Search in the database
            val results = Image.allFromQuery(
                with (conn!!.prepareStatement("SELECT * FROM images" +
                        " WHERE NOT private" +
                        " AND (to_tsvector('simple', title) @@ plainto_tsquery('simple', ?))")) {
                    setString(1, query)
                    executeQuery()
                }
            )

            // Render the search results
            call.respond(ThymeleafContent("search", mapOf("results" to results, "query" to query)))
        }

        // This takes care of all the static files (see resources/static/) such as CSS and static image assets
        static("/static") {
            resources("static")
        }
    }
}

