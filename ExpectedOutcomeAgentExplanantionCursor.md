# ExpectedOutcomeAgent Explanation

This note compares your original `ExpectedOutcomeAgent.java` with the teaching copy `ExpectedOutcomeAgentCopy.java`.

The goal of the changes was **not** to redesign your whole solution. The goal was to keep your structure mostly the same while fixing the framework-specific bugs that were causing crashes and timeouts.

## Big Picture

Your original design already had the right overall shape:

- build a root node
- randomly walk down to an artificial leaf
- run rollouts from that leaf
- update the root q-value for the first action
- choose the move with the best q-value

So the copy does **not** replace your approach with something completely different.

Most of the changes were made because the UNO framework has a few rules that are easy to violate:

1. a copied `Game` needs valid agents for simulation
2. a `Move` must belong to the current simulation agent
3. `orderedLegalMoves` stores **hand indices**, not positions in the list
4. `Node.getUtilityValues()` is **not** a rollout reward
5. special node states must be handled differently

## 1. Changes in `MCTSNode.getChild(...)`

### Old code

```java
Game copyGame = new Game(this.getGameView());
copyGame.resolveMove(move);

return new MCTSNode(
    copyGame.getView(copyGame.getPlayerOrder().getCurrentLogicalPlayerIdx()),
    copyGame.getPlayerOrder().getCurrentLogicalPlayerIdx(),
    this
);
```

### New code

```java
Agent[] agents = new Agent[this.getGameView().getNumPlayers()];
for(int logicalIdx = 0; logicalIdx < agents.length; logicalIdx++)
{
    int playerIdx = this.getGameView().getPlayerOrder().getAgentIdx(logicalIdx);
    agents[logicalIdx] = new RandomAgent(playerIdx, 1L);
}

Game copyGame = new Game(this.getGameView(), agents);
```

### Why this changed

Your old code used `new Game(GameView)`.

That sounds correct at first, but in this framework that copied game ends up without the simulation agents needed for `resolveMove(...)` and `getMove()`. The terminal logs showed this clearly through the `getAgents() == null` crash.

So the new version creates a copied game with a valid `Agent[]`.

### Why the `Agent[]` is built this way

The array must match the framework's expected player ordering:

- the array is filled in **logical player order**
- each replacement agent must use the correct `playerIdx`

That is why the code uses:

```java
this.getGameView().getPlayerOrder().getAgentIdx(logicalIdx)
```

instead of just inventing indices.

## 2. Rebuilding the move before resolving it

### Old code

```java
copyGame.resolveMove(move);
```

### New code

```java
Move actualMove = move;
if(move != null)
{
    Agent currentAgent = copyGame.getCurrentAgent();
    if(move.getNewColorIfWild() != null)
    {
        actualMove = Move.createMove(currentAgent, move.getCardToPlayIdx(), move.getNewColorIfWild());
    }
    else
    {
        actualMove = Move.createMove(currentAgent, move.getCardToPlayIdx());
    }
}

copyGame.resolveMove(actualMove);
```

### Why this changed

The framework checks that the move belongs to the **current simulation agent**.

Your old code reused a `Move` that was created using `this`, but once you go deeper in the tree it may be another player's turn.

So even if the card index is correct, the move can still be rejected because the wrong player is "making" it.

The new code rebuilds the move using:

```java
copyGame.getCurrentAgent()
```

so the move matches the acting player in the copied game.

## 3. Fixing the child view index

### Old code

```java
copyGame.getView(copyGame.getPlayerOrder().getCurrentLogicalPlayerIdx())
```

### New code

```java
copyGame.getView(copyGame.getPlayerOrder().getCurrentAgentIdx())
```

### Why this changed

This framework distinguishes between:

- **logical player index**
- **agent/player index**

`getView(...)` expects an **agent index**, not a logical index.

So the old code was passing the wrong kind of index into `getView(...)`.

The copy fixes that while still storing the logical index inside the node:

```java
copyGame.getPlayerOrder().getCurrentLogicalPlayerIdx()
```

## 4. Adding a small time buffer in `search(...)`

### Old code

```java
long startTime = System.currentTimeMillis();
while (System.currentTimeMillis() - startTime < this.getMaxThinkingTimeInMS())
```

### New code

```java
long startTime = System.currentTimeMillis();
long timeLimit = Math.max(1L, this.getMaxThinkingTimeInMS() - 50L);

while (System.currentTimeMillis() - startTime < timeLimit)
```

### Why this changed

The framework's `timedSearch(...)` waits for `search(...)` to finish before the deadline.

So if your loop runs right up to the exact full time limit, the framework may still declare a timeout before `search(...)` returns.

The small buffer makes it much more likely that `search(...)` returns in time.

## 5. Fixing the hand index bug

### Old code

```java
int handIdx = copyRoot.getOrderedLegalMoves().get(randomIdx);
Card card = myHand.getCard(randomIdx);
```

### New code

```java
int handIdx = copyRoot.getOrderedLegalMoves().get(randomIdx);
Card card = myHand.getCard(handIdx);
```

### Why this changed

This was a simple but important indexing bug.

`randomIdx` is:

- the position in the list of legal moves

`handIdx` is:

- the actual position of the card in the hand

Since `getCard(...)` needs a real hand index, the correct variable is `handIdx`.

## 6. Fixing simulation game creation in rollouts

### Old code

```java
Game simGame = new Game(copyRoot.getGameView());
int maxMoves = game.getMaxNumMoves();
```

### New code

```java
Agent[] agents = new Agent[copyRoot.getGameView().getNumPlayers()];
for(int logicalIdx = 0; logicalIdx < agents.length; logicalIdx++)
{
    int playerIdx = copyRoot.getGameView().getPlayerOrder().getAgentIdx(logicalIdx);
    agents[logicalIdx] = new RandomAgent(playerIdx, 1L);
}

Game simGame = new Game(copyRoot.getGameView(), agents);
int rolloutCap = 60;
```

### Why this changed

This fixes two different problems:

1. `new Game(copyRoot.getGameView())` had the same null-agent problem as in `getChild(...)`
2. `game.getMaxNumMoves()` is the real game hard cap, which is huge and makes rollouts far too long

A rollout should be cheap.

So the copy uses:

- a simulation game with valid agents
- a small rollout cap like `60`

## 7. Letting the framework choose rollout moves

### Old code

```java
Set<Integer> legalMoves = simGame.getCurrentPlayerHand().getLegalMoves(simGame);
...
randomMove = Move.createMove(this, handIdx);
simGame.resolveMove(randomMove);
```

### New code

```java
while(!simGame.isOver() && moveCount < rolloutCap)
{
    Move randomMove = simGame.getMove();
    simGame.resolveMove(randomMove);
    moveCount++;
}
```

### Why this changed

Your old rollout manually created moves using `this`, even when it was another player's turn.

That can make the framework reject the move because:

- the wrong player is making the move
- the wrong method is being used for draw/keep logic

The new code lets the framework do the safe thing:

- `simGame.getMove()` asks the correct current agent what it wants to do
- `simGame.resolveMove(...)` applies that move

This is both simpler and less error-prone.

## 8. Replacing `getUtilityValues()` as the rollout reward

### Old code

```java
Node terminalNode = new MCTSNode(simGame.getView(getLogicalPlayerIdx()), getLogicalPlayerIdx(), null);
float r = terminalNode.getUtilityValues();
```

### New code

```java
float r = 0.0f;
int myCards = simGame.getHand(getLogicalPlayerIdx()).size();
if(simGame.isOver())
{
    r = (myCards == 0) ? 1.0f : -1.0f;
}
else
{
    int otherCards = 0;
    for(int logicalIdx = 0; logicalIdx < simGame.getNumPlayers(); logicalIdx++)
    {
        if(logicalIdx != getLogicalPlayerIdx())
        {
            otherCards += simGame.getHand(logicalIdx).size();
        }
    }
    r = otherCards - myCards;
}
```

### Why this changed

This is one of the most important logic fixes.

`Node.getUtilityValues()` does **not** mean:

"give me the value of this simulated state"

Instead, it means something more like:

"give me the expected value based on this node's stored q-values"

But a fresh node created after a rollout has no meaningful q-values yet.

So using it as the rollout result is not correct.

The copy replaces it with a simple human-readable reward:

- if the game ended and you won: `+1`
- if the game ended and you lost: `-1`
- otherwise: prefer states where you have fewer cards than opponents

That makes the rollout result actually reflect the simulated game.

## 9. Removing the useless update to `terminalNode`

### Old code

```java
terminalNode.setQValueTotal(firstActionIdx, r);
float newTotal = root.getQValueTotal(firstActionIdx) + r;
long newCount = root.getQCount(firstActionIdx) + 1;

root.setQValueTotal(firstActionIdx, newTotal);
root.setQCount(firstActionIdx, newCount);
```

### New code

```java
float newTotal = root.getQValueTotal(firstActionIdx) + r;
long newCount = root.getQCount(firstActionIdx) + 1;
root.setQValueTotal(firstActionIdx, newTotal);
root.setQCount(firstActionIdx, newCount);
```

### Why this changed

The important q-values are the ones stored at the root for the **first action** of the rollout.

Updating the temporary terminal node did not help the final choice.

So the copy only updates the root q-slot for that first action.

## 10. Handling the three node states separately in `search(...)`

### Old idea

```java
// mostly one search path for everything
```

### New idea

```java
if(root.getNodeState() == Node.NodeState.HAS_LEGAL_MOVES)
{
    ...
}
else if(root.getNodeState() == Node.NodeState.NO_LEGAL_MOVES_MAY_PLAY_DRAWN_CARD)
{
    ...
}
else
{
    ...
}
```

### Why this changed

The assignment says these states mean different action spaces:

- `HAS_LEGAL_MOVES`
- `NO_LEGAL_MOVES_MAY_PLAY_DRAWN_CARD`
- `NO_LEGAL_MOVES_UNRESOLVED_CARDS_PRESENT`

That means the q-values have different meanings depending on the node state.

So the copy handles them separately instead of forcing one generic rollout logic for every situation.

## 11. Explicitly modeling "play drawn card" vs "keep drawn card"

### Old code

```java
// no explicit two-branch logic here
```

### New code

```java
// Branch 1: play the drawn card
playGame.resolveMove(playMove);

// Branch 2: keep the drawn card
keepGame.resolveMove(null);
```

### Why this changed

For `NO_LEGAL_MOVES_MAY_PLAY_DRAWN_CARD`, the framework expects **two q-slots**:

- play the drawn card
- keep the drawn card

So the copy explicitly simulates both branches.

The `keep` branch uses:

```java
resolveMove(null)
```

because in this framework that means "do not play a card now".

## 12. Making `argmaxQValues(...)` safer for normal legal moves

### Old code

```java
float value = Float.NEGATIVE_INFINITY;
int valueIndex = -1;
for(int i =0; i< node.getOrderedLegalMoves().size(); i++)
{
    if(node.getQValue(i)>value)
    {
        value = node.getQValue(i);
        valueIndex = i;
    }
}
```

### New code

```java
float value = Float.NEGATIVE_INFINITY;
int valueIndex = 0;
for(int i =0; i< node.getOrderedLegalMoves().size(); i++)
{
    if(node.getQCount(i) > 0 && node.getQValue(i) > value)
    {
        value = node.getQValue(i);
        valueIndex = i;
    }
}
```

### Why this changed

Your old version could leave:

```java
valueIndex = -1
```

if nothing had been sampled yet.

Then this line would fail:

```java
node.getOrderedLegalMoves().get(valueIndex)
```

The copy fixes that by:

- starting from a safe default index
- only trusting q-values that have actually been visited

## 13. Making the drawn-card q-value comparison safer

### Old code

```java
float play = node.getQValue(Node.NoLegalMovesIdxDefaults.DrawSingleCardIdxs.PLAY_CARD_MOVE_IDX);
float keep = node.getQValue(Node.NoLegalMovesIdxDefaults.DrawSingleCardIdxs.KEEP_CARD_MOVE_IDX);
```

### New code

```java
float play = Float.NEGATIVE_INFINITY;
float keep = Float.NEGATIVE_INFINITY;

if(node.getQCount(Node.NoLegalMovesIdxDefaults.DrawSingleCardIdxs.PLAY_CARD_MOVE_IDX) > 0)
{
    play = node.getQValue(Node.NoLegalMovesIdxDefaults.DrawSingleCardIdxs.PLAY_CARD_MOVE_IDX);
}
if(node.getQCount(Node.NoLegalMovesIdxDefaults.DrawSingleCardIdxs.KEEP_CARD_MOVE_IDX) > 0)
{
    keep = node.getQValue(Node.NoLegalMovesIdxDefaults.DrawSingleCardIdxs.KEEP_CARD_MOVE_IDX);
}
```

### Why this changed

If a q-slot has never been sampled, then its q-value is not trustworthy yet.

So the copy checks the count first before using it.

## 14. Returning `null` for unresolved stacked draw cards

### Old code

```java
else
{
    Game copyGame = new Game(node.getGameView());
    return copyGame.getMove();
}
```

### New code

```java
else
{
    return null;
}
```

### Why this changed

This old branch had two issues:

1. it again used `new Game(GameView)` with no simulation agents
2. for unresolved stacked draw cards there is no voluntary card choice to make here

So the safe and simple behavior is just:

```java
return null;
```

## 15. Small non-algorithm change

### Old code

```java
public class ExpectedOutcomeAgent extends MCTSAgent
```

### New code

```java
public class ExpectedOutcomeAgentCopy extends MCTSAgent
```

### Why this changed

This was only needed so the duplicate file compiles with its filename.

It is **not** part of the algorithm itself.

## Final Summary

The copy keeps your original design idea, but fixes the framework mistakes that were making it fail:

- copied games now have valid agents
- simulated moves now belong to the correct acting player
- the hand index bug is fixed
- rollouts are bounded and fast
- rollout rewards are based on the simulated game state instead of node q-values
- special node states are handled separately
- `argmaxQValues(...)` is safer when q-values are still sparse

So the main lesson is:

Your overall structure was already close.

The real issues were mostly about:

- correct use of the UNO framework
- correct move ownership
- correct state interpretation
- safe q-value handling

## Verification Result

When the teaching copy was tested:

- it compiled
- it no longer hit the old immediate timeout behavior
- self-play progressed through normal moves instead of failing right away

