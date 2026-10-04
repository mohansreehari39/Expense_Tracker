CREATE TABLE operationLog (
    opId TEXT NOT NULL PRIMARY KEY,
    entityType TEXT NOT NULL,
    entityId TEXT NOT NULL,
    opType TEXT NOT NULL,
    patchJson TEXT NOT NULL,
    authorDeviceId TEXT NOT NULL,
    hlcPhysical INTEGER NOT NULL,
    hlcCounter INTEGER NOT NULL,
    receivedFrom TEXT
);

CREATE INDEX operationLog_frontier ON operationLog(authorDeviceId, hlcPhysical, hlcCounter);

CREATE TABLE household (
    id TEXT NOT NULL PRIMARY KEY,
    name TEXT NOT NULL,
    createdAt INTEGER NOT NULL,
    defaultBudgetAmountMinorUnits INTEGER,
    defaultBudgetCurrency TEXT,
    settlementEnabled INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE category (
    id TEXT NOT NULL PRIMARY KEY,
    householdId TEXT NOT NULL,
    name TEXT NOT NULL,
    icon TEXT NOT NULL,
    isArchived INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE subcategory (
    id TEXT NOT NULL PRIMARY KEY,
    categoryId TEXT NOT NULL,
    name TEXT NOT NULL,
    isArchived INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE member (
    id TEXT NOT NULL PRIMARY KEY,
    householdId TEXT NOT NULL,
    displayName TEXT NOT NULL,
    deviceId TEXT,
    email TEXT,
    phone TEXT,
    isArchived INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE householdDependent (
    id TEXT NOT NULL PRIMARY KEY,
    householdId TEXT NOT NULL,
    name TEXT NOT NULL,
    category TEXT NOT NULL,
    isArchived INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE monthlyBudget (
    id TEXT NOT NULL PRIMARY KEY,
    householdId TEXT NOT NULL,
    year INTEGER NOT NULL,
    month INTEGER NOT NULL,
    totalAmountMinorUnits INTEGER NOT NULL,
    currency TEXT NOT NULL,
    perCategoryJson TEXT NOT NULL,
    UNIQUE(householdId, year, month)
);

CREATE TABLE householdExpense (
    id TEXT NOT NULL PRIMARY KEY,
    householdId TEXT NOT NULL,
    categoryId TEXT NOT NULL,
    subcategoryId TEXT,
    amountMinorUnits INTEGER NOT NULL,
    currency TEXT NOT NULL,
    paidByMemberId TEXT NOT NULL,
    occurredAt INTEGER NOT NULL,
    note TEXT NOT NULL,
    createdByDeviceId TEXT NOT NULL,
    createdAt INTEGER NOT NULL
);

CREATE INDEX householdExpense_occurredAt ON householdExpense(householdId, occurredAt);

CREATE TABLE householdExpenseBeneficiary (
    id TEXT NOT NULL PRIMARY KEY,
    householdExpenseId TEXT NOT NULL,
    memberId TEXT,
    dependentId TEXT,
    amountMinorUnits INTEGER NOT NULL,
    currency TEXT NOT NULL
);

CREATE TABLE householdExpenseContribution (
    id TEXT NOT NULL PRIMARY KEY,
    householdExpenseId TEXT NOT NULL,
    memberId TEXT NOT NULL,
    amountMinorUnits INTEGER NOT NULL,
    currency TEXT NOT NULL
);

CREATE TABLE trip (
    id TEXT NOT NULL PRIMARY KEY,
    name TEXT NOT NULL,
    startDate INTEGER NOT NULL,
    endDate INTEGER,
    budgetAmountMinorUnits INTEGER NOT NULL,
    currency TEXT NOT NULL,
    createdBy TEXT NOT NULL,
    isClosed INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE tripParticipant (
    id TEXT NOT NULL PRIMARY KEY,
    tripId TEXT NOT NULL,
    displayName TEXT NOT NULL,
    memberId TEXT,
    isArchived INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE tripExpense (
    id TEXT NOT NULL PRIMARY KEY,
    tripId TEXT NOT NULL,
    categoryId TEXT,
    subcategoryId TEXT,
    amountMinorUnits INTEGER NOT NULL,
    currency TEXT NOT NULL,
    paidByParticipantId TEXT NOT NULL,
    occurredAt INTEGER NOT NULL,
    note TEXT NOT NULL
);

CREATE TABLE expenseSplit (
    id TEXT NOT NULL PRIMARY KEY,
    tripExpenseId TEXT NOT NULL,
    participantId TEXT NOT NULL,
    shareAmountMinorUnits INTEGER NOT NULL,
    currency TEXT NOT NULL
);

CREATE TABLE tripExpenseContribution (
    id TEXT NOT NULL PRIMARY KEY,
    tripExpenseId TEXT NOT NULL,
    participantId TEXT NOT NULL,
    amountMinorUnits INTEGER NOT NULL,
    currency TEXT NOT NULL
);

CREATE TABLE settlement (
    id TEXT NOT NULL PRIMARY KEY,
    tripId TEXT NOT NULL,
    fromParticipantId TEXT NOT NULL,
    toParticipantId TEXT NOT NULL,
    amountMinorUnits INTEGER NOT NULL,
    currency TEXT NOT NULL,
    settledAt INTEGER NOT NULL,
    note TEXT NOT NULL
);

CREATE TABLE householdSettlement (
    id TEXT NOT NULL PRIMARY KEY,
    householdId TEXT NOT NULL,
    fromMemberId TEXT NOT NULL,
    toMemberId TEXT NOT NULL,
    amountMinorUnits INTEGER NOT NULL,
    currency TEXT NOT NULL,
    settledAt INTEGER NOT NULL,
    note TEXT NOT NULL
);

CREATE TABLE device (
    deviceId TEXT NOT NULL PRIMARY KEY,
    householdId TEXT NOT NULL,
    ownerMemberId TEXT NOT NULL,
    lastSeenHlcPhysical INTEGER,
    lastSeenHlcCounter INTEGER,
    publicKey TEXT NOT NULL
);

CREATE TABLE pairedDevice (
    id TEXT NOT NULL PRIMARY KEY,
    label TEXT NOT NULL,
    pairingKey TEXT NOT NULL,
    pairedAt INTEGER NOT NULL,
    lastSeenAt INTEGER NOT NULL
);
