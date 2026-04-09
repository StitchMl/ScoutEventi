package it.buonacaccia.app.data

object FetchSafety {

    fun minimumExpectedCountForFullDataset(referenceCount: Int): Int? {
        if (referenceCount < 24) {
            return null
        }

        return maxOf(10, (referenceCount + 2) / 3)
    }
}
