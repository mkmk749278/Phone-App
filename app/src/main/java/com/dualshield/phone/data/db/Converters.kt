package com.dualshield.phone.data.db

import androidx.room.TypeConverter
import com.dualshield.phone.core.model.Confidence
import com.dualshield.phone.core.model.PatternType
import com.dualshield.phone.core.model.Provenance
import com.dualshield.phone.core.model.RuleAction
import com.dualshield.phone.core.model.RuleCategory
import com.dualshield.phone.core.model.SimScope

/**
 * Enums are stored by name rather than ordinal so that reordering an enum can never
 * silently reinterpret existing rows as a different rule scope.
 */
class Converters {
    @TypeConverter fun simScopeToString(value: SimScope): String = value.name

    @TypeConverter
    fun stringToSimScope(value: String): SimScope =
        SimScope.entries.firstOrNull { it.name == value } ?: SimScope.SIM2

    @TypeConverter fun patternTypeToString(value: PatternType): String = value.name

    @TypeConverter
    fun stringToPatternType(value: String): PatternType =
        PatternType.entries.firstOrNull { it.name == value } ?: PatternType.EXACT

    @TypeConverter fun actionToString(value: RuleAction): String = value.name

    @TypeConverter
    fun stringToAction(value: String): RuleAction =
        RuleAction.entries.firstOrNull { it.name == value } ?: RuleAction.BLOCK

    @TypeConverter fun confidenceToString(value: Confidence): String = value.name

    @TypeConverter
    fun stringToConfidence(value: String): Confidence =
        Confidence.entries.firstOrNull { it.name == value } ?: Confidence.LOW

    @TypeConverter fun provenanceToString(value: Provenance): String = value.name

    @TypeConverter
    fun stringToProvenance(value: String): Provenance =
        Provenance.entries.firstOrNull { it.name == value } ?: Provenance.USER_DEFINED

    @TypeConverter fun categoryToString(value: RuleCategory): String = value.name

    @TypeConverter
    fun stringToCategory(value: String): RuleCategory = RuleCategory.fromName(value)
}
