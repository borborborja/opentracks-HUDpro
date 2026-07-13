package cat.rumb.app.data.tracks

/** Sort orders for the home track lists. Names are persisted in prefs — do not rename. */
enum class TrackSort {
    DATE_DESC, DATE_ASC, DISTANCE_DESC, DISTANCE_ASC, MUNICIPALITY, DIFFICULTY, TYPE;

    companion object {
        fun byName(name: String?): TrackSort = entries.firstOrNull { it.name == name } ?: DATE_DESC
    }
}

/** Pure sorting/filtering of track summaries (JVM-testable). */
object TrackSortFilter {

    fun comparator(sort: TrackSort): Comparator<FollowTrackEntity> = when (sort) {
        TrackSort.DATE_DESC -> compareByDescending { it.createdAt }
        TrackSort.DATE_ASC -> compareBy { it.createdAt }
        TrackSort.DISTANCE_DESC -> compareByDescending<FollowTrackEntity> { it.distanceMeters }
            .thenByDescending { it.createdAt }
        TrackSort.DISTANCE_ASC -> compareBy<FollowTrackEntity> { it.distanceMeters }
            .thenByDescending { it.createdAt }
        TrackSort.MUNICIPALITY -> compareBy<FollowTrackEntity, String?>(nullsLast(String.CASE_INSENSITIVE_ORDER)) { it.municipality }
            .thenByDescending { it.createdAt }
        TrackSort.DIFFICULTY -> compareByDescending<FollowTrackEntity> {
            DifficultyCalculator.kmEffort(it.distanceMeters, it.ascentM)
        }.thenByDescending { it.createdAt }
        TrackSort.TYPE -> compareBy<FollowTrackEntity, String?>(nullsLast(naturalOrder())) { it.activityType }
            .thenByDescending { it.createdAt }
    }

    fun apply(tracks: List<FollowTrackEntity>, sort: TrackSort, filterTypeId: String?): List<FollowTrackEntity> {
        val filtered = if (filterTypeId == null) tracks else tracks.filter { it.activityType == filterTypeId }
        return filtered.sortedWith(comparator(sort))
    }
}
