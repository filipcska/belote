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

## Roadmap: learning features

This is a plan only; none of it is built yet. The idea is to let the game teach
someone Belote from scratch, as an **optional layer** over the normal game. With
every learning option switched off, the game plays exactly as it does today.

### Design principles

- **The engine stays unchanged.** A new `com.belote.learn` package holds a
  `Coach` that subscribes to `GameEngine.Listener` events and asks
  `RuleValidator` and `BotPlayer` *why*, without adding tutorial logic to the
  rules.
- **Explanations come from the real logic.** Rule reasons come from
  `RuleValidator` and play reasons from `BotPlayer`, so the coach can never
  contradict how the game actually behaves.
- **Bilingual from day one.** Every explanation is an i18n key in both
  `messages_en.properties` and `messages_bg.properties`. English text gives the
  Bulgarian term on first use (e.g. "forced raise (качване)") so learners can
  follow a real table.
- **Every feature is a toggle.** A "Learning" menu or settings panel holds the
  switches for coach, hints, face-up hands and so on.

### Phase 0 — Foundations

- [ ] **Reasons from `RuleValidator`.** Add a `MoveCheck` (or similar) method
  that returns, for any card in the hand, whether it is legal and, if not, why:
  must follow suit, must beat the highest trump (forced raise), must trump,
  must over-trump. Keep `legalMoves` as it is and build it on top of the new
  method so the two can't drift apart. Unit-test each reason.
- [ ] **Reasons from `BotPlayer`.** Have the card and bid choices return a
  `Decision(card, reasonKey, args)` instead of a bare card. Tag each branch:
  partner's signalled suit, drawing lead, cash a boss card, return partner's
  suit, pull trumps, safe low lead, running-suit signal, partner keeps the
  trick (play points), duck as declarer, cheapest winner, discard. The existing
  `chooseCard` keeps returning just the card. Extend `BotPlayerTest` to assert
  the reasons too.
- [ ] **Bid reasons.** Expose the per-contract hand evaluation the bot already
  computes (score vs `SUIT_THRESHOLD` / `ALL_TRUMPS_THRESHOLD` /
  `NO_TRUMPS_THRESHOLD`) together with what drives it (J and 9 of a suit,
  aces, length).
- [ ] **Stacked deals.** Let `GameEngine` accept a predetermined deck, e.g. a
  `DealSource` that defaults to the shuffled `Deck`, so lessons and tests can
  set up exact hands, dealer and bidding.
- [ ] **Learning settings.** Add the toggles and remember them between runs
  (e.g. `java.util.prefs.Preferences`).

### Phase 1 — Coach mode (during a normal game)

- [ ] **Why can't I play this card?** Clicking or hovering a disabled card shows
  the `RuleValidator` reason in a tooltip or status line, e.g. "You must follow
  hearts" or "You must beat the 9♠ — forced raise (качване)".
- [ ] **Hint button.** It asks the bot what it would play in South's seat and
  highlights that card with its reason: "Lead the A♠: your partner called
  spades, so they likely hold the J, and East may have to give up the 9."
  Optionally count the hints used per hand.
- [ ] **Bot plays explained.** A small caption or log line under each bot card
  shows its `Decision` reason, e.g. "North dropped the J♥: every heart left is
  theirs". Partner explanations matter most; opponents' can be a separate
  toggle.
- [ ] **Bidding hint.** During bidding, show the hand's strength per contract
  and the call the bot would make, with its reason (e.g. "J and 9 of spades plus
  an ace: a good spades call").
- [ ] **Announcements explained.** When a terca, quarta, quinta, carré or belot
  is declared, briefly say what it is, its value and why it scored or was
  cancelled by a higher one.
- [ ] **End-of-hand breakdown.** Expand `HandResult` into a readable summary: points
  per trick, last-trick 10, announcements, the No Trumps multiplier, doubles,
  and whether the contract was made, the declarers went inside (вътре), or the
  points are hanging, with the reason.
- [ ] **Trick review.** Let the player step back through the tricks of the
  finished hand, with the reason shown on each card.

### Phase 2 — Practice aids

- [ ] **Cheat-sheet panel.** A toggleable side panel with the trump and plain
  rankings and points, the announcement values, and a short rule summary that
  highlights what applies to the current contract.
- [ ] **Face-up mode.** Show all four hands face up in practice games.
- [ ] **Undo.** Take back the last card in practice games (this needs the
  engine to snapshot or replay its state; disable it in normal games).
- [ ] **Card memory aid.** Optionally show the played cards per suit and which
  remaining cards are boss (from `CardTracker`), as training wheels for
  counting.
- [ ] **Simplified games.** Practice options that limit the contract to No
  Trumps only or All Trumps only, turn off announcements, or play single hands
  instead of a full game.

### Phase 3 — Guided lessons

- [ ] **Lesson framework.** Each `Lesson` has bilingual intro text, a stacked
  deal (Phase 0), which features are enabled, a goal ("win this trick", "take
  the last 10", "make your contract"), a check on the result, and a short
  debrief. Lessons are data files where possible, so adding one needs no code.
- [ ] **Lesson menu** with progress (completed or locked) saved between runs.
- [ ] **Lessons in order:**
  1. The cards: ranks and points in a plain suit vs in trumps
  2. Following suit and winning a trick
  3. Trumps: trumping and over-trumping, and when you may discard
  4. The forced raise (качване) on a trump lead
  5. The contracts: suit, No Trumps, All Trumps
  6. Bidding: what makes a hand worth a call, doubles and redoubles
  7. Announcements: terca, quarta, quinta, carré, belot, and who scores them
  8. Scoring: last trick, made / inside / hanging, capot, No Trumps doubling
  9. Strategy I: count trumps and boss cards
  10. Strategy II: partnership, with the drawing lead, returning partner's suit
      and the running-suit signal
- [ ] **Free-play bridge.** After the last lesson, offer a normal game with the
  coach switched on.

### Phase 4 — Quizzes

- [ ] **Quiz mode** with generated questions answered from the engine itself:
  - "Which card wins this trick?" (`TrickResolver`)
  - "Is this card legal here?" (`RuleValidator`, explained by the Phase 0
    reasons)
  - "How many points is this trick worth?" (card values)
  - "Which announcement does this hand have, and how much is it worth?"
    (`AnnouncementDetector`)
  - "What would you bid?" (compared with the bot's evaluation)
- [ ] **Score and streak**, and a link to the matching lesson after a wrong
  answer.

### Suggested order

Phase 0 → Phase 1 (the most value, since it reuses the existing logic) →
Phase 2 → Phase 3 → Phase 4. Play-test the base game before starting.

## Roadmap: LAN multiplayer

This is a plan only; none of it is built yet. It is separate from the learning
features above, and neither depends on the other. The goal is to let up to four
people on the same local network play at one table, with bots filling any empty
seats. Single-player games stay exactly as they are.

### What stands in the way today

- `Seat.isHuman()` hard-codes South as the only human, and `GameEngine`,
  `BotPlayer.createBots()` and `BeloteApp` all depend on it.
- The bots are driven by the UI: `BeloteApp` schedules a `PauseTransition` and
  calls `chooseBid` / `chooseCard`. In a networked game the host must run them.
- The UI reads the engine directly, including every player's hand. A remote
  player must only ever receive what their seat may see.
- The table is drawn from South's point of view, but each player should see
  themselves at the bottom.

### Design principles

- **The host decides.** One instance hosts the game and runs the only
  `GameEngine` plus the bots. Clients send requests ("bid 2♠", "play A♥"), the
  host checks them with the engine and sends out the results. A client never
  changes game state itself.
- **Each player gets only their own view.** It holds their own hand, the card
  counts of the other players, the current trick, bids, contract, scores and
  their legal moves. Other players' cards are never sent, which also stops
  anyone reading them from network traffic.
- **Full view, every time.** The state is small, so the host sends the whole
  view after every change. That is simpler and more robust than sending only
  the differences; separate event messages exist only to drive the animations.
- **Few new dependencies.** Plain JDK sockets; for JSON, either Jackson or
  simple hand-written encoding. No Java serialization.

### Phase 1 — Refactor (useful even without networking)

- [ ] **Seat controllers.** Replace `Seat.isHuman()` with a per-seat
  `SeatController`: `LocalHuman`, `Bot`, `Remote`. `GameEngine` takes the seat
  setup instead of deriving it from `Seat`.
- [ ] **Table loop out of the UI.** Move the bot scheduling from `BeloteApp`
  into a `Table` (or `GameHost`) class that owns the engine, asks each
  controller for its move in turn, and keeps the bot think time and the pause
  after a trick. The UI only sends the local player's moves to it.
- [ ] **Per-seat view model.** Add a `SeatView` record built from the engine
  for a given seat, and render `BeloteApp` only from it, never from
  `GameEngine` directly.
- [ ] **Rotated table.** Draw the table relative to the viewer's seat, so they
  are always at the bottom and their partner at the top.
- [ ] **Check that single-player is unchanged.** One local human and three bots
  through the new path, with every existing test still passing.

### Phase 2 — Networking

- [ ] **Messages.** One JSON message per line over TCP, with a protocol version.
  - From client to host: `Hello(version, name, rejoinToken?)`, `TakeSeat`,
    `Ready`, `Bid`, `PlayCard`, `Leave`.
  - From host to client: `Welcome(rejoinToken)`, `Lobby(seats)`,
    `View(SeatView)`, `Event(card played / trick won / hand over…)`,
    `Error(reason key)`.
- [ ] **Host server.** A server socket on a configurable port, one reader
  thread per client, and all incoming requests queued onto a single engine
  thread so the engine is never touched from two threads at once. Send each
  view only to its own seat.
- [ ] **Client.** Connects, sends the player's moves, and hands each incoming
  `View` to the JavaFX UI thread via `Platform.runLater`.
- [ ] **Sending only what a seat may see.** One function builds the outgoing
  view for each seat; unit-test that it never contains another seat's cards.
- [ ] **Error reasons reuse i18n keys**, so they show in each client's own
  language (each player can pick EN or БГ for themselves).

### Phase 3 — Lobby and joining

- [ ] **Host / Join dialogs.** "Host game" (choose name and port) and "Join
  game" (enter the host's IP and port). Show the host's own LAN address so it
  can be read out to the others.
- [ ] **Lobby screen.** Players pick seats and so teams. The host can move
  players, fill empty seats with bots, and start the game when ready.
- [ ] **Automatic discovery on the local network.** UDP broadcast or mDNS
  (e.g. JmDNS), so open games appear in a list and nobody has to type an IP.
- [ ] **Note the macOS firewall prompt** in the README: it appears the first
  time someone hosts.

### Phase 4 — Robustness

- [ ] **Disconnects.** A bot takes over the seat right away, so the game keeps
  going.
- [ ] **Rejoining.** The `rejoinToken` lets a player reclaim their seat and get
  the current view.
- [ ] **Heartbeat and timeouts**, to notice dead connections.
- [ ] **Version check** in `Hello`, refusing a client that runs a different
  protocol version with a clear message.
- [ ] **Host leaving** ends the game cleanly for everyone, with a message.
- [ ] **Optional:** a short chat or quick reactions in the lobby and at the
  table.

### Phase 5 — Tests

- [ ] **Headless clients over local sockets.** Fake clients that play legal
  moves automatically, so complete networked games run in tests, building on
  the existing bot-vs-bot `GameEngineTest`.
- [ ] **Message round-trip tests**, encoding and decoding every message type.
- [ ] **Disconnect and rejoin tests** in the middle of a hand.

### Later, optionally — phones in a browser

- [ ] The host also runs a small built-in HTTP/WebSocket server, so friends can
  join from a phone browser with nothing to install. It uses the same messages
  and the same per-seat views, but needs a separate HTML/JS front end, because
  JavaFX doesn't run in a browser. Only worth doing after the desktop version
  works.

### Suggested order

Phase 1 → 2 → 3 → 4, with Phase 5 tests written along the way. Phases 1–2 alone
give a working LAN game; 3–4 make it pleasant to use. A playable version is
roughly a few days of work.
