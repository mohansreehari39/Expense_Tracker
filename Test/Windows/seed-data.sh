#!/bin/bash
# Seeds the running Kharcha app with realistic sample data, for UI testing.
# Same idea as seed-data.ps1 but for WSL/Linux/macOS; requires python3
# (for JSON parsing) plus curl. Start the app first, leave it running,
# then run this script.
set -e
BASE=http://localhost:47321/api/v1
TODAY=$(date +%s)
YEAR=$(date +%Y)
MONTH=$(date +%-m)
days_ago() { echo $(( (TODAY - $1*86400) * 1000 )); }

json_get() { python3 -c "import json,sys;print(json.load(sys.stdin)$1)"; }

echo "== Household 1: Sharma Family =="
H1=$(curl -s -X POST $BASE/households -H "Content-Type: application/json" -d '{"name":"Sharma Family"}' | json_get "['id']")
curl -s -X POST $BASE/households/$H1/budgets -H "Content-Type: application/json" \
  -d "{\"year\":$YEAR,\"month\":$MONTH,\"totalAmountMinorUnits\":4500000,\"currency\":\"INR\"}" > /dev/null

CATS_JSON=$(curl -s $BASE/households/$H1)
cat_id() { echo "$CATS_JSON" | python3 -c "import json,sys; d=json.load(sys.stdin); print(next(c['id'] for c in d['categories'] if c['name']=='$1'))"; }
GROCERIES=$(cat_id Groceries); UTILITIES=$(cat_id Utilities); RENT=$(cat_id Rent); EATING=$(cat_id "Eating Out"); OTHER=$(cat_id Other)

add_member() { curl -s -X POST $BASE/households/$H1/members -H "Content-Type: application/json" -d "{\"displayName\":\"$1\"}" | json_get "['id']"; }
ROHAN=$(add_member Rohan); SNEHA=$(add_member Sneha)

add_expense() {
  curl -s -X POST $BASE/households/$H1/expenses -H "Content-Type: application/json" \
    -d "{\"categoryId\":\"$1\",\"amountMinorUnits\":$2,\"currency\":\"INR\",\"paidByMemberId\":\"$3\",\"occurredAt\":$(days_ago $4),\"note\":\"$5\"}" > /dev/null
}
add_expense $RENT 1500000 $ROHAN 20 "July rent"
add_expense $GROCERIES 180000 $SNEHA 12 "Big Bazaar run"
add_expense $EATING 95000 $ROHAN 6 "Family dinner"
add_expense $UTILITIES 250000 $ROHAN 5 "Electricity bill"
add_expense $GROCERIES 320000 $SNEHA 3 "Weekly groceries"
add_expense $OTHER 60000 $SNEHA 2 "Household supplies"
add_expense $EATING 310000 $ROHAN 1 "Weekend takeout"
add_expense $GROCERIES 280000 $SNEHA 0 "Fresh produce"
echo "Sharma Family: household=$H1, budget=45000 INR, members=Rohan,Sneha"

echo "== Household 2: Roommates =="
H2=$(curl -s -X POST $BASE/households -H "Content-Type: application/json" -d '{"name":"Roommates"}' | json_get "['id']")
curl -s -X POST $BASE/households/$H2/budgets -H "Content-Type: application/json" \
  -d "{\"year\":$YEAR,\"month\":$MONTH,\"totalAmountMinorUnits\":2000000,\"currency\":\"INR\"}" > /dev/null
CATS2_JSON=$(curl -s $BASE/households/$H2)
cat_id2() { echo "$CATS2_JSON" | python3 -c "import json,sys; d=json.load(sys.stdin); print(next(c['id'] for c in d['categories'] if c['name']=='$1'))"; }
U2=$(cat_id2 Utilities); G2=$(cat_id2 Groceries)
add_member2() { curl -s -X POST $BASE/households/$H2/members -H "Content-Type: application/json" -d "{\"displayName\":\"$1\"}" | json_get "['id']"; }
ALEX=$(add_member2 Alex); JORDAN=$(add_member2 Jordan)
curl -s -X POST $BASE/households/$H2/expenses -H "Content-Type: application/json" \
  -d "{\"categoryId\":\"$U2\",\"amountMinorUnits\":150000,\"currency\":\"INR\",\"paidByMemberId\":\"$ALEX\",\"occurredAt\":$(days_ago 2),\"note\":\"Wifi bill\"}" > /dev/null
curl -s -X POST $BASE/households/$H2/expenses -H "Content-Type: application/json" \
  -d "{\"categoryId\":\"$G2\",\"amountMinorUnits\":90000,\"currency\":\"INR\",\"paidByMemberId\":\"$JORDAN\",\"occurredAt\":$(days_ago 5),\"note\":\"Shared snacks\"}" > /dev/null
echo "Roommates: household=$H2, budget=20000 INR, members=Alex,Jordan"

echo "== Activity 1: Goa Trip =="
T1=$(curl -s -X POST $BASE/trips -H "Content-Type: application/json" \
  -d "{\"name\":\"Goa Trip\",\"startDate\":$(days_ago 10),\"budgetAmountMinorUnits\":3000000,\"currency\":\"INR\",\"participantNames\":[\"Aditi\",\"Rahul\",\"Priya\"]}" | json_get "['id']")
TRIP1_JSON=$(curl -s $BASE/trips/$T1)
p_id() { echo "$TRIP1_JSON" | python3 -c "import json,sys; d=json.load(sys.stdin); print(next(p['id'] for p in d['participants'] if p['displayName']=='$1'))"; }
ADITI=$(p_id Aditi); RAHUL=$(p_id Rahul); PRIYA=$(p_id Priya)
curl -s -X POST $BASE/trips/$T1/expenses -H "Content-Type: application/json" \
  -d "{\"amountMinorUnits\":900000,\"currency\":\"INR\",\"paidByParticipantId\":\"$ADITI\",\"occurredAt\":$(days_ago 10),\"note\":\"Hotel booking\"}" > /dev/null
curl -s -X POST $BASE/trips/$T1/expenses -H "Content-Type: application/json" \
  -d "{\"amountMinorUnits\":450000,\"currency\":\"INR\",\"paidByParticipantId\":\"$RAHUL\",\"occurredAt\":$(days_ago 9),\"note\":\"Dinner at beach shack\"}" > /dev/null
curl -s -X POST $BASE/trips/$T1/expenses -H "Content-Type: application/json" \
  -d "{\"amountMinorUnits\":300000,\"currency\":\"INR\",\"paidByParticipantId\":\"$PRIYA\",\"occurredAt\":$(days_ago 8),\"note\":\"Cab fares\"}" > /dev/null
echo "Goa Trip: trip=$T1, 3 participants, 3 expenses"

echo "== Activity 2: Office Lunch =="
T2=$(curl -s -X POST $BASE/trips -H "Content-Type: application/json" \
  -d "{\"name\":\"Office Lunch\",\"startDate\":$(days_ago 1),\"budgetAmountMinorUnits\":500000,\"currency\":\"INR\",\"participantNames\":[\"You\",\"Sam\"]}" | json_get "['id']")
TRIP2_JSON=$(curl -s $BASE/trips/$T2)
YOU=$(echo "$TRIP2_JSON" | python3 -c "import json,sys; d=json.load(sys.stdin); print(next(p['id'] for p in d['participants'] if p['displayName']=='You'))")
curl -s -X POST $BASE/trips/$T2/expenses -H "Content-Type: application/json" \
  -d "{\"amountMinorUnits\":240000,\"currency\":\"INR\",\"paidByParticipantId\":\"$YOU\",\"occurredAt\":$(days_ago 1),\"note\":\"Team lunch\"}" > /dev/null
echo "Office Lunch: trip=$T2, 2 participants, 1 expense"

echo "== Done =="
