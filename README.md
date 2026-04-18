# Uno AI — Monte Carlo Tree Search

Two AI agents that play Uno using Monte Carlo Tree Search, implemented from scratch in Java for CS440 (Artificial Intelligence) at Boston University.

## What it does

The agents play Uno by simulating thousands of random games from each possible move and picking the one that wins most often. No hardcoded rules. No domain knowledge about Uno strategy. The agents figure out what works purely from outcome statistics accumulated over repeated simulation.

## How MCTS works here

Every time the agent needs to pick a move, it runs a search within a time budget. For each possible action, it simulates complete random games — every player picks randomly from their legal moves until someone wins or the simulation hits a depth cap. The outcome of each simulation updates a Q-value for that action. After the time budget expires, the agent picks the action with the highest average Q-value.

Q-value update: Q(s,a) = total reward / visit count

Terminal reward: win returns +1, loss returns -1. If the simulation hits the depth cap before the game ends, a heuristic fills in: r = opponents' total cards minus my cards. This pushes the agent toward shedding cards aggressively without ever being told to.

## ExpectedOutcomeAgent

This agent uses flat Monte Carlo evaluation. The logic lives in search() and argmaxQValues() inside ExpectedOutcomeAgent.java.

At the start of each turn, search() enumerates every legal move at the root and creates one child node per move by calling getChild(). These children are created upfront and stored in an array. Then the outer while loop runs until the time budget expires. Each iteration loops over every child and runs one rollout from it — a full simulated game using RandomAgent for every player. The outcome updates the root's Q-value at that child's index using setQValueTotal and setQCount.

After search completes, argmaxQValues() reads the Q-values at root and picks the index with the highest mean. It then constructs the actual Move object from that index using getOrderedLegalMoves().get(bestIdx) to look up the hand index, reads the card, checks if it is wild, and calls Move.createMove() with the appropriate arguments.

getChild() inside MCTSNode handles state transitions. It creates a mutable Game copy from the current node's view, re-creates the move bound to the correct agent object using copyGame.getCurrentAgent(), calls resolveMove() to apply it, and returns a new MCTSNode from the resulting game state.

The agent handles three NodeState cases in argmaxQValues(): HAS_LEGAL_MOVES for normal play, NO_LEGAL_MOVES_MAY_PLAY_DRAWN_CARD when the player drew a card and decides whether to play or keep it, and NO_LEGAL_MOVES_UNRESOLVED_CARDS_PRESENT for forced draws from stacked penalties.

## UCTAgent

This agent uses Upper Confidence Trees — the same four-phase MCTS loop but with a smarter selection strategy and full backpropagation. The logic lives in search(), UCBSelect(), isFullyExpanded(), pickUnvitedAction(), and argmaxQValues() inside UCTAgent.java.

The outer while loop runs until the time budget expires. Each iteration starts fresh from root and tracks two lists: path (every node visited) and pathActionIdx (which action was taken at each node).

Selection walks down the tree through fully expanded nodes. At each node, UCBSelect() computes a score for every action and returns the best index. The UCB1 formula balances exploitation and exploration:

UCB(s,a) = Q̄(s,a) + sqrt(2 * log(N(s)) / N(s,a))

Q̄(s,a) is the mean reward for action a from state s. The square root term gives a bonus to actions that have been tried fewer times — low visit count means high exploration bonus. If any action has never been tried, UCBSelect() returns it immediately. A node is fully expanded when every action has a visit count above zero, checked by isFullyExpanded().

Expansion happens when selection stops at a node that is not fully expanded. pickUnvitedAction() finds the first action with zero visits and calls getChild() to create a new node in the tree.

Rollout simulates a random game from the newly expanded node, same as ExpectedOutcomeAgent.

Backpropagation is the key difference from flat MCTS. After the rollout returns an outcome, the loop walks back through every (node, actionIdx) pair in the path and updates both setQValueTotal and setQCount. This means nodes at every depth accumulate evidence from rollouts that pass through them — not just the root. Future UCB scores reflect actual observed outcomes at every level of the tree.

argmaxQValues() is identical to ExpectedOutcomeAgent — same three NodeState cases, same Q-value argmax, same move construction logic.

## How to run

```bash
javac -cp "./lib/*" -d . src/pas/uno/agents/*.java

java -cp "./lib/*:." edu.bu.pas.uno.SingleGameMain pas.uno.agents.UCTAgent edu.bu.pas.uno.agents.RandomAgent
```

For a 4-player game with a fixed seed:

```bash
java -cp "./lib/*:." edu.bu.pas.uno.SingleGameMain --seed 42 --maxThinkingTimeInMS 2000 pas.uno.agents.UCTAgent edu.bu.pas.uno.agents.RandomAgent edu.bu.pas.uno.agents.RandomAgent edu.bu.pas.uno.agents.RandomAgent
```

Other options: --headless runs without the UI window, --mute disables sounds, --colorblind switches to the colorblind-friendly asset set.

## Tech

Java, Java Swing, Monte Carlo Tree Search, Upper Confidence Trees, UCB1, backpropagation, Q-value estimation, game tree search, heuristic reward shaping.
