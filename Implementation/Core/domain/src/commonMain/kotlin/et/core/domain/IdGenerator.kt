package et.core.domain

/** Injected so use cases are testable without a real UUID/random source. */
fun interface IdGenerator {
    fun newId(): String
}
