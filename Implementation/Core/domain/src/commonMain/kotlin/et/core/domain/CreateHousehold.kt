package et.core.domain

import et.core.model.Category
import et.core.model.Household

private val DEFAULT_CATEGORY_NAMES = listOf("Groceries", "Utilities", "Rent", "Eating Out", "Other")

class CreateHousehold(
    private val repository: Repository,
    private val idGenerator: IdGenerator,
) {
    suspend operator fun invoke(name: String, createdAt: Long): Household {
        require(name.isNotBlank()) { "household name must not be blank" }
        val household = Household(id = idGenerator.newId(), name = name, createdAt = createdAt)
        repository.saveHousehold(household)
        for (categoryName in DEFAULT_CATEGORY_NAMES) {
            repository.saveCategory(
                Category(id = idGenerator.newId(), householdId = household.id, name = categoryName, icon = ""),
            )
        }
        return household
    }
}
