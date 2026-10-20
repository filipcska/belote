# Belote (Белот)

A desktop implementation of Bulgarian Belote in Java 25 + JavaFX, with a human
player (South) against three heuristic bots, fully localised in English and
Bulgarian.

## Build and run

Requires **JDK 25** and Maven. The bytecode is class file version 69, so the jar
will not start on an older JVM.

```bash
mvn clean package
java -jar target/belote.jar
```

If you use sdkman, `.sdkmanrc` pins the project JDK — run `sdk env` in this
directory (or `sdk env install` the first time) to switch to it without changing
your global default.

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

## Rules

The rules follow the belot.bg rulebook ("Белот правила: раздаване, анонси и
точкуване" and the accompanying articles on scoring, declarations and card
strength). Each rule below is implemented explicitly and covered by tests.
Where the sources leave a case open, the choice made is marked
*interpretation*: these are the places most likely to differ from a given
table's house rules.

**Play constraints** (`RuleValidator`).
- Follow the led suit whenever you can.
- When trump is led (in a suit contract, or any suit in All Trumps), you must
  beat the highest card so far if you can, even when your partner is
  winning (качване). This follows the rulebook's "задължителното отговаряне на
  цвета и качването в коз" and its example of a partner being forced to take
  a led trump 9 with the jack.
- If you are void in the led suit in a suit contract and the opponents hold
  the trick, you must ruff. If an opponent has already ruffed, you must
  over-ruff when you can, and otherwise you may play any card. If your partner
  holds the trick, you may play anything.
- No Trumps only requires following suit.

**Sequences.** A run of six or more scores as a quinte (100), not as a quinte
plus a tierce (*interpretation*: the rulebook stops at five).

**Sequence priority.** The team holding the single best sequence scores *all* of
its sequences and the opposing team scores none. If the two teams' best
sequences are exactly equal in length and top card, every sequence on both sides
is cancelled.

**Carrés.** Ranked by trump value, J > 9 > A > 10 > K > Q, so four aces beat
four kings although both are worth 100. The winning team scores all its
carrés. Carrés and sequences never cancel each other.

**One card, one combination.** A card shared by a carré and a sequence counts
in only one of them. The engine makes the choice for the player and keeps the
more valuable option; leftover cards can still form a shorter sequence
(*interpretation*: the rulebook leaves the choice to the player).

**Declaring.** Sequences and carrés are declared with the player's first card
of the hand. No Trumps has no declarations and no belote; only last 10 and
capot score there.

**Belote.** K+Q of trump (of any suit in All Trumps) earns 20 when the first of
the two is played and that card leads the trick, follows the led suit, or ruffs,
over-ruffs or under-ruffs in a suit contract. So in All Trumps a K or Q
discarded on another suit loses the belote. It is announced automatically at
that moment, so a misclick cannot cost the 20 points.

**Made / set / hanging** is decided on *raw* points before rounding, so an exact
81–81 split in a suit contract genuinely hangs.

**Rounding.** The two rounded scores always add up to the rounded grand total:
16 for a suit contract, 26 for No Trumps (rounded after doubling), 26 for All
Trumps, plus premiums. The team with the larger last digit rounds up. If both
last digits are equal, the team with fewer points rounds up (the rulebook's
"ending in 4" rule for All Trumps). In a dead heat the hanging points round up,
which gives the rulebook's 106–106 → 10 plus 11 hanging, and 154–154 → 15 plus
16 hanging. Applying the equal-digits rule to suit contracts too, e.g. 86–76 →
8–8, is an *interpretation*.

**Set contracts.** The defenders take the sum of both teams' rounded points, plus
any hanging points; the declarers take zero.

**Double and redouble.** The team with more points takes the whole hand ×2
(×4 for a redouble), all premiums included and whoever declared; the other
team takes nothing. If a doubled hand hangs, all of its multiplied points hang.

**Hanging points** go to whichever team wins the following hand, and hang again
if that hand also ties.

**End of game.** The game ends once a team reaches 151. If both teams pass it in
the same hand, the higher score wins, and an exact tie plays on. A capot hand
cannot end the game ("с капо не се излиза"): another hand is played, and a
passed-out hand does not count as that hand.

**Not modelled.** The two-player (открит) variant described in "Белот за двама"
is a different game and is not implemented.

## Bot behaviour

`CardTracker` records every card played and every revealed void. From that,
`BotPlayer`:

- when its **partner called the contract**, leads the top card of a suit with
  exactly two unseen cards above it (in a suit contract, only the trump suit),
  to pull an opponent's high card under the caller's presumed top card: partner
  calls spades, the bot leads the ace, the next opponent covers with the nine
  and the caller takes both with the jack
- computes **boss cards** — cards no outstanding card can beat — and cashes them,
  richest first, unless an opponent known to be void in the suit may ruff
- **returns the partner's suit** when it holds no boss card of its own, unless
  the suit has run dry or an opponent can ruff it
- **avoids leading into a known void** in a suit contract, where an opponent
  could ruff
- **pulls trumps** when it is on the declaring side of a suit contract with three
  or more
- when the partner already holds the trick, piles points onto it if that is safe
  (last to play, or the partner's card is boss and the opponent behind cannot
  ruff it) and otherwise plays low; either way it keeps back trumps and boss
  cards that would win tricks of their own
- when **every card in its hand would win its trick** and the partner holds
  the trick safely, drops the top card of a suit the partner could still lead
  (in All Trumps, the jack of hearts from J-9 of hearts plus the last two clubs):
  it tells the partner to lead that suit, and banks the card's points even if
  they cannot
- when the **partner signals** that way (a bot or the human player dropping the
  top remaining card of a suit on its partner's trick), leads that suit at its
  next turn, before cashing its own winners, which would force the partner to
  discard theirs
- when an opponent holds it, wins as cheaply as possible; on the declaring side
  it lets a trick worth under 10 go rather than spend a trump or boss card,
  unless it is last to play. Otherwise it discards the least valuable card,
  keeping trumps, boss cards and the partner's suit

Bidding evaluates the five-card hand separately for each contract type and bids
only above a per-contract threshold; doubling and redoubling need a further
margin on top.

## Tests

```bash
mvn test
```

93 tests covering trick resolution and card values, legal-move validation for all
three contract families (including the mandatory raise on a trump lead),
announcement detection and priority resolution (including carré ranking,
shared cards and belote timing), the bidding hierarchy and double/redouble
legality, and scoring: the No Trumps multiplier, capot, total-preserving
rounding, winner-takes-all doubles and the made/set/hanging outcomes.
`BotPlayerTest` checks the bot's lead and follow choices in set positions.

`GameEngineTest` additionally plays complete games bot-versus-bot across 25
different deals, which exercises the whole rule set end to end.

## Verification status

`mvn clean package` on JDK 25 (Temurin 25.0.4) builds a runnable fat jar and all
93 tests pass. The jar launches without error — the only console output is
JavaFX's benign "classes were loaded from 'unnamed module'" notice, which is
inherent to shading JavaFX into a non-modular jar. The JavaFX layout itself has
**not** been visually reviewed — run it and judge the presentation yourself.
