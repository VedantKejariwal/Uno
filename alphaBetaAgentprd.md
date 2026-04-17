# Technical Product Requirement Document
**Target Agent:** Cursor (GPT 5.4)
**Task:** Implement Depth Thresholded Alpha Beta Pruning Agent
**Target File:** `src/labs/rttt/agents/DepthThresholdedAlphaBetaAgent.java`

## 1. Context and Objective
We are upgrading our adversarial search agent for Recursive Tic Tac Toe. While Minimax works, it is too slow. Alpha Beta Pruning is an optimized version of Minimax that skips evaluating branches that mathematically cannot affect the final decision. 

Your objective is to complete the `alphaBeta` method in `DepthThresholdedAlphaBetaAgent.java`.

## 2. Strict Constraints (CRITICAL)
* **Student Level Code:** Write imperative, basic Java. Use standard loops and variables.
* **No Extra Comments:** Do NOT add explanatory inline comments. Leave the original starter comments exactly as they are.
* **Lecture Adherence:** You MUST strictly follow the logic from the lecture notes detailed in Section 4. Do not invent custom pruning conditions or early exits.
* **Move Ordering:** You must use the provided `MoveOrderer` class to sort the children before iterating through them.

## 3. Core Logic Requirements
The method signature might be `alphaBeta(Node node, double alpha, double beta)`. If the starter code only provides `alphaBeta(Node node)`, you must create a private helper method that includes alpha and beta parameters, initializing them to negative infinity and positive infinity respectively on the first call.

1. **Terminal Check:** If `node.isTerminal()` is true, return the `node`.
2. **Depth Threshold Check:** If `node.getDepth() >= this.getMaxDepth()`:
   * Calculate heuristic using `Heuristics.calculateHeuristicValue(node)`.
   * Set this utility on the node using `node.setUtility(...)`.
   * Return the `node`.
3. **Move Ordering:** Retrieve `node.getChildren()` and pass them through `MoveOrderer.order(...)` (or whatever the sorting method is called in `src/labs/rttt/ordering/MoveOrderer.java`) to ensure the best moves are evaluated first.
4. **Recursive Alpha Beta Step:**
   * **If Maximizing Player:**
     * Initialize `bestNode` to null and `bestUtility` to Negative Infinity.
     * Loop through the ordered children:
       * Call `alphaBeta(child, alpha, beta)`.
       * Extract the utility from the returned node.
       * Set this utility on the current `child`.
       * If utility > `bestUtility`, update `bestUtility` and `bestNode = child`.
       * If `bestUtility > beta`, return `bestNode` immediately (this is the pruning step).
       * Update `alpha = Math.max(alpha, bestUtility)`.
     * Return `bestNode`.
   * **If Minimizing Player:**
     * Initialize `bestNode` to null and `bestUtility` to Positive Infinity.
     * Loop through the ordered children:
       * Call `alphaBeta(child, alpha, beta)`.
       * Extract the utility from the returned node.
       * Set this utility on the current `child`.
       * If utility < `bestUtility`, update `bestUtility` and `bestNode = child`.
       * If `bestUtility < alpha`, return `bestNode` immediately (this is the pruning step).
       * Update `beta = Math.min(beta, bestUtility)`.
     * Return `bestNode`.

## 4. Required Classes, Methods, and Data Types

| Class | Package | Relevant Methods and Roles |
| :--- | :--- | :--- |
| `Node` | `edu.bu.labs.rttt.traversal` | `isTerminal()`, `getDepth()`, `getChildren()`, `getUtility()`, `setUtility(double)`, `isMaxNode()` |
| `Heuristics` | `src.labs.rttt.heuristics` | `calculateHeuristicValue(Node node)` returns a `double` |
| `MoveOrderer` | `src.labs.rttt.ordering` | Contains static methods to sort a list of `Node` objects. Used to optimize pruning. |
| `DepthThresholdedAlphaBetaAgent`| `src.labs.rttt.agents` | Contains `getMaxDepth()`. The file you are editing. |
| `PlayerType` | `edu.bu.labs.rttt.game` | Enums `X` and `O`. Represents the maximizing or minimizing player. |

## 5. Lecture Notes Context (Algorithm Foundation)

**Adversarial Search I: The Core Idea**
Games are massive branching trees. One player wants to maximize the score, and the other wants to minimize it. We assume both players play perfectly.

**Adversarial Search II: Practical Limits**
Expanding the whole tree takes too long. We stop the search at a specific depth limit. When stopped, we use a Heuristic function to guess the utility value of the board at that exact moment.

**Adversarial Search III: Alpha Beta Pruning and Move Ordering**
* **Alpha Beta Pruning:** This is an upgrade to Minimax. We keep track of two values: Alpha (the best score the Maximizer is guaranteed to get) and Beta (the best score the Minimizer is guaranteed to get). If we are exploring a path and find a score that is worse than what the opponent can already force us into, we immediately stop exploring that branch. This saves massive amounts of time.
* **Move Ordering:** Pruning only works well if we find the best moves early. If we check the worst moves first, we will not be able to prune anything. We use a Move Orderer to sort the children so we evaluate the most promising ones first.