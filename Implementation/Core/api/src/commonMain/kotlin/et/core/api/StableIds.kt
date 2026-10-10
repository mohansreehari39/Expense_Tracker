package et.core.api

/** SHA-256 of [bytes] — platform-provided (JDK MessageDigest on the JVM, which Android also uses). */
internal expect fun sha256(bytes: ByteArray): ByteArray

/**
 * Ids derived from what a thing *is* rather than random, so two devices
 * that create the same thing independently — offline, before syncing —
 * produce the same id and the two copies simply merge
 * (Test/Sync/corner-cases.md, S3, S4, S5). Renaming keeps the original id;
 * the id only matters at creation.
 */
object StableIds {
    private fun hash(vararg parts: String): String =
        sha256(parts.joinToString("\u0000").encodeToByteArray()).take(16).joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }

    /** Trimmed, inner spaces collapsed, lower-case — "  Eating  Out" and "eating out" are the same name. */
    fun normalizeName(name: String): String = name.trim().replace(Regex("\\s+"), " ").lowercase()

    /** Digits only; the last 10 when longer, so "+91 98450 12345" and "9845012345" match. */
    fun normalizePhone(phone: String): String = phone.filter(Char::isDigit).takeLast(10)

    fun normalizeEmail(email: String): String = email.trim().lowercase()

    fun category(householdId: String, name: String) = "cat-" + hash("category", householdId, normalizeName(name))

    fun subcategory(categoryId: String, name: String) = "sub-" + hash("subcategory", categoryId, normalizeName(name))

    fun dependent(householdId: String, category: String, name: String) = "dep-" + hash("dependent", householdId, category, normalizeName(name))

    /** One budget override per household per month (S5). */
    fun monthlyBudget(householdId: String, year: Int, month: Int) = "budget-" + hash("monthlyBudget", householdId, year.toString(), month.toString())

    /**
     * A person in a household/activity ([scopeId]) is identified by name +
     * age + email + mobile together (S4): when all four are known, the id is
     * derived from them, so the same person added on two phones merges into
     * one. Returns null when any is missing — e.g. a guest typed in by name
     * only — and the caller uses a random id: such people never merge
     * automatically.
     */
    fun person(scopeId: String, name: String?, age: Int?, email: String?, phone: String?): String? {
        if (name.isNullOrBlank() || age == null || email.isNullOrBlank() || phone.isNullOrBlank()) return null
        val digits = normalizePhone(phone)
        if (digits.isEmpty()) return null
        return "person-" + hash("person", scopeId, normalizeName(name), age.toString(), normalizeEmail(email), digits)
    }
}
