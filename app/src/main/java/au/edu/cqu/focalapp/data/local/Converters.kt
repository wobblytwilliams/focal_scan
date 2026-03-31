package au.edu.cqu.focalapp.data.local

import androidx.room.TypeConverter
import au.edu.cqu.focalapp.domain.model.Behavior

class Converters {
    @TypeConverter
    fun fromBehavior(value: Behavior): String = value.name

    @TypeConverter
    fun toBehavior(value: String): Behavior = Behavior.valueOf(value)
}
