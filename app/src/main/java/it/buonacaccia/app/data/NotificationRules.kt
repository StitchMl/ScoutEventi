package it.buonacaccia.app.data

data class NotificationTypeRegionRule(
    val type: String,
    val regions: Set<String> = emptySet()
) {
    init {
        require(type.isNotBlank()) { "type must not be blank" }
    }
}

data class NotificationPreferences(
    val mutedTypes: Set<String> = emptySet(),
    val allowedTypes: Set<String> = emptySet(),
    val defaultRegions: Set<String> = emptySet(),
    val defaultZones: Set<String> = emptySet(),
    val typeRegionRules: List<NotificationTypeRegionRule> = emptyList()
)

object NotificationRuleEngine {
    fun shouldNotify(event: BcEvent, preferences: NotificationPreferences): Boolean {
        val normalizedType = normalize(event.type)
        val normalizedRegion = normalize(event.region)
        val normalizedZone = normalize(event.guessZone())
        val normalizedMutedTypes = preferences.mutedTypes.mapNotNull(::normalize).toSet()
        val normalizedAllowedTypes = selectedTypes(preferences)
        val rulesByType = preferences.typeRegionRules.associateBy { normalize(it.type) }

        if (normalizedMutedTypes.isNotEmpty()) {
            val typeAllowed = normalizedType == null || normalizedType !in normalizedMutedTypes
            return typeAllowed &&
                regionMatches(normalizedRegion, preferences.defaultRegions) &&
                zoneMatches(normalizedZone, preferences.defaultZones)
        }

        if (normalizedAllowedTypes.isNotEmpty()) {
            val typeKey = normalizedType ?: return false
            if (typeKey !in normalizedAllowedTypes) return false
            val explicitRule = rulesByType[typeKey]
            val effectiveRegions = explicitRule?.regions ?: preferences.defaultRegions
            return regionMatches(normalizedRegion, effectiveRegions) &&
                zoneMatches(normalizedZone, preferences.defaultZones)
        }

        return regionMatches(normalizedRegion, preferences.defaultRegions) &&
            zoneMatches(normalizedZone, preferences.defaultZones)
    }

    fun regionsForFetchPrefilter(preferences: NotificationPreferences): Set<String>? {
        val selectedTypes = selectedTypes(preferences)
        if (preferences.mutedTypes.isNotEmpty()) {
            return preferences.defaultRegions.takeIf { it.isNotEmpty() }
        }
        if (selectedTypes.isEmpty()) {
            return preferences.defaultRegions.takeIf { it.isNotEmpty() }
        }

        val rulesByType = preferences.typeRegionRules.associateBy { normalize(it.type) }
        val union = linkedSetOf<String>()

        selectedTypes.forEach { typeKey ->
            val effectiveRegions = rulesByType[typeKey]?.regions ?: preferences.defaultRegions
            if (effectiveRegions.isEmpty()) return null
            union += effectiveRegions
        }

        return union.takeIf { it.isNotEmpty() }
    }

    fun selectedTypes(preferences: NotificationPreferences): Set<String> =
        buildSet {
            preferences.allowedTypes.mapNotNullTo(this, ::normalize)
            preferences.typeRegionRules.mapNotNullTo(this) { normalize(it.type) }
        }

    private fun regionMatches(normalizedRegion: String?, allowedRegions: Set<String>): Boolean {
        if (allowedRegions.isEmpty()) return true
        return normalizedRegion != null && allowedRegions.any { normalize(it) == normalizedRegion }
    }

    private fun zoneMatches(normalizedZone: String?, allowedZones: Set<String>): Boolean {
        if (allowedZones.isEmpty()) return true
        return normalizedZone != null && allowedZones.any { normalize(it) == normalizedZone }
    }

    private fun normalize(value: String?): String? =
        value?.trim()?.takeIf { it.isNotEmpty() }?.lowercase()
}
