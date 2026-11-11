# Container Viewer
Shows the contents of a container as icons when you hover over it, for every container in the game,
with a setting to turn each one on or off.

Containers:
- Looting bag, seed box, tackle box, forestry kit, huntsman's kit, Dizana's quiver, barbarian knapsack
- Rune pouch (divine included), plank sack
- Herb sack (silklined included), gem bag, coal bag, fish barrel (fish sack barrel included),
  log basket, forestry basket, flamtaer bag, gem pouch and gem sack, essence pouches (small to
  colossal), hunter guild meat pouch, fur pouch and seed sack, scroll sack, forager's pouch,
  forestry clothes pouch, forestry cape pouch, Mastering Mixology secondary pouch

The bolt pouch and the druid pouch are left out: the bolt pouch has its own interface and the client
is never told which bolts are in which slot, and the druid pouch holds charges rather than items.

How much the client knows depends on the container:
- The rune pouch and the plank sack are kept in varbits, so those are always exact.
- The looting bag, seed box, tackle box, forestry kit, huntsman's kit, quiver and knapsack are sent
  to the client in full, so those are exact once the container has been opened once.
- The rest are only listed in the chat when checked. Check one to load it, and it is then kept up to
  date from the inventory as you fill it, empty it and pick things up with it open. Items that go in
  without passing through the inventory, such as herbs harvested straight into an open herb sack, are
  not counted until the next check.

Config options:
- Show above other tooltips
- Show how full it is
- Show total value
- Show high alch value
- Sort by value
- Text color
- One toggle per container
