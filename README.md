# Belote (Белот)

A desktop implementation of Bulgarian Belote in Java 17 + JavaFX, with a human
player (South) against three heuristic bots, fully localised in English and
Bulgarian.

## Build and run

Requires JDK 17 or newer and Maven.

```bash
mvn clean package
java -jar target/belote.jar
```

The build produces a single self-contained jar. JavaFX is bundled, defaulting to
the **macOS Apple Silicon** natives. For another platform, override the
classifier:

```bash
mvn clean package -Djavafx.platform=mac        # Intel macOS
mvn clean package -Djavafx.platform=win        # Windows
mvn clean package -Djavafx.platform=linux      # Linux
```

Cards are drawn from shapes and text at runtime, so there are no image assets to
ship or lose.

## Layout

```
com.belote
├── Launcher              entry point for the shaded jar
├── model                 Card, Suit, Rank, Deck, Seat, Player, Trick, Bid, Contract
├── game                  BiddingManager, RuleValidator, TrickResolver,
│                         AnnouncementDetector, ScoreCalculator, GameEngine
├── ai                    BotPlayer, CardTracker
├── i18n                  I18n (ResourceBundle wrapper with live switching)
└── ui                    BeloteApp, CardView
```

`GameEngine` owns all state and emits events through a `Listener` interface, so
the whole game can be driven headlessly — which is exactly what the integration
tests do.

## Language switching

Use the **EN / БГ** buttons in the toolbar. `I18n.setLocale` notifies registered
listeners and the UI re-renders in place; nothing needs restarting. Strings live
in `src/main/resources/i18n/messages_en.properties` and `messages_bg.properties`
(read as UTF-8, so the Cyrillic needs no escaping).

## Rule interpretations

The specification left a handful of cases open. Each decision below is
implemented deliberately and covered by tests — they are the places most likely
to differ from a given table's house rules.

**Play constraints.** The spec states the mandatory-raise (качване) rule only for
All Trumps, and states the trump / over-trump obligations for suit contracts only
in the "cannot follow suit" branch. This is implemented literally: in a suit
contract, a player following a *trump lead* may play any trump, high or low.
Many real tables require over-trumping there too. If you want that behaviour,
it is a small change in `RuleValidator.followingSuit`.

**Long sequences.** A run of six or more scores as a quinte (100), not as a
quinte plus a tierce.

**Sequence priority.** The team holding the single best sequence scores *all* of
its sequences and the opposing team scores none. If the two teams' best
sequences are exactly equal in length and top card, every sequence on both sides
is cancelled.

**Carrés.** Ranked among themselves by the same winner-takes-all rule (J > 9 >
A/K/Q/10), but they never invalidate sequences and sequences never invalidate
them.

**Belote.** Awarded automatically when a player holds K+Q of a trump suit, rather
than requiring an explicit declaration when the first of the two is played. This
avoids a human player silently losing 20 points to a misclick.

**Made / set / hanging** is decided on *raw* points before rounding, so an exact
81–81 split in a suit contract genuinely hangs.

**Rounding.** Totals ending in .5 round against the contracting team: declarers
round half down, defenders round half up.

**Set contracts.** The defenders take the sum of both teams' rounded points, plus
any hanging points; the declarers take zero.

**Hanging points** go to whichever team wins the following hand, and hang again
if that hand also ties.

**Capot** cannot end the game: if a team crosses 151 on a capot hand, play
continues. A dead heat at or above 151 also continues.

## Bot behaviour

`CardTracker` records every card played and every revealed void. From that,
`BotPlayer`:

- computes **boss cards** — cards no outstanding card can beat — and cashes them,
  richest first
- **returns the partner's suit** when it holds no boss card of its own
- **avoids leading into a known void** in a suit contract, where an opponent
  could ruff
- **pulls trumps** when it is on the declaring side of a suit contract with three
  or more
- when the partner already holds the trick, piles points onto it if that is safe
  (last to play, or the partner's card is boss) and otherwise plays low
- when an opponent holds it, wins as cheaply as possible, and otherwise discards
  the least valuable card while keeping the partner's suit intact

Bidding evaluates the five-card hand separately for each contract type and bids
only above a per-contract threshold; doubling and redoubling need a further
margin on top.

## Tests

```bash
mvn test
```

62 tests covering trick resolution and card values, legal-move validation for all
three contract families, announcement detection and priority resolution, the
bidding hierarchy and double/redouble legality, and scoring including the No
Trumps multiplier, capot, rounding and the made/set/hanging outcomes.

`GameEngineTest` additionally plays complete games bot-versus-bot across 25
different deals, which exercises the whole rule set end to end.

## Verification status

`mvn clean package` builds a runnable fat jar and all 62 tests pass. The jar
launches without error. The JavaFX layout itself has **not** been visually
reviewed — run it and judge the presentation yourself.
