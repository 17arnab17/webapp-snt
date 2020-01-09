import java.sql.ResultSet
import java.time.Instant

/**
 * This file stores all data classes for the application
 */

/**
 * Describes an uploaded image
 */
data class Image (val id : Int, val title : String, val width: Int, val height: Int, val path : String) {
    companion object {
        /**
         * Turns the result of connection#prepare#execute() into an Image object
         */
        fun fromQuery(r: ResultSet) : Image {
            // Advance the database cursor by one to get the first result
            r.next()

            // Fill the Image object
            return Image(r.getInt("id"), r.getString("title"), r.getInt("width"), r.getInt("height"), r.getString("path"))
        }

        /**
         * Turns a query for multiple images into a list of Image objects
         */
        fun allFromQuery(r: ResultSet) : List<Image> {
            // This creates a list and fills it with an Image object every time the call to r.next() succeeds
            return generateSequence {
                if (r.next()) Image(
                    r.getInt("id"),
                    r.getString("title"),
                    r.getInt("width"),
                    r.getInt("height"),
                    r.getString("path")
                ) else null
            }.toList()
        }
    }
}

/**
 * This class describes a comment to an image
 */
data class Comment (val image_id : Int, val userName : String, val comment : String) {
    companion object {
        /**
         * Turns the result of connection#prepare#execute() into a Comment object
         */
        fun fromQuery(r: ResultSet) : List<Comment> {
            val comments = mutableListOf<Comment>()
            while (r.next()) {
                comments.add(Comment(
                    r.getInt("image_id"),
                    r.getString("user_name"),
                    r.getString("comment")
                ))
            }
            return comments
        }
    }
}

/**
 * This describes uploaded image metadata
 */
data class MetaData(
    val creationTime: Instant?,
    val cameraMake: String?,
    val cameraModel: String?,
    val orientation: Int?,
    val horizontalPpi: Int?,
    val verticalPpi: Int?,
    val shutterSpeed: Float?,
    val colorSpace: String?
) {
    companion object {
        /**
         * Turns the result of connection#prepare#execute() into a MetaData object
         */
        fun fromQuery(r: ResultSet) : MetaData? {
            // Return null if no data was found
            return if (r.next()) {
                MetaData(
                    r.getTimestamp("creationtime").toInstant(),
                    r.getString("camera_make"),
                    r.getString("camera_model"),
                    r.getInt("orientation"),
                    r.getInt("horizontal_ppi"),
                    r.getInt("vertical_ppi"),
                    r.getFloat("shutter_speed"),
                    r.getString("color_space")
                )
            } else {
                null
            }
        }
    }
}