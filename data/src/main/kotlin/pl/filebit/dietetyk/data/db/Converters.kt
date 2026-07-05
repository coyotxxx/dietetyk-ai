package pl.filebit.dietetyk.data.db

import androidx.room.TypeConverter
import pl.filebit.dietetyk.core.model.ActivityLevel
import pl.filebit.dietetyk.core.model.DietGoalType
import pl.filebit.dietetyk.core.model.Gender

/** Konwertery enumów rdzenia ↔ String (Room). */
class Converters {
    @TypeConverter fun genderToString(v: Gender): String = v.name
    @TypeConverter fun stringToGender(v: String): Gender = Gender.valueOf(v)

    @TypeConverter fun activityToString(v: ActivityLevel): String = v.name
    @TypeConverter fun stringToActivity(v: String): ActivityLevel = ActivityLevel.valueOf(v)

    @TypeConverter fun goalToString(v: DietGoalType): String = v.name
    @TypeConverter fun stringToGoal(v: String): DietGoalType = DietGoalType.valueOf(v)
}
