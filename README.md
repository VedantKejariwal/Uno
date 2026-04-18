# Uno AI Monte Carlo Tree Search

Two AI agents that play Uno using Monte Carlo Tree Search, built from scratch in Java for CS440 (Artificial Intelligence) at Boston University.

## The big picture

The core idea behind both agents is the same. Rather than hardcoding Uno strategy, the agents figure out what works by simulating thousands of random games and measuring which moves win more often. There are no rules like "always play action cards" or "match the color." Strategy emerges entirely from outcome statistics.

The game engine calls things in a fixed order every turn. It calls `search()` which runs the Monte Carlo simulation and populates Q-values on the root node, then immediately calls `argmaxQValues()` on that same root node which reads those Q-values and returns the actual move to play. Both agents override these two methods and the `getChild()` method inside the nested `MCTSNode` class. Everything else is handled by the engine JAR.

The game state comes in two forms. `GameView` is read-only and represents a snapshot of the game from a specific player's perspective. `Game` is mutable and can be used to actually simulate moves. Whenever the agents need to simulate, they create a `Game` copy from a `GameView` so the real game state is never touched during search.

Moves are not represented as card objects during search. They are represented as Q-value indices. For a normal turn where the player has legal cards to play, `getOrderedLegalMoves()` returns a list of hand indices (positions in the player's hand, not card objects), and Q-index 0 corresponds to the first entry in that list, Q-index 1 to the second, and so on. For the situation where a player just drew a card and can optionally play it, there are exactly two Q-slots: index 0 means play the drawn card, index 1 means keep it. For forced draws from stacked Draw Two or Wild Draw Four penalties, there is exactly one Q-slot and no real decision. This Q-index abstraction is what connects `search()` (which writes Q-values) to `argmaxQValues()` (which reads them).

## ExpectedOutcomeAgent

### MCTSNode

`MCTSNode` is a static nested class inside `ExpectedOutcomeAgent` that extends `Node`. It represents a single game state in the search tree. The constructor just calls `super(game, logicalPlayerIdx, parent)` to pass the game view, whose turn it is, and a pointer to the parent node up to the `Node` base class. The only method it implements is `getChild()`.

### MCTSNode.getChild()

`getChild(Move move)` takes a move and returns a new `MCTSNode` representing the game state after that move is played. This is how the tree grows every time an agent needs to explore a child state, it calls `getChild()` on the current node.

The method first creates a full `Agent[]` array with one `RandomAgent` per player. It uses `this.getGameView().getPlayerOrder().getAgentIdx(i)` to map each logical seat to the correct physical agent index. These agents are needed because `new Game(this.getGameView(), agents)` requires an agent for every seat the mutable `Game` needs to know who is playing in order to call `getMove()` on them during rollouts later.

The move passed in was created with the outer `ExpectedOutcomeAgent` instance as the acting agent, but the `Game` copy has its own internal `RandomAgent` objects. So the move needs to be re-created and bound to `copyGame.getCurrentAgent()` before it can be applied. If the move has a wild color (`move.getNewColorIfWild() != null`), it uses the three-argument `Move.createMove(currentAgent, cardToPlayIdx, color)`. Otherwise it uses the two-argument version. If the move is `null`, it means the player is passing or drawing, and `resolveMove(null)` handles that.

After calling `copyGame.resolveMove(actualMove)`, the game state has advanced one step. The method reads the resulting state using `copyGame.getView(copyGame.getPlayerOrder().getCurrentAgentIdx())` this gets the view from the perspective of whoever's turn it is next and returns a new `MCTSNode` with that view, the new logical player index, and `this` as the parent.

### search()

`search()` is where all the Monte Carlo sampling happens. It receives the current `GameView` and returns the root `Node` with Q-values populated.

The first thing it does is create the root: `new MCTSNode(game, getLogicalPlayerIdx(), null)`. The null parent marks it as the tree root. Then it records `startTime` and computes `timeLimit` as `Math.max(1L, getMaxThinkingTimeInMS() - 100L)`, leaving a 100ms buffer so the engine does not cut the agent off mid-computation.

The method then checks `root.getNodeState()`. If the state is `HAS_LEGAL_MOVES`, the full sampling loop runs. The first thing inside this block is upfront child creation. `root.getOrderedLegalMoves()` returns the list of playable hand indices. For each one, the code reads the card from `root.getGameView().getHandView(root.getLogicalPlayerIdx())`, checks if it is wild, builds the appropriate `Move`, and calls `root.getChild(childIteration)` to produce a child node. All children are stored in a `Node[] children` array indexed to match the legal moves list. These children are created once before the main loop and reused across every iteration `getChild()` is never called inside the loop itself.

Then the agent array for rollouts is built: one `RandomAgent` per player, mapped using `root.getGameView().getPlayerOrder().getAgentIdx(p)`.

The outer `while` loop runs until the time budget expires. Inside, the inner `for` loop goes through every child one by one. For each child `i`, it creates a fresh `Game` copy from `children[i].getGameView()` using the shared `agents` array. This is the game state after move `i` has been played. Then the rollout loop runs: `simGame.getMove()` asks whatever `RandomAgent` is currently acting for a random legal move, and `simGame.resolveMove(randomMove)` applies it and advances to the next player. This continues until `simGame.isOver()` is true or `moveCount` reaches the cap of 20.

After the rollout, reward `r` is computed. If `simGame.isOver()` is true and `simGame.getHand(getLogicalPlayerIdx()).size() == 0`, the agent's hand is empty meaning it won, so `r = 1.0`. If the game ended but the agent still has cards, it lost, so `r = -1.0`. If the rollout hit the cap before the game finished, the heuristic kicks in: sum up all opponents' hand sizes into `otherCards`, then `r = otherCards - myCards`. The agent gets rewarded for having fewer cards than its opponents, which teaches card-shedding behavior without any explicit Uno rules.

The reward gets written to the root node at Q-index `i` using `copyRoot.setQValueTotal(i, copyRoot.getQValueTotal(i) + r)` and `copyRoot.setQCount(i, copyRoot.getQCount(i) + 1)`. After many iterations, `root.getQValue(i)` which divides the total by the count gives the average reward for each move, which `argmaxQValues()` uses to make the final decision.

If the root state is not `HAS_LEGAL_MOVES`, the method skips the loop entirely and returns root with no Q-values populated. `argmaxQValues()` handles those states differently.

### argmaxQValues()

`argmaxQValues()` runs immediately after `search()` returns root. It reads the Q-values and produces the concrete `Move` object the engine will actually play.

It first checks `node.getNodeState()`. For `HAS_LEGAL_MOVES`, it loops through all Q-value indices from 0 to `node.getOrderedLegalMoves().size() - 1` and tracks the index with the highest `node.getQValue(i)`. Once found, it converts that Q-index back to a hand index using `node.getOrderedLegalMoves().get(valueIndex)`, reads the card from the hand view, and calls `Move.createMove(this, moveIdx)` or the three-argument wild version to build the final move.

For `NO_LEGAL_MOVES_MAY_PLAY_DRAWN_CARD`, it compares the Q-values at the two named constant indices: `DrawSingleCardIdxs.PLAY_CARD_MOVE_IDX` (index 0, meaning play the drawn card) and `DrawSingleCardIdxs.KEEP_CARD_MOVE_IDX` (index 1, meaning keep it). If playing is better, it constructs a move for the drawn card. The drawn card is always at `myHand.size() - 1` because newly drawn cards get appended to the end of the hand. If keeping is better, it returns `null`, which the engine interprets as the decision to not play.

For `NO_LEGAL_MOVES_UNRESOLVED_CARDS_PRESENT`, there is no choice the player must draw the stacked penalty cards. The method creates a fresh `Game` copy with a `RandomAgent` array and calls `copyGame.getMove()` to get the forced draw move from the engine.

## UCTAgent

UCTAgent has the same overall skeleton as ExpectedOutcomeAgent same nested `MCTSNode`, same three-case `argmaxQValues()` but `search()` is completely different. Instead of treating every root child equally and only updating root Q-values, UCTAgent builds a tree that grows deeper over time and updates every node on the path after each rollout.

### MCTSNode and getChild()

Same structure as ExpectedOutcomeAgent with one critical difference. ExpectedOutcomeAgent stores child nodes using `copyGame.getView(copyGame.getPlayerOrder().getCurrentAgentIdx())`, which returns the game state from the perspective of the next player. In that view, some cards appear as UNKNOWN because the player cannot see their opponents' hands. UCTAgent uses `copyGame.getOmniscientView()` instead, which returns a view where all cards are fully visible. This matters because if a child node stores a partial view with UNKNOWN cards, the next call to `new Game(childView, agents)` inside a later rollout or expansion step will crash the mutable Game constructor rejects any UNKNOWN card values. The omniscient view avoids this entirely.

### getActionCount()

`getActionCount(node)` is a helper that returns how many Q-value slots exist at a given node depending on its state. For `HAS_LEGAL_MOVES` it returns `node.getOrderedLegalMoves().size()` one slot per playable card. For `NO_LEGAL_MOVES_MAY_PLAY_DRAWN_CARD` it returns 2 play or keep. For `NO_LEGAL_MOVES_UNRESOLVED_CARDS_PRESENT` it returns 1 no real choice. This method is used everywhere else so the code does not need to switch on node state repeatedly.

### UCBSelect()

`UCBSelect(node)` decides which action to take at a fully expanded node during the selection phase. It computes a UCB1 score for every action and returns the index with the highest.

First it sums all visit counts across every action at this node to get `totalVisits`. This is N(s) in the UCB formula the total number of times this node has been visited from above. Then it loops through each action index using `getActionCount(node)`.

If any action has `getQCount(i) == 0`, it returns that index immediately. An unvisited action technically has an exploration bonus of infinity, so it always wins UCB this ensures every action is tried at least once before the formula makes any real judgments.

For visited actions, the score is `node.getQValue(i) + Math.sqrt((2.0 * Math.log(totalVisits)) / node.getQCount(i))`. `node.getQValue(i)` is the mean reward for action i this is the exploitation term, rewarding actions that have historically done well. The square root term is the exploration bonus it grows as `totalVisits` grows (encouraging exploration as more is learned) but shrinks as `node.getQCount(i)` grows (penalizing actions that have already been tried a lot). The two terms together balance trying proven moves against investigating underexplored ones. The index with the highest combined score is returned as `bestIdx`.

### isFullyExpanded()

`isFullyExpanded(node)` checks whether every action at a node has been tried at least once. It loops through all indices using `getActionCount(node)` and returns false the moment any `getQCount(i) == 0` is found. If every action has a count above zero, it returns true. This is what the selection phase checks to decide whether to keep walking down the tree or stop and expand.

### pickUnvitedAction()

`pickUnvitedAction(node)` finds the first action index where `getQCount(i) == 0`. This is called during expansion when selection has stopped at a node that is not fully expanded. It always picks the first unvisited action in order so expansion is deterministic rather than random. The fallback `return 0` at the end is only reached if all actions have been visited, which should not happen if `isFullyExpanded()` was checked correctly before calling this.

### search()

`search()` is the UCT main loop. It wraps the same four phases selection, expansion, rollout, backpropagation in an outer while loop that runs until the time budget expires.

After creating root and building the `RandomAgent` array for rollouts, the outer while loop begins. Each iteration creates two fresh lists: `List<Node> path` and `List<Integer> pathActionIdx`. These are the memory of where this iteration went. `path` records every node visited, starting with root. `pathActionIdx` records which action index was taken at each of those nodes. `path` always ends up with one more entry than `pathActionIdx` because the leaf node is added to `path` after expansion but no action is taken from it.

**Selection:** The inner while loop runs as long as the current node is not terminal and `isFullyExpanded(node)` returns true. At each step, `UCBSelect(node)` picks the best action index. The code then translates that index into a concrete `Move` depending on the node state. For `HAS_LEGAL_MOVES`, it reads `node.getOrderedLegalMoves().get(moveActionIdx)` to get the hand index, reads the card, and builds the move. For `NO_LEGAL_MOVES_MAY_PLAY_DRAWN_CARD`, index 1 means null (keep the card) and index 0 means build a move for the drawn card at `myHand.size() - 1`. For `NO_LEGAL_MOVES_UNRESOLVED_CARDS_PRESENT`, it creates a temporary `Game` copy and calls `tempGame.getMove()` to get the forced draw. Then `node.getChild(childIteration)` moves one level deeper. Both the action index and the new node are appended to their respective lists and the loop continues.

**Expansion:** When the selection loop exits because a node is not fully expanded, `pickUnvitedAction(node)` finds the first unvisited action. The same move-building logic as selection runs here too. `node.getChild(childIteration)` creates a brand new node in the tree. The new action index and the new child node are appended to `pathActionIdx` and `path`. After this, `node` points to the newly created leaf.

**Rollout:** A fresh `Game` is built from `child.getGameView()` using the `agents` array. The rollout loop calls `simGame.getMove()` and `simGame.resolveMove()` repeatedly for up to 5 moves or until the game ends. Every player is covered by this loop because `simGame.getMove()` asks whoever's turn it is all players are `RandomAgent` instances, so the simulation cycles through all seats automatically. Reward `r` is computed the same way as in `ExpectedOutcomeAgent`: +1 win, -1 loss, or `otherCards - myCards` if the cap was hit.

**Backpropagation:** This is the defining difference from ExpectedOutcomeAgent. Instead of only updating root, the backprop loop walks back through every entry in `pathActionIdx`. For index `i`, it reads `path.get(i)` to get the node and `pathActionIdx.get(i)` to get the action that was taken there, and calls `setQValueTotal` and `setQCount` on that node at that action index. This updates Q-values at every depth of the tree root, its children, their children, all the way down to the node just above the leaf. The next time `UCBSelect` runs at any of these nodes, the scores will reflect what happened during this rollout.

The loop does not update the leaf node itself because no action was taken from it the leaf is the last entry in `path` but there is no corresponding entry in `pathActionIdx`.

### argmaxQValues()

Identical to `ExpectedOutcomeAgent`. Same three NodeState cases, same Q-value argmax loop, same move construction logic including the wild card check and the `myHand.size() - 1` index for drawn cards. The named constants `DrawSingleCardIdxs.PLAY_CARD_MOVE_IDX` and `DrawSingleCardIdxs.KEEP_CARD_MOVE_IDX` are used for the play/keep comparison to be explicit about which index means what.

## How to run

```bash
javac -cp "./lib/*" -d . src/pas/uno/agents/*.java

java -cp "./lib/*:." edu.bu.pas.uno.SingleGameMain pas.uno.agents.UCTAgent edu.bu.pas.uno.agents.RandomAgent
```

For a 4-player game with a fixed seed:

```bash
java -cp "./lib/*:." edu.bu.pas.uno.SingleGameMain --seed 42 --maxThinkingTimeInMS 2000 pas.uno.agents.UCTAgent edu.bu.pas.uno.agents.RandomAgent edu.bu.pas.uno.agents.RandomAgent edu.bu.pas.uno.agents.RandomAgent
```

Other flags: `--headless` runs without the UI window, `--mute` disables sounds, `--colorblind` switches to the colorblind-friendly asset set.

## Tech

Java, Java Swing, Monte Carlo Tree Search, Upper Confidence Trees, UCB1, backpropagation, Q-value estimation, game tree search, heuristic reward shaping.
