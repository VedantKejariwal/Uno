# UNO Codebase Notes

## Detailed technical documentation: UCTAgent and ExpectedOutcomeAgent

This section describes the student-authored implementations in `src/pas/uno/agents/UCTAgent.java` and `src/pas/uno/agents/ExpectedOutcomeAgent.java`: how they connect to the framework, what algorithms they implement, and how they differ from each other. It is written to complement the rest of this file (JAR layout, `doc/pas/uno/` Javadoc, and core class summaries below).

### Files and linkage map

| Your file | Extends | Nested type | Primary framework dependencies |
|-----------|---------|-------------|--------------------------------|
| `src/pas/uno/agents/UCTAgent.java` | `edu.bu.pas.uno.agents.MCTSAgent` | `UCTAgent.MCTSNode` extends `edu.bu.pas.uno.tree.Node` | `Game`, `Game.GameView`, `Move`, `Card`, `Hand.HandView`, `RandomAgent`, `Color`, `Node.NodeState` |
| `src/pas/uno/agents/ExpectedOutcomeAgent.java` | same | `ExpectedOutcomeAgent.MCTSNode` extends `Node` | same |

**Call path (how the engine reaches your code).** The compiled framework’s `MCTSAgent` implements `chooseCardToPlay` and `maybePlayDrawnCard` by delegating to `timedSearch(GameView, Integer)`, which invokes your `search(...)`, then `argmaxQValues(...)` on the returned root `Node`. That flow is documented under `edu.bu.pas.uno.agents.MCTSAgent` in `doc/pas/uno/edu/bu/pas/uno/agents/MCTSAgent.html`. Your classes only override `search` and `argmaxQValues`; they do not replace the timing wrapper.

**Simulation copies.** Both agents construct `Game` from a `GameView` with a full table of `RandomAgent` instances (one per logical seat, mapped via `getPlayerOrder().getAgentIdx(...)`). That matches the pattern described for `Game(GameView, Agent...)` in the notes on `edu.bu.pas.uno.Game` below: the mutable `Game` drives `resolveMove`, `getMove`, and rollouts.

### Shared concepts and techniques

- **Monte Carlo rollouts.** After building a successor state, both agents run forward simulations using `Game.getMove()` (random legal play) and `resolveMove`, which is the default-policy rollout described in the MCTS assignment mental model at the end of this document.
- **Q-values on `Node`.** The framework’s `Node` stores per-action totals and counts; `getQValue(i)` is the empirical mean. Both implementations update `setQValueTotal` / `setQCount` so that `argmaxQValues` can pick the best index.
- **Move construction.** Moves use hand indices, not card objects. Wild cards use `Move.createMove(agent, handIdx, color)` as required by `edu.bu.pas.uno.moves.Move`. When cloning a move into a simulation `Game`, the code re-binds the move to `copyGame.getCurrentAgent()` so the acting agent matches the engine’s expectations.
- **Reward shaping.** When the rollout does not finish the game within the cap, both use the same intermediate heuristic: `r = (sum of opponents’ hand sizes) − (my hand size)`, so smaller own hands and larger opponents’ hands receive higher reward. Terminal states use `+1` if the searching player emptied their hand (win), `-1` otherwise.

### `UCTAgent` — design and behavior

**Role in the assignment.** This class implements a **UCT-style** loop: **selection** along the tree using an upper-confidence bound, **expansion** when a node is not fully visited, **simulation** (rollout), and **backpropagation** along the path. That aligns with the scaffold intent described later for `UCTAgent` (UCT exploration during selection).

**Nested `MCTSNode.getChild(Move)`.** It builds `Game` from the node’s view, resolves the move (or `null` where the engine expects a pass), and creates the child with `copyGame.getOmniscientView()` and the new `getCurrentLogicalPlayerIdx()`. Using the **omniscient** view means the search tree sees full card information below the root, which simplifies consistent child expansion when the assignment uses full observability; compare to `ExpectedOutcomeAgent`, which uses the current player’s view (see below).

**Selection (`UCBSelect`).** For each action slot `i`, the formula is effectively **average return plus exploration**: `getQValue(i) + sqrt((2 * ln(totalVisits)) / getQCount(i))`, where `totalVisits` is the **sum** of per-action visit counts at that node. Any action with zero visits is selected immediately (standard “try each child first” behavior). **Fully expanded** means every action index has been visited at least once.

**Expansion and action indexing.** `getActionCount` mirrors `Node`’s three states: one slot per legal card when `HAS_LEGAL_MOVES`; two slots when `NO_LEGAL_MOVES_MAY_PLAY_DRAWN_CARD` (play drawn vs keep); one slot when the engine requires drawing unresolved cards. Traversal converts an action index into a concrete `Move` (including random wild color during search) or delegates to `Game.getMove()` for forced-draw states.

**Rollout.** From the leaf reached by selection/expansion, a new `Game` runs until termination or **5** plies (`rolloutCap`), whichever comes first.

**Time budget.** Search runs until `maxThinkingTimeInMS - 400` ms have elapsed (reserving headroom for framework overhead).

**`argmaxQValues`.** After search, it picks the move with highest mean Q-value: for `HAS_LEGAL_MOVES`, argmax over legal move indices; for `NO_LEGAL_MOVES_MAY_PLAY_DRAWN_CARD`, compares indices 0 vs 1 (play vs keep); for unresolved draws, falls back to `Game.getMove()` on a fresh copy (same pattern as in the framework-oriented notes on `Node.NodeState`).

### `ExpectedOutcomeAgent` — design and behavior

**Role in the assignment.** This implementation emphasizes **expected outcome from rollouts** at the **root** when the player has legal moves: it materializes one child node per legal move, then repeatedly samples rollouts and accumulates statistics **on the root’s Q-values** for each child. It does **not** build a deep UCT tree like `UCTAgent`; it is closer to **flat Monte Carlo evaluation** (many rollouts per root action) with a longer per-rollout horizon.

**Nested `MCTSNode.getChild(Move)`.** It matches the “current agent view” pattern: after `resolveMove`, the child uses `copyGame.getView(copyGame.getPlayerOrder().getCurrentAgentIdx())`. That ties the stored `GameView` to the **player to move**’s observability, which is the usual choice for imperfect-information play when you avoid omniscient children.

**`search` structure.** When the root state is `HAS_LEGAL_MOVES`, it precomputes all `children[i]` for each ordered legal move. The main loop (until `maxThinkingTimeInMS - 100` ms) runs an inner loop over **every** child `i` each time: for each child, it runs one rollout (up to **20** moves), computes the same reward as in `UCTAgent`, and adds it to `root`’s `QValueTotal(i)` and `QCount(i)`. So each outer iteration adds **one rollout sample per legal move**, keeping per-action estimates balanced. If the root is not `HAS_LEGAL_MOVES`, the method returns without populating Q-values through this loop (the parent `MCTSAgent` / `argmaxQValues` path still applies as implemented).

**`argmaxQValues`.** Logic parallels `UCTAgent` for the three node states; for play-vs-keep, it uses the named constants `Node.NoLegalMovesIdxDefaults.DrawSingleCardIdxs.PLAY_CARD_MOVE_IDX` and `KEEP_CARD_MOVE_IDX` for clarity.

### Side-by-side comparison (same framework, different search geometry)

| Aspect | `UCTAgent` | `ExpectedOutcomeAgent` |
|--------|------------|------------------------|
| Tree depth | Grows a multi-level tree; UCB walks down | Root-level children for legal moves only (when that branch runs) |
| Child `GameView` | `getOmniscientView()` | `getView(currentAgentIdx)` |
| Rollout length cap | 5 | 20 |
| Time reserve | 400 ms | 100 ms |
| Core formula | UCB1-style selection + backprop along path | Monte Carlo mean per root child |

### CS / AI concepts, algorithms, and code-to-technique map

This subsection names standard **umbrella topics** (courses and textbooks often group them this way) and ties them to **specific places in your code**. Terminology aligns with common references on [Monte Carlo tree search](https://en.wikipedia.org/wiki/Monte_Carlo_tree_search) and UCT/UCB1 (e.g. selection / expansion / simulation / backpropagation, and the upper-confidence bound for bandits).

#### Umbrella concepts (what kind of problem and toolkit)

| Concept | What it means here |
|--------|---------------------|
| **Adversarial / multi-agent sequential games** | UNO is a stochastic, turn-taking game; both agents plan a single move under a time limit using forward simulation. |
| **Online planning (look-ahead search)** | No separate training phase: each decision runs `search` until the time budget expires (`UCTAgent.java` ~163–164, `ExpectedOutcomeAgent.java` ~95–96). |
| **Monte Carlo methods** | Future play is approximated by **random rollouts** (`getMove` + `resolveMove`), not by an exact game tree. |
| **Heuristic evaluation** | If the rollout hits a **depth cap** before the game ends, reward uses a **hand-count heuristic** instead of win/loss (`UCTAgent.java` ~315–325, `ExpectedOutcomeAgent.java` ~157–167). |
| **Imperfect information (optional lens)** | `ExpectedOutcomeAgent` stores child views with `getView(currentAgentIdx)` (`ExpectedOutcomeAgent.java` ~66–67); `UCTAgent` uses `getOmniscientView()` in the tree (`UCTAgent.java` ~67–68), which is closer to **full-information search** inside the simulator (sometimes used when the framework or assignment assumes full observability in copies). |

#### Algorithms and techniques (specific names used in CS / ML)

| Technique | Where it appears in your code |
|-----------|-------------------------------|
| **Monte Carlo Tree Search (MCTS)** — iterate: select → expand → roll out → backprop | Full four-phase loop in `UCTAgent.search`: path lists (`UCTAgent.java` ~176–180), descent while expanded (~183–232), expansion (~234–284), rollout (~294–326), backprop (~337–342). |
| **UCT (Upper Confidence bounds applied to Trees)** | Implemented by `UCBSelect` plus tree traversal; UCT is the usual name for MCTS + UCB-style selection at each node. |
| **UCB1-style selection** (explore/exploit) | `UCTAgent.UCBSelect`: score = `getQValue(i)` + `sqrt((2 * ln(totalVisits)) / getQCount(i))`, with `totalVisits` = sum of per-action visit counts at that node (`UCTAgent.java` ~99–118). Literature often writes the same idea as “mean reward + *c*·sqrt(ln *N* / *n_i*)” with *N* = parent visits; your implementation uses the **sum of child counts** as *N* (same explore/exploit tradeoff, slightly different constant bookkeeping). **First-visit priority**: any arm with `getQCount(i)==0` returns immediately (~106–108), a standard way to try each child once before UCB dominates. |
| **Expansion** (grow the tree by one edge) | `pickUnvitedAction` chooses an unvisited action index; `getChild` applies the move (`UCTAgent.java` ~135–145, ~234–284). |
| **Rollout / play-out / simulation policy** | Random legal moves via `Game.getMove()` after constructing `Game(child.getGameView(), agents)` (`UCTAgent.java` ~294–301, `ExpectedOutcomeAgent.java` ~135–142). All seats use `RandomAgent` in the copy (~166–170 / ~119–124), i.e. a **uniform random default policy** through the engine. |
| **Backpropagation** (backup) | `UCTAgent`: add reward to every `(node, actionIdx)` on the path (`UCTAgent.java` ~337–342). |
| **Empirical mean Q-values** | Framework `Node` keeps totals and counts; `getQValue(i)` is the running mean used for UCB and for final `argmaxQValues`. |
| **Flat Monte Carlo (root-only) evaluation** | `ExpectedOutcomeAgent`: precompute one child per legal move (~100–118), then repeatedly roll out from each child and update **only the root’s** stats (~125–172). This is **not** deep UCT; it is **Monte Carlo estimation of the value of each root action** (related ideas: *rollout-based move ordering*, *playout policy evaluation*). |
| **Greedy action selection (exploitation)** | `argmaxQValues` picks the move index with largest mean Q (`UCTAgent.java` ~357–427, `ExpectedOutcomeAgent.java` ~227–295). |
| **Sparse terminal reward + shaped intermediate reward** | Win/loss ±1 when `isOver()`; else `otherCards - myCards` (both files at the cited heuristic lines). |
| **Anytime algorithm** | Loop until wall-clock exceeds `timeLimit`; more iterations → better statistics on average. |

#### Engineering / CS skills (what the code is practicing)

| Skill | Example in your implementation |
|-------|--------------------------------|
| **State copy + simulation API** | `new Game(gameView, agents)` then `resolveMove` / `getMove` — separates **read-only view** from **mutable rollout** (framework design described elsewhere in this doc). |
| **Indexing discipline** | `getOrderedLegalMoves()` maps Q-index to hand index; `Node.NodeState` switches between legal moves vs play/keep (`getActionCount`, `UCTAgent.java` ~79–92). |
| **Nested inheritance** | `MCTSNode extends Node` overrides `getChild` to define transitions for **your** search only. |

#### Quick “if you see this in the code, say this in an exam / report”

- **`UCBSelect`** → UCB1 / **multi-armed bandit** exploration term at each decision node; part of **UCT**.
- **`path` + `pathActionIdx` + loop updating ancestors** → **MCTS backpropagation**.
- **`rolloutCap` with random `getMove`** → **rollout policy** (here: engine-driven random play).
- **`getOmniscientView()` in `UCTAgent.MCTSNode`** → search children see full state in copies (know the API choice and justify vs `getView` in `ExpectedOutcomeAgent`).
- **Root `children[]` + inner `for` over all moves** → **flat Monte Carlo** / **per-move rollout averages**, not a deep tree.

### Relationship to other files in this repo

- **`uno.srcs`** (listed under [Student-editable files](#student-editable-files)) names three agents including these two; the third is `UnoMCTSAgent.java`.
- **`lib/pas-uno-jar-1.0.7.jar`** supplies `MCTSAgent`, `Node`, `Game`, etc.; behavior is as summarized in [Core framework packages](#core-framework-packages-in-the-jar) and the class sections below.
- **`doc/pas/uno/`** HTML pages document the same public API; use [How the docs folder is organized](#how-the-docs-folder-is-organized) for navigation.
- **Copies / variants** (`UCTAgentCopy.java`, `UCTAgent2.java`, `ExpectedOutcomeAgentCopy.java`, …) are local experiments; they are not referenced by `uno.srcs` but may mirror the same patterns.

---

## What this project contains

This UNO project is split into three important parts:

1. `src/pas/uno/`
   This is the student-authored area. In this workspace, it contains exactly three agent files you are expected to implement or modify.

2. `lib/pas-uno-jar-1.0.7.jar`
   This is the actual framework/runtime JAR. It contains the game engine, base agent classes, tree/node support, enums, move logic, UI classes, assets, and some internal history classes.

3. `doc/pas/uno/`
   This is a generated Javadoc site for the UNO framework API. It is useful for understanding the intended API surface, package layout, class summaries, and member signatures.

## Important version note

There is a version mismatch:

- The runtime library is `lib/pas-uno-jar-1.0.7.jar`.
- The generated docs in `doc/pas/uno/` identify themselves as `pas-uno-jar 1.0.1`.

In practice, the core public API used by student code appears aligned, but the JAR contains some extra classes and members that are not emphasized in the docs, especially:

- `edu.bu.pas.uno.history.*`
- some UI/runtime classes such as `Registry`, `CardFlightAnimation`, and `DelayAnimation`
- `Game.getHistory()` and `Game.GameView.getHistory()`

When there is any disagreement, trust the JAR signatures over the HTML docs.

## Student-editable files

### `uno.srcs`

This file lists the UNO student source files that belong to this assignment:

- `src/pas/uno/agents/ExpectedOutcomeAgent.java`
- `src/pas/uno/agents/UCTAgent.java`
- `src/pas/uno/agents/UnoMCTSAgent.java`

This strongly suggests the assignment expects the student work to live only in those files.

### `src/pas/uno/agents/UnoMCTSAgent.java`

This is a scaffold for a custom Monte Carlo Tree Search UNO agent.

- Package: `src.pas.uno.agents`
- Inherits from `edu.bu.pas.uno.agents.MCTSAgent`
- Defines a nested `MCTSNode extends Node`
- Leaves three critical things unimplemented:
  - `MCTSNode.getChild(Move move)`
  - `search(GameView game, Integer drawnCardIdx)`
  - `argmaxQValues(Node node)`

Interpretation:

- `search(...)` is where the actual MCTS loop should happen: selection, expansion, simulation, and backpropagation.
- `argmaxQValues(...)` is the policy extraction step from the populated root node.
- `MCTSNode.getChild(...)` is how successor states are generated when traversing the tree.

### `src/pas/uno/agents/UCTAgent.java`

This file is structurally identical to `UnoMCTSAgent.java`, but the name suggests it is intended to implement a UCT-style tree policy.

The likely distinction is:

- `UnoMCTSAgent`: a general MCTS agent scaffold
- `UCTAgent`: an MCTS variant that uses the UCT exploration formula during selection

**Implementation notes (this workspace):** `UCTAgent` provides a full selection / expansion / rollout / backpropagation loop with `UCBSelect`, `isFullyExpanded`, and `pickUnvitedAction`, nested `MCTSNode` children using `getOmniscientView()`, rollout cap 5, and a time budget of `maxThinkingTimeInMS - 400`. See the [detailed section](#detailed-technical-documentation-uctagent-and-expectedoutcomeagent) at the top of this document for behavior and framework links.

### `src/pas/uno/agents/ExpectedOutcomeAgent.java`

This file is also structurally identical to the other two scaffolds.

The likely intended distinction is that this agent chooses moves using expected-value estimates from search rollouts or tree statistics, but the exact behavior is up to the student implementation.

**Implementation notes (this workspace):** `ExpectedOutcomeAgent` evaluates root legal moves by repeated rollouts into precomputed child nodes, accumulating mean returns on the root `Node` (flat Monte Carlo per action rather than a deep UCT tree). Nested `MCTSNode` uses `getView(currentAgentIdx)` after transitions; rollout cap 20; time budget `maxThinkingTimeInMS - 100`. Details are in the [detailed section](#detailed-technical-documentation-uctagent-and-expectedoutcomeagent) at the top.

### Common structure across all three student agents

All three files:

- extend `MCTSAgent`
- import the same framework classes:
  - `Card`
  - `Game.GameView`
  - `Hand.HandView`
  - `Color`
  - `Value`
  - `Move`
  - `Node`
- include a nested node subclass
- are currently just TODO scaffolds

This means the assignment is not about building the game engine. It is about implementing search logic on top of the provided framework.

**Addendum:** In this workspace, `UCTAgent` and `ExpectedOutcomeAgent` are implemented as described in the [detailed section](#detailed-technical-documentation-uctagent-and-expectedoutcomeagent) at the top. `UnoMCTSAgent.java` is a third variant in the same assignment family (nested `MCTSNode`, `search`, `argmaxQValues`); it is not expanded in that top section, so treat its source as the source of truth for its behavior.

## Core framework packages in the JAR

The JAR contains these major packages:

- `edu.bu.pas.uno`
- `edu.bu.pas.uno.agents`
- `edu.bu.pas.uno.enums`
- `edu.bu.pas.uno.moves`
- `edu.bu.pas.uno.tree`
- `edu.bu.pas.uno.ui`
- `edu.bu.pas.uno.ui.animations`
- `edu.bu.pas.uno.utils`
- `edu.bu.pas.uno.history` (present in the JAR, not prominently documented in package overview)

For agent implementation, the most important packages are:

- `edu.bu.pas.uno`
- `edu.bu.pas.uno.agents`
- `edu.bu.pas.uno.enums`
- `edu.bu.pas.uno.moves`
- `edu.bu.pas.uno.tree`

The UI and animation packages are mostly runtime/visualization support, not core search logic.

## Most important framework classes

### `edu.bu.pas.uno.agents.Agent`

This is the base class for all UNO agents.

Key methods:

- `Agent(int playerIdx, long maxThinkingTimeInMS)`
- `getPlayerIdx()`
- `getMaxThinkingTimeInMS()`
- `getLogicalPlayerIdx()`
- `setLogicalPlayerIdx(int)`
- `chooseCardToPlay(Game.GameView game)` (abstract)
- `maybePlayDrawnCard(Game.GameView game, int drawnCardIdx)` (abstract)

Meaning:

- `playerIdx` is the real agent/player identity.
- `logicalPlayerIdx` is the agent's position in the current logical ordering of play.
- `chooseCardToPlay(...)` is called when the agent already has legal moves.
- `maybePlayDrawnCard(...)` is called only when the agent had to draw one card and that drawn card is playable.
- `maybePlayDrawnCard(...)` is allowed to return `null`, meaning "keep the drawn card instead of playing it."

### `edu.bu.pas.uno.agents.MCTSAgent`

This is the assignment-critical abstraction. Your student agents inherit from this instead of directly from `Agent`.

Key methods:

- `MCTSAgent(int playerIdx, long maxThinkingTimeInMS)`
- `getRandom()`
- `setRandom(Random)`
- `search(Game.GameView game, Integer drawnCardIdx)` (abstract)
- `argmaxQValues(Node node)` (abstract)
- `timedSearch(Game.GameView game, Integer drawnCardIdx)`
- `chooseCardToPlay(Game.GameView game)`
- `maybePlayDrawnCard(Game.GameView game, int drawnCardIdx)`

Meaning:

- `MCTSAgent` already handles the outer interaction with the framework.
- Its concrete implementations only need to provide:
  - how to search a tree rooted at the current state
  - how to map the finished root node to a chosen move
- `timedSearch(...)` appears to run `search(...)` and `argmaxQValues(...)` within the agent's time budget.
- Because `chooseCardToPlay(...)` and `maybePlayDrawnCard(...)` are already implemented in `MCTSAgent`, your agent logic is expected to live mostly in `search(...)`, `argmaxQValues(...)`, and child generation.

### `edu.bu.pas.uno.agents.RandomAgent`

This is a baseline/reference-style agent supplied by the framework.

Key methods:

- constructor
- `getRandom()`
- `chooseCardToPlay(...)`
- `maybePlayDrawnCard(...)`

It is likely useful as a conceptual rollout/default policy baseline, even if you never directly instantiate it.

### `edu.bu.pas.uno.Game`

This is the mutable full game-state class used by the runtime and also useful for simulations.

Important constants:

- `INIT_NUM_CARDS = 7`
- `DEFAULT_MAX_NUM_MOVES = 1000000`

Important constructors:

- `Game(Observability, int, Random, Agent...)`
- `Game(Observability, Random, Agent...)`
- `Game(Random, Agent...)`
- `Game(Game.GameView)`
- `Game(Game.GameView, Agent...)`
- `Game(Deck, Hand[], Game.GameView, Agent[])`

Important methods:

- `getNumPlayers()`
- `getMaxNumMoves()`
- `getObservability()`
- `getCurrentMoveIdx()`
- `getDrawPile()`
- `getDiscardPile()`
- `getCurrentAgent()`
- `getAgent(int)`
- `getHand(int)`
- `getPlayerOrder()`
- `getCurrentColor()`
- `getLastPlayedCard()`
- `getUnresolvedCards()`
- `getCurrentPlayerHand()`
- `getRandom()`
- `getTimeoutTracker()`
- `getHistory()`
- `setCurrentColor(Color)`
- `setLastPlayedCard(Card)`
- `setCurrentMoveIdx(int)`
- `getView(int)`
- `getOmniscientView()`
- `deal()`
- `chooseRandomColor()`
- `makeInitialDiscardPile()`
- `shuffleDiscardPileIntoDrawPile()`
- `drawCard(Hand)`
- `drawTotal(Hand, int)`
- `addToHistory(HistoryItem)`
- `getMove()`
- `resolveMove(Move)`
- `isOver()`

Why this matters for search:

- `Game.GameView` is what the framework gives your agent.
- `Game` is what you can use when you want to simulate forward from a view.
- The constructor `Game(GameView)` is especially important because it means you can create a mutable game copy from a view for search/rollout purposes.
- `resolveMove(Move)` is likely the main transition function for successor generation.

### `edu.bu.pas.uno.Game.GameView`

This is the immutable view your agent receives.

Important methods:

- `getMaxNumMoves()`
- `getObservability()`
- `getCurrentMoveIdx()`
- `getNumPlayers()`
- `getDrawPile()`
- `getDiscardPile()`
- `getHandView(int logicalPlayerIdx)`
- `getPlayerOrder()`
- `getCurrentColor()`
- `getLastPlayedCard()`
- `getUnresolvedCards()`
- `getTimeoutTracker()`
- `getDrawPileSize()`
- `getHistory()`
- `isOver()`

Meaning:

- This is the primary read-only state interface for agent logic.
- `getHandView(...)` is how the agent inspects its own or other players' visible hands.
- `getObservability()` matters a lot because the amount of hidden information changes what you can safely infer.
- `getHistory()` may be useful for debugging or for heuristic/context-based reasoning.

### `edu.bu.pas.uno.Hand.HandView`

This is a view over a hand. The docs explicitly say it is mutable as a data object, even though modifying it does not change the real game.

Important methods:

- `size()`
- `getCard(int idx)`
- `setCard(int idx, Card card)`
- `getLegalMoves(Game.GameView view)`
- `hasLegalMoves(Game.GameView view)`
- `toString()`

Important semantics from the docs:

- In partially observable games, opponents' hand views may contain the correct number of cards but with `UNKNOWN` color/value placeholders.
- Because the object is mutable, you can replace unknown cards with hypothetical cards if you want to build determinizations or sampled hidden states.

This is extremely important for search under hidden information.

### `edu.bu.pas.uno.Hand`

This is the mutable full-hand class.

Important methods:

- constructor from scratch
- constructor from `HandView`
- `getCards()`
- `add(Card)`
- `getCard(int)`
- `remove(int)`
- `size()`
- `isEmpty()`
- `getView(boolean, boolean)`
- `getLegalMoves(Game)`
- `hasLegalMoves(Game)`

Useful implication:

- The constructor `Hand(HandView)` suggests the framework supports rebuilding a mutable hand from a view, which can be useful when reconstructing hypothetical states.

### `edu.bu.pas.uno.Card`

`Card` is a record:

- `Card(Color color, Value value)`

Important methods:

- `applyEffects(Game)`
- `isAction()`
- `isWild()`
- `canBePlayedAsDrawCard(Game)`
- `color()`
- `value()`

Meaning:

- A `Card` carries both color and face/action value.
- `applyEffects(Game)` changes game state according to card behavior.
- `canBePlayedAsDrawCard(Game)` is particularly relevant when unresolved draw effects exist.

### `edu.bu.pas.uno.moves.Move`

This class represents an actual move chosen by an agent.

Important methods:

- `getPlayerIdx()`
- `getCardToPlayIdx()`
- `getNewColorIfWild()`
- `applyEffects(Game)`
- `toString()`
- `createMove(Agent, int, Color)`
- `createMove(Agent, int)`

Important semantics:

- The move stores a hand index, not the card object itself.
- If the chosen card is wild, you should use `createMove(agent, idx, newColor)`.
- The docs explicitly say: do not use the two-argument factory for wild cards.

### `edu.bu.pas.uno.tree.Node`

This is the core search-tree abstraction for the assignment.

Important constructor:

- `Node(Game.GameView game, int logicalPlayerIdx, Node parent)`

Important methods:

- `getGameView()`
- `getDepth()`
- `getLogicalPlayerIdx()`
- `getParent()`
- `getOrderedLegalMoves()`
- `getNodeState()`
- `isTerminal()`
- `getStateCount()`
- `getQValueTotal(int moveIdx)`
- `getQCount(int moveIdx)`
- `setQValueTotal(int moveIdx, float)`
- `setQCount(int moveIdx, long)`
- `getQValue(int moveIdx)`
- `getUtilityValues()`
- `getChild(Move move)` (abstract)

This class appears to manage:

- the state/view at a tree node
- node depth and parent linkage
- q-value totals and visit counts
- move indexing conventions
- terminal detection
- utility access

### `edu.bu.pas.uno.tree.Node.NodeState`

This enum tells you how to interpret the q-values in a node:

- `HAS_LEGAL_MOVES`
- `NO_LEGAL_MOVES_UNRESOLVED_CARDS_PRESENT`
- `NO_LEGAL_MOVES_MAY_PLAY_DRAWN_CARD`

This is one of the most important parts of the framework because it changes what each q-value index means.

### Q-value index conventions for `Node`

When `node.getNodeState() == HAS_LEGAL_MOVES`:

- each q-value index corresponds to one legal move
- the mapping from q-value index to hand-card index is given by `node.getOrderedLegalMoves()`

When `node.getNodeState() == NO_LEGAL_MOVES_MAY_PLAY_DRAWN_CARD`:

- there are exactly two q-value slots
- `Node.NoLegalMovesIdxDefaults.DrawSingleCardIdxs.PLAY_CARD_MOVE_IDX = 0`
- `Node.NoLegalMovesIdxDefaults.DrawSingleCardIdxs.KEEP_CARD_MOVE_IDX = 1`

When `node.getNodeState() == NO_LEGAL_MOVES_UNRESOLVED_CARDS_PRESENT`:

- there is exactly one q-value slot
- `Node.NoLegalMovesIdxDefaults.DrawUnresolvedCardsIdxs.MOVE_IDX = 0`

This means `argmaxQValues(...)` must not assume "q-value index == hand index" unless the node actually has legal moves.

### `edu.bu.pas.uno.Game.PlayerOrder`

This class handles logical turn order and direction.

Important methods:

- `getLogicalIdx(int)`
- `getAgentIdx(int)`
- `getCurrentLogicalPlayerIdx()`
- `getCurrentAgentIdx()`
- `getCurrentDirection()`
- `setCurrentDirection(Direction)`
- `switchDirection()`
- `advance()`

This matters because "logical player index" and "agent/player index" are not the same thing.

### `edu.bu.pas.uno.Game.UnresolvedCardBuffer`

This appears to track stacked unresolved draw cards.

Important methods:

- `getUnresolvedCards()`
- `add(Card)`
- `total()`
- `isEmpty()`
- `size()`
- `clear()`

This is very relevant for UNO variants where stacked draw effects are pending.

### `edu.bu.pas.uno.Game.TimeoutTracker`

This tracks agent timeouts.

Important constant:

- `DEFAULT_THRESHOLD = 3`

Important methods:

- `getTimeoutsLeft(int)`
- `getThreshold()`
- `markTimeout(int)`
- `isAnyOutOfTimeoutsLeft()`

This matters because the game engine appears to care about repeated timeouts.

### `edu.bu.pas.uno.DiscardPile` and `DiscardPile.DiscardPileView`

These represent the discard pile in mutable and view form.

Important methods on `DiscardPile`:

- `getPile()`
- `push(Card)`
- `peek()`
- `pop()`
- `size()`
- `getView()`

Important methods on `DiscardPileView`:

- `push(Card)`
- `pop()`
- `peek()`
- `size()`
- `pile()`

### `edu.bu.pas.uno.Deck`

`Deck` extends `LinkedList<Card>`.

Constructors:

- `Deck(boolean)`
- `Deck()`

The docs do not expose much additional behavior beyond its role as the draw pile container.

## Important enums

### `edu.bu.pas.uno.enums.Color`

Constants:

- `YELLOW`
- `GREEN`
- `RED`
- `BLUE`
- `UNKNOWN`

Methods:

- `getLegalOptions()`
- `getRandomColor(Random)`

`UNKNOWN` is especially important under partial observability.

### `edu.bu.pas.uno.enums.Value`

Constants:

- `ZERO` through `NINE`
- `SKIP`
- `REVERSE`
- `DRAW_TWO`
- `WILD`
- `WILD_DRAW_FOUR`
- `UNKNOWN`

Methods:

- `isAction()`
- `isWild()`
- `getLegalOptions()`
- `getRandomValue(Random)`

### `edu.bu.pas.uno.enums.Observability`

Constants:

- `FULL`
- `PARTIAL_NO_DECK`
- `PARTIAL_NO_DECK_NO_HANDS`

This is one of the most important design constraints for search because it determines how much hidden information exists.

### `edu.bu.pas.uno.enums.Direction`

Constants:

- `LEFT`
- `RIGHT`

Methods:

- `getNextLogicalPlayerIdx(int, int)`
- `getOtherDirection()`

## Main entry points

### `edu.bu.pas.uno.SingleGameMain`

Important methods:

- `getAgent(String agentClassName, int playerIdx, long maxThinkingTimeInMS)`
- `main(String[] args)`

Purpose:

- single-run executable entry point
- uses reflection or class-name lookup to instantiate agents

### `edu.bu.pas.uno.MultiGameMain`

Important methods:

- `getAgent(String agentClassName, int playerIdx, long maxThinkingTimeInMS)`
- `main(String[] args)`

Purpose:

- batch or repeated evaluation entry point

## How the docs folder is organized

### `doc/pas/uno/index.html`

This is the Javadoc landing page.

It lists the documented packages:

- `edu.bu.pas.uno`
- `edu.bu.pas.uno.agents`
- `edu.bu.pas.uno.enums`
- `edu.bu.pas.uno.moves`
- `edu.bu.pas.uno.tree`
- `edu.bu.pas.uno.ui`
- `edu.bu.pas.uno.ui.animations`
- `edu.bu.pas.uno.utils`

Use this page when you want a top-down package view.

### `doc/pas/uno/index-all.html`

This is the alphabetized symbol index for the documentation.

It contains:

- classes
- enums
- constructors
- methods
- fields
- links to summary pages such as all classes, all packages, constant values, and serialized form

Use this page when you already know roughly what symbol name you want to find.

### `doc/pas/uno/allclasses-index.html`

This is the alphabetized class/type list. It is the quickest way to see the whole documented public API at the class level.

### `doc/pas/uno/help-doc.html`

This is the standard Javadoc help page. It explains what each kind of Javadoc page means:

- overview
- package
- class
- use
- tree
- constant values
- serialized form
- all classes
- index

### `doc/pas/uno/overview-summary.html`

This is just a redirect to `index.html`. It is not independently important.

### `doc/pas/uno/serialized-form.html`

This documents Java serialization details for serializable classes. It is mainly relevant to implementation internals, not agent logic.

### Other top-level files in `doc/pas/uno/`

These are generated Javadoc site assets, not assignment logic:

- `stylesheet.css`
- `script.js`
- `search.js`
- `search-page.js`
- `search.html`
- `module-search-index.js`
- `package-search-index.js`
- `type-search-index.js`
- `member-search-index.js`
- `tag-search-index.js`
- `script-dir/*`
- `copy.svg`
- `link.svg`
- `legal/*`

These support site styling, search, and licensing. They are not part of the UNO engine API itself.

## JAR contents beyond the most relevant public docs

The runtime JAR also bundles:

- image assets for cards/backgrounds/logos
- sound assets
- colorblind variants of assets
- font assets
- runtime UI classes
- history-tracking classes in `edu.bu.pas.uno.history`

For agent development, these are mostly nonessential, but they explain why the JAR is larger than a minimal game-logic library.

## Practical implementation notes for another model

If another model is asked to help implement the UNO agents, these are the most important takeaways:

- Do not try to implement the game engine from scratch. The engine already exists in the JAR.
- The real student work is in the three files under `src/pas/uno/agents/`.
- For a concrete reference of two finished strategies in this codebase, read [Detailed technical documentation: UCTAgent and ExpectedOutcomeAgent](#detailed-technical-documentation-uctagent-and-expectedoutcomeagent) at the top of this file: it explains UCT tree search vs root-level expected-outcome rollouts, omniscient vs current-player `GameView` in `getChild`, and shared reward logic.
- The most important framework types for agent logic are:
  - `Game.GameView`
  - `Hand.HandView`
  - `Move`
  - `Node`
  - `MCTSAgent`
  - `Card`
  - the enums
- `GameView` is read-only, but there is a `Game(GameView)` constructor for turning a view into a mutable simulation state.
- `HandView` can contain `UNKNOWN` cards under partial observability.
- `Move` stores card indices, not direct card references.
- Wild cards require `Move.createMove(agent, idx, color)`.
- `maybePlayDrawnCard(...)` may legally return `null`, but `chooseCardToPlay(...)` may not.
- In `Node`, q-value indices only line up with card indices when the node state is `HAS_LEGAL_MOVES`.
- For `HAS_LEGAL_MOVES`, use `node.getOrderedLegalMoves()` to translate q-value positions to actual hand indices.
- For `NO_LEGAL_MOVES_MAY_PLAY_DRAWN_CARD`, q-index `0` means play the drawn card and q-index `1` means keep it.
- For unresolved stacked draw cards, there is only one action/q-slot.
- There is a difference between real player index and logical player index. Turn-order code uses logical indices.

## Best mental model of the assignment

The assignment appears to be:

- receive a `Game.GameView`
- reason about legal plays under possibly partial observability
- optionally reconstruct or sample full mutable states for simulation
- build a search tree of `Node` objects
- generate child states with `getChild(Move move)`
- store/update q-values and visit counts in each `Node`
- use `argmaxQValues(...)` to convert tree statistics into a final framework `Move`

In short: this is an MCTS-over-a-provided-UNO-engine assignment, not a full-game implementation assignment.
