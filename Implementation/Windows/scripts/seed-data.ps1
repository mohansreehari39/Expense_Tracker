<#
.SYNOPSIS
  Seeds the running Kharcha app with realistic sample data, for UI testing.

.DESCRIPTION
  Talks to the app's own REST API on localhost:47321 — same as the UI does —
  so start the app first (./gradlew :app:run or the installed .exe) and
  leave it running before executing this script.

  Creates two households (one deliberately near its weekly budget limit, to
  show the amber/red status) and two activities (a 3-person trip with mixed
  balances and settle-up suggestions, plus a simple 2-person one), spread
  across the current month/week so the dashboard and weekly charts aren't
  empty.

.EXAMPLE
  .\seed-data.ps1
#>

$ErrorActionPreference = "Stop"
$Base = "http://localhost:47321/api/v1"

function DaysAgoMillis($days) {
    [DateTimeOffset]::UtcNow.AddDays(-$days).ToUnixTimeMilliseconds()
}

function CatId($detail, $name) {
    ($detail.categories | Where-Object { $_.name -eq $name }).id
}

function ParticipantId($detail, $name) {
    ($detail.participants | Where-Object { $_.displayName -eq $name }).id
}

function AddHouseholdExpense($householdId, $categoryId, $amountMinorUnits, $daysAgo, $note) {
    $body = @{
        categoryId       = $categoryId
        amountMinorUnits = $amountMinorUnits
        currency         = "INR"
        paidByMemberId   = "local"
        occurredAt       = (DaysAgoMillis $daysAgo)
        note             = $note
    } | ConvertTo-Json -Depth 5
    Invoke-RestMethod -Uri "$Base/households/$householdId/expenses" -Method Post -ContentType "application/json" -Body $body | Out-Null
}

function AddTripExpense($tripId, $participantId, $amountMinorUnits, $daysAgo, $note) {
    $body = @{
        amountMinorUnits    = $amountMinorUnits
        currency            = "INR"
        paidByParticipantId = $participantId
        occurredAt          = (DaysAgoMillis $daysAgo)
        note                = $note
    } | ConvertTo-Json -Depth 5
    Invoke-RestMethod -Uri "$Base/trips/$tripId/expenses" -Method Post -ContentType "application/json" -Body $body | Out-Null
}

$Year = (Get-Date).Year
$Month = (Get-Date).Month

Write-Host "== Household 1: Sharma Family (near weekly budget limit) =="
$h1 = Invoke-RestMethod -Uri "$Base/households" -Method Post -ContentType "application/json" -Body (@{ name = "Sharma Family" } | ConvertTo-Json)
$h1Id = $h1.id
Invoke-RestMethod -Uri "$Base/households/$h1Id/budgets" -Method Post -ContentType "application/json" `
    -Body (@{ year = $Year; month = $Month; totalAmountMinorUnits = 4500000; currency = "INR" } | ConvertTo-Json) | Out-Null

$h1Detail = Invoke-RestMethod -Uri "$Base/households/$h1Id"
$groceries = CatId $h1Detail "Groceries"
$utilities = CatId $h1Detail "Utilities"
$rent = CatId $h1Detail "Rent"
$eating = CatId $h1Detail "Eating Out"
$other = CatId $h1Detail "Other"

AddHouseholdExpense $h1Id $rent 1500000 20 "July rent"
AddHouseholdExpense $h1Id $groceries 180000 12 "Big Bazaar run"
AddHouseholdExpense $h1Id $eating 95000 6 "Family dinner"
AddHouseholdExpense $h1Id $utilities 250000 5 "Electricity bill"
AddHouseholdExpense $h1Id $groceries 320000 3 "Weekly groceries"
AddHouseholdExpense $h1Id $other 60000 2 "Household supplies"
AddHouseholdExpense $h1Id $eating 310000 1 "Weekend takeout"
AddHouseholdExpense $h1Id $groceries 280000 0 "Fresh produce"
Write-Host "  household=$h1Id, budget=Rs 45,000"

Write-Host "== Household 2: Roommates (comfortably under budget) =="
$h2 = Invoke-RestMethod -Uri "$Base/households" -Method Post -ContentType "application/json" -Body (@{ name = "Roommates" } | ConvertTo-Json)
$h2Id = $h2.id
Invoke-RestMethod -Uri "$Base/households/$h2Id/budgets" -Method Post -ContentType "application/json" `
    -Body (@{ year = $Year; month = $Month; totalAmountMinorUnits = 2000000; currency = "INR" } | ConvertTo-Json) | Out-Null
$h2Detail = Invoke-RestMethod -Uri "$Base/households/$h2Id"
AddHouseholdExpense $h2Id (CatId $h2Detail "Utilities") 150000 2 "Wifi bill"
AddHouseholdExpense $h2Id (CatId $h2Detail "Groceries") 90000 5 "Shared snacks"
Write-Host "  household=$h2Id, budget=Rs 20,000"

Write-Host "== Activity 1: Goa Trip (3 participants, mixed balances) =="
$t1Body = @{
    name                    = "Goa Trip"
    startDate               = (DaysAgoMillis 10)
    budgetAmountMinorUnits  = 3000000
    currency                = "INR"
    participantNames        = @("Aditi", "Rahul", "Priya")
} | ConvertTo-Json -Depth 5
$t1 = Invoke-RestMethod -Uri "$Base/trips" -Method Post -ContentType "application/json" -Body $t1Body
$t1Id = $t1.id
$t1Detail = Invoke-RestMethod -Uri "$Base/trips/$t1Id"
AddTripExpense $t1Id (ParticipantId $t1Detail "Aditi") 900000 10 "Hotel booking"
AddTripExpense $t1Id (ParticipantId $t1Detail "Rahul") 450000 9 "Dinner at beach shack"
AddTripExpense $t1Id (ParticipantId $t1Detail "Priya") 300000 8 "Cab fares"
Write-Host "  trip=$t1Id, budget=Rs 30,000, 3 expenses"

Write-Host "== Activity 2: Office Lunch (2 participants, simple case) =="
$t2Body = @{
    name                   = "Office Lunch"
    startDate              = (DaysAgoMillis 1)
    budgetAmountMinorUnits = 500000
    currency               = "INR"
    participantNames       = @("You", "Sam")
} | ConvertTo-Json -Depth 5
$t2 = Invoke-RestMethod -Uri "$Base/trips" -Method Post -ContentType "application/json" -Body $t2Body
$t2Id = $t2.id
$t2Detail = Invoke-RestMethod -Uri "$Base/trips/$t2Id"
AddTripExpense $t2Id (ParticipantId $t2Detail "You") 240000 1 "Team lunch"
Write-Host "  trip=$t2Id, budget=Rs 5,000, 1 expense"

Write-Host ""
Write-Host "Done. Open (or switch to) the Kharcha window to see it."
